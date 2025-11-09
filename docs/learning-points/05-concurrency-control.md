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

> **참고**: ConcurrentHashMap의 상세한 내부 동작 원리와 활용법은 [09. Thread-Safe 컬렉션](./09-concurrent-collections.md)을 참조하세요.

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

## 🔍 synchronized vs ReentrantLock vs CAS 상세 비교 ⭐

### 코치 피드백
> synchronized 외에도 ReentrantLock이 더 나은 경우가 있습니다. 각 방식의 장단점을 비교해보세요.

### 비교표

| 항목 | synchronized | ReentrantLock | CAS (AtomicInteger) |
|------|--------------|---------------|---------------------|
| **성능** | 보통 (전체 메서드 잠금) | 보통 (명시적 잠금) | 빠름 (Lock-free) |
| **공정성** | 보장 안 됨 | 보장 가능 (fair=true) | 보장 안 됨 |
| **타임아웃** | 불가능 | 가능 (tryLock) | N/A |
| **조건 변수** | wait/notify | Condition 지원 | 불가능 |
| **복잡도** | 쉬움 | 보통 (finally 필수) | 어려움 (while loop) |
| **사용 사례** | 간단한 동기화 | 복잡한 제어 | 단순 카운터 |
| **경합 시 성능** | 보통 | 보통 | 우수 (스핀 락) |

---

### 1️⃣ synchronized의 장단점

#### 장점
- ✅ 구현이 가장 간단
- ✅ JVM 레벨에서 최적화
- ✅ 자동 락 해제 (메서드 종료 시)

#### 단점
- ❌ 메서드 전체를 잠금 (세밀한 제어 불가)
- ❌ 공정성 보장 안 됨 (먼저 요청한 스레드가 먼저 획득 보장 X)
- ❌ 타임아웃 설정 불가
- ❌ 조건 변수 사용 제한적 (wait/notify만 가능)

**사용 시기:**
- 단순한 동기화 로직
- 메서드 전체를 보호해야 하는 경우
- 빠른 개발이 필요한 경우

---

### 2️⃣ ReentrantLock의 장점과 고급 기능

#### 장점
- ✅ 공정성 보장 가능 (FIFO 순서)
- ✅ tryLock으로 타임아웃 설정
- ✅ Condition을 활용한 복잡한 대기/통지
- ✅ 락 획득/해제 시점 제어 가능

#### 단점
- ❌ finally 블록에서 unlock 필수 (실수 가능)
- ❌ synchronized보다 복잡
- ❌ 성능은 synchronized와 비슷

---

#### 고급 기능 1: 공정성 보장 (Fairness)

**문제:** synchronized는 먼저 대기한 스레드가 먼저 락을 획득한다는 보장이 없음

**해결:**
```java
public class CouponService {
    // fair=true: FIFO 순서로 락 획득 보장
    private final ReentrantLock lock = new ReentrantLock(true);
    private final CouponRepository couponRepository;

    public UserCoupon issueCoupon(String userId, String couponId) {
        lock.lock();  // 먼저 대기한 스레드가 먼저 획득
        try {
            Coupon coupon = couponRepository.findByIdOrThrow(couponId);

            if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
                throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
            }

            coupon.increaseIssuedQuantity();
            return userCouponRepository.save(new UserCoupon(userId, couponId));
        } finally {
            lock.unlock();  // 반드시 unlock
        }
    }
}
```

**사용 시기:**
- 선착순 이벤트에서 대기 순서가 중요한 경우
- 응답 시간의 일관성이 중요한 경우

---

#### 고급 기능 2: Condition (조건 변수)

**문제:** synchronized의 wait/notify는 단일 조건만 지원

**해결:**
```java
public class CouponService {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition couponAvailable = lock.newCondition();  // 조건 변수
    private final Condition refillNeeded = lock.newCondition();     // 여러 조건 가능

    // 쿠폰 발급 대기
    public UserCoupon waitForCoupon(String userId, String couponId)
        throws InterruptedException {

        lock.lock();
        try {
            Coupon coupon = couponRepository.findByIdOrThrow(couponId);

            // 쿠폰이 없으면 대기
            while (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
                couponAvailable.await();  // 대기 (락 해제)
            }

            // 쿠폰 발급
            coupon.increaseIssuedQuantity();
            return userCouponRepository.save(new UserCoupon(userId, couponId));
        } finally {
            lock.unlock();
        }
    }

    // 쿠폰 재고 보충
    public void refillCoupon(String couponId, int quantity) {
        lock.lock();
        try {
            Coupon coupon = couponRepository.findByIdOrThrow(couponId);
            coupon.increaseTotalQuantity(quantity);

            couponAvailable.signalAll();  // 대기 중인 스레드 깨우기
        } finally {
            lock.unlock();
        }
    }
}
```

**사용 시기:**
- 생산자-소비자 패턴
- 여러 조건에 따른 대기/통지가 필요한 경우

---

#### 고급 기능 3: tryLock (타임아웃)

**문제:** synchronized는 무한정 대기 (데드락 위험)

**해결:**
```java
public UserCoupon issueCoupon(String userId, String couponId) {
    try {
        // 1초 내 락 획득 시도
        if (lock.tryLock(1, TimeUnit.SECONDS)) {
            try {
                Coupon coupon = couponRepository.findByIdOrThrow(couponId);

                if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
                    throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
                }

                coupon.increaseIssuedQuantity();
                return userCouponRepository.save(new UserCoupon(userId, couponId));
            } finally {
                lock.unlock();
            }
        } else {
            // 락 획득 실패 (타임아웃)
            throw new BusinessException(
                ErrorCode.COUPON_BUSY,
                "쿠폰 발급 중입니다. 잠시 후 다시 시도해주세요."
            );
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new BusinessException(ErrorCode.COUPON_INTERRUPTED);
    }
}
```

**사용 시기:**
- 데드락 방지가 필요한 경우
- 일정 시간 내 응답이 필요한 경우
- 락 대기 시간을 제한하고 싶은 경우

---

### 3️⃣ CAS (AtomicInteger)의 장점

#### 장점
- ✅ **Lock-free**: 스레드 블로킹 없음
- ✅ **성능**: 경합이 낮을 때 가장 빠름
- ✅ **데드락 없음**: 락을 사용하지 않음
- ✅ **컨텍스트 스위칭 비용 없음**

#### 단점
- ❌ 단순 증감만 가능 (복잡한 로직 불가)
- ❌ 경합이 높으면 스핀으로 CPU 사용
- ❌ 공정성 보장 안 됨

---

#### CAS 동작 원리

```java
// CAS (Compare-And-Swap) 의사 코드
boolean compareAndSet(int expect, int update) {
    if (currentValue == expect) {
        currentValue = update;
        return true;  // 성공
    }
    return false;  // 실패 (다른 스레드가 변경함)
}
```

**실제 사용:**
```java
public class Coupon {
    private AtomicInteger issuedQuantity = new AtomicInteger(0);
    private Integer totalQuantity = 100;

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
            // CAS 실패 → 다른 스레드가 변경함 → 재시도 (스핀)
        }
    }
}
```

**왜 빠른가?**
1. **락 없음**: 스레드 블로킹이 없음
2. **CPU 명령어**: 하드웨어 레벨에서 원자성 보장 (CMPXCHG)
3. **경합이 낮으면**: 한 번에 성공 (재시도 없음)

---

### 4️⃣ 어떤 것을 선택해야 하나?

#### Week 3 과제: CAS (AtomicInteger) 권장 ⭐

**이유:**
- ✅ 선착순 쿠폰 발급은 단순 카운터 증가
- ✅ Lock-free로 가장 빠른 성능
- ✅ 공정성이 필수가 아님 (선착순)
- ✅ 경합이 높지 않음 (쿠폰 발급 시간이 짧음)

```java
// Week 3 과제에 가장 적합
public class Coupon {
    private AtomicInteger issuedQuantity = new AtomicInteger(0);

    public boolean tryIssue() {
        // CAS 기반 동시성 제어
    }
}
```

---

#### 프로덕션 확장 시: ReentrantLock 고려

**고려 상황:**
- 복잡한 로직 (여러 검증 단계)
- 공정성 필요 (FIFO 보장)
- 조건 변수 필요 (대기/통지)
- 타임아웃 제어 필요

```java
// 복잡한 비즈니스 로직 + 공정성 필요
public class CouponService {
    private final ReentrantLock lock = new ReentrantLock(true);  // 공정성

    public UserCoupon issueCoupon(String userId, String couponId) {
        lock.lock();
        try {
            // 1. 중복 발급 체크
            // 2. 사용자 등급 확인
            // 3. 발급 가능 시간 확인
            // 4. 쿠폰 발급
        } finally {
            lock.unlock();
        }
    }
}
```

---

#### synchronized는 언제 사용?

**사용 시기:**
- 간단한 동기화 (메서드 전체 보호)
- 빠른 개발이 우선
- 성능이 크게 중요하지 않음

```java
// 간단한 케이스
public synchronized void updateCache(String key, String value) {
    cache.put(key, value);
}
```

---

### 선택 가이드 요약

```
┌─────────────────────────────────────────────────────────┐
│ 단순 카운터 증감 (쿠폰 발급량, 재고)                     │
│ → AtomicInteger (CAS)                                   │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ 복잡한 로직 + 공정성 필요 + 타임아웃 제어                │
│ → ReentrantLock                                         │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ 간단한 동기화 + 빠른 개발                                │
│ → synchronized                                          │
└─────────────────────────────────────────────────────────┘
```

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
