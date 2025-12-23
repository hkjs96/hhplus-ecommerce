# K6 부하 테스트 문제 해결 가이드

**작성일**: 2025-11-24
**대상**: Order Create / Payment Process 테스트 실패 분석 및 해결

---

## 🚨 발견된 문제

### Test 1: Order Create (주문 생성)

#### 증상
```
총 요청: 140,473
성공: 299 (0.21%)
실패: 140,174 (99.78%)
실패 원인: Stock Depleted (재고 소진)
Error Rate: 100%
```

#### 근본 원인
```javascript
// ❌ Before: 단일 상품에 1000명이 동시 주문
const USER_ID = __ENV.USER_ID || '1';
const PRODUCT_ID = __ENV.PRODUCT_ID || '1';  // 고정!

// 1000 VU가 모두 PRODUCT_ID=1에 주문
// → 재고 299개 소진 후 나머지 140,174건 실패
```

**문제점**:
1. **단일 리소스 경합**: 모든 VU가 동일한 상품을 주문
2. **재고 부족**: 초기 재고(예: 1000개)가 테스트 규모(140,473 요청)보다 적음
3. **현실성 부족**: 실제 환경에서는 다양한 상품에 분산됨

---

### Test 2: Payment Process (결제 처리)

#### 증상
```
총 요청: 26,755
주문 생성 성공: 50 (0.19%)
주문 생성 실패: 26,705 (99.81%)
멱등성 검증: 0건 (테스트 불가)
Error Rate: 100%
```

#### 근본 원인
```javascript
// ❌ Before: 단일 상품으로 주문 생성
function createOrder() {
  const payload = JSON.stringify({
    userId: parseInt(USER_ID),
    items: [
      {
        productId: 1,  // 고정!
        quantity: 1,
      },
    ],
  });
}
```

**문제점**:
1. **주문 생성 실패**: 재고 소진으로 주문 자체가 생성되지 않음
2. **멱등성 검증 불가**: 주문이 없으면 결제 테스트 불가능
3. **테스트 목적 달성 실패**: Idempotency Key 검증이 목표였으나 수행 불가

---

## ✅ 해결 방안

### 전략 1: 부하 분산 (Load Distribution)

#### 다중 사용자 + 다중 상품 전략

```javascript
// ✅ After: 랜덤 사용자 및 상품 선택
const MIN_USER_ID = parseInt(__ENV.MIN_USER_ID || '1');
const MAX_USER_ID = parseInt(__ENV.MAX_USER_ID || '100');
const MIN_PRODUCT_ID = parseInt(__ENV.MIN_PRODUCT_ID || '1');
const MAX_PRODUCT_ID = parseInt(__ENV.MAX_PRODUCT_ID || '10');

function getRandomUserId() {
  return Math.floor(Math.random() * (MAX_USER_ID - MIN_USER_ID + 1)) + MIN_USER_ID;
}

function getRandomProductId() {
  return Math.floor(Math.random() * (MAX_PRODUCT_ID - MIN_PRODUCT_ID + 1)) + MIN_PRODUCT_ID;
}

export default function() {
  const userId = getRandomUserId();
  const productId = getRandomProductId();

  // 이제 100명의 사용자가 10개의 상품에 분산 주문
}
```

**효과**:
- 재고 경합 10배 감소 (1개 상품 → 10개 상품)
- Lock Contention 감소
- 실제 환경 시뮬레이션

---

### 전략 2: 재고 소진 재시도 (Retry on Stock Depletion)

#### Payment Process에 재시도 로직 추가

```javascript
// ✅ After: 재고 소진 시 다른 상품으로 재시도
function createOrderWithRetry(userId, initialProductId, maxRetries) {
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    // 재시도 시 다른 상품 선택
    const productId = attempt === 0 ? initialProductId : getRandomProductId();
    const orderId = createOrder(userId, productId);

    if (orderId) {
      return orderId;  // 성공 시 즉시 반환
    }

    // 재고 소진인 경우 다른 상품으로 재시도
  }

  return null;  // maxRetries 후에도 실패
}
```

**효과**:
- 주문 생성 성공률 증가
- 멱등성 테스트 가능
- 재고 소진에 강건

---

## 📋 수정된 테스트 스크립트

### order-create.js (주문 생성)

**변경 사항**:
```diff
- const USER_ID = __ENV.USER_ID || '1';
- const PRODUCT_ID = __ENV.PRODUCT_ID || '1';
+ const MIN_USER_ID = parseInt(__ENV.MIN_USER_ID || '1');
+ const MAX_USER_ID = parseInt(__ENV.MAX_USER_ID || '100');
+ const MIN_PRODUCT_ID = parseInt(__ENV.MIN_PRODUCT_ID || '1');
+ const MAX_PRODUCT_ID = parseInt(__ENV.MAX_PRODUCT_ID || '10');

export default function() {
-   const payload = JSON.stringify({
-     userId: parseInt(USER_ID),
-     items: [{ productId: parseInt(PRODUCT_ID), quantity: 1 }],
-   });
+   const userId = getRandomUserId();
+   const productId = getRandomProductId();
+   const payload = JSON.stringify({
+     userId: userId,
+     items: [{ productId: productId, quantity: 1 }],
+   });
}
```

**실행 방법**:
```bash
# 기본 실행 (USER 1~100, PRODUCT 1~10)
k6 run docs/week5/verification/k6/scripts/order-create.js

# 사용자 범위 확장
k6 run -e MIN_USER_ID=1 -e MAX_USER_ID=200 \
  -e MIN_PRODUCT_ID=1 -e MAX_PRODUCT_ID=20 \
  docs/week5/verification/k6/scripts/order-create.js
```

---

### payment-process.js (결제 처리)

**변경 사항**:
```diff
- const USER_ID = __ENV.USER_ID || '1';
+ const MIN_USER_ID = parseInt(__ENV.MIN_USER_ID || '1');
+ const MAX_USER_ID = parseInt(__ENV.MAX_USER_ID || '100');
+ const MIN_PRODUCT_ID = parseInt(__ENV.MIN_PRODUCT_ID || '1');
+ const MAX_PRODUCT_ID = parseInt(__ENV.MAX_PRODUCT_ID || '10');
+ const MAX_RETRIES = parseInt(__ENV.MAX_RETRIES || '3');

export default function() {
-   const orderId = createOrder();
+   const userId = getRandomUserId();
+   const productId = getRandomProductId();
+   const orderId = createOrderWithRetry(userId, productId, MAX_RETRIES);
}

+ function createOrderWithRetry(userId, initialProductId, maxRetries) {
+   for (let attempt = 0; attempt < maxRetries; attempt++) {
+     const productId = attempt === 0 ? initialProductId : getRandomProductId();
+     const orderId = createOrder(userId, productId);
+     if (orderId) return orderId;
+   }
+   return null;
+ }
```

**실행 방법**:
```bash
# 기본 실행
k6 run docs/week5/verification/k6/scripts/payment-process.js

# 재시도 횟수 증가
k6 run -e MAX_RETRIES=5 \
  docs/week5/verification/k6/scripts/payment-process.js
```

---

## 🎯 예상 개선 효과

### Order Create 테스트

| 메트릭 | Before (단일 상품) | After (다중 상품) | 개선율 |
|--------|-------------------|------------------|--------|
| **성공률** | 0.21% ❌ | >80% ✅ | +400배 |
| **재고 소진 실패** | 99.78% | <20% | -80% |
| **Lock Contention** | 심각 | 최소 | -90% |
| **테스트 현실성** | 낮음 | 높음 | ✅ |

**근거**:
- 10개 상품 분산 → Lock Contention 10배 감소
- 각 상품 재고 1000개 × 10 = 총 10,000개 재고
- 140,473 요청 중 대부분 성공 가능

---

### Payment Process 테스트

| 메트릭 | Before | After | 개선율 |
|--------|--------|-------|--------|
| **주문 생성 성공률** | 0.19% ❌ | >80% ✅ | +400배 |
| **멱등성 검증** | 0건 ❌ | >1000건 ✅ | ∞ |
| **테스트 완료율** | 0% | >95% | ✅ |

**근거**:
- 재시도 로직으로 주문 생성 성공률 증가
- 주문 성공 시 멱등성 검증 가능
- 테스트 목적 달성

---

## 🚀 테스트 실행 가이드

### 사전 준비

#### 1. 테스트 데이터 초기화

```sql
-- MySQL 접속
mysql -u root -p ecommerce

-- 상품 재고 확인 및 증가
SELECT id, name, stock FROM products WHERE id BETWEEN 1 AND 10;

-- 재고 증가 (필요 시)
UPDATE products SET stock = 10000 WHERE id BETWEEN 1 AND 10;

-- 사용자 잔액 확인 및 증가
SELECT user_id, balance FROM user_balance WHERE user_id BETWEEN 1 AND 100;

-- 잔액 증가 (필요 시)
UPDATE user_balance SET balance = 1000000 WHERE user_id BETWEEN 1 AND 100;
```

#### 2. 애플리케이션 실행

```bash
# Spring Boot 애플리케이션 시작
./gradlew bootRun

# 다른 터미널에서 health check
curl http://localhost:8080/actuator/health
```

---

### 테스트 실행

#### Test 1: Order Create (수정된 버전)

```bash
# 기본 실행
k6 run docs/week5/verification/k6/scripts/order-create.js

# 사용자/상품 범위 커스터마이징
k6 run \
  -e MIN_USER_ID=1 -e MAX_USER_ID=200 \
  -e MIN_PRODUCT_ID=1 -e MAX_PRODUCT_ID=20 \
  docs/week5/verification/k6/scripts/order-create.js

# 결과 JSON 파일로 저장
k6 run --out json=results/order-create-fixed.json \
  docs/week5/verification/k6/scripts/order-create.js
```

**예상 결과**:
```
✅ errors: rate < 20%
✅ success: rate > 80%
✅ pessimistic_lock_timeouts: count < 200
✅ http_req_duration p(95): < 3.5s
```

---

#### Test 2: Payment Process (수정된 버전)

```bash
# 기본 실행
k6 run docs/week5/verification/k6/scripts/payment-process.js

# 재시도 횟수 증가
k6 run -e MAX_RETRIES=5 \
  docs/week5/verification/k6/scripts/payment-process.js

# 결과 JSON 파일로 저장
k6 run --out json=results/payment-process-fixed.json \
  docs/week5/verification/k6/scripts/payment-process.js
```

**예상 결과**:
```
✅ http_req_duration p(95): < 1s
✅ idempotency_verification_success: count > 0
✅ duplicate_payments_prevented: count > 0
```

---

## 📊 결과 검증

### Order Create 검증

```bash
# 주문 건수 확인
mysql -u root -p -e "
  SELECT COUNT(*) as total_orders
  FROM ecommerce.orders
  WHERE created_at > DATE_SUB(NOW(), INTERVAL 10 MINUTE);
"

# 상품별 재고 확인
mysql -u root -p -e "
  SELECT id, name, stock
  FROM ecommerce.products
  WHERE id BETWEEN 1 AND 10
  ORDER BY id;
"
```

---

### Payment Process 검증

```bash
# 멱등성 키 중복 확인
mysql -u root -p -e "
  SELECT idempotency_key, COUNT(*) as count
  FROM ecommerce.payment_idempotency
  GROUP BY idempotency_key
  HAVING count > 1;
"
# 출력: Empty set (0.00 sec)  ← 중복 없음 (정상)

# 멱등성 검증 성공 건수
mysql -u root -p -e "
  SELECT COUNT(*) as total_payments
  FROM ecommerce.payment_idempotency
  WHERE created_at > DATE_SUB(NOW(), INTERVAL 10 MINUTE);
"
```

---

## 💡 추가 개선 제안

### 1. 동적 재고 모니터링

테스트 중 재고를 실시간으로 모니터링하고, 재고가 부족하면 자동으로 보충:

```javascript
export function setup() {
  // 테스트 시작 전 재고 확인 및 보충
  const stockCheckUrl = `${BASE_URL}/admin/products/stock/check`;
  const response = http.get(stockCheckUrl);

  const stocks = JSON.parse(response.body);
  stocks.forEach(product => {
    if (product.stock < 1000) {
      console.warn(`Product ${product.id} has low stock: ${product.stock}`);
      // Auto-replenish
      http.post(`${BASE_URL}/admin/products/${product.id}/stock/add`, { amount: 10000 });
    }
  });
}
```

---

### 2. 커스텀 메트릭 강화

```javascript
export let stockByProduct = new Counter('stock_by_product');

export default function() {
  const productId = getRandomProductId();

  // Record which product was ordered
  stockByProduct.add(1, { product_id: productId });
}
```

**효과**: 어떤 상품의 재고가 먼저 소진되는지 파악 가능

---

### 3. Graceful Degradation 테스트

재고 소진 시 대체 상품 추천 로직 테스트:

```javascript
export default function() {
  const orderId = createOrderWithFallback(userId, productId);
}

function createOrderWithFallback(userId, primaryProductId) {
  // 1차 시도: 원하는 상품
  let orderId = createOrder(userId, primaryProductId);
  if (orderId) return orderId;

  // 2차 시도: 같은 카테고리의 대체 상품
  const alternativeProductId = getSimilarProduct(primaryProductId);
  orderId = createOrder(userId, alternativeProductId);
  if (orderId) return orderId;

  // 3차 시도: 아무 재고 있는 상품
  const anyProductId = getAnyAvailableProduct();
  return createOrder(userId, anyProductId);
}
```

---

## 🎓 학습 포인트

### 1. 부하 테스트 설계 원칙

**나쁜 예 (Before)**:
- ❌ 단일 리소스에 집중 공격
- ❌ 재고 부족 고려 안 함
- ❌ 실제 환경과 다름

**좋은 예 (After)**:
- ✅ 다중 리소스에 분산
- ✅ 재고 부족 시 재시도
- ✅ 실제 환경 시뮬레이션

---

### 2. 테스트 실패 분석 방법

1. **로그 분석**: "Stock Depleted" 패턴 발견
2. **메트릭 분석**: 99.78% 실패율 → 체계적인 문제
3. **스크립트 검토**: 고정된 PRODUCT_ID 발견
4. **근본 원인 파악**: 단일 리소스 경합
5. **해결 방안 설계**: 부하 분산 전략

---

### 3. K6 테스트 베스트 프랙티스

1. **환경 변수 활용**: `-e` 플래그로 유연한 설정
2. **랜덤화**: 실제 사용자 행동 시뮬레이션
3. **재시도 로직**: 일시적 실패 처리
4. **커스텀 메트릭**: 비즈니스 로직 검증
5. **Setup/Teardown**: 테스트 전후 처리

---

## 📝 체크리스트

테스트 실행 전 확인:

- [ ] MySQL 실행 중
- [ ] 상품 재고 충분 (각 상품 10,000개 이상)
- [ ] 사용자 잔액 충분 (각 사용자 1,000,000원 이상)
- [ ] Spring Boot 애플리케이션 실행 중
- [ ] K6 설치 확인 (`k6 version`)
- [ ] 테스트 스크립트 수정 완료

테스트 실행 후 확인:

- [ ] Error Rate < 20%
- [ ] Success Rate > 80%
- [ ] 멱등성 검증 성공 (Payment Process)
- [ ] 데이터베이스 정합성 확인
- [ ] 성능 메트릭 수집 완료

---

**작성자**: Claude Code
**버전**: 1.0
**다음 테스트 예정일**: 개선 후 즉시 실행 권장
