# 잔액 락 키 통일 수정 가이드

> **발견 일시**: 2025-11-26
> **문제**: 잔액 충전과 차감이 서로 다른 락 키 사용 → Lost Update 위험
> **해결**: 동일한 락 키 `balance:user:{userId}` 사용

---

## 🚨 문제 상황

### 코치님 피드백
> "포인트(잔액) 충전, 조회, 사용(차감) 등에는 **같은 락 키를 사용**해야 합니다.
> 그렇지 않으면 충전과 차감이 동시에 실행되어 Lost Update가 발생할 수 있습니다."

### 현재 잘못된 상태 ❌

**ChargeBalanceUseCase** (잔액 충전):
```java
@DistributedLock(
    key = "'charge:user:' + #userId",  // ❌ "charge:user:123"
    waitTime = 5,
    leaseTime = 10
)
```

**PaymentTransactionService** (잔액 차감):
```java
@DistributedLock(
    key = "'payment:user:' + #request.userId()",  // ❌ "payment:user:123"
    waitTime = 10,
    leaseTime = 30
)
```

**GetBalanceUseCase** (잔액 조회):
```java
// ❌ 분산락 없음
@Transactional(readOnly = true)
public BalanceResponse execute(Long userId)
```

---

## ⚠️ Lost Update 시나리오

### 문제 재현
```
초기 상태:
- 사용자 ID: 123
- 잔액: 10,000원

시간 순서:
T0: Thread 1 (충전 5,000원) 시작
    → 락 획득: "charge:user:123" ✅
    → 잔액 읽음: 10,000원

T1: Thread 2 (차감 8,000원) 시작
    → 락 획득: "payment:user:123" ✅ (다른 키라서 획득 가능!)
    → 잔액 읽음: 10,000원

T2: Thread 1 충전 완료
    → 10,000 + 5,000 = 15,000원 저장
    → 락 해제: "charge:user:123"

T3: Thread 2 차감 완료
    → 10,000 - 8,000 = 2,000원 저장 (Thread 1 결과 덮어쓰기!)
    → 락 해제: "payment:user:123"

최종 결과: 2,000원 ❌
예상 결과: 7,000원 (10,000 + 5,000 - 8,000)

→ 5,000원 손실! 💸
```

---

## ✅ 해결 방안

### 원칙: 동일한 리소스는 동일한 락 키 사용

**통일된 락 키: `balance:user:{userId}`**

---

## 📝 수정 내용

### 1. ChargeBalanceUseCase 수정

**Before (잘못됨):**
```java
@DistributedLock(
    key = "'charge:user:' + #userId",  // ❌
    waitTime = 5,
    leaseTime = 10
)
@Transactional
protected ChargeBalanceResponse chargeBalance(Long userId, ChargeBalanceRequest request)
```

**After (올바름):**
```java
@DistributedLock(
    key = "'balance:user:' + #userId",  // ✅ balance:user:123
    waitTime = 10,   // ✅ 10초로 증가 (결제와 동일)
    leaseTime = 30   // ✅ 30초로 증가 (결제와 동일)
)
@Transactional
protected ChargeBalanceResponse chargeBalance(Long userId, ChargeBalanceRequest request)
```

**변경 이유:**
- ✅ 락 키 통일: `balance:user:{userId}`
- ✅ waitTime 증가: 5초 → 10초 (결제와 동일, 안전성 우선)
- ✅ leaseTime 증가: 10초 → 30초 (결제와 동일, 데드락 방지)

---

### 2. PaymentTransactionService 수정

**Before (잘못됨):**
```java
@DistributedLock(
    key = "'payment:user:' + #request.userId()",  // ❌
    waitTime = 10,
    leaseTime = 30
)
@Transactional
public Order reservePayment(Long orderId, PaymentRequest request)
```

**After (올바름):**
```java
@DistributedLock(
    key = "'balance:user:' + #request.userId()",  // ✅ balance:user:123
    waitTime = 10,
    leaseTime = 30
)
@Transactional
public Order reservePayment(Long orderId, PaymentRequest request)
```

**변경 이유:**
- ✅ 락 키 통일: `balance:user:{userId}`
- ✅ 충전과 동일한 락 사용 → Lost Update 방지

---

### 3. GetBalanceUseCase (선택적)

**현재:**
```java
@Transactional(readOnly = true)
public BalanceResponse execute(Long userId) {
    User user = userRepository.findByIdOrThrow(userId);
    return BalanceResponse.of(user.getId(), user.getBalance());
}
```

**선택 1: 분산락 없이 유지 (권장)**
```java
// ✅ 읽기 전용이므로 락 불필요
// Dirty Read는 허용 (최신 값이 아닐 수 있음)
@Transactional(readOnly = true)
public BalanceResponse execute(Long userId)
```

**선택 2: 분산락 추가 (정확한 값이 필요한 경우)**
```java
@DistributedLock(
    key = "'balance:user:' + #userId",
    waitTime = 3,   // 조회는 짧게
    leaseTime = 5
)
@Transactional(readOnly = true)
public BalanceResponse execute(Long userId)
```

**권장 사항:**
- ✅ **선택 1 권장**: 조회는 락 없이 유지 (성능 우선)
- ⚠️ 선택 2는 필요 시만 (예: 정산, 감사 등 정확성 필수 시)

---

## 🔍 수정 후 동작 검증

### 올바른 동작 (수정 후) ✅

```
초기 상태:
- 사용자 ID: 123
- 잔액: 10,000원

시간 순서:
T0: Thread 1 (충전 5,000원) 시작
    → 락 획득 시도: "balance:user:123" ✅ 성공
    → 잔액 읽음: 10,000원

T1: Thread 2 (차감 8,000원) 시작
    → 락 획득 시도: "balance:user:123" ⏳ 대기 (Thread 1이 보유 중)

T2: Thread 1 충전 완료
    → 10,000 + 5,000 = 15,000원 저장
    → 락 해제: "balance:user:123"

T3: Thread 2 락 획득 성공
    → 잔액 읽음: 15,000원 (Thread 1 반영된 값)
    → 15,000 - 8,000 = 7,000원 저장
    → 락 해제: "balance:user:123"

최종 결과: 7,000원 ✅
예상 결과: 7,000원 (10,000 + 5,000 - 8,000)

→ 정확함! ✅
```

---

## 🧪 테스트 계획

### 동시성 테스트: 충전 + 차감 동시 실행

```java
@SpringBootTest
@Import(TestContainersConfig.class)
class BalanceConcurrencyTest {

    @Autowired
    private ChargeBalanceUseCase chargeBalanceUseCase;

    @Autowired
    private PaymentTransactionService paymentTransactionService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("동시 충전/차감 시 Lost Update 방지 검증")
    void 동시_충전_차감_정확성_테스트() throws InterruptedException {
        // Given: 초기 잔액 10,000원
        Long userId = 1L;
        User user = User.create("test@test.com", "테스트", 10_000L);
        userRepository.save(user);

        int chargeThreads = 50;  // 충전 50회 (각 1,000원)
        int deductThreads = 30;  // 차감 30회 (각 1,000원)
        int totalThreads = chargeThreads + deductThreads;

        ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch latch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When: 충전 50회 + 차감 30회 동시 실행
        // 충전 스레드
        for (int i = 0; i < chargeThreads; i++) {
            executorService.submit(() -> {
                try {
                    chargeBalanceUseCase.execute(
                        userId,
                        new ChargeBalanceRequest(1_000L)
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 차감 스레드 (결제)
        for (int i = 0; i < deductThreads; i++) {
            final int orderNum = i;
            executorService.submit(() -> {
                try {
                    // 결제 로직 (간소화)
                    Order order = createTestOrder(userId, 1_000L);
                    paymentTransactionService.reservePayment(
                        order.getId(),
                        new PaymentRequest(userId, "idempotency-" + orderNum)
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 최종 잔액 검증
        User finalUser = userRepository.findById(userId).orElseThrow();

        long expectedBalance = 10_000L  // 초기
                             + (chargeThreads * 1_000L)  // 충전 50,000
                             - (deductThreads * 1_000L); // 차감 30,000
        // 예상: 30,000원

        assertThat(finalUser.getBalance()).isEqualTo(expectedBalance);
        log.info("최종 잔액: {}원 (예상: {}원)", finalUser.getBalance(), expectedBalance);
        log.info("성공: {}, 실패: {}", successCount.get(), failCount.get());
    }

    private Order createTestOrder(Long userId, Long amount) {
        // 테스트용 주문 생성 로직
        Order order = Order.create("ORDER-TEST", userId, amount, 0L);
        return orderRepository.save(order);
    }
}
```

---

## 📊 락 키 설계 원칙

### 원칙: 동일한 리소스는 동일한 락 키

| 리소스 | 락 키 패턴 | 사용 위치 |
|-------|----------|----------|
| **사용자 잔액** | `balance:user:{userId}` | 충전, 차감, 조회(선택) |
| **상품 재고** | `stock:product:{productId}` | 재고 증가, 감소 |
| **쿠폰 발급** | `coupon:issue:{couponId}` | 쿠폰 발급 |
| **주문 생성** | `order:create:user:{userId}` | 주문 생성 |

### 락 키 명명 규칙

```
{도메인}:{리소스}:{식별자}

예시:
- balance:user:123
- stock:product:456
- coupon:issue:789
- order:create:user:123
```

---

## 🚀 수정 순서

### 1단계: ChargeBalanceUseCase 수정
```java
// src/main/java/io/hhplus/ecommerce/application/usecase/user/ChargeBalanceUseCase.java

@DistributedLock(
    key = "'balance:user:' + #userId",  // ✅ 변경
    waitTime = 10,                      // ✅ 변경
    leaseTime = 30                      // ✅ 변경
)
@Transactional
protected ChargeBalanceResponse chargeBalance(Long userId, ChargeBalanceRequest request)
```

### 2단계: PaymentTransactionService 수정
```java
// src/main/java/io/hhplus/ecommerce/application/usecase/order/PaymentTransactionService.java

@DistributedLock(
    key = "'balance:user:' + #request.userId()",  // ✅ 변경
    waitTime = 10,
    leaseTime = 30
)
@Transactional
public Order reservePayment(Long orderId, PaymentRequest request)
```

### 3단계: 동시성 테스트 작성
```bash
# 테스트 파일 생성
touch src/test/java/io/hhplus/ecommerce/application/usecase/user/BalanceConcurrencyTest.java

# 테스트 실행
./gradlew test --tests BalanceConcurrencyTest
```

### 4단계: 문서 업데이트
```bash
# DB_LOCK_TO_REDIS_LOCK_ANALYSIS.md 업데이트
# - ChargeBalanceUseCase 락 키 변경 반영
# - PaymentTransactionService 락 키 변경 반영
```

---

## ✅ 체크리스트

### 수정 완료 확인
- [ ] ChargeBalanceUseCase 락 키 변경 (`balance:user:{userId}`)
- [ ] ChargeBalanceUseCase waitTime/leaseTime 조정 (10/30초)
- [ ] PaymentTransactionService 락 키 변경 (`balance:user:{userId}`)
- [ ] 동시성 테스트 작성 (충전 + 차감 동시 실행)
- [ ] 테스트 통과 확인 (Lost Update 방지)
- [ ] 문서 업데이트 (DB_LOCK_TO_REDIS_LOCK_ANALYSIS.md)

### 추가 검증
- [ ] 충전만 100회 동시 실행 → 정확한 잔액
- [ ] 차감만 100회 동시 실행 → 정확한 잔액
- [ ] 충전 50회 + 차감 50회 동시 실행 → 정확한 잔액
- [ ] 조회는 락 없이도 정상 동작

---

## 📚 참고 자료

### 멘토링 내용
- `docs/week6/MENTOR_QNA.md` - 제이 코치님: "동일한 리소스는 동일한 락 키 사용"

### 관련 문서
- `docs/week6/DB_LOCK_TO_REDIS_LOCK_ANALYSIS.md` - 전체 락 전환 분석
- `docs/week6/LEARNING_SUMMARY.md` - 학습 정리

---

## 🎯 핵심 요약

### 문제
- ❌ 충전: `charge:user:123`
- ❌ 차감: `payment:user:123`
- ❌ 서로 다른 락 → Lost Update 발생

### 해결
- ✅ 충전: `balance:user:123`
- ✅ 차감: `balance:user:123`
- ✅ 동일한 락 → Lost Update 방지

### 원칙
**"동일한 리소스는 동일한 락 키를 사용한다"**

---

**작성자**: 항해플러스 백엔드 6기
**최종 수정일**: 2025-11-26
