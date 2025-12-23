import http from 'k6/http';
import { check } from 'k6';
import config from './config.js';
import { buildChargePayload, jsonHeaders } from './test-data.js';

export function chargeBalance({ userId = config.userId, amount = config.balanceChargeAmount } = {}) {
  const payload = buildChargePayload({
    amount,
    idempotencyKey: `loadtest-balance-${userId}-${Date.now()}`,
  });

  const res = http.post(
    `${config.baseUrl}/api/users/${userId}/balance/charge`,
    JSON.stringify(payload),
    jsonHeaders
  );

  check(res, {
    'initial balance charged': (r) => r.status === 200,
  });

  return res;
}

export function ensureCouponOwnership({ userId = config.userId, couponId = config.couponId } = {}) {
  const payload = {
    userId,
    couponId,
  };

  const res = http.post(
    `${config.baseUrl}/api/coupons/${couponId}/issue`,
    JSON.stringify(payload),
    jsonHeaders
  );

  check(res, {
    'coupon issued or already owned': (r) => [200, 201, 409].includes(r.status),
  });

  return res;
}
