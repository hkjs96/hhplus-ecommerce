import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import config from './common/config.js';
import { buildOrderPayload, buildPaymentPayload, jsonHeaders } from './common/test-data.js';

// Custom Metrics
const rankingQueryDuration = new Trend('ranking_query_duration');
const rankingUpdateDuration = new Trend('ranking_update_duration');
const rankingAccuracy = new Rate('ranking_accuracy');
const orderSuccessRate = new Rate('order_success_rate');
const paymentSuccessRate = new Rate('payment_success_rate');
const zincrbyCalls = new Counter('zincrby_calls');

// Test Configuration
export const options = {
  scenarios: {
    // 시나리오 1: 랭킹 조회 성능 테스트 (읽기 집중)
    rankingReadTest: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },   // 워밍업
        { duration: '1m', target: 50 },    // 증가
        { duration: '2m', target: 50 },    // 유지
        { duration: '30s', target: 0 },    // 감소
      ],
      exec: 'readRanking',
      tags: { scenario: 'read' },
    },

    // 시나리오 2: 주문/결제로 인한 랭킹 업데이트 (쓰기 집중)
    rankingWriteTest: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 5 },    // 천천히 시작
        { duration: '1m', target: 15 },    // 증가
        { duration: '2m', target: 15 },    // 유지
        { duration: '30s', target: 0 },    // 감소
      ],
      exec: 'createOrderWithPayment',
      tags: { scenario: 'write' },
      startTime: '10s',  // 조회 테스트 시작 후 10초 뒤 시작
    },

    // 시나리오 3: 동시성 정확도 검증
    accuracyTest: {
      executor: 'shared-iterations',
      vus: 50,
      iterations: 50,
      maxDuration: '2m',
      exec: 'verifyAccuracy',
      tags: { scenario: 'accuracy' },
      startTime: '30s',  // 시스템 안정화 후 시작
    },
  },

  thresholds: {
    // 더 현실적인 임계값 설정
    'ranking_query_duration': [
      'p(95)<200',    // 95%가 200ms 이내
      'p(99)<500',    // 99%가 500ms 이내
    ],
    'ranking_update_duration': [
      'p(95)<1000',   // 주문+결제는 1초 이내
      'p(99)<2000',   // 99%가 2초 이내
    ],
    'ranking_accuracy': [
      'rate>0.90',    // 90% 이상 정확도
    ],
    'order_success_rate': [
      'rate>0.85',    // 85% 이상 주문 성공
    ],
    'payment_success_rate': [
      'rate>0.85',    // 85% 이상 결제 성공
    ],
    'http_req_failed{scenario:read}': [
      'rate<0.05',    // 읽기는 5% 미만 실패
    ],
    'http_req_failed{scenario:write}': [
      'rate<0.15',    // 쓰기는 15% 미만 실패 허용
    ],
    'http_req_duration{scenario:read}': [
      'p(95)<300',    // 조회는 빠르게
    ],
  },
};

// 랭킹 조회 함수
function getRanking(limit = 10) {
  const startTime = Date.now();
  const res = http.get(`${config.baseUrl}/api/products/ranking/top?limit=${limit}`);
  const duration = Date.now() - startTime;

  rankingQueryDuration.add(duration);

  const success = check(res, {
    'ranking query status 200': (r) => r.status === 200,
    'ranking has data': (r) => {
      if (r.status !== 200) return false;
      const body = r.json();
      return body && Array.isArray(body.rankings);
    },
  });

  return { res, success, duration };
}

// 주문 생성 함수
function createOrder(suffix = '') {
  const idempotencyKey = `test-${__VU}-${__ITER}-${Date.now()}-${suffix}`;
  const orderPayload = buildOrderPayload({ idempotencyKey });

  const startTime = Date.now();
  const res = http.post(
    `${config.baseUrl}/api/orders`,
    JSON.stringify(orderPayload),
    jsonHeaders
  );
  const duration = Date.now() - startTime;

  const success = res.status === 201;
  orderSuccessRate.add(success);

  check(res, {
    'order creation status 201': (r) => r.status === 201,
  });

  return { res, success, duration, orderPayload };
}

// 결제 처리 함수
function processPayment(orderId, userId, suffix = '') {
  const idempotencyKey = `payment-${__VU}-${__ITER}-${Date.now()}-${suffix}`;
  const paymentPayload = buildPaymentPayload({ userId, idempotencyKey });

  const startTime = Date.now();
  const res = http.post(
    `${config.baseUrl}/api/orders/${orderId}/payment`,
    JSON.stringify(paymentPayload),
    jsonHeaders
  );
  const duration = Date.now() - startTime;

  const success = res.status === 200;
  paymentSuccessRate.add(success);

  check(res, {
    'payment status 200': (r) => r.status === 200,
  });

  return { res, success, duration };
}

// 시나리오 1: 랭킹 조회만 집중
export function readRanking() {
  group('Ranking Read', () => {
    const { success } = getRanking(10);

    if (success) {
      sleep(0.1 + Math.random() * 0.2);  // 100-300ms 대기
    } else {
      sleep(0.5);  // 실패 시 더 길게 대기
    }
  });
}

// 시나리오 2: 주문 생성 후 결제 처리 (랭킹 업데이트 유발)
export function createOrderWithPayment() {
  group('Order and Payment Flow', () => {
    // 1. 주문 생성
    const order = createOrder('write');

    if (!order.success) {
      console.log(`Order creation failed: ${order.res.status}`);
      sleep(1);
      return;
    }

    const orderId = order.res.json('orderId');
    if (!orderId) {
      console.log('No orderId in response');
      sleep(1);
      return;
    }

    sleep(0.2);  // 짧은 대기

    // 2. 결제 처리
    const payment = processPayment(orderId, order.orderPayload.userId, 'write');
    rankingUpdateDuration.add(order.duration + payment.duration);

    if (payment.success) {
      zincrbyCalls.add(1);  // 결제 성공 시 ZINCRBY 호출됨
    }

    sleep(0.5 + Math.random() * 0.5);  // 500ms-1s 대기
  });
}

// 시나리오 3: 정확도 검증
export function verifyAccuracy() {
  group('Accuracy Verification', () => {
    // 1. 주문 생성 및 결제
    const order = createOrder('accuracy');

    if (!order.success) {
      sleep(1);
      return;
    }

    const orderId = order.res.json('orderId');
    if (!orderId) {
      sleep(1);
      return;
    }

    sleep(0.1);

    const payment = processPayment(orderId, order.orderPayload.userId, 'accuracy');

    if (!payment.success) {
      sleep(1);
      return;
    }

    // 2. 랭킹이 업데이트될 때까지 대기 (비동기 처리 고려)
    sleep(1);

    // 3. 랭킹 조회 및 검증
    const ranking = getRanking(10);

    if (ranking.success) {
      const rankings = ranking.res.json('rankings') || [];
      const targetProduct = rankings.find(r => r.productId === config.productId);

      // 상품이 랭킹에 있고 salesCount가 양수인지 확인
      const isAccurate = targetProduct && targetProduct.salesCount > 0;
      rankingAccuracy.add(isAccurate);

      if (!isAccurate) {
        console.log(`Accuracy check failed. Target product ${config.productId} not found or invalid count`);
      }
    }

    sleep(0.5);
  });
}

// 테스트 시작/종료 로그
export function setup() {
  console.log('=== Starting Improved Ranking Load Test ===');
  console.log(`Base URL: ${config.baseUrl}`);
  console.log(`Product ID: ${config.productId}`);
  console.log(`User ID: ${config.userId}`);
  return { startTime: Date.now() };
}

export function teardown(data) {
  console.log('=== Test Completed ===');
  console.log(`Total duration: ${(Date.now() - data.startTime) / 1000}s`);
}
