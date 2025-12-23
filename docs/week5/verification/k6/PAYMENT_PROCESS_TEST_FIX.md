# Payment Process Test 수정 사항

## 문제점

K6 부하 테스트 실행 시 100% 실패율 발생:

```
CUSTOM
idempotency_verification_success...: 0       0/s
errors.............................: 100.00% 19219 out of 19219
```

### 근본 원인

**payment-process.js:202** - `processPayment()` 함수에서 **랜덤 userId 사용**

```javascript
// ❌ 문제 코드
function processPayment(orderId, idempotencyKey, attemptNumber) {
  const payload = JSON.stringify({
    userId: getRandomUserId(),  // 주문 생성한 userId와 다른 랜덤 userId 사용!
    amount: 10000,
    idempotencyKey: idempotencyKey,
  });
}
```

### 에러 로그

```
주문한 사용자와 결제 요청 사용자가 다릅니다.
io.hhplus.ecommerce.common.exception.BusinessException
```

**비즈니스 검증 로직** (`PaymentTransactionService`):
```java
if (!order.getUserId().equals(userId)) {
    throw new BusinessException(CommonErrorCode.UNAUTHORIZED,
        "주문한 사용자와 결제 요청 사용자가 다릅니다.");
}
```

---

## 수정 사항

### 1. Main Test Function

**Before:**
```javascript
const paymentResults = processPaymentWithRetries(orderId, idempotencyKey);
```

**After:**
```javascript
const paymentResults = processPaymentWithRetries(orderId, userId, idempotencyKey);
```

### 2. processPaymentWithRetries()

**Before:**
```javascript
function processPaymentWithRetries(orderId, idempotencyKey) {
  const results = [];
  for (let i = 0; i < 3; i++) {
    const result = processPayment(orderId, idempotencyKey, i + 1);
    results.push(result);
    sleep(0.1);
  }
  return results;
}
```

**After:**
```javascript
function processPaymentWithRetries(orderId, userId, idempotencyKey) {
  const results = [];
  for (let i = 0; i < 3; i++) {
    const result = processPayment(orderId, userId, idempotencyKey, i + 1);
    results.push(result);
    sleep(0.1);
  }
  return results;
}
```

### 3. processPayment()

**Before:**
```javascript
function processPayment(orderId, idempotencyKey, attemptNumber) {
  const url = `${BASE_URL}/api/orders/${orderId}/payment`;

  const payload = JSON.stringify({
    userId: getRandomUserId(),  // ❌ 랜덤 userId
    amount: 10000,
    idempotencyKey: idempotencyKey,
  });
}
```

**After:**
```javascript
function processPayment(orderId, userId, idempotencyKey, attemptNumber) {
  const url = `${BASE_URL}/api/orders/${orderId}/payment`;

  const payload = JSON.stringify({
    userId: userId,  // ✅ 주문 생성한 동일 userId
    amount: 10000,
    idempotencyKey: idempotencyKey,
  });
}
```

---

## 테스트 시나리오 (수정 후 기대 동작)

### Flow

1. **주문 생성**: `userId=42`로 주문 생성 → `orderId=1234`
2. **결제 시도 3회** (동일 Idempotency Key):
   - Attempt 1: `userId=42`, `idempotencyKey=abc123` → **200 SUCCESS**
   - Attempt 2: `userId=42`, `idempotencyKey=abc123` → **409 CONFLICT** (중복 방지)
   - Attempt 3: `userId=42`, `idempotencyKey=abc123` → **409 CONFLICT** (중복 방지)

### 검증 로직

```javascript
const successCount = paymentResults.filter(r => r === 'SUCCESS').length;
const conflictCount = paymentResults.filter(r => r === 'CONFLICT').length;

if (successCount === 1 && conflictCount === 2) {
  idempotencyVerificationSuccess.add(1);
  console.log('✅ Idempotency verified: 1 success, 2 prevented');
} else {
  idempotencyVerificationFailure.add(1);
  console.log('❌ Idempotency failed');
}
```

---

## 실행 방법

### 1. Application 실행

```bash
./gradlew bootRun
```

### 2. K6 테스트 실행

```bash
k6 run docs/week5/verification/k6/scripts/payment-process.js
```

### 3. 기대 결과

```
CUSTOM
idempotency_verification_success...: >0 (성공)
duplicate_payments_prevented.......: >0 (중복 방지 카운트)
errors.............................: <10% (에러율 감소)

THRESHOLDS
✓ idempotency_verification_success: count>0
✓ duplicate_payments_prevented: count>0
✓ http_req_duration: p(95)<1000
```

---

## 학습 포인트

### 1. 비즈니스 검증의 중요성

애플리케이션은 **주문 생성자와 결제 요청자가 동일한지 검증**합니다:

```java
// PaymentTransactionService.java
if (!order.getUserId().equals(userId)) {
    throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
}
```

**이유**:
- 보안: 다른 사용자가 내 주문을 결제하는 것 방지
- 무결성: 주문-결제 일관성 보장

### 2. 부하 테스트 설계 시 주의사항

**랜덤 데이터 사용 시 주의**:
- ✅ 부하 분산 목적: 서로 다른 사용자가 서로 다른 상품 주문
- ❌ 무분별한 랜덤: 같은 주문 내에서 다른 사용자로 결제 시도

**올바른 테스트 설계**:
```javascript
// User A가 주문 생성
const orderUserId = getRandomUserId();
const orderId = createOrder(orderUserId, productId);

// User A가 결제 (동일 userId 사용)
processPayment(orderId, orderUserId, idempotencyKey);
```

### 3. Idempotency Key 검증 방법

**핵심 요구사항**:
- 동일 Key로 3번 요청 → **1번만 성공**, 2번은 중복 방지
- `payment_idempotency` 테이블의 **UNIQUE 제약조건** 검증

**K6 메트릭**:
```javascript
idempotency_verification_success  // 1 성공 + 2 방지 = 검증 성공
duplicate_payments_prevented       // 중복 방지 카운트
```

---

## 다음 단계

1. ✅ **payment-process.js 수정 완료**
2. ⏳ **K6 재실행 및 결과 검증**
3. ⏳ **문서화**: 테스트 결과를 `PAYMENT_PROCESS_TEST_RESULTS.md`에 기록

---

## 참고

- **테스트 파일**: `docs/week5/verification/k6/scripts/payment-process.js`
- **비즈니스 검증**: `PaymentTransactionService.java:reservePayment()`
- **Idempotency 구현**: `PaymentIdempotencyService.java`
