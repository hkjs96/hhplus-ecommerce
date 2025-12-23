import http from 'k6/http';
import { check, group, sleep, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom Metrics
const cacheHitDuration = new Trend('cache_hit_duration');
const cacheEvictDuration = new Trend('cache_evict_duration');
const cacheConsistencyRate = new Rate('cache_consistency_rate');
const cacheErrors = new Counter('cache_errors');

// Test Configuration
export const options = {
    stages: [
        { duration: '30s', target: 50 },    // Ramp up to 50 VUs
        { duration: '1m', target: 100 },    // Ramp up to 100 VUs
        { duration: '2m', target: 100 },    // Stay at 100 VUs
        { duration: '30s', target: 0 },     // Ramp down
    ],
    thresholds: {
        'http_req_duration': ['p(95)<300', 'p(99)<500'],
        'cache_hit_duration': ['p(95)<100'],                // Cache hit under 100ms
        'cache_evict_duration': ['p(95)<200'],              // Update under 200ms
        'cache_consistency_rate': ['rate>0.95'],            // 95% consistency
        'http_req_failed': ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Test Data
const users = [];
const products = [1, 2, 3, 4, 5];

export function setup() {
    console.log('Setting up test users...');

    // Create test users and charge balance
    for (let i = 1; i <= 10; i++) {
        const chargeResponse = http.post(
            `${BASE_URL}/api/users/${i}/balance/charge`,
            JSON.stringify({
                userId: i,
                amount: 1000000,
                idempotencyKey: `CHARGE_${i}_${Date.now()}`
            }),
            {
                headers: { 'Content-Type': 'application/json' },
            }
        );

        if (chargeResponse.status === 200) {
            users.push(i);
        } else {
            console.error(`Failed to charge user ${i}: status=${chargeResponse.status}`);
        }
    }

    console.log(`Created ${users.length} test users`);
    if (users.length === 0) {
        throw new Error('테스트 사용자 생성에 모두 실패했습니다. BASE_URL이 올바르고 서버가 실행 중인지 확인하세요.');
    }

    return { users, products };
}

export default function (data) {
    if (!data || !data.users || data.users.length === 0) {
        fail('setup()에서 사용자 데이터를 전달받지 못했습니다. 서버가 실행 중인지 확인하세요.');
    }

    // Each VU uses a different user
    const userId = data.users[__VU % data.users.length];
    const productId = data.products[Math.floor(Math.random() * data.products.length)];

    group('Cart Query (Cache: 1 day)', () => {
        const startTime = new Date();
        const response = http.get(`${BASE_URL}/api/cart?userId=${userId}`, {
            tags: { name: 'GetCart' },
        });
        const duration = new Date() - startTime;

        const success = check(response, {
            'get cart: status 200': (r) => r.status === 200,
            'get cart: has cart data': (r) => {
                const body = JSON.parse(r.body);
                return body.userId === userId;
            },
            'get cart: fast response': (r) => r.timings.duration < 300,
        });

        if (success) {
            cacheHitDuration.add(duration);
        } else {
            cacheErrors.add(1);
        }
    });

    group('Cart Update with Cache Eviction', () => {
        // 1. Get current cart state
        const getResponse1 = http.get(`${BASE_URL}/api/cart?userId=${userId}`);
        const cartBefore = JSON.parse(getResponse1.body);

        // 2. Add item to cart (should evict cache)
        const addStartTime = new Date();
        const addResponse = http.post(
            `${BASE_URL}/api/cart/items`,
            JSON.stringify({
                userId: userId,
                productId: productId,
                quantity: 1
            }),
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'AddToCart' },
            }
        );
        const addDuration = new Date() - addStartTime;

        const addSuccess = check(addResponse, {
            'add to cart: status 201': (r) => r.status === 201,
            'add to cart: updated correctly': (r) => {
                const body = JSON.parse(r.body);
                return body.items && body.items.some(item => item.productId === productId);
            },
        });

        if (addSuccess) {
            cacheEvictDuration.add(addDuration);

            // 3. Verify cache consistency (get cart again)
            sleep(0.1); // Small delay to ensure cache eviction completed

            const getResponse2 = http.get(`${BASE_URL}/api/cart?userId=${userId}`);
            const cartAfter = JSON.parse(getResponse2.body);

            const isConsistent = check({ cartBefore, cartAfter, productId }, {
                'cache consistency: cart updated': (data) => {
                    // Cart should have more items or updated quantity
                    const beforeCount = data.cartBefore.items.length;
                    const afterCount = data.cartAfter.items.length;

                    // Check if item was added
                    const itemExists = data.cartAfter.items.some(
                        item => item.productId === data.productId
                    );

                    return afterCount >= beforeCount && itemExists;
                },
            });

            cacheConsistencyRate.add(isConsistent);
        } else {
            cacheErrors.add(1);
        }
    });

    group('Cart Item Update with Cache Eviction', () => {
        // 1. Get cart
        const getResponse = http.get(`${BASE_URL}/api/cart?userId=${userId}`);
        if (getResponse.status !== 200) {
            cacheErrors.add(1);
            return;
        }

        const cart = JSON.parse(getResponse.body);
        if (!cart.items || cart.items.length === 0) {
            return; // No items to update
        }

        const cartItem = cart.items[0];
        const targetProductId = cartItem.productId;

        // 2. Update quantity (should evict cache)
        const newQuantity = cartItem.quantity + 1;
        const updateStartTime = new Date();
        const updateResponse = http.put(
            `${BASE_URL}/api/cart/items`,
            JSON.stringify({
                userId: userId,
                productId: targetProductId,
                quantity: newQuantity
            }),
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'UpdateCartItem' },
            }
        );
        const updateDuration = new Date() - updateStartTime;

        const updateSuccess = check(updateResponse, {
            'update cart item: status 200': (r) => r.status === 200,
            'update cart item: quantity updated': (r) => {
                const body = JSON.parse(r.body);
                return body.quantity === newQuantity;
            },
        });

        if (updateSuccess) {
            cacheEvictDuration.add(updateDuration);

            // 3. Verify cache consistency
            sleep(0.1);

            const getResponse2 = http.get(`${BASE_URL}/api/cart?userId=${userId}`);
            const updatedCart = JSON.parse(getResponse2.body);

            const isConsistent = check({ cartItem, updatedCart, newQuantity }, {
                'cache consistency: quantity updated': (data) => {
                    const item = data.updatedCart.items.find(i => i.productId === data.cartItem.productId);
                    return item && item.quantity === data.newQuantity;
                },
            });

            cacheConsistencyRate.add(isConsistent);
        } else {
            cacheErrors.add(1);
        }
    });

    group('Cart Item Removal with Cache Eviction', () => {
        // 1. Get cart
        const getResponse = http.get(`${BASE_URL}/api/cart?userId=${userId}`);
        if (getResponse.status !== 200) {
            cacheErrors.add(1);
            return;
        }

        const cart = JSON.parse(getResponse.body);
        if (!cart.items || cart.items.length === 0) {
            return; // No items to remove
        }

        const cartItemToRemove = cart.items[cart.items.length - 1]; // Remove last item
        const targetProductId = cartItemToRemove.productId;

        // 2. Remove item (should evict cache)
        const removeStartTime = new Date();
        const removeResponse = http.del(
            `${BASE_URL}/api/cart/items`,
            JSON.stringify({
                userId: userId,
                productId: targetProductId
            }),
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'RemoveCartItem' },
            }
        );
        const removeDuration = new Date() - removeStartTime;

        const removeSuccess = check(removeResponse, {
            'remove cart item: status 200': (r) => r.status === 200,
        });

        if (removeSuccess) {
            cacheEvictDuration.add(removeDuration);

            // 3. Verify cache consistency
            sleep(0.1);

            const getResponse2 = http.get(`${BASE_URL}/api/cart?userId=${userId}`);
            const updatedCart = JSON.parse(getResponse2.body);

            const isConsistent = check({ cartItemToRemove, updatedCart }, {
                'cache consistency: item removed': (data) => {
                    const itemExists = data.updatedCart.items.some(
                        item => item.productId === data.cartItemToRemove.productId
                    );
                    return !itemExists; // Item should not exist
                },
            });

            cacheConsistencyRate.add(isConsistent);
        } else {
            cacheErrors.add(1);
        }
    });

    sleep(0.3);
}

export function teardown(data) {
    console.log('\n=== Cart Cache Test Completed ===');
    console.log(`Tested with ${data.users.length} users`);
}

export function handleSummary(data) {
    const summaryDir = (__ENV.SUMMARY_DIR || 'results').replace(/\/$/, '');
    const summaryPath = `${summaryDir}/cart-cache-summary.json`;

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
    summary += indent + 'Cart Cache Performance Test Results\n';
    summary += indent + '='.repeat(60) + '\n\n';

    // Request Statistics
    summary += indent + 'HTTP Request Statistics:\n';
    summary += indent + `  Total Requests: ${getMetricValue(data.metrics.http_reqs, 'count') ?? 0}\n`;
    summary += indent + `  Failed Requests: ${formatPercent(getMetricValue(data.metrics.http_req_failed, 'rate'))}\n`;
    summary += indent + `  Avg Duration: ${formatMs(getMetricValue(data.metrics.http_req_duration, 'avg'))}\n`;
    summary += indent + `  P95 Duration: ${formatMs(getMetricValue(data.metrics.http_req_duration, 'p(95)'))}\n`;
    summary += indent + `  P99 Duration: ${formatMs(getMetricValue(data.metrics.http_req_duration, 'p(99)'))}\n\n`;

    // Cache Performance
    if (data.metrics.cache_hit_duration && data.metrics.cache_hit_duration.values) {
        summary += indent + 'Cache Performance:\n';
        summary += indent + `  Cache Hit Avg: ${formatMs(getMetricValue(data.metrics.cache_hit_duration, 'avg'))}\n`;
        summary += indent + `  Cache Hit P95: ${formatMs(getMetricValue(data.metrics.cache_hit_duration, 'p(95)'))}\n`;

        if (data.metrics.cache_evict_duration && data.metrics.cache_evict_duration.values) {
            summary += indent + `  Cache Evict Avg: ${formatMs(getMetricValue(data.metrics.cache_evict_duration, 'avg'))}\n`;
            summary += indent + `  Cache Evict P95: ${formatMs(getMetricValue(data.metrics.cache_evict_duration, 'p(95)'))}\n`;
        }

        if (data.metrics.cache_consistency_rate && data.metrics.cache_consistency_rate.values) {
            summary += indent + `  Cache Consistency Rate: ${formatPercent(getMetricValue(data.metrics.cache_consistency_rate, 'rate'))}\n`;
        }

        if (data.metrics.cache_errors && data.metrics.cache_errors.values) {
            summary += indent + `  Cache Errors: ${getMetricValue(data.metrics.cache_errors, 'count') ?? 0}\n`;
        }

        summary += '\n';
    }

    // Throughput
    summary += indent + 'Throughput:\n';
    summary += indent + `  Requests/sec: ${(data.metrics.http_reqs.values.count / data.state.testRunDurationMs * 1000).toFixed(2)}\n\n`;

    // Operation Breakdown
    summary += indent + 'Operation Breakdown:\n';
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
