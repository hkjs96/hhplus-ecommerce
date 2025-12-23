# K6 테스트 개선 요약

**작성일**: 2025-11-24
**작업**: Order Create / Payment Process 테스트 실패 분석 및 수정

---

## 🎯 목표

두 K6 테스트의 실패 원인을 분석하고, 근본적인 문제를 해결하여 실제 환경을 시뮬레이션하는 테스트로 개선

---

## 📊 문제 상황

### Test 1: order-create.js

```
실행 결과: 140,473 요청
성공: 299 (0.21%)
실패: 140,174 (99.78%) ← Stock Depleted
Error Rate: 100%
```

**문제**: 단일 상품(PRODUCT_ID=1)에 1000명이 동시 주문 → 재고 소진

---

### Test 2: payment-process.js

```
실행 결과: 26,755 요청
주문 생성 성공: 50 (0.19%)
주문 생성 실패: 26,705 (99.81%)
멱등성 검증: 불가능
Error Rate: 100%
```

**문제**: 주문 생성 단계에서 실패 → 멱등성 테스트 불가능

---

## 🔍 근본 원인 분석

### 1. 단일 리소스 경합 (Single Resource Contention)

```javascript
// ❌ Before
const PRODUCT_ID = __ENV.PRODUCT_ID || '1';  // 모든 VU가 동일 상품 주문

// 1000 VU → PRODUCT_ID=1 (재고 1000개)
// 결과: 1000개 성공 → 나머지 139,473개 실패
```

**문제점**:
- 극단적인 Lock Contention
- 재고 부족
- 비현실적인 시나리오

---

### 2. 테스트 설계 결함

**현실성 부족**:
- 실제 환경: 다양한 사용자가 다양한 상품 주문
- 테스트 환경: 1000명이 동일 상품 주문 (비현실적)

**종속성 문제**:
- payment-process.js는 주문 생성에 의존
- 주문 생성 실패 → 멱등성 테스트 불가능

---

## ✅ 해결 방안

### 전략 1: 부하 분산 (Load Distribution)

#### 다중 상품 전략

```javascript
// ✅ After
const MIN_PRODUCT_ID = parseInt(__ENV.MIN_PRODUCT_ID || '1');
const MAX_PRODUCT_ID = parseInt(__ENV.MAX_PRODUCT_ID || '10');

function getRandomProductId() {
  return Math.floor(Math.random() * (MAX_PRODUCT_ID - MIN_PRODUCT_ID + 1)) + MIN_PRODUCT_ID;
}

export default function() {
  const productId = getRandomProductId();  // 1~10 중 랜덤
  // ...
}
```

**효과**:
- Lock Contention 10배 감소
- 재고 10배 증가 (1000 → 10,000)
- 실제 환경 시뮬레이션

---

#### 다중 사용자 전략

```javascript
// ✅ After
const MIN_USER_ID = parseInt(__ENV.MIN_USER_ID || '1');
const MAX_USER_ID = parseInt(__ENV.MAX_USER_ID || '100');

function getRandomUserId() {
  return Math.floor(Math.random() * (MAX_USER_ID - MIN_USER_ID + 1)) + MIN_USER_ID;
}

export default function() {
  const userId = getRandomUserId();  // 1~100 중 랜덤
  // ...
}
```

**효과**:
- 사용자별 분산
- 잔액 경합 최소화
- 실제 사용 패턴 반영

---

### 전략 2: 재시도 로직 (Retry on Failure)

#### payment-process.js에 재시도 추가

```javascript
// ✅ New Function
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
- 재고 소진에 강건
- 주문 생성 성공률 증가
- 멱등성 테스트 가능

---

## 📈 예상 개선 효과

### order-create.js

| 메트릭 | Before | After (Expected) | 개선율 |
|--------|--------|------------------|--------|
| **성공률** | 0.21% | >80% | +38,000% |
| **실패율** | 99.78% | <20% | -80% |
| **Lock Contention** | 심각 | 최소 | -90% |
| **TPS** | ~460 | ~1000+ | +100% |
| **Lock Timeout** | 예상됨 | 최소 | -95% |

**근거**:
- 10개 상품 × 1000 재고 = 10,000개 재고
- 140,473 요청 대부분 성공 가능
- Lock Contention 대폭 감소

---

### payment-process.js

| 메트릭 | Before | After (Expected) | 개선율 |
|--------|--------|------------------|--------|
| **주문 생성 성공률** | 0.19% | >80% | +42,000% |
| **멱등성 검증** | 0건 | >1000건 | ∞ |
| **테스트 완료율** | 0% | >95% | ∞ |
| **중복 결제 방지** | 검증 불가 | 검증 완료 | ✅ |

**근거**:
- 재시도 로직으로 주문 생성 성공률 증가
- 주문 성공 시 멱등성 검증 가능
- 테스트 목적 달성

---

## 🔧 수정 사항 상세

### order-create.js

**변경된 코드**:
```diff
- const USER_ID = __ENV.USER_ID || '1';
- const PRODUCT_ID = __ENV.PRODUCT_ID || '1';
+ const MIN_USER_ID = parseInt(__ENV.MIN_USER_ID || '1');
+ const MAX_USER_ID = parseInt(__ENV.MAX_USER_ID || '100');
+ const MIN_PRODUCT_ID = parseInt(__ENV.MIN_PRODUCT_ID || '1');
+ const MAX_PRODUCT_ID = parseInt(__ENV.MAX_PRODUCT_ID || '10');

+ function getRandomUserId() { ... }
+ function getRandomProductId() { ... }

export default function() {
+   const userId = getRandomUserId();
+   const productId = getRandomProductId();

    const payload = JSON.stringify({
-     userId: parseInt(USER_ID),
+     userId: userId,
      items: [
        {
-         productId: parseInt(PRODUCT_ID),
+         productId: productId,
          quantity: 1,
        },
      ],
    });
}
```

---

### payment-process.js

**변경된 코드**:
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

function createOrder(userId, productId) {
    const payload = JSON.stringify({
-     userId: parseInt(USER_ID),
+     userId: userId,
      items: [
        {
-         productId: 1,
+         productId: productId,
          quantity: 1,
        },
      ],
    });
}
```

---

## 📚 생성된 문서

### 1. TROUBLESHOOTING.md (400+ 줄)

**내용**:
- 문제 상황 상세 분석
- 근본 원인 파악
- 해결 방안 (Before/After 코드)
- 예상 개선 효과
- 테스트 실행 가이드
- 검증 방법
- 추가 개선 제안
- 학습 포인트
- 체크리스트

**용도**: 테스트 실패 시 참조용

---

### 2. README.md 업데이트

**추가 내용**:
- 🚨 테스트 실패 시 문제 해결 섹션 (상단)
- 📁 사용 가능한 테스트 스크립트 목록
- 🔧 최근 개선 사항 (2025-11-24)
- 📚 관련 문서 링크

---

### 3. IMPROVEMENTS_SUMMARY.md (이 문서)

**내용**:
- 목표 및 문제 상황
- 근본 원인 분석
- 해결 방안
- 예상 개선 효과
- 수정 사항 상세
- 실행 방법
- 다음 단계

---

## 🚀 테스트 실행 방법

### 사전 준비

```sql
-- 1. MySQL 접속
mysql -u root -p ecommerce

-- 2. 상품 재고 확인 및 증가
UPDATE products SET stock = 10000 WHERE id BETWEEN 1 AND 10;

-- 3. 사용자 잔액 확인 및 증가
UPDATE user_balance SET balance = 1000000 WHERE user_id BETWEEN 1 AND 100;
```

---

### order-create.js 실행

```bash
# 기본 실행 (USER 1~100, PRODUCT 1~10)
k6 run docs/week5/verification/k6/scripts/order-create.js

# 사용자/상품 범위 확장
k6 run \
  -e MIN_USER_ID=1 -e MAX_USER_ID=200 \
  -e MIN_PRODUCT_ID=1 -e MAX_PRODUCT_ID=20 \
  docs/week5/verification/k6/scripts/order-create.js

# 결과 저장
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

### payment-process.js 실행

```bash
# 기본 실행 (MAX_RETRIES=3)
k6 run docs/week5/verification/k6/scripts/payment-process.js

# 재시도 횟수 증가
k6 run -e MAX_RETRIES=5 \
  docs/week5/verification/k6/scripts/payment-process.js

# 결과 저장
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

## 🎓 학습 포인트

### 1. 부하 테스트 설계 원칙

**나쁜 설계 (Before)**:
- ❌ 단일 리소스 집중 공격
- ❌ 재고/잔액 부족 고려 안 함
- ❌ 비현실적인 시나리오
- ❌ 테스트 목적 달성 불가

**좋은 설계 (After)**:
- ✅ 다중 리소스 분산
- ✅ 재고/잔액 충분히 준비
- ✅ 실제 환경 시뮬레이션
- ✅ 테스트 목적 명확히 달성

---

### 2. 테스트 실패 분석 프로세스

1. **로그 분석**: "Stock Depleted" 패턴 발견
2. **메트릭 분석**: 99.78% 실패율 → 체계적인 문제
3. **스크립트 검토**: 고정된 PRODUCT_ID 발견
4. **근본 원인 파악**: 단일 리소스 경합
5. **해결 방안 설계**: 부하 분산 전략
6. **구현 및 검증**: 코드 수정 → 재테스트

---

### 3. K6 베스트 프랙티스

**환경 변수 활용**:
```bash
k6 run -e MIN_USER_ID=1 -e MAX_USER_ID=100 script.js
```

**랜덤화**:
```javascript
const userId = getRandomUserId();  // 실제 사용자 행동 시뮬레이션
```

**재시도 로직**:
```javascript
for (let attempt = 0; attempt < maxRetries; attempt++) {
  // 일시적 실패 처리
}
```

**커스텀 메트릭**:
```javascript
export let stockDepletions = new Counter('stock_depletions');
stockDepletions.add(1);  // 비즈니스 로직 검증
```

---

## ⏭️ 다음 단계

### 1. 테스트 실행 ⏳

```bash
# order-create.js 실행
k6 run docs/week5/verification/k6/scripts/order-create.js

# payment-process.js 실행
k6 run docs/week5/verification/k6/scripts/payment-process.js
```

---

### 2. 결과 검증 ⏳

**order-create.js**:
- [ ] 성공률 >80%
- [ ] Lock Timeout <200
- [ ] P95 레이턴시 <3.5s

**payment-process.js**:
- [ ] 주문 생성 성공률 >80%
- [ ] 멱등성 검증 >1000건
- [ ] 중복 결제 방지 확인

---

### 3. 문서 업데이트 ⏳

- [ ] 실제 테스트 결과 기록
- [ ] Before/After 비교 분석
- [ ] 추가 개선 사항 식별
- [ ] 성능 보고서 업데이트

---

### 4. 추가 최적화 (선택) ⏸️

- [ ] 동적 재고 모니터링
- [ ] 커스텀 메트릭 강화
- [ ] Graceful Degradation 테스트
- [ ] APM 통합

---

## 📊 체크리스트

### 실행 전
- [ ] MySQL 실행 중
- [ ] 상품 재고 충분 (각 10,000개)
- [ ] 사용자 잔액 충분 (각 1,000,000원)
- [ ] Spring Boot 실행 중
- [ ] K6 설치 확인

### 실행 후
- [ ] Error Rate <20%
- [ ] Success Rate >80%
- [ ] 멱등성 검증 완료
- [ ] 데이터베이스 정합성 확인
- [ ] 성능 메트릭 수집

---

## 📚 관련 문서

- **[TROUBLESHOOTING.md](./TROUBLESHOOTING.md)** - 상세 문제 해결 가이드
- **[README.md](./README.md)** - K6 테스트 전체 가이드
- **[PERFORMANCE_REPORT.md](./PERFORMANCE_REPORT.md)** - 성능 분석 보고서
- **[TEST_RESULTS_SUMMARY.md](./TEST_RESULTS_SUMMARY.md)** - 테스트 결과 요약

---

**작성자**: Claude Code
**버전**: 1.0
**다음 작업**: 개선된 스크립트로 테스트 실행 및 결과 검증
