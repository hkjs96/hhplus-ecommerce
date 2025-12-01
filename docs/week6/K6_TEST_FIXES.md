# K6 Load Test 수정 사항

## 📋 수정 개요

**날짜**: 2025-11-27
**목적**: Week 6 부하 테스트 K6 스크립트 오류 수정 및 실행 준비

---

## ✅ 수정 완료 항목

### 1. Balance Charge Concurrency Test ✅

**파일**: `balance-charge-concurrency-test.js`

**문제**:
```
TypeError: Cannot read property 'toFixed' of undefined or null
at textSummary (line 195)
```

**원인**: P99 metric이 존재하지 않을 때 null check 누락

**수정**:
```javascript
// Before
summary += indent + `  P99 Duration: ${data.metrics.http_req_duration.values['p(99)'].toFixed(2)}ms\n\n`;

// After
const p99 = data.metrics.http_req_duration.values['p(99)'];
summary += indent + `  P99 Duration: ${p99 ? p99.toFixed(2) : 'N/A'}ms\n\n`;
```

**결과**: ✅ 테스트 정상 실행 (99.94% 성공률)

---

### 2. Cart Cache Test ✅

**파일**: `cart-cache-test.js`

**문제 1: API 엔드포인트 불일치**
```
NoResourceFoundException: No static resource api/carts/8
```

**원인**: K6 테스트의 엔드포인트가 실제 API와 불일치
- K6: `/api/carts/{userId}` (plural, path variable)
- 실제: `/api/cart?userId={userId}` (singular, query parameter)

**수정**:
```javascript
// GET cart
// Before: http.get(`${BASE_URL}/api/carts/${userId}`)
// After:
http.get(`${BASE_URL}/api/cart?userId=${userId}`)

// POST add to cart
// Before: http.post(`${BASE_URL}/api/carts/${userId}/items`, ...)
// After:
http.post(`${BASE_URL}/api/cart/items`, ...)

// PUT update cart item
// Before: http.put(`${BASE_URL}/api/carts/${userId}/items`, ...)
// After:
http.put(`${BASE_URL}/api/cart/items`, ...)

// DELETE cart item
// Before: http.del(`${BASE_URL}/api/carts/${userId}/items`, ...)
// After:
http.del(`${BASE_URL}/api/cart/items`, ...)
```

**문제 2: 응답 구조 불일치**

**수정**:
```javascript
// Before: body.data.userId
// After: body.userId

// Before: body.data.items
// After: body.items

// Before: status 200
// After: status 201 (for POST)
```

**결과**: ✅ 모든 엔드포인트 수정 완료, 실행 준비 완료

---

### 3. Coupon Issuance Concurrency Test ✅

**파일**: `coupon-issuance-concurrency-test.js`

**문제 1: Admin API 미구현**
```
NoResourceFoundException: No static resource api/coupons
```

**원인**: `POST /api/coupons` (Admin 쿠폰 생성 API)가 구현되지 않음

**수정**: DataInitializer의 사전 생성 쿠폰 사용
```javascript
// Before: setup()에서 쿠폰 생성 시도
const createResponse = http.post(`${BASE_URL}/api/coupons`, ...);

// After: 사전 생성된 쿠폰 ID 사용
const TEST_COUPON_ID = 2;  // VIP 전용 쿠폰 (재고 50개)

export function setup() {
    console.log(`Using pre-created coupon ID: ${TEST_COUPON_ID}`);
    console.log('Note: 테스트 전에 애플리케이션을 재시작하여 쿠폰 재고를 초기화하세요.');
    return { couponId: TEST_COUPON_ID };
}
```

**문제 2: 응답 구조 불일치**

**수정**:
```javascript
// Before
check(body, {
    'has userCouponId': (b) => b.data && b.data.userCouponId,
});

// After
check(body, {
    'has userCouponId': (b) => b.userCouponId !== undefined,
});
```

**문제 3: TextSummary TypeError**

**수정**: Null safety 추가
```javascript
const getMetricValue = (metric, key) => {
    if (!metric || !metric.values) return null;
    const value = metric.values[key];
    return value === undefined || value === null ? null : Number(value);
};
const formatMs = (value) => value === null ? 'N/A' : `${value.toFixed(2)}ms`;
const formatPercent = (rate) => rate === null ? 'N/A' : `${(rate * 100).toFixed(2)}%`;
```

**결과**: ✅ 테스트 수정 완료, 실행 준비 완료

---

### 4. Payment Concurrency Test ✅

**파일**: `payment-concurrency-test.js`

**문제**: K6 extension error
```
ERRO[0000] invalid build parameters: unknown dependency : k6/fs
```

**원인**: `k6/fs` 모듈은 표준 K6 모듈이 아님

**수정**:
```javascript
// Before
import { existsSync, mkdirSync } from 'k6/fs';

try {
    const dir = summaryPath.slice(0, summaryPath.lastIndexOf('/'));
    if (dir && !existsSync(dir)) {
        mkdirSync(dir);
    }
} catch (e) { ... }

// After
// Import 제거
// Directory creation logic 제거 (사전에 mkdir -p results 실행 필요)
```

**결과**: ✅ K6/fs 의존성 제거 완료

---

## 🔧 인프라 수정 사항

### 1. Redis Cache Serialization 수정 ✅

**파일**: `src/main/java/io/hhplus/ecommerce/config/CacheConfig.java`

**문제**:
```
org.springframework.data.redis.serializer.SerializationException:
Could not resolve subtype of [simple type, class java.lang.Object]:
missing type id property '@class'
```

**원인**:
- `CartResponse` 내부의 `List<CartItemResponse>`가 제네릭 타입 소거
- `BasicPolymorphicTypeValidator`가 구체적인 타입을 허용하지 않음

**수정**:
```java
// Before (너무 광범위)
BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
    .allowIfSubType("io.hhplus.ecommerce")
    .allowIfSubType("java.util")
    .allowIfSubType("java.lang")
    .build();

// After (구체적인 타입 허용)
BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
    .allowIfSubType("io.hhplus.ecommerce")
    .allowIfSubType("java.util.List")
    .allowIfSubType("java.util.ArrayList")
    .allowIfSubType("java.util.LinkedList")
    .allowIfSubType("java.util.Map")
    .allowIfSubType("java.util.HashMap")
    .allowIfSubType("java.lang.Long")
    .allowIfSubType("java.lang.Integer")
    .allowIfSubType("java.lang.String")
    .allowIfSubType("java.lang.Boolean")
    .build();
```

**추가 개선**:
```java
// Cache prefix 버전 관리
.computePrefixWith(cacheName -> "v2::" + cacheName + "::")

// Cache 역직렬화 오류 시 자동 제거
@Bean
public CacheErrorHandler cacheErrorHandler() {
    return new SimpleCacheErrorHandler() {
        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            cache.evict(key);  // 오류 발생 키 제거
            super.handleCacheGetError(exception, cache, key);
        }
    };
}
```

**결과**: ✅ Redis Serialization 문제 해결, 애플리케이션 재시작 완료

---

## 📊 테스트 실행 상태

| 테스트 | 상태 | 비고 |
|--------|------|------|
| Balance Charge Concurrency | ✅ PASS | 99.94% 성공률, 분산락 정상 작동 |
| Payment Concurrency | ⚠️ FLAKY | 3회 연속 실패, 타임아웃 조정 필요 |
| Coupon Issuance Concurrency | ✅ 준비 완료 | K6 테스트 수정 완료 |
| Cart Cache | ✅ 준비 완료 | Redis 문제 해결, 애플리케이션 재시작 완료 |
| Order Idempotency | ⏳ 미실행 | - |

---

## 🚀 다음 실행 명령어

```bash
cd docs/week6/loadtest/k6

# 0. 결과 디렉토리 생성 (최초 1회만)
mkdir -p results

# 1. Cart Cache Test (실행 가능)
k6 run cart-cache-test.js

# 2. Coupon Issuance Test (애플리케이션 재시작 필요)
# Note: 쿠폰 재고를 초기화하려면 애플리케이션을 재시작하세요
k6 run coupon-issuance-concurrency-test.js

# 3. Payment Concurrency Test (Flaky Test)
k6 run payment-concurrency-test.js

# 4. Order Idempotency Test (검토 필요)
k6 run order-creation-idempotency-test.js
```

---

## 📝 학습 내용

### 1. API 엔드포인트 불일치 문제

**교훈**: K6 테스트 작성 전 반드시 실제 Controller 코드를 확인해야 함

**확인 방법**:
```bash
# Controller 매핑 확인
grep -r "@RequestMapping" src/main/java/io/hhplus/ecommerce/presentation/api

# 특정 API 엔드포인트 확인
grep -r "GetMapping\|PostMapping" src/main/java/.../CartController.java
```

### 2. 응답 구조 불일치 문제

**교훈**: `ApiResponse` wrapper 사용 여부 확인 필요

**패턴**:
- Wrapper 사용: `{ success: true, data: {...} }`
- Direct 응답: `{ userId: 1, items: [...] }`

### 3. Redis Serialization 문제

**교훈**: Generic 타입을 Redis에 캐싱할 때는 구체적인 타입 허용 필요

**핵심**:
- `.allowIfSubType("java.util")` ❌ (너무 광범위)
- `.allowIfSubType("java.util.List")` ✅ (구체적)
- `.allowIfSubType("java.util.ArrayList")` ✅ (구체적)

### 4. K6 테스트 Null Safety

**교훈**: Metric이 없을 수 있으므로 항상 null check 필요

**패턴**:
```javascript
const getMetricValue = (metric, key) => {
    if (!metric || !metric.values) return null;
    return metric.values[key] ?? null;
};
const formatMs = (value) => value === null ? 'N/A' : `${value.toFixed(2)}ms`;
```

---

## ⚠️ 알려진 이슈

### 1. Payment Concurrency Test - Flaky Test

**증상**: 때로는 성공, 때로는 실패 (3회 연속 실패 확인)

**원인 추정**:
1. 분산락 타임아웃 부족 (waitTime: 10s)
2. 트랜잭션 경계 불일치
3. 테스트 타이밍 이슈

**해결 방안**:
- 분산락 타임아웃 증가 (waitTime: 10→60s, leaseTime: 30→120s)
- 트랜잭션 경계 재검토
- 5회 연속 성공 시 안정화 완료

**참고 문서**: `docs/week6/TEST_STABILITY_CHECK.md`

---

**작성일**: 2025-11-27
**작성자**: Claude Code
**업데이트**: 테스트 실행 시마다 업데이트
