# 5. 동시성 제어 (Concurrency Control)

## 📌 핵심 개념

**동시성 제어**: 여러 스레드가 동시에 같은 자원에 접근할 때 데이터 일관성을 보장하는 기법

---

## 🎯 Week 3 동시성 제어 범위

### 로이코치님 조언
> "Week 3에서 동시성 제어는 Step 6의 선착순 쿠폰 발급만 고민하면 됩니다."

### Step 5: 동시성 제어 불필요 ❌
- ConcurrentHashMap만으로 충분
- 레이어드 아키텍처 구현에 집중

### Step 6: 선착순 쿠폰만 동시성 제어 ✅
- Race Condition 방지 필수
- 200명 요청 → 정확히 100개만 발급

---

## ⚠️ Race Condition이란?

### 문제 상황
```java
// 동시성 제어 없는 쿠폰 발급 (❌ 문제 있음)
public class Coupon {
    private Integer totalQuantity = 100;
    private Integer issuedQuantity = 0;  // 일반 Integer

    public void issue() {
        // Race Condition 발생!
        if (issuedQuantity < totalQuantity) {  // 1. 체크
            issuedQuantity++;  // 2. 증가
        }
    }
}
```

**시나리오:**
```
Thread A: issuedQuantity=99, totalQuantity=100
Thread B: issuedQuantity=99, totalQuantity=100

Thread A: if (99 < 100) ✅ → issuedQuantity=100
Thread B: if (99 < 100) ✅ → issuedQuantity=101  ❌ 초과 발급!
```

**결과**: 200명이 동시 요청 시 100개를 초과하여 발급될 수 있음

---

## 🔒 4가지 동시성 제어 방식

### 1. synchronized (가장 간단)

**특징:**
- ✅ 구현이 가장 간단
- ❌ 메서드 전체를 잠금 (성능 저하)

```java
public class CouponService {
    private final CouponRepository couponRepository;

    // 메서드 전체에 Lock
    public synchronized UserCoupon issueCoupon(String userId, String couponId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_COUPON));

        if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }

        coupon.increaseIssuedQuantity();
        return userCouponRepository.save(new UserCoupon(userId, couponId));
    }
}
```

**장점:** 구현 간단
**단점:** 전체 메서드 잠금 (성능 저하)

---

### 2. ReentrantLock (세밀한 제어)

**특징:**
- ✅ tryLock(), timeout 등 세밀한 제어 가능
- ❌ synchronized보다 복잡

```java
public class CouponService {
    private final ReentrantLock lock = new ReentrantLock();
    private final CouponRepository couponRepository;

    public UserCoupon issueCoupon(String userId, String couponId) {
        lock.lock();  // Lock 획득
        try {
            Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_COUPON));

            if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
                throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
            }

            coupon.increaseIssuedQuantity();
            return userCouponRepository.save(new UserCoupon(userId, couponId));
        } finally {
            lock.unlock();  // 반드시 unlock (finally 블록)
        }
    }
}
```

**장점:** 세밀한 제어, tryLock() 사용 가능
**단점:** synchronized보다 복잡

---

### 3. AtomicInteger (가장 빠름) ⭐ 권장

**특징:**
- ✅ Lock-free (가장 빠른 성능)
- ✅ CAS (Compare-And-Swap) 기반
- ❌ 단순 증감만 가능

```java
@Getter
public class Coupon {
    private String id;
    private String name;
    private Integer totalQuantity;
    private AtomicInteger issuedQuantity;  // Atomic 사용

    public Coupon(String id, String name, int total) {
        this.id = id;
        this.name = name;
        this.totalQuantity = total;
        this.issuedQuantity = new AtomicInteger(0);
    }

    /**
     * CAS (Compare-And-Swap) 기반 동시성 제어
     * Lock 없이 원자적 연산으로 안전하게 증가
     */
    public boolean tryIssue() {
        while (true) {
            int current = issuedQuantity.get();

            // 수량 초과 체크
            if (current >= totalQuantity) {
                return false;  // 발급 실패
            }

            // CAS 연산: current 값이 그대로면 current+1로 변경
            if (issuedQuantity.compareAndSet(current, current + 1)) {
                return true;  // 발급 성공
            }
            // CAS 실패 → 다른 스레드가 변경함 → 재시도
        }
    }

    public int getRemainingQuantity() {
        return totalQuantity - issuedQuantity.get();
    }
}
```

**장점:** 가장 빠름, Lock-free
**단점:** 복잡한 로직에는 부적합

---

### 4. BlockingQueue (순차 처리)

**특징:**
- ✅ 순차 처리로 동시성 문제 원천 차단
- ❌ 비동기 처리 (즉시 응답 불가)

```java
@Service
public class CouponService {
    private final BlockingQueue<CouponIssueRequest> queue = new LinkedBlockingQueue<>();

    @PostConstruct
    public void init() {
        // 별도 스레드에서 큐 처리
        new Thread(() -> {
            while (true) {
                try {
                    CouponIssueRequest request = queue.take();
                    processIssueCoupon(request);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    public void issueCoupon(String userId, String couponId) {
        // 큐에 추가 (비동기 처리)
        queue.offer(new CouponIssueRequest(userId, couponId));
    }

    private void processIssueCoupon(CouponIssueRequest request) {
        // 순차적으로 쿠폰 발급 처리
        // Race Condition 발생 안 함
    }
}
```

**장점:** 순차 처리로 안전
**단점:** 비동기 처리 (즉시 응답 불가)

---

## 📊 방식 비교

| 방식 | 성능 | 구현 난이도 | 사용 시기 | Week 3 추천 |
|------|------|------------|----------|------------|
| **synchronized** | ⚡⚡ | 쉬움 | 간단한 로직 | ✅ |
| **ReentrantLock** | ⚡⚡ | 보통 | 세밀한 제어 필요 | ✅ |
| **AtomicInteger** | ⚡⚡⚡ | 어려움 | 카운터, 수량 관리 | ⭐ 가장 추천 |
| **BlockingQueue** | ⚡ | 어려움 | 비동기 처리 OK | △ |

---

## 🧪 동시성 테스트 작성

### ExecutorService + CountDownLatch 활용

```java
@SpringBootTest
class CouponConcurrencyTest {

    @Autowired
    private CouponUseCase couponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Test
    void 선착순_쿠폰_동시성_테스트() throws InterruptedException {
        // Given: 쿠폰 100개 생성
        String couponId = "C001";
        Coupon coupon = new Coupon(couponId, "10% 할인", 10, 100);
        couponRepository.save(coupon);

        int threadCount = 200;  // 200명이 동시에 요청
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 200명이 동시에 쿠폰 발급 시도
        for (int i = 0; i < threadCount; i++) {
            String userId = "U" + String.format("%03d", i);
            executorService.submit(() -> {
                try {
                    couponUseCase.issueCoupon(userId, couponId);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();  // 모든 스레드 완료 대기
        executorService.shutdown();

        // Then: 정확히 100개만 발급되어야 함
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(100);

        Coupon result = couponRepository.findById(couponId).orElseThrow();
        assertThat(result.getIssuedQuantity()).isEqualTo(100);
    }
}
```

**핵심 포인트:**
- `ExecutorService`: 200개 스레드 동시 실행
- `CountDownLatch`: 모든 스레드 완료 대기
- `AtomicInteger`: Thread-safe 카운터

---

## ✅ Pass 기준 (Step 6)

### 동시성 제어 구현
- [ ] Race Condition 방지 (200명 요청 → 정확히 100개 발급)
- [ ] 동시성 제어 방식 선택 (synchronized, Lock, Atomic, Queue 중 택1)
- [ ] 일관성 보장 (테스트 실행마다 같은 결과)

### 통합 테스트
- [ ] ExecutorService + CountDownLatch 활용
- [ ] 200명 요청 → 100명 성공, 100명 실패 검증
- [ ] 테스트 100% 통과

### 문서화
- [ ] README.md에 동시성 제어 방식 설명
- [ ] 선택 이유 작성
- [ ] 대안 비교 (최소 2가지)

---

## ❌ Fail 사유

### 동시성 제어 Fail
- ❌ Race Condition 발생 (100개 초과 발급)
- ❌ 동시성 제어 부재
- ❌ 불안정한 결과 (실행마다 다름)

### 테스트 Fail
- ❌ 동시성 테스트 부재
- ❌ 단일 스레드 테스트만 존재
- ❌ 테스트 통과 실패

---

## 🎯 학습 체크리스트

### 이론 이해
- [ ] Race Condition이 무엇인지 설명할 수 있다
- [ ] 4가지 동시성 제어 방식의 차이를 설명할 수 있다
- [ ] CAS (Compare-And-Swap)의 동작 원리를 설명할 수 있다

### 실전 적용
- [ ] AtomicInteger로 동시성 제어를 구현할 수 있다
- [ ] ExecutorService로 동시성 테스트를 작성할 수 있다
- [ ] CountDownLatch의 역할을 이해하고 사용할 수 있다

### 토론 주제
- "synchronized와 ReentrantLock의 차이는?"
- "AtomicInteger가 가장 빠른 이유는?"
- "BlockingQueue 방식의 장단점은?"

---

## 📚 참고 자료

- [Java Concurrency in Practice](https://jcip.net/)
- [Oracle - Java Concurrency Tutorial](https://docs.oracle.com/javase/tutorial/essential/concurrency/)
- CLAUDE.md - Q13. Week 3에서 동시성 제어를 고민해야 하나요?

---

## 💡 실전 팁

### Step 5 vs Step 6 구분
```java
// Step 5: 동시성 제어 불필요
@Repository
public class InMemoryProductRepository {
    private final Map<String, Product> storage = new ConcurrentHashMap<>();  // 이것만으로 충분
}

// Step 6: 선착순 쿠폰만 동시성 제어 추가
public class Coupon {
    private AtomicInteger issuedQuantity;  // 동시성 제어 추가

    public boolean tryIssue() {
        // CAS 기반 동시성 제어
    }
}
```

---

**이전 학습**: [04. Repository 패턴](./04-repository-pattern.md)
**다음 학습**: [06. 테스트 전략](./06-testing-strategy.md)
