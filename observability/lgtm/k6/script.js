import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 3,
  duration: "15m",
};

const BASE_URL = __ENV.BASE_URL || "http://app:8080";
const USER_ID = Number(__ENV.USER_ID || 1);
const CHARGE_AMOUNT = Number(__ENV.CHARGE_AMOUNT || 1000);

function json(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function idempotencyKey(prefix) {
  return `${prefix}-${__VU}-${__ITER}-${Date.now()}`;
}

export function setup() {
  const res = http.get(`${BASE_URL}/api/products`);
  check(res, { "products list is 200": (r) => r.status === 200 });

  const body = json(res);
  const products = body && body.products ? body.products : [];

  const normal = products.find((p) => (p.stock || 0) >= 100 && p.productId);
  const lowStock = products.find((p) => (p.stock || 0) > 0 && (p.stock || 0) <= 2 && p.productId);
  const soldOut = products.find((p) => (p.stock || 0) === 0 && p.productId);

  return {
    normalProductId: normal ? normal.productId : null,
    lowStockProductId: lowStock ? lowStock.productId : null,
    soldOutProductId: soldOut ? soldOut.productId : null,
  };
}

export default function (data) {
  const health = http.get(`${BASE_URL}/actuator/health`);
  check(health, { "health is 200": (r) => r.status === 200 });

  // POST: 잔액 충전 (내부 로직/이벤트/락 등 확인용)
  if (__ITER % 20 === 0) {
    const charge = http.post(
      `${BASE_URL}/api/users/${USER_ID}/balance/charge`,
      JSON.stringify({ amount: CHARGE_AMOUNT, idempotencyKey: idempotencyKey("charge") }),
      { headers: { "Content-Type": "application/json" } },
    );
    check(charge, { "charge is 200": (r) => r.status === 200 });
  }

  // 기본 조회 플로우
  const products = http.get(`${BASE_URL}/api/products`);
  check(products, { "products is 200": (r) => r.status === 200 });

  const user = http.get(`${BASE_URL}/api/users/${USER_ID}`);
  check(user, { "user is 200": (r) => r.status === 200 });

  const balance = http.get(`${BASE_URL}/api/users/${USER_ID}/balance`);
  check(balance, { "balance is 200": (r) => r.status === 200 });

  // 정상 플로우: 장바구니 추가 -> 주문 생성 -> 결제
  if (data.normalProductId) {
    const addCart = http.post(
      `${BASE_URL}/api/cart/items`,
      JSON.stringify({
        userId: USER_ID,
        productId: data.normalProductId,
        quantity: 1,
      }),
      { headers: { "Content-Type": "application/json" } },
    );
    check(addCart, { "add cart is 201": (r) => r.status === 201 });

    // PUT/DELETE도 섞어서 확인
    if (__ITER % 5 === 0) {
      const updateCart = http.put(
        `${BASE_URL}/api/cart/items`,
        JSON.stringify({
          userId: USER_ID,
          productId: data.normalProductId,
          quantity: 2,
        }),
        { headers: { "Content-Type": "application/json" } },
      );
      check(updateCart, { "update cart is 200": (r) => r.status === 200 });
    }

    const createOrder = http.post(
      `${BASE_URL}/api/orders`,
      JSON.stringify({
        userId: USER_ID,
        items: [{ productId: data.normalProductId, quantity: 1 }],
        couponId: null,
        idempotencyKey: idempotencyKey("order"),
      }),
      { headers: { "Content-Type": "application/json" } },
    );

    check(createOrder, { "create order is 201": (r) => r.status === 201 });

    const created = json(createOrder);
    const orderId = created && created.orderId ? created.orderId : null;

    if (orderId) {
      const pay = http.post(
        `${BASE_URL}/api/orders/${orderId}/payment`,
        JSON.stringify({
          userId: USER_ID,
          idempotencyKey: idempotencyKey("pay"),
        }),
        { headers: { "Content-Type": "application/json" } },
      );

      check(pay, { "payment is 200": (r) => r.status === 200 });

      const orders = http.get(`${BASE_URL}/api/orders?userId=${USER_ID}`);
      check(orders, { "orders is 200": (r) => r.status === 200 });
    }
  }

  // 실패 케이스: 주기적으로 일부러 실패를 만들어(4xx/5xx) 가시성 확인
  if (__ITER % 10 === 0) {
    // (1) 품절 상품 장바구니 추가 시도
    if (data.soldOutProductId) {
      const soldOutAdd = http.post(
        `${BASE_URL}/api/cart/items`,
        JSON.stringify({
          userId: USER_ID,
          productId: data.soldOutProductId,
          quantity: 1,
        }),
        { headers: { "Content-Type": "application/json" } },
      );
      check(soldOutAdd, { "sold out add is 4xx/5xx": (r) => r.status >= 400 });
    }

    // (2) 재고 초과 주문 생성 시도 (low stock 상품을 크게 요청)
    if (data.lowStockProductId) {
      const lowStockOrder = http.post(
        `${BASE_URL}/api/orders`,
        JSON.stringify({
          userId: USER_ID,
          items: [{ productId: data.lowStockProductId, quantity: 999 }],
          couponId: null,
          idempotencyKey: idempotencyKey("order-fail"),
        }),
        { headers: { "Content-Type": "application/json" } },
      );
      check(lowStockOrder, { "low stock order is 4xx/5xx": (r) => r.status >= 400 });
    }

    // (3) 존재하지 않는 사용자 조회
    const notFoundUser = http.get(`${BASE_URL}/api/users/999999999`);
    check(notFoundUser, { "user 404": (r) => r.status === 404 });
  }

  // 과도하지 않게 일정한 트래픽 유지
  sleep(0.5);
}
