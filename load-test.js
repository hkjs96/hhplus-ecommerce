import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// 커스텀 메트릭 정의
const errorRate = new Rate('errors');
const orderDuration = new Trend('order_duration');
const paymentDuration = new Trend('payment_duration');
const couponDuration = new Trend('coupon_duration');
const productDuration = new Trend('product_duration');

const orderSuccess = new Counter('order_success');
const orderFailure = new Counter('order_failure');
const paymentSuccess = new Counter('payment_success');
const paymentFailure = new Counter('payment_failure');

// 부하 테스트 시나리오 설정
// 목표: 재고 소진 케이스 테스트 + 쿠폰 동시성 검증 (150명 vs 100개)
export const options = {
    stages: [
        // Warm-up: 10초 동안 VU 0 → 10
        { duration: '10s', target: 10 },

        // Ramp-up: 30초 동안 VU 10 → 50
        { duration: '30s', target: 50 },

        // Sustained Load: 1분 동안 VU 50 유지
        { duration: '1m', target: 50 },

        // Peak Load: 30초 동안 VU 50 → 100
        { duration: '30s', target: 100 },

        // Sustained Peak: 1분 동안 VU 100 유지 (재고 소진 발생 구간)
        { duration: '1m', target: 100 },

        // Ramp-down: 30초 동안 VU 100 → 0
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        // HTTP 요청 성공률 95% 이상 (409 재고 부족은 실패로 카운트되나 정상 동작)
        http_req_failed: ['rate<0.05'],

        // HTTP 응답 시간
        http_req_duration: [
            'p(50)<200',   // P50: 200ms 미만
            'p(95)<500',   // P95: 500ms 미만
            'p(99)<1000',  // P99: 1초 미만
        ],

        // 시스템 에러율 5% 미만 (409 재고 부족은 제외)
        'errors': ['rate<0.05'],

        // 주문 처리 시간
        'order_duration': ['p(95)<500'],

        // 결제 처리 시간
        'payment_duration': ['p(95)<1000'],
    },
};

// 환경 변수 설정
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TEST_DATA = {
    couponId: 1,  // 쿠폰 동시성 테스트용
};

// 랜덤 사용자 ID 생성 함수 (1~150) - 동시성 테스트용 (150명 vs 100개 쿠폰)
function getRandomUserId() {
    return Math.floor(Math.random() * 150) + 1;
}

// 랜덤 상품 ID 생성 함수 (1~21) - 21개 상품 중 랜덤 선택 (품절 상품 포함)
function getRandomProductId() {
    return Math.floor(Math.random() * 21) + 1;
}

// 랜덤 수량 생성 함수 (1~3) - 재고 소진 시나리오 테스트
function getRandomQuantity() {
    return Math.floor(Math.random() * 3) + 1;
}

// 테스트 시작 시 초기 데이터 확인
export function setup() {
    console.log('=== Load Test Setup ===');
    console.log(`Base URL: ${BASE_URL}`);
    console.log(`Test will run with ${options.stages.length} stages`);

    // 헬스 체크
    const healthCheck = http.get(`${BASE_URL}/actuator/health`);
    if (healthCheck.status !== 200) {
        throw new Error('Application is not healthy');
    }

    console.log('Application health check passed');
    return { timestamp: new Date().toISOString() };
}

// 메인 테스트 시나리오
export default function (data) {
    const headers = {
        'Content-Type': 'application/json',
    };

    // 시나리오 1: 상품 조회 (60% 트래픽)
    if (Math.random() < 0.6) {
        testProductList(headers);
        sleep(1);
        return;
    }

    // 시나리오 2: 주문 + 결제 플로우 (30% 트래픽) - 재고 소진 케이스 테스트
    if (Math.random() < 0.3 / 0.4) {
        testOrderAndPaymentFlow(headers);
        sleep(2);
        return;
    }

    // 시나리오 3: 쿠폰 발급 (10% 트래픽) - 동시성 테스트
    testCouponIssuance(headers);
    sleep(1);
}

// 상품 목록 조회 테스트
function testProductList(headers) {
    const start = new Date().getTime();

    const response = http.get(`${BASE_URL}/api/products`, {
        headers: headers,
        tags: { name: 'GetProducts' },
    });

    const duration = new Date().getTime() - start;
    productDuration.add(duration);

    const success = check(response, {
        'Product list status is 200': (r) => r.status === 200,
        'Product list has data': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.products && Array.isArray(body.products);
            } catch (e) {
                return false;
            }
        },
    });

    if (!success) {
        errorRate.add(1);
        console.error(`Product list failed: ${response.status} - ${response.body}`);
    } else {
        errorRate.add(0);
    }
}

// 주문 + 결제 플로우 테스트
function testOrderAndPaymentFlow(headers) {
    const userId = getRandomUserId();  // 랜덤 사용자 (1~150)
    const productId = getRandomProductId();  // 랜덤 상품 (1~21)
    const quantity = getRandomQuantity();  // 랜덤 수량 (1~3)

    // Step 1: 주문 생성
    const orderStart = new Date().getTime();

    const orderPayload = JSON.stringify({
        userId: userId,
        items: [
            {
                productId: productId,
                quantity: quantity
            }
        ],
        couponId: null
    });

    const orderResponse = http.post(
        `${BASE_URL}/api/orders`,
        orderPayload,
        {
            headers: headers,
            tags: { name: 'CreateOrder' },
        }
    );

    const orderDurationMs = new Date().getTime() - orderStart;
    orderDuration.add(orderDurationMs);

    const orderCheckSuccess = check(orderResponse, {
        'Order creation status is 201 or 409': (r) => r.status === 201 || r.status === 409,
        'Order has orderId': (r) => {
            if (r.status === 409) return true;  // 재고 부족은 정상 케이스
            try {
                const body = JSON.parse(r.body);
                return body.orderId != null;
            } catch (e) {
                return false;
            }
        },
    });

    // 409 (재고 부족)는 정상적인 비즈니스 응답
    if (orderResponse.status === 409) {
        errorRate.add(0);  // 에러율에 포함하지 않음
        orderFailure.add(1);  // 실패 카운트만 증가
        return;
    }

    if (!orderCheckSuccess) {
        errorRate.add(1);
        orderFailure.add(1);
        console.error(`Order creation failed: ${orderResponse.status} - ${orderResponse.body}`);
        return;
    }

    errorRate.add(0);
    orderSuccess.add(1);

    // Step 2: 결제 처리
    let orderId;
    try {
        const orderBody = JSON.parse(orderResponse.body);
        orderId = orderBody.orderId;
    } catch (e) {
        console.error('Failed to parse order response');
        return;
    }

    const paymentStart = new Date().getTime();

    const paymentPayload = JSON.stringify({
        userId: userId,  // 주문과 동일한 사용자
        idempotencyKey: `test-${Date.now()}-${Math.random()}`,
        paymentMethod: 'BALANCE'
    });

    const paymentResponse = http.post(
        `${BASE_URL}/api/orders/${orderId}/payment`,
        paymentPayload,
        {
            headers: headers,
            tags: { name: 'ProcessPayment' },
        }
    );

    const paymentDurationMs = new Date().getTime() - paymentStart;
    paymentDuration.add(paymentDurationMs);

    const paymentSuccessCheck = check(paymentResponse, {
        'Payment status is 200 or 400': (r) => r.status === 200 || r.status === 400,
        'Payment has result': (r) => r.body && r.body.length > 0,
    });

    if (paymentResponse.status === 200) {
        paymentSuccess.add(1);
        errorRate.add(0);
    } else {
        paymentFailure.add(1);
        if (paymentResponse.status >= 500) {
            errorRate.add(1);
        } else {
            errorRate.add(0);  // 4xx는 비즈니스 에러이므로 에러율에 포함하지 않음
        }
    }
}

// 쿠폰 발급 테스트
function testCouponIssuance(headers) {
    const userId = getRandomUserId();  // 랜덤 사용자 (1~10)
    const start = new Date().getTime();

    const payload = JSON.stringify({
        userId: userId,
    });

    const response = http.post(
        `${BASE_URL}/api/coupons/${TEST_DATA.couponId}/issue`,
        payload,
        {
            headers: headers,
            tags: { name: 'IssueCoupon' },
        }
    );

    const duration = new Date().getTime() - start;
    couponDuration.add(duration);

    // 쿠폰 발급은 중복 발급 시 409 Conflict가 정상
    const success = check(response, {
        'Coupon issuance status is 200 or 409': (r) => r.status === 200 || r.status === 409,
    });

    if (!success && response.status >= 500) {
        errorRate.add(1);
        console.error(`Coupon issuance failed: ${response.status} - ${response.body}`);
    } else {
        errorRate.add(0);
    }
}

// 테스트 종료 후 요약
export function teardown(data) {
    console.log('=== Load Test Completed ===');
    console.log(`Started at: ${data.timestamp}`);
    console.log(`Completed at: ${new Date().toISOString()}`);
    console.log('Check the metrics above for detailed results');
}

/**
 * K6 부하 테스트 실행 방법
 *
 * 1. K6 설치 (macOS/Linux):
 *    brew install k6
 *    # 또는
 *    sudo apt-get install k6
 *
 * 2. 기본 실행:
 *    k6 run load-test.js
 *
 * 3. 환경 변수 지정:
 *    k6 run --env BASE_URL=http://localhost:8080 load-test.js
 *
 * 4. 결과를 JSON으로 저장:
 *    k6 run --out json=load-test-results.json load-test.js
 *
 * 5. 특정 단계만 테스트 (예: 10 VUs, 30초):
 *    k6 run --vus 10 --duration 30s load-test.js
 *
 * 6. 요약 출력:
 *    k6 run --summary-export=summary.json load-test.js
 *
 * 테스트 시나리오:
 * - 상품 조회: 60% (21개 상품 랜덤)
 * - 주문+결제: 30% (21개 상품 랜덤, 1-3개 수량, 재고 소진 케이스 포함)
 * - 쿠폰 발급: 10% (150명 vs 100개, 동시성 테스트)
 *
 * 예상 메트릭:
 * - TPS (Transactions Per Second): http_reqs 메트릭 참조
 * - P50/P95/P99 레이턴시: http_req_duration 메트릭 참조
 * - 시스템 에러율: errors 메트릭 참조 (409는 제외됨)
 * - 성공/실패 카운트: order_success, order_failure (재고 소진은 정상 실패)
 * - 쿠폰 동시성: 정확히 100개만 발급, 중복 0건 예상
 */
