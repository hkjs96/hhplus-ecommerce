import config from './config.js';

const jsonHeaders = {
  headers: { 'Content-Type': 'application/json' },
};

const defaultItems = [
  {
    productId: config.productId,
    quantity: 1,
  },
];

export function buildOrderPayload({ userId = config.userId, couponId = config.couponId, idempotencyKey, items = defaultItems } = {}) {
  const payload = {
    userId,
    items,
    idempotencyKey,
  };

  if (couponId) {
    payload.couponId = couponId;
  }

  return payload;
}

export function buildPaymentPayload({ userId = config.userId, idempotencyKey }) {
  return {
    userId,
    idempotencyKey,
  };
}

export function buildReservePayload(userId) {
  return {
    userId,
  };
}

export function randomUserId() {
  // LoadTestDataInitializer가 생성하는 범위와 일치
  // userId 1000-10999 범위에서 랜덤 선택 (extremeConcurrency 사용자)
  return Math.floor(Math.random() * 10000) + 1000;
}

export function buildChargePayload({ amount, idempotencyKey }) {
  return {
    amount,
    idempotencyKey,
  };
}

export { jsonHeaders };
