import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://gateway:8080';

export const options = {
  scenarios: {
    default: {
      executor: 'constant-vus',
      vus: 3,
      duration: '15m',
      gracefulStop: '30s',
    },
  },
};

function jsonHeaders() {
  return { headers: { 'Content-Type': 'application/json' } };
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

export default function () {
  // Keep it simple: generate steady traces across gateway -> order -> payment -> db.
  const userId = 1;
  const productId = randomInt(2, 7);

  // Top-up user balance periodically to avoid "insufficient balance" turning into 5xx noise.
  // This keeps most traffic happy-path and makes service graph easier to read.
  if (__ITER % 20 === 0) {
    const chargeRes = http.post(
      `${BASE_URL}/api/users/${userId}/balance/charge`,
      JSON.stringify({
        amount: 1000000000,
        idempotencyKey: `k6-charge-vu${__VU}-iter${__ITER}-${Date.now()}`,
      }),
      jsonHeaders()
    );
    check(chargeRes, { 'balance charge returns 200/201': (r) => r.status >= 200 && r.status < 300 });
  }

  // 1) Happy-path: create+pay in one call
  const createAndPayRes = http.post(
    `${BASE_URL}/api/orders/complete`,
    JSON.stringify({
      userId,
      items: [{ productId, quantity: 1 }],
      couponId: null,
    }),
    jsonHeaders()
  );

  check(createAndPayRes, {
    'complete order returns 201': (r) => r.status === 201,
  });

  // 2) Small failure mix: invalid user (expected 4xx) to show errors without overwhelming the graphs.
  if (Math.random() < 0.02) {
    const invalidUserRes = http.post(
      `${BASE_URL}/api/orders/complete`,
      JSON.stringify({
        userId: 99999999,
        items: [{ productId, quantity: 1 }],
        couponId: null,
      }),
      jsonHeaders()
    );
    check(invalidUserRes, {
      'invalid user returns 4xx/5xx': (r) => r.status >= 400,
    });
  }

  // 3) A couple of reads to populate route dropdown + show “gateway -> order-service” traces.
  if (Math.random() < 0.3) {
    const ordersRes = http.get(`${BASE_URL}/api/orders?userId=${userId}`);
    check(ordersRes, { 'get orders returns 200': (r) => r.status === 200 });
  }

  sleep(1);
}
