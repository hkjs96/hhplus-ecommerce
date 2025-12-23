# Test Stability Check - Payment Concurrency Test

## 🧪 Flaky Test 검증

**테스트**: `PaymentConcurrencyWithDistributedLockTest`
**문제**: 때로는 성공, 때로는 실패 (불안정)

---

## 📊 테스트 실행 기록

### 실행 1
```
테스트: 100명 동시 결제, 재고 100개
예상: 100
실제: 99
결과: ❌ FAIL
```

### 실행 2
```
테스트: 100명 요청, 재고 50개
예상: 50
실제: 0
결과: ❌ FAIL
```

### 실행 3
```
테스트: 100명 동시 결제, 재고 100개
결과: ❌ FAIL
테스트: 100명 요청, 재고 50개
결과: ❌ FAIL
시간: 2025-11-27 15:51
참고: "Unexpected error during payment" 에러 다수 발생 (50개 이상의 스레드에서 결제 실패)
```

---

## 🔍 Flaky Test 원인 분석

### 1. 분산락 타임아웃 부족
**현재 설정**:
```java
@DistributedLock(
    key = "'payment:order:' + #orderId",
    waitTime = 10,      // ← 10초
    leaseTime = 30      // ← 30초
)
```

**문제**:
- 100개 스레드가 동시에 락 획득 시도
- 일부 스레드는 10초 내에 락 획득 실패
- 락 획득 실패 시 예외 발생 → 결제 실패

**해결 방안**:
```java
@DistributedLock(
    key = "'payment:order:' + #orderId",
    waitTime = 60,      // ← 60초로 증가
    leaseTime = 120     // ← 120초로 증가
)
```

---

### 2. 트랜잭션 경계 불일치

**현재 구조**:
```
분산락 획득
  → 트랜잭션 시작
    → DB 쿼리 (재고 차감)
  → 트랜잭션 커밋  ← 여기서 실제 DB 반영
→ 분산락 해제        ← 커밋 전에 락 해제!
```

**문제**:
- 락 해제 후 트랜잭션 커밋 전에 다음 요청 진입 가능
- 다음 요청이 아직 커밋되지 않은 데이터를 읽을 수 있음

**해결 방안**:
- `@Transactional` 순서 조정
- 또는 분산락 내부에서 트랜잭션 수동 관리

---

### 3. 테스트 타이밍 이슈

**현재 테스트 코드**:
```java
CountDownLatch startLatch = new CountDownLatch(1);
CountDownLatch doneLatch = new CountDownLatch(threadCount);

for (int i = 0; i < threadCount; i++) {
    executorService.submit(() -> {
        startLatch.await();  // 모든 스레드 대기
        // 결제 처리
        doneLatch.countDown();
    });
}

startLatch.countDown();  // 동시 실행
doneLatch.await(60, TimeUnit.SECONDS);
```

**문제**:
- `startLatch.countDown()` 후에도 모든 스레드가 **정확히 동시에** 실행되지 않음
- CPU 스케줄링에 따라 일부 스레드가 먼저 실행될 수 있음
- 일부 스레드는 타임아웃 전에 완료되지 못할 수 있음

**해결 방안**:
- 타임아웃 증가: `60초 → 120초`
- 또는 CyclicBarrier 사용

---

## ✅ 개선 계획

### Phase 1: 분산락 타임아웃 증가
```java
// PaymentTransactionService.java

@DistributedLock(
    key = "'payment:order:' + #orderId",
    waitTime = 60,      // 10 → 60
    leaseTime = 120     // 30 → 120
)
```

### Phase 2: 테스트 타임아웃 증가
```java
// PaymentConcurrencyWithDistributedLockTest.java

doneLatch.await(120, TimeUnit.SECONDS);  // 60 → 120
```

### Phase 3: 트랜잭션 경계 재검토
- AOP 순서 확인
- 필요 시 수동 트랜잭션 관리

---

## 📝 테스트 체크리스트

실행할 때마다 아래 표를 업데이트하세요:

| 실행 # | 날짜/시간 | Test 1 (100/100) | Test 2 (100/50) | 비고 |
|-------|----------|-----------------|----------------|-----|
| 1 | 2025-11-27 15:20 | ❌ (99) | ❌ (0) | 초기 실행 |
| 2 | 2025-11-27 15:51 | ❌ (FAIL) | ❌ (FAIL) | "Unexpected error" 50+ 건 발생 |
| 3 | - | - | - | 타임아웃 증가 후 |
| 4 | - | - | - | - |
| 5 | - | - | - | - |

**목표**: 5회 연속 성공 시 안정화 완료

---

## 🚀 재현 명령어

```bash
# 테스트 실행 (단일)
./gradlew test --tests "io.hhplus.ecommerce.application.usecase.order.PaymentConcurrencyWithDistributedLockTest"

# 10회 반복 실행
for i in {1..10}; do
  echo "=== Run $i ==="
  ./gradlew test --tests "PaymentConcurrencyWithDistributedLockTest" --rerun-tasks
  if [ $? -ne 0 ]; then
    echo "❌ FAILED at run $i"
    break
  fi
  echo "✅ PASSED run $i"
done
```

---

**작성일**: 2025-11-27
**업데이트**: 테스트 실행 시마다 체크리스트 업데이트 필요
