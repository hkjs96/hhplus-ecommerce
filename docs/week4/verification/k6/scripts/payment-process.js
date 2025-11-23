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
const USER_ID = __ENV.USER_ID || '1';

// ============================================================
// Main Test Function
// ============================================================
export default function() {
  // Step 1: Create Order
  const orderId = createOrder();

  if (!orderId) {
    errorRate.add(1);
    console.log(`[VU ${__VU}, Iter ${__ITER}] Failed to create order`);
    sleep(1);
    return;
  }

  // Step 2: Process Payment with Idempotency Key
  const idempotencyKey = randomString(32);
  const paymentResults = processPaymentWithRetries(orderId, idempotencyKey);

  // Step 3: Verify Idempotency (Only 1 success, others should fail)
  const successCount = paymentResults.filter(r => r === 'SUCCESS').length;
  const conflictCount = paymentResults.filter(r => r === 'CONFLICT').length;

  if (successCount === 1 && conflictCount === 2) {
    // Perfect! Idempotency works correctly
    successRate.add(1);
    idempotencyVerificationSuccess.add(1);
    duplicatePaymentsPrevented.add(conflictCount);
    console.log(`[VU ${__VU}, Iter ${__ITER}] ✅ Idempotency verified: 1 success, 2 prevented`);

  } else {
    // Idempotency failed!
    errorRate.add(1);
    idempotencyVerificationFailure.add(1);
    console.log(`[VU ${__VU}, Iter ${__ITER}] ❌ Idempotency failed: ${successCount} successes, ${conflictCount} conflicts`);
  }

  // Think Time
  sleep(1);
}

// ============================================================
// Helper Functions
// ============================================================

/**
 * 주문 생성
 */
function createOrder() {
  const url = `${BASE_URL}/api/orders`;

  const payload = JSON.stringify({
    userId: parseInt(USER_ID),
    items: [
      {
        productId: 1,
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
function processPaymentWithRetries(orderId, idempotencyKey) {
  const results = [];

  for (let i = 0; i < 3; i++) {
    const result = processPayment(orderId, idempotencyKey, i + 1);
    results.push(result);

    // Small delay between retries (100ms)
    sleep(0.1);
  }

  return results;
}

/**
 * 결제 처리
 */
function processPayment(orderId, idempotencyKey, attemptNumber) {
  const url = `${BASE_URL}/api/orders/${orderId}/payment`;

  const payload = JSON.stringify({
    userId: parseInt(USER_ID),
    amount: 50000,
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
    return 'SUCCESS';

  } else if (response.status === 409) {
    // Idempotency Conflict (Expected for duplicate requests)
    idempotencyConflicts.add(1);
    console.log(`[VU ${__VU}, Iter ${__ITER}, Attempt ${attemptNumber}] Payment CONFLICT (Duplicate prevented)`);
    return 'CONFLICT';

  } else {
    // Other errors
    console.log(`[VU ${__VU}, Iter ${__ITER}, Attempt ${attemptNumber}] Payment ERROR: ${response.status}`);
    return 'ERROR';
  }
}

// ============================================================
// Setup Function
// ============================================================
export function setup() {
  console.log('=== K6 Load Test: Payment Process ===');
  console.log(`BASE_URL: ${BASE_URL}`);
  console.log(`USER_ID: ${USER_ID}`);
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
