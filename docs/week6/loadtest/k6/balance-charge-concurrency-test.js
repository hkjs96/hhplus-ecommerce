import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom Metrics
const chargeSuccessCount = new Counter('charge_success_count');
const chargeFailureCount = new Counter('charge_failure_count');
const optimisticLockRetryRate = new Rate('optimistic_lock_retry_rate');
const chargeDuration = new Trend('charge_duration');

// Test Configuration
export const options = {
    scenarios: {
        // Scenario 1: 동일 사용자에게 동시 충전 (낙관락 테스트)
        same_user_concurrent: {
            executor: 'per-vu-iterations',
            vus: 50,
            iterations: 2,
            maxDuration: '2m',
            exec: 'sameUserConcurrent',
        },
        // Scenario 2: 서로 다른 사용자 동시 충전 (병렬 처리)
        different_users_concurrent: {
            executor: 'constant-vus',
            vus: 100,
            duration: '1m',
            exec: 'differentUsersConcurrent',
            startTime: '2m30s',
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<500', 'p(99)<1000'],
        'charge_duration': ['p(95)<300'],
        'optimistic_lock_retry_rate': ['rate<0.3'],  // 30% 이하 재시도
        'http_req_failed': ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 테스트 데이터
const TEST_USER_COUNT = 100;
const CHARGE_AMOUNT = 10000;

export function setup() {
    console.log('Setting up test users...');

    // 테스트 사용자 생성 (User 1~100)
    const users = [];
    for (let i = 1; i <= TEST_USER_COUNT; i++) {
        users.push({
            id: i,
            email: `user${i}@test.com`,
            name: `TestUser${i}`
        });
    }

    console.log(`Setup completed with ${users.length} users`);
    return { users };
}

/**
 * Scenario 1: 동일 사용자에게 동시 충전 (낙관락 테스트)
 *
 * 목적: OptimisticLockException이 발생하고 재시도 로직이 작동하는지 검증
 */
export function sameUserConcurrent(data) {
    const userId = 1;  // 모든 VU가 동일한 사용자에게 충전

    group('Same User Concurrent Charge (Optimistic Lock)', () => {
        const idempotencyKey = `CHARGE_${userId}_${__VU}_${__ITER}_${Date.now()}`;

        const payload = JSON.stringify({
            userId: userId,
            amount: CHARGE_AMOUNT,
            idempotencyKey: idempotencyKey
        });

        const startTime = new Date();
        const response = http.post(
            `${BASE_URL}/api/users/${userId}/balance/charge`,
            payload,
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'SameUserCharge' },
            }
        );
        const duration = new Date() - startTime;

        const success = check(response, {
            'charge: status 200': (r) => r.status === 200,
            'charge: has balance': (r) => {
                if (r.status !== 200) return false;
                const body = JSON.parse(r.body);
                return body.balance !== undefined;
            },
        });

        chargeDuration.add(duration);

        if (success) {
            chargeSuccessCount.add(1);
        } else {
            chargeFailureCount.add(1);

            // OptimisticLockException 검증
            if (response.status === 409 || response.body.includes('OptimisticLock')) {
                optimisticLockRetryRate.add(true);
            } else {
                optimisticLockRetryRate.add(false);
            }
        }
    });

    sleep(0.1);
}

/**
 * Scenario 2: 서로 다른 사용자 동시 충전 (병렬 처리)
 *
 * 목적: 서로 다른 사용자는 병렬로 처리되는지 검증
 */
export function differentUsersConcurrent(data) {
    // 각 VU가 서로 다른 사용자 사용
    const userId = (__VU % TEST_USER_COUNT) + 1;

    group('Different Users Concurrent Charge (Parallel)', () => {
        const idempotencyKey = `CHARGE_${userId}_${Date.now()}_${Math.random()}`;

        const payload = JSON.stringify({
            userId: userId,
            amount: CHARGE_AMOUNT,
            idempotencyKey: idempotencyKey
        });

        const startTime = new Date();
        const response = http.post(
            `${BASE_URL}/api/users/${userId}/balance/charge`,
            payload,
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'DifferentUsersCharge' },
            }
        );
        const duration = new Date() - startTime;

        const success = check(response, {
            'charge different users: status 200': (r) => r.status === 200,
            'charge different users: balance increased': (r) => {
                if (r.status !== 200) return false;
                const body = JSON.parse(r.body);
                return body.balance > 0;
            },
            'charge different users: fast response': (r) => r.timings.duration < 500,
        });

        chargeDuration.add(duration);

        if (success) {
            chargeSuccessCount.add(1);
        } else {
            chargeFailureCount.add(1);
        }
    });

    sleep(0.2);
}

export function teardown(data) {
    console.log('\n=== Balance Charge Concurrency Test Summary ===');
    console.log(`Total test users: ${data.users.length}`);
}

export function handleSummary(data) {
    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'docs/week6/loadtest/k6/results/balance-charge-concurrency-summary.json': JSON.stringify(data),
    };
}

function textSummary(data, options) {
    const indent = options.indent || '';

    let summary = '\n';
    summary += indent + '='.repeat(60) + '\n';
    summary += indent + 'Balance Charge Concurrency Test Results\n';
    summary += indent + '='.repeat(60) + '\n\n';

    // Request Statistics
    summary += indent + 'HTTP Request Statistics:\n';
    summary += indent + `  Total Requests: ${data.metrics.http_reqs.values.count}\n`;
    summary += indent + `  Failed Requests: ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%\n`;
    summary += indent + `  Avg Duration: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms\n`;
    summary += indent + `  P95 Duration: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms\n`;
    const p99 = data.metrics.http_req_duration.values['p(99)'];
    summary += indent + `  P99 Duration: ${p99 ? p99.toFixed(2) : 'N/A'}ms\n\n`;

    // Charge Metrics
    if (data.metrics.charge_success_count && data.metrics.charge_failure_count) {
        const totalCharges = data.metrics.charge_success_count.values.count +
                            data.metrics.charge_failure_count.values.count;
        const successRate = (data.metrics.charge_success_count.values.count / totalCharges * 100).toFixed(2);

        summary += indent + 'Charge Performance:\n';
        summary += indent + `  Total Charges: ${totalCharges}\n`;
        summary += indent + `  Successful: ${data.metrics.charge_success_count.values.count}\n`;
        summary += indent + `  Failed: ${data.metrics.charge_failure_count.values.count}\n`;
        summary += indent + `  Success Rate: ${successRate}%\n`;

        if (data.metrics.charge_duration && data.metrics.charge_duration.values) {
            summary += indent + `  Avg Charge Duration: ${data.metrics.charge_duration.values.avg.toFixed(2)}ms\n`;
            summary += indent + `  P95 Charge Duration: ${data.metrics.charge_duration.values['p(95)'].toFixed(2)}ms\n`;
        }
        summary += '\n';
    }

    // Optimistic Lock Metrics
    if (data.metrics.optimistic_lock_retry_rate && data.metrics.optimistic_lock_retry_rate.values) {
        summary += indent + 'Optimistic Lock Performance:\n';
        summary += indent + `  Retry Rate: ${(data.metrics.optimistic_lock_retry_rate.values.rate * 100).toFixed(2)}%\n`;
        summary += indent + `  (Expected: < 30% for good performance)\n\n`;
    }

    // Throughput
    summary += indent + 'Throughput:\n';
    summary += indent + `  Requests/sec: ${(data.metrics.http_reqs.values.count / data.state.testRunDurationMs * 1000).toFixed(2)}\n\n`;

    // Scenario Breakdown
    summary += indent + 'Scenario Breakdown:\n';
    if (data.root_group && data.root_group.groups) {
        Object.entries(data.root_group.groups).forEach(([name, group]) => {
            if (group.checks) {
                const totalChecks = Object.values(group.checks).reduce((sum, check) => sum + check.passes + check.fails, 0);
                const passedChecks = Object.values(group.checks).reduce((sum, check) => sum + check.passes, 0);
                const passRate = totalChecks > 0 ? (passedChecks / totalChecks * 100).toFixed(2) : 0;
                summary += indent + `  ${name}: ${passRate}% pass rate\n`;
            }
        });
    }
    summary += '\n';

    // Thresholds
    summary += indent + 'Threshold Results:\n';
    Object.entries(data.thresholds || {}).forEach(([name, threshold]) => {
        const status = threshold.ok ? '✓ PASS' : '✗ FAIL';
        summary += indent + `  ${status}: ${name}\n`;
    });

    summary += indent + '\n' + '='.repeat(60) + '\n';

    return summary;
}
