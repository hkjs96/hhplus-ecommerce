# K6 테스트 검증 로직 수정

## 🔍 발견된 오해

### 이전 가정 (잘못됨)
"동일한 Idempotency Key로 3번 요청 시 → 1번만 200 OK, 2-3번은 409 CONFLICT"

### 실제 동작 (올바름)
"동일한 Idempotency Key로 3번 요청 시 → 1번은 새 결제 (200 OK), 2-3번은 **캐시된 응답 반환 (200 OK)**"

---

## ✅ Idempotency 실제 동작

### Application 로그 분석

```
# Request 1: 새로운 결제
SELECT ... FROM payment_idempotency WHERE idempotency_key=? FOR UPDATE  -- NULL
INSERT INTO payment_idempotency (status='PROCESSING', ...)
-- 결제 처리 --
UPDATE payment_idempotency SET status='COMPLETED', response_payload='...'
→ 200 OK + 결제 완료 응답

# Request 2: 동일한 키로 재요청
SELECT ... FROM payment_idempotency WHERE idempotency_key=? FOR UPDATE  -- COMPLETED
INFO Found completed payment for idempotencyKey: xxx
INFO Returning cached payment result
→ 200 OK + **동일한 캐시 응답**

# Request 3: 동일한 키로 재요청
SELECT ... FROM payment_idempotency WHERE idempotency_key=? FOR UPDATE  -- COMPLETED
INFO Found completed payment for idempotencyKey: xxx
INFO Returning cached payment result
→ 200 OK + **동일한 캐시 응답**
```

**핵심**:
- COMPLETED 상태의 결제는 **409 CONFLICT가 아닌 200 OK** 반환
- 응답 본문은 첫 번째와 **완전히 동일** (캐시된 응답)

---

## 🔴 이전 K6 검증 로직의 문제

### Before: 잘못된 검증
```javascript
const successCount = paymentResults.filter(r => r === 'SUCCESS').length;
const conflictCount = paymentResults.filter(r => r === 'CONFLICT').length;

if (successCount === 1 && conflictCount === 2) {
  // ✅ Idempotency verified
} else {
  // ❌ Idempotency failed
}
```

**문제**:
- 2-3번째 요청도 200 OK 반환 → 'SUCCESS'로 카운트
- `successCount === 3`, `conflictCount === 0`
- → ❌ Idempotency failed (잘못된 판정)

---

## ✅ 수정된 K6 검증 로직

### After: 응답 본문 비교

```javascript
function processPaymentWithRetries(orderId, userId, idempotencyKey) {
  const results = [];
  let firstResponseBody = null;

  for (let i = 0; i < 3; i++) {
    const result = processPayment(orderId, userId, idempotencyKey, i + 1);

    // 첫 번째 응답 본문 저장
    if (i === 0 && result.body) {
      firstResponseBody = result.body;
    }

    // 두 번째, 세 번째 요청은 첫 번째와 동일한 응답인지 확인
    if (i > 0 && result.body && firstResponseBody) {
      if (result.body === firstResponseBody) {
        // 캐시된 응답 (중복 방지 성공)
        results.push('CACHED');
        continue;
      }
    }

    results.push(result.status);
    sleep(0.1);
  }

  return results;
}
```

### 검증 로직

```javascript
const successCount = paymentResults.filter(r => r === 'SUCCESS').length;
const cachedCount = paymentResults.filter(r => r === 'CACHED').length;
const conflictCount = paymentResults.filter(r => r === 'CONFLICT').length;

// Idempotency 성공 조건:
// - 1번만 새 결제 (SUCCESS)
// - 2~3번은 캐시 반환 (CACHED) 또는 충돌 (CONFLICT)
if (successCount === 1 && (cachedCount + conflictCount) === 2) {
  idempotencyVerificationSuccess.add(1);
  duplicatePaymentsPrevented.add(cachedCount + conflictCount);
  console.log(`✅ Idempotency verified: 1 new, ${cachedCount} cached, ${conflictCount} conflicts`);
} else {
  idempotencyVerificationFailure.add(1);
  console.log(`❌ Idempotency failed: ${successCount} new, ${cachedCount} cached, ${conflictCount} conflicts`);
}
```

---

## 📊 기대 결과

### Before (잘못된 검증)
```
[VU 8, Iter 147, Attempt 1] Payment SUCCESS
[VU 8, Iter 147, Attempt 2] Payment SUCCESS
[VU 8, Iter 147, Attempt 3] Payment SUCCESS
❌ Idempotency failed: 3 successes, 0 conflicts

idempotency_verification_success...: 0/s  ❌
```

### After (올바른 검증)
```
[VU 8, Iter 147, Attempt 1] Payment SUCCESS
[VU 8, Iter 147, Attempt 2] Payment SUCCESS (CACHED)
[VU 8, Iter 147, Attempt 3] Payment SUCCESS (CACHED)
✅ Idempotency verified: 1 new, 2 cached, 0 conflicts

idempotency_verification_success...: >20000/s  ✅
duplicate_payments_prevented.......: >40000/s  ✅
```

---

## 🎯 Idempotency 패턴 비교

### Pattern 1: 409 CONFLICT (PROCESSING 상태 충돌)

**시나리오**: 동시 요청이 거의 동시에 도착

```
Time   | Request 1                     | Request 2
-------|-------------------------------|-------------------------------
T+0ms  | SELECT FOR UPDATE → NULL      |
T+10ms | INSERT → PROCESSING           |
T+20ms |                               | SELECT FOR UPDATE → Lock 대기
T+100ms| 결제 처리 중...                | (여전히 대기)
T+200ms|                               | Lock 획득 → PROCESSING 조회
T+210ms|                               | → 409 CONFLICT ✅
```

**응답**: 409 CONFLICT

### Pattern 2: 200 OK + CACHED (COMPLETED 상태)

**시나리오**: 첫 번째 결제 완료 후 재요청

```
Time   | Request 1                     | Request 2
-------|-------------------------------|-------------------------------
T+0ms  | SELECT FOR UPDATE → NULL      |
T+100ms| INSERT → PROCESSING           |
T+200ms| 결제 완료 → COMPLETED          |
T+300ms|                               | SELECT FOR UPDATE → COMPLETED 조회
T+310ms|                               | → 200 OK + Cached Response ✅
```

**응답**: 200 OK (캐시된 응답)

---

## 📝 학습 포인트

### 1. Idempotency ≠ 409 CONFLICT

**잘못된 이해**:
- "중복 요청은 항상 409 CONFLICT를 반환해야 한다"

**올바른 이해**:
- **PROCESSING 상태 충돌** → 409 CONFLICT (동시 요청)
- **COMPLETED 상태 재요청** → 200 OK + 캐시 응답 (멱등성 보장)

### 2. 멱등성의 정의

> "동일한 요청을 여러 번 해도 **결과가 동일**하다"

**핵심**:
- 응답 코드가 동일할 필요는 없음
- **응답 내용이 동일**하면 멱등성 보장

### 3. K6 테스트 설계 시 주의

**응답 코드만으로 판단하면 안 됨**:
```javascript
// ❌ 잘못된 검증
if (response.status === 200) return 'SUCCESS';
if (response.status === 409) return 'CONFLICT';
```

**응답 본문까지 비교해야 함**:
```javascript
// ✅ 올바른 검증
if (response.body === firstResponseBody) return 'CACHED';
```

---

## 🚀 적용 방법

### 1. K6 스크립트 수정 완료 ✅
- `processPayment()`: 응답 객체 반환 (status + body)
- `processPaymentWithRetries()`: 응답 본문 비교
- 검증 로직: CACHED 카운트 추가

### 2. K6 테스트 재실행
```bash
k6 run docs/week5/verification/k6/scripts/payment-process.js
```

### 3. 기대 결과
```
✅ THRESHOLDS (모두 통과)
  ✓ idempotency_verification_success: count>0
  ✓ duplicate_payments_prevented: count>0
  ✓ http_req_duration: p(95)<1000

✅ SUCCESS METRICS
  idempotency_verification_success...: >18000  (1 new + 2 cached per iteration)
  duplicate_payments_prevented.......: >36000  (2 cached per iteration)
  errors.............................: <5%
```

---

## 🔍 Application 코드는 올바름!

### PaymentIdempotencyService.java (정상 동작)

```java
@Transactional
public PaymentIdempotencyResult getOrCreate(PaymentRequest request) {
    Optional<PaymentIdempotency> existing = paymentIdempotencyRepository
        .findByIdempotencyKeyWithLock(request.idempotencyKey());  // ✅ Pessimistic Lock

    if (existing.isPresent()) {
        PaymentIdempotency idempotency = existing.get();

        // COMPLETED: 캐시된 결과 반환 (200 OK)  ✅
        if (idempotency.isCompleted()) {
            log.info("Found completed payment for idempotencyKey: {}", request.idempotencyKey());
            PaymentResponse cachedResponse = deserializeResponse(idempotency.getResponsePayload());
            return PaymentIdempotencyResult.completed(cachedResponse);
        }

        // PROCESSING: 동시 요청 (409 Conflict)  ✅
        if (idempotency.isProcessing()) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "...");
        }
    }

    // 새로 생성  ✅
    PaymentIdempotency newKey = PaymentIdempotency.create(...);
    return paymentIdempotencyRepository.save(newKey);
}
```

**모두 올바르게 구현됨!** ✅

---

## ✅ 체크리스트

- [x] Application 로그 분석 (Pessimistic Lock 정상 동작)
- [x] Idempotency 패턴 이해 (COMPLETED → 200 OK + Cached)
- [x] K6 검증 로직 수정 (응답 본문 비교)
- [ ] K6 테스트 재실행
- [ ] Idempotency 검증 성공 확인 (>18000)

---

## 📚 참고

- **수정 파일**: `payment-process.js:181-247`
- **Application 코드**: `PaymentIdempotencyService.java:37-74` (정상)
- **관련 문서**: `IDEMPOTENCY_RACE_CONDITION_FIX.md`
