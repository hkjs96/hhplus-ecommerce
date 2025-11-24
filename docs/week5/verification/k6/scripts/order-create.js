/**
 * K6 Load Test: 주문 생성 (Order Create)
 *
 * 제이 코치 피드백 반영:
 * "K6 같은 도구로 100 → 500 → 1000명 단계적 부하를 걸어보세요.
 * Lock Contention이 증가하는 시점을 파악할 수 있습니다."
 *
 * 테스트 시나리오:
 * - Pessimistic Lock (SELECT FOR UPDATE) + 타임아웃 3초
 * - 단계적 부하: 100 → 500 → 1000 VUs
 * - Lock Timeout 분석
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ============================================================
// Custom Metrics
// ============================================================
export let errorRate = new Rate('errors');
export let successRate = new Rate('success');
export let pessimisticLockTimeouts = new Counter('pessimistic_lock_timeouts');
export let stockDepletions = new Counter('stock_depletions');
export let lockWaitTime = new Trend('lock_wait_time');

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
    // Response Time (Lock Timeout = 3s)
    'http_req_duration': [
      'p(95)<3500',  // 95% < 3.5s
      'p(99)<5000',  // 99% < 5s
    ],

    // Error Rates (Lock contention expected)
    'errors': ['rate<0.2'], // Less than 20% error rate
    'success': ['rate>0.8'], // More than 80% success rate

    // Lock Timeouts
    'pessimistic_lock_timeouts': ['count<200'], // Less than 200 timeouts
  },
};

// ============================================================
// Environment Variables
// ============================================================
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USER_ID = __ENV.USER_ID || '1';
const PRODUCT_ID = __ENV.PRODUCT_ID || '1';

// ============================================================
// Main Test Function
// ============================================================
export default function() {
  const url = `${BASE_URL}/api/orders`;

  const payload = JSON.stringify({
    userId: parseInt(USER_ID),
    items: [
      {
        productId: parseInt(PRODUCT_ID),
        quantity: 1,
      },
    ],
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // Execute HTTP Request
  const startTime = new Date().getTime();
  const response = http.post(url, payload, params);
  const endTime = new Date().getTime();
  const duration = endTime - startTime;

  // Validate Response
  const success = check(response, {
    'status is 200 or 201': (r) => r.status === 200 || r.status === 201,
    'response has orderId': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.orderId !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  // Record Metrics
  if (success) {
    successRate.add(1);
    lockWaitTime.add(duration);
  } else {
    errorRate.add(1);

    // Analyze Error Type
    if (response.status === 408 ||
        response.body.includes('timeout') ||
        response.body.includes('타임아웃') ||
        duration > 3000) {
      // Pessimistic Lock Timeout (3 seconds)
      pessimisticLockTimeouts.add(1);
      console.log(`[VU ${__VU}, Iter ${__ITER}] Pessimistic Lock Timeout (${duration}ms)`);

    } else if (response.body.includes('재고') ||
               response.body.includes('stock') ||
               response.status === 400) {
      // Stock Depletion
      stockDepletions.add(1);
      console.log(`[VU ${__VU}, Iter ${__ITER}] Stock Depleted`);

    } else {
      // Other errors
      console.log(`[VU ${__VU}, Iter ${__ITER}] Error: ${response.status} - ${response.body.substring(0, 100)}`);
    }
  }

  // Think Time (1 second)
  sleep(1);
}

// ============================================================
// Setup Function
// ============================================================
export function setup() {
  console.log('=== K6 Load Test: Order Create ===');
  console.log(`BASE_URL: ${BASE_URL}`);
  console.log(`USER_ID: ${USER_ID}`);
  console.log(`PRODUCT_ID: ${PRODUCT_ID}`);
  console.log('Starting load test in 5 seconds...');
  sleep(5);
}

// ============================================================
// Teardown Function
// ============================================================
export function teardown(data) {
  console.log('=== Load Test Completed ===');
  console.log('Check the summary report above for detailed metrics.');
  console.log('Analyze Lock Timeouts and Lock Wait Time trends.');
}
