import http from 'k6/http';
import { check, sleep } from 'k6';
import config from './common/config.js';
import {
  rankingQueryDuration,
  rankingQuerySuccessRate,
  rankingUpdateDuration,
  rankingAccuracyRate,
  zincrbyOperationCount,
} from './common/metrics.js';
import { buildOrderPayload, buildPaymentPayload, jsonHeaders } from './common/test-data.js';
import { chargeBalance, ensureCouponOwnership } from './common/setup.js';

const rankingLimit = Number(__ENV.RANKING_LIMIT || 5);
const rankingRate = Number(__ENV.RANKING_RATE || 60);
const rankingTimeUnit = __ENV.RANKING_TIME_UNIT || '1s';
const rankingDuration = __ENV.RANKING_DURATION || '1m';
const rankingPreAllocatedVUs = Number(__ENV.RANKING_PRE_ALLOCATED_VUS || 80);
const rankingMaxVUs = Number(__ENV.RANKING_MAX_VUS || 200);

const orderStage1Target = Number(__ENV.ORDER_STAGE1_TARGET || 50);
const orderStage2Target = Number(__ENV.ORDER_STAGE2_TARGET || orderStage1Target);
const orderPeakVUs = Number(__ENV.ORDER_PEAK_VUS || 100);
const stageDurations = {
  stage1: __ENV.ORDER_STAGE1_DURATION || '30s',
  stage2: __ENV.ORDER_STAGE2_DURATION || '1m',
  stage3: __ENV.ORDER_STAGE3_DURATION || '30s',
  stage4: __ENV.ORDER_STAGE4_DURATION || '1m',
  stage5: __ENV.ORDER_STAGE5_DURATION || '30s',
};

export function setup() {
  chargeBalance();
  return ensureCouponOwnership();
}

export const options = {
  scenarios: {
    getRanking: {
      executor: 'constant-arrival-rate',
      rate: rankingRate,
      timeUnit: rankingTimeUnit,
      duration: rankingDuration,
      preAllocatedVUs: rankingPreAllocatedVUs,
      maxVUs: rankingMaxVUs,
      exec: 'getRanking',
    },
    createOrderWithRanking: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: stageDurations.stage1, target: orderStage1Target },
        { duration: stageDurations.stage2, target: orderStage2Target },
        { duration: stageDurations.stage3, target: orderPeakVUs },
        { duration: stageDurations.stage4, target: orderPeakVUs },
        { duration: stageDurations.stage5, target: 0 },
      ],
      exec: 'createOrderWithRanking',
    },
    verifyRankingAccuracy: {
      executor: 'shared-iterations',
      vus: 100,
      iterations: 100,
      maxDuration: '3m',
      exec: 'verifyRankingAccuracy',
    },
  },
  thresholds: {
    ranking_query_duration: ['p(95)<50', 'p(99)<100'],
    ranking_update_duration: ['p(95)<500'],
    ranking_accuracy_rate: ['rate>0.95'],
    http_req_failed: ['rate<0.1'],
  },
};

function getRankingResponse(limit = rankingLimit) {
  const res = http.get(`${config.baseUrl}/api/products/ranking/top?limit=${limit}`, {
    timeout: config.timeout || '5s',  // 빠른 실패
  });
  rankingQueryDuration.add(res.timings.duration);
  rankingQuerySuccessRate.add(res.status === 200);
  check(res, {
    'ranking lookup success': (r) => r.status === 200,
  });
  return res;
}

function executeOrderFlow(idempotencySuffix) {
  const idempotencyKey = `loadtest-order-${__VU}-${__ITER}-${Date.now()}-${idempotencySuffix}`;
  const orderPayload = buildOrderPayload({ idempotencyKey });
  const orderRes = http.post(`${config.baseUrl}/api/orders`, JSON.stringify(orderPayload), {
    ...jsonHeaders,
    timeout: config.timeout || '5s',  // 빠른 실패
  });
  rankingUpdateDuration.add(orderRes.timings.duration);
  const orderCreated = orderRes.status === 201;
  zincrbyOperationCount.add(orderCreated ? 1 : 0);

  check(orderRes, {
    'order created status': (r) => r.status === 201,
  });

  if (!orderCreated) {
    return null;
  }

  const orderId = orderRes.json('orderId');
  const paymentPayload = buildPaymentPayload({
    userId: orderPayload.userId,
    idempotencyKey: `${idempotencyKey}-payment`,
  });
  const paymentRes = http.post(`${config.baseUrl}/api/orders/${orderId}/payment`, JSON.stringify(paymentPayload), {
    ...jsonHeaders,
    timeout: config.timeout || '5s',  // 빠른 실패
  });
  check(paymentRes, {
    'payment status 200': (r) => r.status === 200,
  });
  sleep(1);
  return { orderId, orderPayload };
}

export function getRanking() {
  getRankingResponse();
  sleep(0.1);
}

export function createOrderWithRanking() {
  const flow = executeOrderFlow('ranking');
  if (!flow) {
    return;
  }

  const rankingRes = getRankingResponse();
  const rankingList = rankingRes.json('rankings') || [];
  const targetEntry = rankingList.find((item) => item.productId === config.productId);
  const accuracyHit = Boolean(targetEntry);
  rankingAccuracyRate.add(accuracyHit);
  sleep(1);
}

export function verifyRankingAccuracy() {
  executeOrderFlow('accuracy');
  sleep(0.5);
  const rankingRes = getRankingResponse(10);
  const rankingList = rankingRes.json('rankings') || [];
  const targetEntry = rankingList.find((item) => item.productId === config.productId);
  const scoreMatch = targetEntry && targetEntry.salesCount >= 0;
  rankingAccuracyRate.add(Boolean(scoreMatch));
}
