/**
 * K6 Load Test: 잔액 충전 - 단일 사용자 동시성 테스트
 *
 * 목적: Optimistic Lock 충돌 및 재시도 메커니즘 검증
 *
 * 테스트 시나리오:
 * - 단일 사용자(USER_ID=1)에게 집중 공격
 * - Optimistic Lock (@Version) 충돌 의도적 발생
 * - 재시도 메커니즘 작동 확인
 * - Lock Contention 분석
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ============================================================
// Custom Metrics
// ============================================================
export let errorRate = new Rate('errors');
export let successRate = new Rate('success');
export let optimisticLockConflicts = new Counter('optimistic_lock_conflicts');
export let retryAttempts = new Trend('retry_attempts');

// ============================================================
// Test Configuration
// ============================================================
export let options = {
  stages: [
    // Stage 1: Warm-up (100 VUs)
    { duration: '30s', target: 100 },
    { duration: '1m', target: 100 },

    // Stage 2: Medium Load (500 VUs)
    { duration: '30s', target: 500 },
    { duration: '1m', target: 500 },

    // Stage 3: High Load (1000 VUs)
    { duration: '30s', target: 1000 },
    { duration: '1m', target: 1000 },

    // Stage 4: Cool-down
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    // Response Time
    'http_req_duration': [
      'p(95)<1000',  // 95% < 1s
      'p(99)<2000',  // 99% < 2s
    ],

    // Error Rates
    'errors': ['rate<0.05'],  // Less than 5% error rate
    'success': ['rate>0.95'], // More than 95% success rate

    // Concurrency
    'optimistic_lock_conflicts': ['count<1000'], // Less than 1000 conflicts
  },
};

// ============================================================
// Environment Variables
// ============================================================
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USER_ID = __ENV.USER_ID || '1';  // 단일 사용자 (동시성 테스트)
const CHARGE_AMOUNT = __ENV.CHARGE_AMOUNT || '10000';

// ============================================================
// Main Test Function
// ============================================================
export default function() {
  // 단일 사용자에게 집중 공격 (Optimistic Lock 충돌 유발)
  const url = `${BASE_URL}/api/users/${USER_ID}/balance/charge`;

  const payload = JSON.stringify({
    amount: parseInt(CHARGE_AMOUNT),
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // Execute HTTP Request
  const response = http.post(url, payload, params);

  // Validate Response
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'response has balance': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.balance !== undefined;
      } catch (e) {
        return false;
      }
    },
    'balance increased correctly': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.chargedAmount === parseInt(CHARGE_AMOUNT);
      } catch (e) {
        return false;
      }
    },
  });

  // Record Metrics
  // Rate metrics require true/false for each request
  successRate.add(success);
  errorRate.add(!success);

  if (!success) {
    // Analyze Error Type (null safe)
    const responseBody = response.body || '';
    if (response.status === 409 ||
        responseBody.includes('OptimisticLock') ||
        responseBody.includes('동시')) {
      optimisticLockConflicts.add(1);
      console.log(`[VU ${__VU}, Iter ${__ITER}] Optimistic Lock Conflict detected`);
    }

    // Log error details (null safe)
    console.log(`[VU ${__VU}, Iter ${__ITER}] Error: ${response.status} - ${responseBody.substring(0, 100)}`);
  }

  // Think Time (1 second)
  sleep(1);
}

// ============================================================
// Setup Function (Optional)
// ============================================================
export function setup() {
  console.log('=== K6 Load Test: Balance Charge (Single User Concurrency) ===');
  console.log(`BASE_URL: ${BASE_URL}`);
  console.log(`USER_ID: ${USER_ID} (단일 사용자 집중 테스트)`);
  console.log(`CHARGE_AMOUNT: ${CHARGE_AMOUNT}`);
  console.log('Starting load test in 5 seconds...');
  sleep(5);
}

// ============================================================
// Teardown Function (Optional)
// ============================================================
export function teardown(data) {
  console.log('=== Load Test Completed ===');
  console.log('Check the summary report above for detailed metrics.');
}
