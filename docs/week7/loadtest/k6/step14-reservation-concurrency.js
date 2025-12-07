import http from 'k6/http';
import { check, sleep } from 'k6';
import config from './common/config.js';
import {
  reservationSuccessCount,
  reservationSoldOutCount,
  reservationDuplicateCount,
  reservationErrorCount,
  reservationDuration,
  issuanceDuration,
  sequenceAccuracyRate,
  duplicatePreventionRate,
} from './common/metrics.js';
import { buildReservePayload, jsonHeaders, randomUserId } from './common/test-data.js';
import { chargeBalance } from './common/setup.js';

const reservationUrl = `${config.baseUrl}/api/coupons/${config.couponId}/reserve`;

export const options = {
  scenarios: {
    reservationConcurrency: {
      executor: 'shared-iterations',
      vus: 100,
      iterations: 1000,
      maxDuration: '2m',
      exec: 'reservationConcurrency',
    },
    duplicateReservationAttempt: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 10,
      maxDuration: '30s',
      startTime: '2m30s',
      exec: 'duplicateReservationAttempt',
    },
    reservationIssuanceFlow: {
      executor: 'constant-vus',
      vus: 10,
      duration: '1m',
      startTime: '3m',
      exec: 'reservationIssuanceFlow',
    },
  },
  thresholds: {
    reservation_duration: ['p(95)<200', 'p(99)<500'],
    sequence_accuracy_rate: ['rate==1.0'],
    duplicate_prevention_rate: ['rate>0.95'],
    http_req_failed: ['rate<0.1'],
  },
};

export function setup() {
  return chargeBalance();
}

function safeParse(res) {
  try {
    return res.json();
  } catch (e) {
    return {};
  }
}

function postReservation(userId) {
  const res = http.post(reservationUrl, JSON.stringify(buildReservePayload(userId)), jsonHeaders);
  reservationDuration.add(res.timings.duration);
  const body = safeParse(res);
  const success = res.status === 200;
  const conflict = res.status === 409;

  if (success) {
    reservationSuccessCount.add(1);
    const sequence = body?.sequenceNumber;
    sequenceAccuracyRate.add(typeof sequence === 'number');
  } else if (conflict) {
    const statusLabel = body?.status || '';
    if (statusLabel === 'ALREADY_ISSUED') {
      reservationDuplicateCount.add(1);
      duplicatePreventionRate.add(1);
    } else {
      reservationSoldOutCount.add(1);
    }
  } else {
    reservationErrorCount.add(1);
  }

  check(res, {
    'reservation responded': (r) => r.status === 200 || r.status === 409,
  });

  return { res, success, body };
}

export function reservationConcurrency() {
  postReservation(randomUserId());
  sleep(0.05);
}

export function duplicateReservationAttempt() {
  const userId = config.userId;
  const result = postReservation(userId);
  if (result.success) {
    duplicatePreventionRate.add(1);
  }
  sleep(0.1);
}

export function reservationIssuanceFlow() {
  const userId = randomUserId();
  const reservation = postReservation(userId);
  sleep(3);
  const couponListRes = http.get(`${config.baseUrl}/api/users/${userId}/coupons?status=AVAILABLE`);
  issuanceDuration.add(couponListRes.timings.duration);
  check(couponListRes, {
    'coupon list success': (r) => r.status === 200,
  });
  if (reservation.success) {
    check(couponListRes, {
      'coupon delivered': (r) => (r.json('coupons') || []).length >= 0,
    });
  }
  sleep(1);
}
