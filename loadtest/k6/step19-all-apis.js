import http from 'k6/http';
import { check, group } from 'k6';
import { sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_BASE_URL = `${BASE_URL}/api`;

// DataInitializer 기준 (src/main/.../DataInitializer.java)
const USER_ID_MIN = Number(__ENV.USER_ID_MIN || 1);
const USER_ID_MAX = Number(__ENV.USER_ID_MAX || 153);
const PRODUCT_ID_MIN = Number(__ENV.PRODUCT_ID_MIN || 1);
const PRODUCT_ID_MAX = Number(__ENV.PRODUCT_ID_MAX || 21);
const MIN_STOCK_FOR_STABLE_FLOW = Number(__ENV.MIN_STOCK_FOR_STABLE_FLOW || 1000);

const DEFAULT_HEADERS = {
  'Content-Type': 'application/json',
};

const DEBUG = __ENV.DEBUG === '1';

export const options = {
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1500'],
  },
  scenarios: {
    allApisRamp: {
      executor: 'ramping-vus',
      startVUs: Number(__ENV.START_VUS || 1),
      stages: [
        { duration: __ENV.STAGE_1_DURATION || '30s', target: Number(__ENV.STAGE_1_VUS || 1) },
        { duration: __ENV.STAGE_2_DURATION || '60s', target: Number(__ENV.STAGE_2_VUS || 3) },
        { duration: __ENV.STAGE_3_DURATION || '60s', target: Number(__ENV.STAGE_3_VUS || 5) },
        { duration: __ENV.STAGE_4_DURATION || '30s', target: Number(__ENV.STAGE_4_VUS || 1) },
      ],
      gracefulRampDown: __ENV.GRACEFUL_RAMP_DOWN || '30s',
    },
  },
};

function randomIntInclusive(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function pickStableProductId() {
  // DB가 이미 초기화된 환경에서는 Product ID가 고정이라는 보장이 없어서,
  // 우선은 범위 랜덤으로 fallback 한다. (가능하면 /api/products 응답에서 선별)
  return randomIntInclusive(PRODUCT_ID_MIN, PRODUCT_ID_MAX);
}

function perVuUserId() {
  // DataInitializer: 1~153 유저 생성 (1~3 기본 + 4~153 테스트)
  // VU마다 사용자 분리해서 cart/order 충돌을 줄인다.
  const userRange = USER_ID_MAX - USER_ID_MIN + 1;
  return USER_ID_MIN + ((__VU - 1) % userRange);
}

function idempotencyKey(prefix) {
  return `${prefix}-${__VU}-${__ITER}-${Date.now()}`;
}

function maybe(probability) {
  return Math.random() < probability;
}

function selectStableProductIdFromList(products) {
  if (!products || !products.length) return null;

  const stable = products.filter((p) => typeof p.stock === 'number' && p.stock >= MIN_STOCK_FOR_STABLE_FLOW);
  const candidate = stable.length ? stable : products.filter((p) => typeof p.stock === 'number' && p.stock > 0);
  if (!candidate.length) return null;

  const picked = candidate[randomIntInclusive(0, candidate.length - 1)];
  return picked.productId || picked.id || null;
}

function debugResponse(label, response) {
  if (!DEBUG) return;
  const body = typeof response.body === 'string' ? response.body.slice(0, 500) : '';
  // eslint-disable-next-line no-console
  console.error(`[DEBUG] ${label}: status=${response.status} body=${body}`);
}

export default function () {
  const userId = perVuUserId();
  let productId = pickStableProductId();
  const quantity = 1;

  group('00. health (non-api)', () => {
    const res = http.get(`${BASE_URL}/actuator/health`);
    check(res, { 'health: 200': (r) => r.status === 200 });
  });

  group('01. products (read)', () => {
    const listRes = http.get(`${API_BASE_URL}/products`);
    check(listRes, { 'products list: 200': (r) => r.status === 200 });

    if (listRes.status === 200) {
      const payload = listRes.json();
      if (payload && payload.products) {
        const selected = selectStableProductIdFromList(payload.products);
        if (selected) productId = selected;
      }
    }

    const detailRes = http.get(`${API_BASE_URL}/products/${productId}`);
    check(detailRes, { 'product detail: 200': (r) => r.status === 200 });

    const topRes = http.get(`${API_BASE_URL}/products/top`);
    check(topRes, { 'products top: 200': (r) => r.status === 200 });

    const rankingTopRes = http.get(`${API_BASE_URL}/products/ranking/top`);
    check(rankingTopRes, { 'ranking top: 200': (r) => r.status === 200 });

    const rankingProductRes = http.get(`${API_BASE_URL}/products/ranking/product/${productId}`);
    check(rankingProductRes, { 'ranking by product: 200': (r) => r.status === 200 });
  });

  group('02. user (read/write)', () => {
    const userRes = http.get(`${API_BASE_URL}/users/${userId}`);
    check(userRes, { 'user: 200': (r) => r.status === 200 });

    const balanceRes = http.get(`${API_BASE_URL}/users/${userId}/balance`);
    check(balanceRes, { 'balance: 200': (r) => r.status === 200 });

    // amount는 작은 값으로 유지 (정확한 성능 측정에서 불필요한 상태 변화 최소화)
    const chargeBody = JSON.stringify({
      amount: 1000,
      idempotencyKey: idempotencyKey('charge'),
    });
    const chargeRes = http.post(`${API_BASE_URL}/users/${userId}/balance/charge`, chargeBody, {
      headers: DEFAULT_HEADERS,
    });
    check(chargeRes, { 'balance charge: 200': (r) => r.status === 200 });
  });

  group('03. cart (write/read/write/delete)', () => {
    const cartRes = http.get(`${API_BASE_URL}/cart?userId=${userId}`);
    check(cartRes, { 'cart get: 200': (r) => r.status === 200 });

    // 장바구니 write는 DB에 영향을 크게 주므로 (과제/측정 목적상) 기본값은 "낮은 확률"로만 수행한다.
    // 필요하면 CART_WRITE_PROB를 올려서 강하게 테스트한다.
    if (!maybe(Number(__ENV.CART_WRITE_PROB || 0.3))) return;

    const addBody = JSON.stringify({ userId, productId, quantity });
    const addRes = http.post(`${API_BASE_URL}/cart/items`, addBody, { headers: DEFAULT_HEADERS });
    if (addRes.status !== 201) debugResponse('cart add', addRes);
    check(addRes, { 'cart add: 201': (r) => r.status === 201 });
    if (addRes.status !== 201) return;

    const updateBody = JSON.stringify({ userId, productId, quantity: 2 });
    const updateRes = http.put(`${API_BASE_URL}/cart/items`, updateBody, { headers: DEFAULT_HEADERS });
    if (updateRes.status !== 200) debugResponse('cart update', updateRes);
    check(updateRes, { 'cart update: 200': (r) => r.status === 200 });
    if (updateRes.status !== 200) return;

    const deleteBody = JSON.stringify({ userId, productId });
    const deleteRes = http.del(`${API_BASE_URL}/cart/items`, deleteBody, { headers: DEFAULT_HEADERS });
    if (deleteRes.status !== 200) debugResponse('cart delete', deleteRes);
    check(deleteRes, { 'cart delete: 200': (r) => r.status === 200 });
  });

  group('04. coupons (read + optional write)', () => {
    const listRes = http.get(`${API_BASE_URL}/users/${userId}/coupons`);
    check(listRes, { 'user coupons: 200': (r) => r.status === 200 });

    // 쿠폰은 소진될 수 있으니, 기본값은 "낮은 확률"로만 실행한다.
    // reserve: Redis INCR 기반 (선착순)
    if (maybe(Number(__ENV.COUPON_RESERVE_PROB || 0.05))) {
      const reserveRes = http.post(
        `${API_BASE_URL}/coupons/1/reserve`,
        JSON.stringify({ userId: USER_ID_MIN + ((__VU - 1 + __ITER) % (USER_ID_MAX - USER_ID_MIN + 1)) }),
        { headers: DEFAULT_HEADERS },
      );
      check(reserveRes, {
        'coupon reserve: 200|4xx': (r) => r.status === 200 || (r.status >= 400 && r.status < 500),
      });
    }

    // issue: DB 기반 발급
    if (maybe(Number(__ENV.COUPON_ISSUE_PROB || 0.02))) {
      const issueRes = http.post(
        `${API_BASE_URL}/coupons/2/issue`,
        JSON.stringify({ userId: USER_ID_MIN + ((__VU - 1 + __ITER) % (USER_ID_MAX - USER_ID_MIN + 1)) }),
        { headers: DEFAULT_HEADERS },
      );
      check(issueRes, {
        'coupon issue: 200|4xx': (r) => r.status === 200 || (r.status >= 400 && r.status < 500),
      });
    }
  });

  group('05. orders (read + optional write)', () => {
    const listRes = http.get(`${API_BASE_URL}/orders?userId=${userId}`);
    check(listRes, { 'orders list: 200': (r) => r.status === 200 });

    // 주문 생성/결제는 상태 변화를 크게 만들 수 있어 기본값은 낮은 확률로만 실행한다.
    if (maybe(Number(__ENV.ORDER_CREATE_PROB || 0.05))) {
      const createBody = JSON.stringify({
        userId,
        items: [{ productId, quantity: 1 }],
        couponId: null,
        idempotencyKey: idempotencyKey('create-order'),
      });
      const createRes = http.post(`${API_BASE_URL}/orders`, createBody, { headers: DEFAULT_HEADERS });
      check(createRes, { 'order create: 201|4xx': (r) => r.status === 201 || (r.status >= 400 && r.status < 500) });

      if (createRes.status === 201) {
        const payload = createRes.json();
        const orderId = payload && payload.orderId;
        if (orderId) {
          const payBody = JSON.stringify({ userId, idempotencyKey: idempotencyKey('payment') });
          const payRes = http.post(`${API_BASE_URL}/orders/${orderId}/payment`, payBody, { headers: DEFAULT_HEADERS });
          check(payRes, { 'order payment: 200|4xx': (r) => r.status === 200 || (r.status >= 400 && r.status < 500) });
        }
      }
    }

    if (maybe(Number(__ENV.ORDER_COMPLETE_PROB || 0.02))) {
      const completeBody = JSON.stringify({
        userId,
        items: [{ productId, quantity: 1 }],
        couponId: null,
      });
      const completeRes = http.post(`${API_BASE_URL}/orders/complete`, completeBody, { headers: DEFAULT_HEADERS });
      check(completeRes, {
        'order complete: 201|4xx': (r) => r.status === 201 || (r.status >= 400 && r.status < 500),
      });
    }
  });

  // 과제 가이드 기준으로 "인위적 sleep 없이" 진행하려면 THINK_TIME_SEC=0 (기본값)로 둔다.
  const thinkTimeSec = Number(__ENV.THINK_TIME_SEC || 0);
  if (thinkTimeSec > 0) sleep(thinkTimeSec);
}
