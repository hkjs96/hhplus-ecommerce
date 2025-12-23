import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom Metrics
const orderCreationDuration = new Trend('order_creation_duration');
const cachedResponseDuration = new Trend('cached_response_duration');
const duplicateRequestRate = new Rate('duplicate_request_rate');
const idempotencyErrors = new Counter('idempotency_errors');

function safeJsonParse(payload) {
    if (!payload) return null;
    try {
        return JSON.parse(payload);
    } catch (e) {
        return null;
    }
}

// Test Configuration
export const options = {
    stages: [
        { duration: '30s', target: 50 },   // Ramp up to 50 VUs
        { duration: '1m', target: 100 },   // Ramp up to 100 VUs
        { duration: '2m', target: 100 },   // Stay at 100 VUs
        { duration: '30s', target: 0 },    // Ramp down
    ],
    thresholds: {
        'http_req_duration': ['p(95)<500', 'p(99)<1000'],  // 95% under 500ms, 99% under 1s
        'order_creation_duration': ['p(95)<1000'],         // First request under 1s
        'cached_response_duration': ['p(95)<100'],         // Cached response under 100ms
        'duplicate_request_rate': ['rate>0.5'],            // At least 50% duplicate requests
        'http_req_failed': ['rate<0.01'],                  // Less than 1% failures
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Test Data
let userId = 1;
let productId = 1;
const idempotencyKeys = [];

export function setup() {
    // Initialize test data
    console.log('Setting up test data...');

    // Charge user balance
    const chargeResponse = http.post(
        `${BASE_URL}/api/users/${userId}/balance/charge`,
        JSON.stringify({
            userId: userId,
            amount: 10000000,
            idempotencyKey: `CHARGE_${userId}_${Date.now()}`
        }),
        {
            headers: { 'Content-Type': 'application/json' },
        }
    );

    check(chargeResponse, {
        'setup: user charged successfully': (r) => r.status === 200,
    });

    return { userId, productId };
}

export default function (data) {
    const testUserId = data.userId;
    const testProductId = data.productId;

    group('Order Creation with Idempotency', () => {
        // Scenario 1: First request (should create order)
        const idempotencyKey = `ORDER_${testUserId}_${__VU}_${__ITER}_${Date.now()}`;

        const orderRequest = {
            userId: testUserId,
            items: [
                {
                    productId: testProductId,
                    quantity: 1
                }
            ],
            couponId: null,
            idempotencyKey: idempotencyKey
        };

        // First Request
        const startTime1 = new Date();
        const response1 = http.post(
            `${BASE_URL}/api/orders`,
            JSON.stringify(orderRequest),
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'FirstRequest' },
            }
        );
        const duration1 = new Date() - startTime1;

        const success1 = check(response1, {
            'first request: status 200': (r) => r.status === 200,
            'first request: has orderId': (r) => {
                const body = safeJsonParse(r.body);
                return body && body.data && body.data.orderId;
            },
        });

        if (success1) {
            orderCreationDuration.add(duration1);
            idempotencyKeys.push(idempotencyKey);

            // Store order ID for duplicate request
            const orderData = safeJsonParse(response1.body)?.data;
            const orderId = orderData?.orderId;

            // Scenario 2: Duplicate request (should return cached response)
            sleep(0.1); // Small delay

            const startTime2 = new Date();
            const response2 = http.post(
                `${BASE_URL}/api/orders`,
                JSON.stringify(orderRequest),
                {
                    headers: { 'Content-Type': 'application/json' },
                    tags: { name: 'DuplicateRequest' },
                }
            );
            const duration2 = new Date() - startTime2;

            const isDuplicate = check(response2, {
                'duplicate request: status 200': (r) => r.status === 200,
                'duplicate request: same orderId': (r) => {
                    const body = safeJsonParse(r.body);
                    return orderId && body && body.data && body.data.orderId === orderId;
                },
                'duplicate request: faster than first': (r) => duration2 < duration1,
            });

            if (isDuplicate) {
                cachedResponseDuration.add(duration2);
                duplicateRequestRate.add(true);
            } else {
                duplicateRequestRate.add(false);
                idempotencyErrors.add(1);
            }

            // Performance Comparison
            const speedupRatio = duration1 / duration2;
            check({ speedupRatio }, {
                'cached response 5x faster': (data) => data.speedupRatio > 5,
                'cached response 10x faster': (data) => data.speedupRatio > 10,
            });

        } else {
            idempotencyErrors.add(1);
        }
    });

    group('Concurrent Duplicate Requests', () => {
        // Test concurrent requests with same idempotencyKey
        const sharedKey = `CONCURRENT_ORDER_${testUserId}_${__VU}_${Date.now()}`;

        const orderRequest = {
            userId: testUserId,
            items: [
                {
                    productId: testProductId,
                    quantity: 1
                }
            ],
            couponId: null,
            idempotencyKey: sharedKey
        };

        // Send 3 concurrent requests
        const responses = http.batch([
            ['POST', `${BASE_URL}/api/orders`, JSON.stringify(orderRequest), {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'ConcurrentRequest1' },
            }],
            ['POST', `${BASE_URL}/api/orders`, JSON.stringify(orderRequest), {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'ConcurrentRequest2' },
            }],
            ['POST', `${BASE_URL}/api/orders`, JSON.stringify(orderRequest), {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'ConcurrentRequest3' },
            }],
        ]);

        // All responses should have same orderId or PROCESSING error
        const orderIds = responses
            .filter(r => r.status === 200)
            .map(r => safeJsonParse(r.body))
            .filter(Boolean)
            .map(body => body.data && body.data.orderId)
            .filter(Boolean);

        const processingErrors = responses
            .filter(r => r.status === 400 || r.status === 409)
            .filter(r => r.body && r.body.includes('이미 처리 중'));

        check({ orderIds, processingErrors }, {
            'concurrent requests: at least one success': (data) => data.orderIds.length >= 1,
            'concurrent requests: all same orderId': (data) => {
                if (data.orderIds.length === 0) return false;
                return data.orderIds.every(id => id === data.orderIds[0]);
            },
            'concurrent requests: no duplicate orders': (data) => {
                return data.orderIds.length + data.processingErrors.length === 3;
            },
        });
    });

    sleep(0.5);
}

export function teardown(data) {
    console.log('\n=== Idempotency Test Summary ===');
    console.log(`Total unique idempotency keys created: ${idempotencyKeys.length}`);
    console.log('Test completed successfully!');
}

export function handleSummary(data) {
    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'docs/week6/loadtest/k6/results/order-idempotency-summary.json': JSON.stringify(data),
    };
}

function textSummary(data, options) {
    const indent = options.indent || '';
    const enableColors = options.enableColors || false;

    const formatMetric = (metric, field) => {
        const value = metric && metric.values ? metric.values[field] : undefined;
        return typeof value === 'number' ? value.toFixed(2) : 'N/A';
    };

    let summary = '\n';
    summary += indent + '='.repeat(60) + '\n';
    summary += indent + 'Order Creation Idempotency Load Test Results\n';
    summary += indent + '='.repeat(60) + '\n\n';

    // Request Statistics
    summary += indent + 'HTTP Request Statistics:\n';
    summary += indent + `  Total Requests: ${data.metrics.http_reqs?.values?.count || 0}\n`;
    summary += indent + `  Failed Requests: ${data.metrics.http_req_failed ? (data.metrics.http_req_failed.values.rate * 100).toFixed(2) : 'N/A'}%\n`;
    summary += indent + `  Avg Duration: ${formatMetric(data.metrics.http_req_duration, 'avg')}ms\n`;
    summary += indent + `  P95 Duration: ${formatMetric(data.metrics.http_req_duration, 'p(95)')}ms\n`;
    summary += indent + `  P99 Duration: ${formatMetric(data.metrics.http_req_duration, 'p(99)')}ms\n\n`;

    // Idempotency Metrics
    if (data.metrics.order_creation_duration) {
        summary += indent + 'Idempotency Performance:\n';
        summary += indent + `  First Request Avg: ${formatMetric(data.metrics.order_creation_duration, 'avg')}ms\n`;
        summary += indent + `  Cached Response Avg: ${formatMetric(data.metrics.cached_response_duration, 'avg')}ms\n`;

        const speedup = (data.metrics.cached_response_duration?.values?.avg
            ? data.metrics.order_creation_duration.values.avg / data.metrics.cached_response_duration.values.avg
            : null);
        summary += indent + `  Performance Improvement: ${speedup ? speedup.toFixed(2) : 'N/A'}x faster\n`;
        summary += indent + `  Duplicate Request Rate: ${data.metrics.duplicate_request_rate ? (data.metrics.duplicate_request_rate.values.rate * 100).toFixed(2) : 'N/A'}%\n`;
        summary += indent + `  Idempotency Errors: ${data.metrics.idempotency_errors ? data.metrics.idempotency_errors.values.count : 0}\n\n`;
    }

    // Thresholds
    summary += indent + 'Threshold Results:\n';
    Object.entries(data.thresholds || {}).forEach(([name, threshold]) => {
        const status = threshold.ok ? '✓ PASS' : '✗ FAIL';
        summary += indent + `  ${status}: ${name}\n`;
    });

    summary += indent + '\n' + '='.repeat(60) + '\n';

    return summary;
}
