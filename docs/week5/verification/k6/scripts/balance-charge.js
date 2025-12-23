/**
 * K6 Load Test: 잔액 충전 (Balance Charge)
 *
 * 제이 코치 피드백 반영:
 * "K6 같은 도구로 100 → 500 → 1000명 단계적 부하를 걸어보세요.
 * Lock Contention이 증가하는 시점을 파악할 수 있습니다."
 *
 * 테스트 시나리오:
 * - 분산락 (Redis) + Optimistic Lock (@Version) + 멱등성 보장
 * - 단계적 부하: 100 → 500 → 1000 VUs
 * - 다중 사용자 분산 (USER_COUNT=100)
 * - Lock Contention 분석
 * - 멱등성 키로 중복 충전 방지 (각 요청마다 고유 UUID)
 *
 * 3중 방어:
 * 1. 분산락 (balance:user:{userId}) - 인스턴스 간 동시성 제어
 * 2. Optimistic Lock (@Version) - DB 레벨 Lost Update 방지
 * 3. 멱등성 키 (idempotencyKey) - 중복 요청 방지
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

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
const USER_COUNT = parseInt(__ENV.USER_COUNT) || 100;  // ✅ 사용자 100명 (변경 가능)
const CHARGE_AMOUNT = __ENV.CHARGE_AMOUNT || '10000';

// ============================================================
// Main Test Function
// ============================================================
export default function() {
  // ✅ VU 번호를 사용하여 사용자 분산 (1~100)
  const userId = (__VU % USER_COUNT) + 1;
  const url = `${BASE_URL}/api/users/${userId}/balance/charge`;

  // ✅ 멱등성 키 생성 (각 요청마다 고유한 UUID)
  const payload = JSON.stringify({
    amount: parseInt(CHARGE_AMOUNT),
    idempotencyKey: uuidv4(),  // ✅ 필수: 중복 충전 방지
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
  if (success) {
    successRate.add(1);
  } else {
    errorRate.add(1);

    // Analyze Error Type
    if (response.status === 409 ||
        response.body.includes('OptimisticLock') ||
        response.body.includes('동시')) {
      optimisticLockConflicts.add(1);
      console.log(`[VU ${__VU}, Iter ${__ITER}] Optimistic Lock Conflict detected`);
    }

    // Log error details
    console.log(`[VU ${__VU}, Iter ${__ITER}] Error: ${response.status} - ${response.body.substring(0, 100)}`);
  }

  // Think Time (1 second)
  sleep(1);
}

// ============================================================
// Setup Function (Optional)
// ============================================================
export function setup() {
  console.log('=== K6 Load Test: Balance Charge ===');
  console.log(`BASE_URL: ${BASE_URL}`);
  console.log(`USER_COUNT: ${USER_COUNT} users (distributed load)`);
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
