import http from 'k6/http';
import { check, sleep } from 'k6';
import config from './common/config.js';
import {
  rankingQueryDuration,
  rankingUpdateDuration,
  rankingAccuracyRate,
  reservationDuration,
  issuanceDuration,
  zincrbyOperationCount,
} from './common/metrics.js';
import {
  buildChargePayload,
  buildOrderPayload,
  buildPaymentPayload,
  buildReservePayload,
  jsonHeaders,
  randomUserId,
} from './common/test-data.js';

export const options = {
  scenarios: {
    realistic_user_flow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 50 },
        { duration: '3m', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '2m', target: 100 },
        { duration: '1m', target: 0 },
      ],
      exec: 'realisticUserFlow',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed: ['rate<0.1'],
  },
};

export function setup() {
  const res = http.post(
    `${config.baseUrl}/api/users/${config.userId}/balance/charge`,
    JSON.stringify(
      buildChargePayload({
        amount: config.balanceChargeAmount,
        idempotencyKey: `loadtest-balance-${Date.now()}`,
      })
    ),
    jsonHeaders
  );

  check(res, {
    'initial balance charged': (r) => r.status === 200,
  });
}

const rankingUrl = `${config.baseUrl}/api/products/ranking/top?limit=5`;
const reserveUrl = `${config.baseUrl}/api/coupons/${config.couponId}/reserve`;

function recordRankingMetrics(res) {
  rankingQueryDuration.add(res.timings.duration);
  rankingUpdateDuration.add(res.timings.duration);
  rankingAccuracyRate.add(res.status === 200);
}

function sendOrderWithPayment(userId) {
  const idempotencyKey = `integration-order-${__VU}-${__ITER}-${Date.now()}`;
  const orderPayload = buildOrderPayload({ userId, idempotencyKey, couponId: config.couponId });
  const orderRes = http.post(`${config.baseUrl}/api/orders`, JSON.stringify(orderPayload), jsonHeaders);
  zincrbyOperationCount.add(orderRes.status === 201 ? 1 : 0);
  check(orderRes, { 'integration order created': (r) => r.status === 201 });

  if (orderRes.status !== 201) {
    return null;
  }

  const paymentPayload = buildPaymentPayload({
    userId,
    idempotencyKey: `${idempotencyKey}-payment`,
  });
  const paymentRes = http.post(`${config.baseUrl}/api/orders/${orderRes.json('orderId')}/payment`, JSON.stringify(paymentPayload), jsonHeaders);
  check(paymentRes, { 'integration payment': (r) => r.status === 200 });
  return orderRes.json('orderId');
}

export function realisticUserFlow() {
  const userId = randomUserId();
  const initialRanking = http.get(rankingUrl);
  recordRankingMetrics(initialRanking);

  const reserveRes = http.post(reserveUrl, JSON.stringify(buildReservePayload(userId)), jsonHeaders);
  reservationDuration.add(reserveRes.timings.duration);
  check(reserveRes, { 'integration reserve status': (r) => r.status === 200 || r.status === 409 });

  const issuedId = sendOrderWithPayment(userId);
  if (issuedId) {
    sleep(3);
    const couponListRes = http.get(`${config.baseUrl}/api/users/${userId}/coupons?status=AVAILABLE`);
    issuanceDuration.add(couponListRes.timings.duration);
    check(couponListRes, {
      'integration coupon list': (r) => r.status === 200,
    });
  }

  const finalRanking = http.get(rankingUrl);
  recordRankingMetrics(finalRanking);
  sleep(1);
}
