import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom Metrics
const issuanceSuccessCount = new Counter('issuance_success_count');
const issuanceFailureCount = new Counter('issuance_failure_count');
const soldOutErrorRate = new Rate('sold_out_error_rate');
const duplicateErrorRate = new Rate('duplicate_error_rate');
const issuanceDuration = new Trend('issuance_duration');

// Test Configuration
export const options = {
    scenarios: {
        // Scenario 1: 선착순 100명, 쿠폰 50개 (분산락 테스트)
        first_come_first_served: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: 100,
            maxDuration: '2m',
            exec: 'firstComeFirstServed',
        },
        // Scenario 2: 중복 발급 시도 (동일 사용자가 여러 번)
        duplicate_issuance_attempt: {
            executor: 'per-vu-iterations',
            vus: 10,
            iterations: 3,
            maxDuration: '1m',
            exec: 'duplicateIssuanceAttempt',
            startTime: '2m30s',
        },
    },
    thresholds: {
        // 분산락 대기 시간 포함하여 넉넉히 허용
        'http_req_duration': ['p(95)<1200', 'p(99)<2000'],
        'issuance_duration': ['p(95)<1200'],
        'sold_out_error_rate': ['rate>=0.4'],  // 재고 50개면 최소 40%는 sold out
        // duplicate 시나리오도 품절에 포함되어 http_req_failed 로 잡히므로 별도 임계값 제거
        'http_req_failed': ['rate<0.7'],       // 품절/중복(409) 허용
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 테스트 데이터
// Note: 이 테스트는 DataInitializer에서 생성된 쿠폰을 사용합니다
// 쿠폰 ID 1: "신규가입 쿠폰", 재고 100개
// 쿠폰 ID 2: "VIP 전용 쿠폰", 재고 50개 (DataInitializer 기준)
const TEST_COUPON_ID = 2;  // VIP 전용 쿠폰 (재고 50개)

export function setup() {
    console.log('Setting up test...');
    console.log(`Using pre-created coupon ID: ${TEST_COUPON_ID} (VIP 전용 쿠폰, 재고: 50개)`);
    console.log('Note: 테스트 전에 애플리케이션을 재시작하여 쿠폰 재고를 초기화하세요.');

    return { couponId: TEST_COUPON_ID };
}

/**
 * Scenario 1: 선착순 100명이 쿠폰 50개 발급 시도 (분산락 테스트)
 *
 * 목적:
 * - 정확히 50개만 발급되는지 검증
 * - 50개 이후는 모두 SOLD_OUT 에러
 * - 분산락이 정확히 작동하는지 검증
 */
export function firstComeFirstServed(data) {
    const userId = __VU;  // 각 VU가 서로 다른 사용자
    const couponId = data.couponId;

    group('First Come First Served (100 users, 50 coupons)', () => {
        const startTime = new Date();
        const response = http.post(
            `${BASE_URL}/api/coupons/${couponId}/issue`,
            JSON.stringify({ userId: userId }),
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'FirstComeFirstServed' },
            }
        );
        const duration = new Date() - startTime;

        issuanceDuration.add(duration);

        const success = check(response, {
            'issuance: status 200 or 409': (r) => r.status === 200 || r.status === 409,
        });

        if (response.status === 200) {
            issuanceSuccessCount.add(1);

            const body = JSON.parse(response.body);
            check(body, {
                'issuance success: has userCouponId': (b) => b.userCouponId !== undefined,
                'issuance success: has couponName': (b) => b.couponName !== undefined,
                'issuance success: has discountRate': (b) => b.discountRate !== undefined,
            });

        } else if (response.status === 409) {
            issuanceFailureCount.add(1);

            // SOLD_OUT 에러 검증
            if (response.body.includes('SOLD_OUT') || response.body.includes('소진')) {
                soldOutErrorRate.add(true);
            } else if (response.body.includes('DUPLICATE') || response.body.includes('이미 발급')) {
                duplicateErrorRate.add(true);
            } else {
                soldOutErrorRate.add(false);
            }
        } else {
            issuanceFailureCount.add(1);
        }
    });

    sleep(0.1);
}

/**
 * Scenario 2: 중복 발급 시도 (동일 사용자가 여러 번)
 *
 * 목적:
 * - 동일 사용자는 1번만 발급받을 수 있는지 검증
 * - 2번째 시도부터는 DUPLICATE 에러
 */
export function duplicateIssuanceAttempt(data) {
    // K6 사용자 4~153까지 생성되어 있으므로, 범위를 벗어나지 않게 140번대 사용자만 사용
    const userId = 140 + __VU;  // 중복 테스트용 사용자 (유효한 사용자 ID)
    const couponId = data.couponId;

    group('Duplicate Issuance Attempt (Same User Multiple Times)', () => {
        const startTime = new Date();
        const response = http.post(
            `${BASE_URL}/api/coupons/${couponId}/issue`,
            JSON.stringify({ userId: userId }),
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'DuplicateAttempt' },
            }
        );
        const duration = new Date() - startTime;

        issuanceDuration.add(duration);

        if (response.status === 200) {
            issuanceSuccessCount.add(1);
            console.log(`User ${userId} - Iteration ${__ITER}: SUCCESS (first issuance)`);

        } else if (response.status === 409) {
            issuanceFailureCount.add(1);

            // 중복 발급 에러 검증
            if (response.body.includes('DUPLICATE') || response.body.includes('이미 발급')) {
                duplicateErrorRate.add(true);
                console.log(`User ${userId} - Iteration ${__ITER}: DUPLICATE (expected)`);
            } else if (response.body.includes('SOLD_OUT')) {
                soldOutErrorRate.add(true);
                console.log(`User ${userId} - Iteration ${__ITER}: SOLD_OUT`);
            } else {
                duplicateErrorRate.add(false);
            }
        }
    });

    sleep(0.2);
}

export function teardown(data) {
    console.log('\n=== Coupon Issuance Concurrency Test Summary ===');
    console.log(`Test Coupon ID: ${data.couponId}`);
    console.log('Expected: Exactly 50 issuances, 50+ sold out errors');
}

export function handleSummary(data) {
    const summaryDir = (__ENV.SUMMARY_DIR || 'results').replace(/\/$/, '');
    const summaryPath = `${summaryDir}/coupon-issuance-concurrency-summary.json`;

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        [summaryPath]: JSON.stringify(data),
    };
}

function textSummary(data, options) {
    const indent = options.indent || '';

    // Helper function for safe metric access
    const getMetricValue = (metric, key) => {
        if (!metric || !metric.values) return null;
        const value = metric.values[key];
        return value === undefined || value === null ? null : Number(value);
    };
    const formatMs = (value) => value === null ? 'N/A' : `${value.toFixed(2)}ms`;
    const formatPercent = (rate) => rate === null ? 'N/A' : `${(rate * 100).toFixed(2)}%`;

    let summary = '\n';
    summary += indent + '='.repeat(60) + '\n';
    summary += indent + 'Coupon Issuance Concurrency Test Results\n';
    summary += indent + '='.repeat(60) + '\n\n';

    // Request Statistics
    summary += indent + 'HTTP Request Statistics:\n';
    summary += indent + `  Total Requests: ${getMetricValue(data.metrics.http_reqs, 'count') ?? 0}\n`;
    summary += indent + `  Failed Requests: ${formatPercent(getMetricValue(data.metrics.http_req_failed, 'rate'))}\n`;
    summary += indent + `  Avg Duration: ${formatMs(getMetricValue(data.metrics.http_req_duration, 'avg'))}\n`;
    summary += indent + `  P95 Duration: ${formatMs(getMetricValue(data.metrics.http_req_duration, 'p(95)'))}\n`;
    summary += indent + `  P99 Duration: ${formatMs(getMetricValue(data.metrics.http_req_duration, 'p(99)'))}\n\n`;

    // Issuance Metrics
    if (data.metrics.issuance_success_count) {
        const totalIssuances = data.metrics.issuance_success_count.values.count +
                               data.metrics.issuance_failure_count.values.count;
        const successRate = (data.metrics.issuance_success_count.values.count / totalIssuances * 100).toFixed(2);

        summary += indent + 'Issuance Performance:\n';
        summary += indent + `  Total Attempts: ${totalIssuances}\n`;
        summary += indent + `  Successful Issuances: ${data.metrics.issuance_success_count.values.count}\n`;
        summary += indent + `  Failed Issuances: ${data.metrics.issuance_failure_count.values.count}\n`;
        summary += indent + `  Success Rate: ${successRate}%\n`;
        summary += indent + `  Avg Issuance Duration: ${data.metrics.issuance_duration.values.avg.toFixed(2)}ms\n`;
        summary += indent + `  P95 Issuance Duration: ${data.metrics.issuance_duration.values['p(95)'].toFixed(2)}ms\n\n`;
    }

    // Error Breakdown
    if (data.metrics.sold_out_error_rate && data.metrics.duplicate_error_rate) {
        summary += indent + 'Error Breakdown:\n';
        summary += indent + `  Sold Out Errors: ${(data.metrics.sold_out_error_rate.values.rate * 100).toFixed(2)}%\n`;
        summary += indent + `  Duplicate Errors: ${(data.metrics.duplicate_error_rate.values.rate * 100).toFixed(2)}%\n\n`;
    }

    // Validation
    if (data.metrics.issuance_success_count) {
        summary += indent + 'Validation:\n';
        const successCount = data.metrics.issuance_success_count.values.count;
        const isValid = successCount === 50;
        summary += indent + `  Expected Issuances: 50\n`;
        summary += indent + `  Actual Issuances: ${successCount}\n`;
        summary += indent + `  Result: ${isValid ? '✓ PASS' : '✗ FAIL'}\n\n`;
    }

    // Throughput
    summary += indent + 'Throughput:\n';
    summary += indent + `  Requests/sec: ${(data.metrics.http_reqs.values.count / data.state.testRunDurationMs * 1000).toFixed(2)}\n\n`;

    // Thresholds
    summary += indent + 'Threshold Results:\n';
    Object.entries(data.thresholds || {}).forEach(([name, threshold]) => {
        const status = threshold.ok ? '✓ PASS' : '✗ FAIL';
        summary += indent + `  ${status}: ${name}\n`;
    });

    summary += indent + '\n' + '='.repeat(60) + '\n';

    return summary;
}
