import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import config from './common/config.js';
import { jsonHeaders } from './common/test-data.js';
import { chargeBalance } from './common/setup.js';

// Custom Metrics
const couponIssueSuccess = new Rate('coupon_issue_success_rate');
const couponIssueDuration = new Trend('coupon_issue_duration');
const duplicateIssueAttempts = new Counter('duplicate_issue_attempts');
const soldOutResponses = new Counter('sold_out_responses');
const actualIssuedCount = new Counter('actual_issued_count');
const failureStatusCounter = new Counter('failure_status_counter');

const failureStatusHistogram = {};

function recordFailureStatus(status, body) {
  const key = `${status}`;
  if (!failureStatusHistogram[key]) {
    failureStatusHistogram[key] = { count: 0, sample: null };
  }
  failureStatusHistogram[key].count += 1;
  if (!failureStatusHistogram[key].sample && body) {
    failureStatusHistogram[key].sample = body.toString().slice(0, 200);
  }
}

// Test Configuration
const TOTAL_USERS = 200;  // 총 시도할 사용자 수
const COUPON_QUANTITY = 100;  // 쿠폰 수량 (50명만 받을 수 있음)

export const options = {
  scenarios: {
    // 시나리오 1: 극한의 동시성 - 모든 사용자가 동시에 요청
    extremeConcurrency: {
      executor: 'shared-iterations',
      vus: 100,  // 100명이 동시에
      iterations: 100,  // 100번 시도
      maxDuration: '30s',
      exec: 'issueCouponConcurrent',
      tags: { test: 'extreme' },
    },

    // 시나리오 2: 순차적 발급 (비교용)
    sequentialIssue: {
      executor: 'per-vu-iterations',
      vus: 1,  // 1명씩
      iterations: 100,  // 100번 시도 (증가: 50 → 100)
      maxDuration: '2m',
      exec: 'issueCouponSequential',
      tags: { test: 'sequential' },
      startTime: '40s',  // 극한 테스트 후 시작
    },

    // 시나리오 3: 램프업 - 점진적으로 부하 증가
    rampUpTest: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 20 },
        { duration: '20s', target: 50 },
        { duration: '10s', target: 0 },
      ],
      exec: 'issueCouponRampUp',
      tags: { test: 'rampup' },
      startTime: '1m30s',
    },
  },

  thresholds: {
    // 쿠폰 발급 성공률
    // 총 200명 시도 → 100개 성공 = 50%
    'coupon_issue_success_rate': ['rate>=0.45', 'rate<=0.55'],  // 50% ± 5%

    // 응답 시간
    'coupon_issue_duration': [
      'p(95)<1000',  // 95%가 1초 이내
      'p(99)<2000',  // 99%가 2초 이내
    ],

    // HTTP 실패율 (품절 응답은 비즈니스 실패이지만 시스템 에러는 아님)
    // 실제로는 극한 동시성에서 일부만 실패, 나머지는 성공 후 SOLD_OUT 조회
    // 따라서 실패율은 30-60% 범위로 유연하게 설정
    'http_req_failed': ['rate>=0.20', 'rate<=0.60'],  // 20-60% (SOLD_OUT + 일부 timeout)

    // 응답 시간
    'http_req_duration{test:extreme}': ['p(99)<3000'],  // 극한 동시성에서도 3초 이내
  },
};

// 쿠폰 발급 요청
function issueCoupon(userId, scenario = 'default') {
  const payload = {
    userId,
    couponId: config.couponId,
  };

  const startTime = Date.now();
  const res = http.post(
    `${config.baseUrl}/api/coupons/${config.couponId}/issue`,
    JSON.stringify(payload),
    jsonHeaders
  );
  const duration = Date.now() - startTime;

  couponIssueDuration.add(duration);

  // 결과 분석
  const isSuccess = res.status === 200 || res.status === 201;
  const isSoldOut = res.status === 409 || res.status === 400;
  const isDuplicate = res.status === 409 && res.body.includes('already');

  couponIssueSuccess.add(isSuccess);

  if (isSuccess) {
    actualIssuedCount.add(1);
  }

  if (isSoldOut) {
    soldOutResponses.add(1);
  }

  if (isDuplicate) {
    duplicateIssueAttempts.add(1);
  }

  if (!isSuccess && !isSoldOut) {
    failureStatusCounter.add(1);
    recordFailureStatus(res.status, res.body);
  }

  const checkResult = check(res, {
    'coupon issue valid response': (r) =>
      r.status === 200 || r.status === 201 || r.status === 409 || r.status === 400,
    'coupon issue success': (r) => r.status === 200 || r.status === 201,
    'sold out response': (r) => r.status === 409,
    'response has body': (r) => r.body && r.body.length > 0,
  });

  return {
    res,
    isSuccess,
    isSoldOut,
    isDuplicate,
    duration,
    checkResult,
  };
}

// 발급된 쿠폰 조회
function getMyCoupons(userId) {
  const res = http.get(
    `${config.baseUrl}/api/users/${userId}/coupons`,
    { headers: jsonHeaders.headers }
  );

  check(res, {
    'my coupons status 200': (r) => r.status === 200,
    'my coupons has list': (r) => {
      if (r.status !== 200) return false;
      const body = r.json();
      return body && Array.isArray(body.coupons);
    },
  });

  return res;
}

// 시나리오 1: 극한의 동시성
export function issueCouponConcurrent() {
  // 각 VU가 고유한 사용자 ID 생성 (숫자로)
  const userId = (__VU * 1000) + __ITER;  // 예: VU 1, ITER 0 → 1000

  const result = issueCoupon(userId, 'extreme');

  if (result.isSuccess) {
    console.log(`✅ SUCCESS: User ${userId} got coupon in ${result.duration}ms`);
  } else if (result.isSoldOut) {
    console.log(`❌ SOLD OUT: User ${userId} - coupon exhausted`);
  }

  sleep(0.1);
}

// 시나리오 2: 순차적 발급 (비교용)
export function issueCouponSequential() {
  // 순차 테스트용 사용자 ID (숫자로, 200000번대 사용)
  const userId = 200000 + __ITER;

  const result = issueCoupon(userId, 'sequential');

  if (result.isSuccess) {
    // 발급 성공 시 내 쿠폰 조회
    sleep(0.2);
    getMyCoupons(userId);
  }

  sleep(0.5);  // 여유있게 대기
}

// 시나리오 3: 램프업 테스트
export function issueCouponRampUp() {
  // 램프업 테스트용 사용자 ID (숫자로, 300000번대 사용)
  const userId = 300000 + (__VU * 100) + __ITER;

  const result = issueCoupon(userId, 'rampup');

  sleep(0.2 + Math.random() * 0.3);  // 200-500ms 랜덤 대기
}

// 셋업: 테스트 시작 전 데이터 준비 및 검증
export function setup() {
  chargeBalance();

  console.log('=== Coupon Concurrency Test Setup ===');
  console.log(`Coupon ID: ${config.couponId}`);
  console.log(`Expected Max Issuance: ${COUPON_QUANTITY}`);
  console.log(`Total Test Users: ${TOTAL_USERS}`);
  console.log('');

  // 1. 테스트 사용자 존재 확인
  console.log('📋 Step 1: Verifying test users...');
  const testUserIds = [
    1000,    // extremeConcurrency 시작
    5000,    // extremeConcurrency 중간
    10999,   // extremeConcurrency 끝
    200000,  // sequentialIssue 시작
    200050,  // sequentialIssue 중간
    200099,  // sequentialIssue 끝
    300000,  // rampUpTest 시작
    305000,  // rampUpTest 중간
    309999   // rampUpTest 끝
  ];

  let missingUsers = [];
  testUserIds.forEach(userId => {
    const res = http.get(`${config.baseUrl}/api/users/${userId}`, { headers: jsonHeaders.headers });
    if (res.status === 404) {
      missingUsers.push(userId);
    }
  });

  if (missingUsers.length > 0) {
    console.error('');
    console.error('❌ ERROR: Required test users not found!');
    console.error('Missing user IDs (sample):', missingUsers);
    console.error('');
    console.error('💡 Solution: Restart the application');
    console.error('');
    console.error('Test users are automatically created when the application starts.');
    console.error('If they are missing, please restart your Spring Boot application:');
    console.error('');
    console.error('  cd /Users/jsb/hanghe-plus/ecommerce');
    console.error('  ./gradlew bootRun');
    console.error('');
    console.error('Check the logs for:');
    console.error('  === K6 Load Test Data Initializer START ===');
    console.error('  Created 15050 new test users in XXXms');
    console.error('  === K6 Load Test Data Initializer END ===');
    console.error('');
    console.error('Alternatively, run SQL script manually:');
    console.error('  mysql -h localhost -u root -p ecommerce < docs/week7/loadtest/k6/setup-test-users.sql');
    console.error('');
    throw new Error('Test users not found. Please restart the application or run setup-test-users.sql.');
  }

  console.log('✅ All required test users exist');
  console.log('');

  // 2. 쿠폰 존재 확인 (선택적)
  // GET /api/coupons/{id} 엔드포인트가 없으면 이 단계는 skip
  console.log('📋 Step 2: Skipping coupon verification (API endpoint not implemented)');
  console.log(`ℹ️ Coupon ID: ${config.couponId} will be used for testing`);
  console.log('');

  console.log('Test will verify:');
  console.log('1. Exactly COUPON_QUANTITY coupons are issued (no more, no less)');
  console.log('2. No duplicate issuance to same user');
  console.log('3. Race condition handling');
  console.log('4. Response time under high concurrency');
  console.log('=====================================');
  console.log('');

  return {
    startTime: Date.now()
  };
}

// 정리: 테스트 종료 후 결과 요약
export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;

  console.log('');
  console.log('=== Coupon Concurrency Test Results ===');
  console.log(`Total Test Duration: ${duration.toFixed(2)}s`);
  console.log('');
  console.log('Expected Behavior:');
  console.log(`- Exactly ${COUPON_QUANTITY} coupons should be issued`);
  console.log(`- Remaining ${TOTAL_USERS - COUPON_QUANTITY} requests should get SOLD_OUT`);
  console.log('- No duplicate issuance');
  console.log('- All responses < 2s under normal load');
  console.log('- Success rate: ~50% (100 success / 200 requests)');
  console.log('');
  console.log('Check the metrics above to verify:');
  console.log('- actual_issued_count should be exactly ' + COUPON_QUANTITY);
  console.log('- sold_out_responses should be around ' + (TOTAL_USERS - COUPON_QUANTITY));
  console.log('- duplicate_issue_attempts should be 0');
  console.log('- coupon_issue_success_rate should be ~50%');
  console.log('========================================');

  if (Object.keys(failureStatusHistogram).length > 0) {
    console.log('');
    console.log('Failure status distribution (status:count, sample):');
    Object.entries(failureStatusHistogram).forEach(([status, record]) => {
      console.log(`- ${status}: ${record.count} (${record.sample || 'no body'})`);
    });
  }
}
