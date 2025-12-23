import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom Metrics
const cacheHitDuration = new Trend('cache_hit_duration');
const cacheMissDuration = new Trend('cache_miss_duration');
const cacheHitRate = new Rate('cache_hit_rate');
const cacheErrors = new Counter('cache_errors');

// Test Configuration
export const options = {
    stages: [
        { duration: '30s', target: 100 },   // Ramp up to 100 VUs
        { duration: '1m', target: 200 },    // Ramp up to 200 VUs
        { duration: '3m', target: 200 },    // Stay at 200 VUs (sustained load)
        { duration: '30s', target: 0 },     // Ramp down
    ],
    thresholds: {
        'http_req_duration': ['p(95)<200', 'p(99)<500'],   // 95% under 200ms
        'cache_hit_duration': ['p(95)<50'],                 // Cache hit under 50ms
        'cache_miss_duration': ['p(95)<300'],               // Cache miss under 300ms
        'cache_hit_rate': ['rate>0.9'],                     // 90% cache hit rate
        'http_req_failed': ['rate<0.01'],                   // Less than 1% failures
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Test Scenarios
export function setup() {
    console.log('Warming up cache...');

    // Warm up cache with initial requests
    http.get(`${BASE_URL}/api/products`);
    http.get(`${BASE_URL}/api/products/1`);
    http.get(`${BASE_URL}/api/products/top`);

    sleep(1);
    console.log('Cache warmed up. Starting load test...');

    return {};
}

export default function () {
    const parseJson = (r) => {
        try {
            return JSON.parse(r.body);
        } catch (e) {
            return null;
        }
    };

    group('Product List Query (Cache: 1 hour)', () => {
        const startTime = new Date();
        const response = http.get(`${BASE_URL}/api/products`, {
            tags: { name: 'ProductList' },
        });
        const duration = new Date() - startTime;
        const body = parseJson(response);

        const success = check(response, {
            'product list: status 200': (r) => r.status === 200,
            'product list: has products': () => body && body.products && body.products.length > 0,
            'product list: response under 200ms': (r) => r.timings.duration < 200,
        });

        if (success) {
            // Assume cache hit if response is very fast (< 50ms)
            if (duration < 50) {
                cacheHitDuration.add(duration);
                cacheHitRate.add(true);
            } else {
                cacheMissDuration.add(duration);
                cacheHitRate.add(false);
            }
        } else {
            cacheErrors.add(1);
        }
    });

    group('Product Detail Query (Cache: 1 hour)', () => {
        // Randomly query one of 10 products
        const productId = Math.floor(Math.random() * 10) + 1;

        const startTime = new Date();
        const response = http.get(`${BASE_URL}/api/products/${productId}`, {
            tags: { name: 'ProductDetail' },
        });
        const duration = new Date() - startTime;
        const body = parseJson(response);

        const success = check(response, {
            'product detail: status 200': (r) => r.status === 200,
            'product detail: has product data': () => body && body.id === productId,
            'product detail: response under 200ms': (r) => r.timings.duration < 200,
        });

        if (success) {
            if (duration < 50) {
                cacheHitDuration.add(duration);
                cacheHitRate.add(true);
            } else {
                cacheMissDuration.add(duration);
                cacheHitRate.add(false);
            }
        } else {
            cacheErrors.add(1);
        }
    });

    group('Top Products Query (Cache: 5 minutes)', () => {
        const startTime = new Date();
        const response = http.get(`${BASE_URL}/api/products/top`, {
            tags: { name: 'TopProducts' },
        });
        const duration = new Date() - startTime;
        const body = parseJson(response);

        const success = check(response, {
            'top products: status 200': (r) => r.status === 200,
            'top products: has products': () => body && body.products && body.products.length > 0,
            'top products: response under 200ms': (r) => r.timings.duration < 200,
        });

        if (success) {
            if (duration < 50) {
                cacheHitDuration.add(duration);
                cacheHitRate.add(true);
            } else {
                cacheMissDuration.add(duration);
                cacheHitRate.add(false);
            }
        } else {
            cacheErrors.add(1);
        }
    });

    group('Category Filter Query (Cache: 1 hour)', () => {
        // 카테고리 값은 DB에 저장된 한글 카테고리명과 일치해야 필터링 결과가 나옵니다.
        const categories = ['전자제품', '가구', '도서', '의류', '잡화'];
        const category = categories[Math.floor(Math.random() * categories.length)];
        const startTime = new Date();
        const response = http.get(`${BASE_URL}/api/products`, {
            tags: { name: 'CategoryFilter' },
            params: { category },
        });
        const duration = new Date() - startTime;
        const body = parseJson(response);

        const success = check(response, {
            'category filter: status 200': (r) => r.status === 200,
            'category filter: filtered correctly': () => {
                if (!body || !body.products) return false;
                return body.products.every(p => p.category === category);
            },
        });

        if (success) {
            if (duration < 50) {
                cacheHitDuration.add(duration);
                cacheHitRate.add(true);
            } else {
                cacheMissDuration.add(duration);
                cacheHitRate.add(false);
            }
        } else {
            cacheErrors.add(1);
        }
    });

    sleep(0.1);
}

export function teardown(data) {
    console.log('\n=== Cache Performance Test Completed ===');
}

export function handleSummary(data) {
    const summaryFile = __ENV.SUMMARY_PATH || 'product-cache-summary.json';
    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        [summaryFile]: JSON.stringify(data, null, 2),
    };
}

function textSummary(data, options) {
    const indent = options.indent || '';
    const safeFixed = (value) => {
        if (value === undefined || value === null || Number.isNaN(value)) {
            return 'N/A';
        }
        return Number(value).toFixed(2);
    };

    let summary = '\n';
    summary += indent + '='.repeat(60) + '\n';
    summary += indent + 'Product Query Cache Performance Test Results\n';
    summary += indent + '='.repeat(60) + '\n\n';

    // Request Statistics
    summary += indent + 'HTTP Request Statistics:\n';
    summary += indent + `  Total Requests: ${data.metrics.http_reqs.values.count}\n`;
    summary += indent + `  Failed Requests: ${safeFixed(data.metrics.http_req_failed.values.rate * 100)}%\n`;
    summary += indent + `  Avg Duration: ${safeFixed(data.metrics.http_req_duration.values.avg)}ms\n`;
    summary += indent + `  P95 Duration: ${safeFixed(data.metrics.http_req_duration.values['p(95)'])}ms\n`;
    summary += indent + `  P99 Duration: ${safeFixed(data.metrics.http_req_duration.values['p(99)'])}ms\n\n`;

    // Cache Performance
    if (data.metrics.cache_hit_duration && data.metrics.cache_miss_duration) {
        summary += indent + 'Cache Performance:\n';
        summary += indent + `  Cache Hit Rate: ${safeFixed(data.metrics.cache_hit_rate.values.rate * 100)}%\n`;
        summary += indent + `  Cache Hit Avg: ${safeFixed(data.metrics.cache_hit_duration.values.avg)}ms\n`;
        summary += indent + `  Cache Miss Avg: ${safeFixed(data.metrics.cache_miss_duration.values.avg)}ms\n`;

        const hitAvg = data.metrics.cache_hit_duration.values.avg;
        const missAvg = data.metrics.cache_miss_duration.values.avg;
        const improvement = hitAvg && missAvg ? missAvg / hitAvg : null;
        summary += indent + `  Performance Improvement: ${safeFixed(improvement)}x faster with cache\n`;
        summary += indent + `  Cache Errors: ${data.metrics.cache_errors.values.count}\n\n`;
    }

    // Throughput
    summary += indent + 'Throughput:\n';
    summary += indent + `  Requests/sec: ${safeFixed(data.metrics.http_reqs.values.count / data.state.testRunDurationMs * 1000)}\n`;
    summary += indent + `  Data Received: ${safeFixed(data.metrics.data_received.values.count / 1024 / 1024)} MB\n\n`;

    // Breakdown by Endpoint
    summary += indent + 'Breakdown by Endpoint:\n';
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
