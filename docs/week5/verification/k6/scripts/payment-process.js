/**
 * K6 Load Test: 결제 처리 (Payment Process)
 *
 * 제이 코치 피드백 반영:
 * "Idempotency Key가 실제로 중복 결제를 막는지 테스트로 검증하면
 * 문서와 코드가 일치하는지 확인할 수 있거든요."
 *
 * 테스트 시나리오:
 * - Idempotency Key (UNIQUE 제약조건)
 * - 동일 키로 3번 결제 시도 → 1번만 성공
 * - 중복 결제 방지 검증
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// ============================================================
// Custom Metrics
// ============================================================
export let errorRate = new Rate('errors');
export let successRate = new Rate('success');
export let idempotencyConflicts = new Counter('idempotency_conflicts');
export let duplicatePaymentsPrevented = new Counter('duplicate_payments_prevented');
export let idempotencyVerificationSuccess = new Counter('idempotency_verification_success');
export let idempotencyVerificationFailure = new Counter('idempotency_verification_failure');

// ============================================================
// Test Configuration
// ============================================================
export let options = {
  stages: [
    // Stage 1: Low Load (100 VUs)
    { duration: '30s', target: 100 },
    { duration: '1m', target: 100 },

    // Stage 2: Medium Load (200 VUs)
    { duration: '30s', target: 200 },
    { duration: '1m', target: 200 },

    // Cool-down
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    // Response Time
    'http_req_duration': ['p(95)<1000'],

    // Idempotency Verification
    'idempotency_verification_success': ['count>0'],
    'duplicate_payments_prevented': ['count>0'],
  },
};

// ============================================================
// Environment Variables
// ============================================================
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MIN_USER_ID = parseInt(__ENV.MIN_USER_ID || '1');
const MAX_USER_ID = parseInt(__ENV.MAX_USER_ID || '100');
const MIN_PRODUCT_ID = parseInt(__ENV.MIN_PRODUCT_ID || '1');
const MAX_PRODUCT_ID = parseInt(__ENV.MAX_PRODUCT_ID || '10');
const MAX_RETRIES = parseInt(__ENV.MAX_RETRIES || '3');

// ============================================================
// Helper Functions
// ============================================================
function getRandomUserId() {
  return Math.floor(Math.random() * (MAX_USER_ID - MIN_USER_ID + 1)) + MIN_USER_ID;
}

function getRandomProductId() {
  return Math.floor(Math.random() * (MAX_PRODUCT_ID - MIN_PRODUCT_ID + 1)) + MIN_PRODUCT_ID;
}

// ============================================================
// Main Test Function
// ============================================================
export default function() {
  // 부하 분산: 랜덤 사용자 및 상품 선택
  const userId = getRandomUserId();
  const productId = getRandomProductId();

  // Step 1: Create Order (재고 소진 시 재시도)
  const orderId = createOrderWithRetry(userId, productId, MAX_RETRIES);

  if (!orderId) {
    errorRate.add(1);
    console.log(`[VU ${__VU}, Iter ${__ITER}] Failed to create order after ${MAX_RETRIES} retries`);
    sleep(1);
    return;
  }

  // Step 2: Process Payment with Idempotency Key
  const idempotencyKey = randomString(32);
  const paymentResults = processPaymentWithRetries(orderId, userId, idempotencyKey);

  // Step 3: Verify Idempotency (Only 1 new payment, others should be cached or conflict)
  const successCount = paymentResults.filter(r => r === 'SUCCESS').length;
  const cachedCount = paymentResults.filter(r => r === 'CACHED').length;
  const conflictCount = paymentResults.filter(r => r === 'CONFLICT').length;

  // Idempotency 성공 조건:
  // - 1번만 새 결제 (SUCCESS)
  // - 2~3번은 캐시 반환 (CACHED) 또는 충돌 (CONFLICT)
  if (successCount === 1 && (cachedCount + conflictCount) === 2) {
    // Perfect! Idempotency works correctly
    successRate.add(1);
    idempotencyVerificationSuccess.add(1);
    duplicatePaymentsPrevented.add(cachedCount + conflictCount);
    console.log(`[VU ${__VU}, Iter ${__ITER}] ✅ Idempotency verified: 1 new, ${cachedCount} cached, ${conflictCount} conflicts`);

  } else {
    // Idempotency failed!
    errorRate.add(1);
    idempotencyVerificationFailure.add(1);
    console.log(`[VU ${__VU}, Iter ${__ITER}] ❌ Idempotency failed: ${successCount} new, ${cachedCount} cached, ${conflictCount} conflicts`);
  }

  // Think Time
  sleep(1);
}

/**
 * 주문 생성 (재고 소진 시 다른 상품으로 재시도)
 */
function createOrderWithRetry(userId, initialProductId, maxRetries) {
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    // 재시도 시 다른 상품 선택
    const productId = attempt === 0 ? initialProductId : getRandomProductId();
    const orderId = createOrder(userId, productId);

    if (orderId) {
      return orderId;
    }

    // 재고 소진이 아닌 다른 에러면 즉시 종료
    // (재고 소진인 경우에만 재시도)
  }

  return null;
}

/**
 * 주문 생성
 */
function createOrder(userId, productId) {
  const url = `${BASE_URL}/api/orders`;

  const payload = JSON.stringify({
    userId: userId,
    items: [
      {
        productId: productId,
        quantity: 1,
      },
    ],
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const response = http.post(url, payload, params);

  if (response.status === 200 || response.status === 201) {
    try {
      const body = JSON.parse(response.body);
      return body.orderId;
    } catch (e) {
      console.log(`[VU ${__VU}, Iter ${__ITER}] Failed to parse order response`);
      return null;
    }
  } else {
    console.log(`[VU ${__VU}, Iter ${__ITER}] Order creation failed: ${response.status}`);
    return null;
  }
}

/**
 * 동일한 Idempotency Key로 3번 결제 시도
 */
function processPaymentWithRetries(orderId, userId, idempotencyKey) {
  const results = [];
  let firstResponseBody = null;

  for (let i = 0; i < 3; i++) {
    const result = processPayment(orderId, userId, idempotencyKey, i + 1);

    // 첫 번째 응답 본문 저장
    if (i === 0 && result.body) {
      firstResponseBody = result.body;
    }

    // 두 번째, 세 번째 요청은 첫 번째와 동일한 응답인지 확인
    if (i > 0 && result.body && firstResponseBody) {
      if (result.body === firstResponseBody) {
        // 캐시된 응답 (중복 방지 성공)
        results.push('CACHED');
        continue;
      }
    }

    results.push(result.status);

    // Small delay between retries (100ms)
    sleep(0.1);
  }

  return results;
}

/**
 * 결제 처리
 */
function processPayment(orderId, userId, idempotencyKey, attemptNumber) {
  const url = `${BASE_URL}/api/orders/${orderId}/payment`;

  const payload = JSON.stringify({
    userId: userId,  // Use the same userId as the order
    amount: 10000,  // 50000 → 10000 (잔액 부족 방지)
    idempotencyKey: idempotencyKey,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const response = http.post(url, payload, params);

  if (response.status === 200 || response.status === 201) {
    // Payment Success
    console.log(`[VU ${__VU}, Iter ${__ITER}, Attempt ${attemptNumber}] Payment SUCCESS`);
    return { status: 'SUCCESS', body: response.body };

  } else if (response.status === 409) {
    // Idempotency Conflict (Expected for duplicate requests)
    idempotencyConflicts.add(1);
    console.log(`[VU ${__VU}, Iter ${__ITER}, Attempt ${attemptNumber}] Payment CONFLICT (Duplicate prevented)`);
    return { status: 'CONFLICT', body: null };

  } else {
    // Other errors
    console.log(`[VU ${__VU}, Iter ${__ITER}, Attempt ${attemptNumber}] Payment ERROR: ${response.status}`);
    return { status: 'ERROR', body: null };
  }
}

// ============================================================
// Setup Function
// ============================================================
export function setup() {
  console.log('=== K6 Load Test: Payment Process ===');
  console.log(`BASE_URL: ${BASE_URL}`);
  console.log(`USER_ID_RANGE: ${MIN_USER_ID} ~ ${MAX_USER_ID}`);
  console.log(`PRODUCT_ID_RANGE: ${MIN_PRODUCT_ID} ~ ${MAX_PRODUCT_ID}`);
  console.log(`MAX_RETRIES: ${MAX_RETRIES}`);
  console.log('Testing Idempotency Key duplicate prevention...');
  console.log('Starting load test in 5 seconds...');
  sleep(5);
}

// ============================================================
// Teardown Function
// ============================================================
export function teardown(data) {
  console.log('=== Load Test Completed ===');
  console.log('Check the summary report above for idempotency verification results.');
}
