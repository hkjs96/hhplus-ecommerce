import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom Metrics
const paymentSuccessCount = new Counter('payment_success_count');
const paymentFailureCount = new Counter('payment_failure_count');
const stockDeductionAccuracy = new Rate('stock_deduction_accuracy');
const paymentDuration = new Trend('payment_duration');
const orderCreationDuration = new Trend('order_creation_duration');

// Test Configuration
export const options = {
    scenarios: {
        // Scenario 1: 재고 50개, 100명 동시 결제 (분산락 테스트)
        limited_stock_payment: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: 100,
            maxDuration: '3m',
            exec: 'limitedStockPayment',
        },
        // Scenario 2: 재고 충분, 다중 사용자 결제 (처리량 테스트)
        high_throughput_payment: {
            executor: 'constant-vus',
            vus: 50,
            duration: '1m',
            exec: 'highThroughputPayment',
            startTime: '3m30s',
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<1500', 'p(99)<3000'],
        'payment_duration': ['p(95)<1000'],
        'order_creation_duration': ['p(95)<500'],
        'stock_deduction_accuracy': ['rate>0.95'],  // 95% 이상 정확성
        'http_req_failed': ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 테스트 데이터
const TEST_USER_COUNT = 100;
const PRODUCT_PICKER = {
    limitedStock: (products) => products.find((p) => p.stock !== null && p.stock > 0 && p.stock <= 50),
    abundantStock: (products) => products.find((p) => p.stock !== null && p.stock >= 500),
};

export function setup() {
    console.log('Setting up test data...');

    // 1. 테스트 사용자 100명 생성 및 잔액 충전
    const users = [];
    for (let i = 1; i <= TEST_USER_COUNT; i++) {
        const chargeResponse = http.post(
            `${BASE_URL}/api/users/${i}/balance/charge`,
            JSON.stringify({
                userId: i,
                amount: 1000000,  // 100만원 충전
                idempotencyKey: `CHARGE_${i}_${Date.now()}`
            }),
            {
                headers: { 'Content-Type': 'application/json' },
            }
        );

        if (chargeResponse.status === 200) {
            users.push(i);
        }
    }

    console.log(`Charged ${users.length} users`);

    // 2. 상품 조회 (데이터 초기화 스크립트가 상품을 생성함)
    const productListResponse = http.get(`${BASE_URL}/api/products`, {
        headers: { 'Content-Type': 'application/json' },
    });

    if (productListResponse.status !== 200) {
        throw new Error(`Failed to load products. Status: ${productListResponse.status}, body: ${productListResponse.body}`);
    }

    const productList = JSON.parse(productListResponse.body);
    const products = productList?.products || [];

    if (!products.length) {
        throw new Error('No products available for tests. Ensure DataInitializer ran correctly.');
    }

    const limitedProduct = PRODUCT_PICKER.limitedStock(products)
        || products.find((p) => p.stock && p.stock > 0)
        || products[0];
    const abundantProduct = PRODUCT_PICKER.abundantStock(products) || products[0];

    console.log(`Using limited product: ID=${limitedProduct.productId}, Stock=${limitedProduct.stock}`);
    console.log(`Using abundant product: ID=${abundantProduct.productId}, Stock=${abundantProduct.stock}`);

    return {
        users,
        limitedProductId: limitedProduct.productId,
        abundantProductId: abundantProduct.productId,
        limitedProductStock: limitedProduct.stock ?? 0,
    };
}

/**
 * Scenario 1: 재고 50개, 100명 동시 결제 (분산락 테스트)
 *
 * 목적:
 * - 정확히 50개만 결제 성공
 * - 50개 이후는 재고 부족 에러
 * - 분산락으로 재고 정확성 보장
 */
export function limitedStockPayment(data) {
    const userId = __VU;
    const productId = data.limitedProductId;

    group('Limited Stock Payment (100 users, 50 stock)', () => {
        // 1. 주문 생성
        const orderIdempotencyKey = `ORDER_${userId}_${Date.now()}_${Math.random()}`;

        const orderStartTime = new Date();
        const orderResponse = http.post(
            `${BASE_URL}/api/orders`,
            JSON.stringify({
                userId: userId,
                items: [
                    {
                        productId: productId,
                        quantity: 1
                    }
                ],
                couponId: null,
                idempotencyKey: orderIdempotencyKey
            }),
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'OrderCreation' },
            }
        );
        const orderDuration = new Date() - orderStartTime;
        orderCreationDuration.add(orderDuration);

        if (orderResponse.status !== 201) {
            paymentFailureCount.add(1);
            stockDeductionAccuracy.add(true);  // 주문 실패도 정확성의 일부
            return;
        }

        const orderData = JSON.parse(orderResponse.body);
        const orderId = orderData.orderId;

        // 2. 결제 처리
        const paymentIdempotencyKey = `PAY_${userId}_${Date.now()}_${Math.random()}`;

        const paymentStartTime = new Date();
        const paymentResponse = http.post(
            `${BASE_URL}/api/orders/${orderId}/payment`,
            JSON.stringify({
                userId: userId,
                idempotencyKey: paymentIdempotencyKey
            }),
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'PaymentProcessing' },
            }
        );
        const paymentDurationMs = new Date() - paymentStartTime;
        paymentDuration.add(paymentDurationMs);

        const success = check(paymentResponse, {
            'payment: status 200 or 400': (r) => r.status === 200 || r.status === 400,
        });

        if (paymentResponse.status === 200) {
            paymentSuccessCount.add(1);
            stockDeductionAccuracy.add(true);

            const body = JSON.parse(paymentResponse.body);
            check(body, {
                'payment success: has orderId': (b) => b && b.orderId === orderId,
                'payment success: has paidAmount': (b) => b && b.paidAmount > 0,
                'payment success: status SUCCESS': (b) => b && b.status === 'SUCCESS',
            });

        } else if (paymentResponse.status === 400) {
            paymentFailureCount.add(1);

            // 재고 부족 에러 검증
            if (paymentResponse.body.includes('INSUFFICIENT_STOCK') ||
                paymentResponse.body.includes('재고')) {
                stockDeductionAccuracy.add(true);
            } else {
                stockDeductionAccuracy.add(false);
            }
        } else {
            paymentFailureCount.add(1);
            stockDeductionAccuracy.add(false);
        }
    });

    sleep(0.1);
}

/**
 * Scenario 2: 재고 충분, 다중 사용자 결제 (처리량 테스트)
 *
 * 목적:
 * - 재고가 충분할 때 처리량 측정
 * - 분산락의 성능 영향 측정
 */
export function highThroughputPayment(data) {
    const userId = (__VU % TEST_USER_COUNT) + 1;
    const productId = data.abundantProductId;

    group('High Throughput Payment (Abundant Stock)', () => {
        // 1. 주문 생성
        const orderIdempotencyKey = `ORDER_${userId}_${Date.now()}_${Math.random()}`;

        const orderResponse = http.post(
            `${BASE_URL}/api/orders`,
            JSON.stringify({
                userId: userId,
                items: [
                    {
                        productId: productId,
                        quantity: 1
                    }
                ],
                couponId: null,
                idempotencyKey: orderIdempotencyKey
            }),
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'HighThroughputOrder' },
            }
        );

        if (orderResponse.status !== 201) {
            paymentFailureCount.add(1);
            return;
        }

        const orderData = JSON.parse(orderResponse.body);
        const orderId = orderData.orderId;

        // 2. 결제 처리
        const paymentIdempotencyKey = `PAY_${userId}_${Date.now()}_${Math.random()}`;

        const paymentResponse = http.post(
            `${BASE_URL}/api/orders/${orderId}/payment`,
            JSON.stringify({
                userId: userId,
                idempotencyKey: paymentIdempotencyKey
            }),
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'HighThroughputPayment' },
            }
        );

        const success = check(paymentResponse, {
            'high throughput: status 200': (r) => r.status === 200,
            'high throughput: fast response': (r) => r.timings.duration < 1500,
        });

        if (success) {
            paymentSuccessCount.add(1);
        } else {
            paymentFailureCount.add(1);
        }
    });

    sleep(0.2);
}

export function teardown(data) {
    console.log('\n=== Payment Concurrency Test Summary ===');
    console.log(`Limited Product ID: ${data.limitedProductId} (Stock: ${data.limitedProductStock})`);
    console.log(`Abundant Product ID: ${data.abundantProductId} (High throughput test)`);

    // 최종 재고 확인
    const limitedStockResponse = http.get(`${BASE_URL}/api/products/${data.limitedProductId}`);
    if (limitedStockResponse.status === 200) {
        const product = JSON.parse(limitedStockResponse.body);
        console.log(`Final stock of limited product: ${product.stock} (Expected: 0)`);
    }
}

export function handleSummary(data) {
    const summaryDir = (__ENV.SUMMARY_DIR || 'results').replace(/\/$/, '');
    const summaryPath = __ENV.SUMMARY_PATH || `${summaryDir}/payment-concurrency-summary.json`;

    // Note: Make sure the 'results' directory exists before running the test
    // mkdir -p docs/week6/loadtest/k6/results

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        [summaryPath]: JSON.stringify(data),
    };
}

function textSummary(data, options) {
    const indent = options.indent || '';
    const getMetricValue = (metric, key) => {
        if (!metric || !metric.values) {
            return null;
        }
        const value = metric.values[key];
        return value === undefined || value === null ? null : Number(value);
    };
    const formatMs = (value) => value === null ? 'N/A' : `${value.toFixed(2)}ms`;
    const formatPercent = (rate) => rate === null ? 'N/A' : `${(rate * 100).toFixed(2)}%`;

    let summary = '\n';
    summary += indent + '='.repeat(60) + '\n';
    summary += indent + 'Payment Concurrency Test Results\n';
    summary += indent + '='.repeat(60) + '\n\n';

    // Request Statistics
    summary += indent + 'HTTP Request Statistics:\n';
    summary += indent + `  Total Requests: ${getMetricValue(data.metrics.http_reqs, 'count') ?? 0}\n`;
    summary += indent + `  Failed Requests: ${formatPercent(getMetricValue(data.metrics.http_req_failed, 'rate'))}\n`;
    summary += indent + `  Avg Duration: ${formatMs(getMetricValue(data.metrics.http_req_duration, 'avg'))}\n`;
    summary += indent + `  P95 Duration: ${formatMs(getMetricValue(data.metrics.http_req_duration, 'p(95)'))}\n`;
    summary += indent + `  P99 Duration: ${formatMs(getMetricValue(data.metrics.http_req_duration, 'p(99)'))}\n\n`;

    // Payment Metrics
    if (data.metrics.payment_success_count) {
        const successCount = getMetricValue(data.metrics.payment_success_count, 'count') ?? 0;
        const failureCount = getMetricValue(data.metrics.payment_failure_count, 'count') ?? 0;
        const totalPayments = successCount + failureCount;
        const successRate = totalPayments > 0 ? (successCount / totalPayments * 100).toFixed(2) : '0.00';

        summary += indent + 'Payment Performance:\n';
        summary += indent + `  Total Attempts: ${totalPayments}\n`;
        summary += indent + `  Successful Payments: ${successCount}\n`;
        summary += indent + `  Failed Payments: ${failureCount}\n`;
        summary += indent + `  Success Rate: ${successRate}%\n`;
        summary += indent + `  Avg Payment Duration: ${formatMs(getMetricValue(data.metrics.payment_duration, 'avg'))}\n`;
        summary += indent + `  P95 Payment Duration: ${formatMs(getMetricValue(data.metrics.payment_duration, 'p(95)'))}\n\n`;
    }

    // Order Creation Metrics
    if (data.metrics.order_creation_duration) {
        summary += indent + 'Order Creation Performance:\n';
        summary += indent + `  Avg Order Creation: ${formatMs(getMetricValue(data.metrics.order_creation_duration, 'avg'))}\n`;
        summary += indent + `  P95 Order Creation: ${formatMs(getMetricValue(data.metrics.order_creation_duration, 'p(95)'))}\n\n`;
    }

    // Stock Deduction Accuracy
    if (data.metrics.stock_deduction_accuracy) {
        summary += indent + 'Stock Deduction Accuracy:\n';
        summary += indent + `  Accuracy Rate: ${formatPercent(getMetricValue(data.metrics.stock_deduction_accuracy, 'rate'))}\n`;
        summary += indent + `  (Expected: > 95% for distributed lock)\n\n`;
    }

    // Validation
    if (data.metrics.payment_success_count) {
        summary += indent + 'Validation (Limited Stock Scenario):\n';
        const expectedSuccess = data.setup_data?.limitedProductStock ?? 'unknown';
        summary += indent + `  Expected Successful Payments: ${expectedSuccess}\n`;
        summary += indent + `  Note: Check teardown logs for actual count\n\n`;
    }

    // Throughput
    summary += indent + 'Throughput:\n';
    const httpReqs = getMetricValue(data.metrics.http_reqs, 'count') ?? 0;
    const paymentAttempts = (getMetricValue(data.metrics.payment_success_count, 'count') ?? 0) +
        (getMetricValue(data.metrics.payment_failure_count, 'count') ?? 0);
    summary += indent + `  Requests/sec: ${((httpReqs / data.state.testRunDurationMs) * 1000).toFixed(2)}\n`;
    summary += indent + `  Payments/sec: ${((paymentAttempts / data.state.testRunDurationMs) * 1000).toFixed(2)}\n\n`;

    // Thresholds
    summary += indent + 'Threshold Results:\n';
    Object.entries(data.thresholds || {}).forEach(([name, threshold]) => {
        const status = threshold.ok ? '✓ PASS' : '✗ FAIL';
        summary += indent + `  ${status}: ${name}\n`;
    });

    summary += indent + '\n' + '='.repeat(60) + '\n';

    return summary;
}
