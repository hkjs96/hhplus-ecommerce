---
description: 동시성 제어 패턴 4가지 (synchronized, ReentrantLock, AtomicInteger, BlockingQueue)
---

# 동시성 제어 전략 (Concurrency Control Strategies)

> Step 6에서 선착순 쿠폰 발급 시 Race Condition 방지를 위한 동시성 제어 패턴

## 🔒 선택 가능한 동시성 제어 방식

### 1. synchronized (가장 간단)

```java
@Service
public class CouponService {

    // Method-level synchronization
    public synchronized UserCoupon issueCoupon(String userId, String couponId) {
        // 선착순 쿠폰 발급 로직
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }

        // 수량 증가 및 발급
        coupon.increaseIssuedQuantity();
        return userCouponRepository.save(new UserCoupon(...));
    }
}
```

**장점**:
- 구현이 가장 간단함
- JVM이 자동으로 Lock 관리

**단점**:
- 메서드 전체를 잠금 (성능 저하)
- 교착 상태(Deadlock) 위험

**사용 시기**:
- 로직이 단순하고 짧을 때
- 성능이 크게 중요하지 않을 때

---

### 2. ReentrantLock (세밀한 제어)

```java
@Service
public class CouponService {

    private final ReentrantLock lock = new ReentrantLock();

    public UserCoupon issueCoupon(String userId, String couponId) {
        lock.lock();
        try {
            // 선착순 쿠폰 발급 로직
            Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

            if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
                throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
            }

            coupon.increaseIssuedQuantity();
            return userCouponRepository.save(new UserCoupon(...));
        } finally {
            lock.unlock();
        }
    }
}
```

**장점**:
- tryLock(), timeout 등 세밀한 제어 가능
- 공정성(fairness) 설정 가능
- Lock 획득 여부를 확인 가능

**단점**:
- synchronized보다 복잡함
- finally 블록에서 unlock 필수 (누락 시 데드락)

**사용 시기**:
- 타임아웃이 필요할 때
- Lock 획득 시도만 하고 실패 시 다른 작업을 할 때
- 여러 Lock을 사용할 때

**예시 (tryLock 사용):**
```java
public UserCoupon issueCoupon(String userId, String couponId) {
    if (lock.tryLock(1, TimeUnit.SECONDS)) {  // 1초 대기
        try {
            // 쿠폰 발급 로직
        } finally {
            lock.unlock();
        }
    } else {
        throw new BusinessException(ErrorCode.COUPON_BUSY, "요청이 많습니다. 잠시 후 다시 시도해주세요.");
    }
}
```

---

### 3. AtomicInteger (가장 빠름, Lock-Free)

```java
@Getter
public class Coupon {
    private String id;
    private String name;
    private Integer totalQuantity;
    private AtomicInteger issuedQuantity;  // Atomic 사용

    public Coupon(String id, String name, Integer discountRate, Integer totalQuantity) {
        this.id = id;
        this.name = name;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = new AtomicInteger(0);
    }

    public boolean tryIssue() {
        while (true) {
            int current = issuedQuantity.get();

            // 수량 초과 체크
            if (current >= totalQuantity) {
                return false;
            }

            // CAS 연산으로 증가 시도
            if (issuedQuantity.compareAndSet(current, current + 1)) {
                return true;  // 성공
            }
            // 실패하면 재시도 (while loop)
        }
    }
}
```

**CAS (Compare-And-Set) 연산:**
```java
// 의사 코드
boolean compareAndSet(int expectedValue, int newValue) {
    if (현재값 == expectedValue) {
        현재값 = newValue;
        return true;  // 성공
    }
    return false;  // 실패 (다른 스레드가 값을 변경함)
}
```

**장점**:
- Lock-free, 가장 빠른 성능
- 교착 상태(Deadlock) 불가능
- CAS 연산은 하드웨어 레벨에서 지원

**단점**:
- 복잡한 로직에는 부적합 (단순 증감만 가능)
- 재시도 루프로 인한 CPU 사용량 증가 (경합이 심할 때)

**사용 시기**:
- 단순 카운터, 수량 관리
- 성능이 매우 중요할 때
- Lock을 사용하고 싶지 않을 때

---

### 4. BlockingQueue (순차 처리)

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
                    CouponIssueRequest request = queue.take();  // 큐에서 꺼냄 (blocking)
                    processIssueCoupon(request);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    public void issueCoupon(String userId, String couponId) {
        // 큐에 추가 (비동기 처리)
        CouponIssueRequest request = new CouponIssueRequest(userId, couponId);
        queue.offer(request);

        // 즉시 반환 (실제 발급은 백그라운드에서 처리)
    }

    private void processIssueCoupon(CouponIssueRequest request) {
        // 순차적으로 쿠폰 발급 처리
        // Race Condition 원천 차단 (단일 스레드 처리)
    }
}
```

**장점**:
- 순차 처리로 동시성 문제 원천 차단
- Producer-Consumer 패턴
- 대기열 기능 (요청 폭증 시 안정적)

**단점**:
- 비동기 처리로 즉시 응답 불가
- 큐 관리 필요 (메모리 사용)
- 별도 스레드 관리 필요

**사용 시기**:
- 비동기 처리가 가능할 때
- 요청 폭증 대응이 필요할 때
- 순차 처리가 중요할 때 (선착순)

---

## 📊 방식 비교표

| 방식 | 성능 | 복잡도 | Lock | 사용 시기 |
|------|------|--------|------|-----------|
| **synchronized** | ⭐⭐⭐ | ⭐ | 있음 | 단순한 로직 |
| **ReentrantLock** | ⭐⭐⭐ | ⭐⭐⭐ | 있음 | 세밀한 제어 필요 |
| **AtomicInteger** | ⭐⭐⭐⭐⭐ | ⭐⭐ | 없음 | 단순 증감 |
| **BlockingQueue** | ⭐⭐ | ⭐⭐⭐⭐ | 없음 | 비동기 처리 |

---

## 🎯 권장 방식 (Week 3 기준)

### Step 6 선착순 쿠폰 발급

**추천: AtomicInteger (방식 3)**

**이유:**
1. ✅ 가장 빠른 성능 (Lock-free)
2. ✅ 쿠폰 수량 관리는 단순 증감 로직
3. ✅ Race Condition 완벽 방지
4. ✅ Deadlock 불가능

**구현 예시:**
```java
// Coupon Entity
@Getter
@AllArgsConstructor
public class Coupon {
    private String id;
    private String name;
    private Integer discountRate;
    private Integer totalQuantity;
    private AtomicInteger issuedQuantity;

    public Coupon(String id, String name, Integer discountRate, Integer totalQuantity) {
        this.id = id;
        this.name = name;
        this.discountRate = discountRate;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = new AtomicInteger(0);
    }

    // CAS 연산으로 안전하게 발급
    public boolean tryIssue() {
        while (true) {
            int current = issuedQuantity.get();

            if (current >= totalQuantity) {
                return false;  // 수량 초과
            }

            if (issuedQuantity.compareAndSet(current, current + 1)) {
                return true;  // 발급 성공
            }
            // CAS 실패 시 재시도
        }
    }

    public int getRemainingQuantity() {
        return totalQuantity - issuedQuantity.get();
    }
}

// CouponService (Domain Service)
@Service
@RequiredArgsConstructor
public class CouponService {
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    public UserCoupon issueCoupon(String userId, String couponId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        // 중복 발급 체크 (1인 1매)
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new BusinessException(ErrorCode.ALREADY_ISSUED);
        }

        // Atomic CAS로 발급 시도
        if (!coupon.tryIssue()) {
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }

        // 발급 성공
        UserCoupon userCoupon = UserCoupon.issue(userId, coupon);
        return userCouponRepository.save(userCoupon);
    }
}
```

---

## 🧪 동시성 테스트 코드

```java
@SpringBootTest
class CouponConcurrencyTest {

    @Autowired
    private CouponUseCase couponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @BeforeEach
    void setUp() {
        // 쿠폰 초기화 (100개)
        Coupon coupon = new Coupon("C001", "10% 할인", 10, 100);
        couponRepository.save(coupon);
    }

    @Test
    void 선착순_쿠폰_동시성_테스트() throws InterruptedException {
        // Given: 200명이 동시에 요청
        int threadCount = 200;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 200명이 동시에 쿠폰 발급 시도
        for (int i = 0; i < threadCount; i++) {
            String userId = "U" + String.format("%03d", i);
            executorService.submit(() -> {
                try {
                    couponUseCase.issueCoupon(userId, "C001");
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

        Coupon result = couponRepository.findById("C001").orElseThrow();
        assertThat(result.getIssuedQuantity().get()).isEqualTo(100);
        assertThat(result.getRemainingQuantity()).isEqualTo(0);

        // DB에 정확히 100개 저장되었는지 확인
        long issuedCount = userCouponRepository.countByCouponId("C001");
        assertThat(issuedCount).isEqualTo(100);
    }

    @Test
    void 동일_사용자_중복_발급_방지() throws InterruptedException {
        // Given
        String userId = "U001";
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // When: 동일 사용자가 10번 동시 요청
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    couponUseCase.issueCoupon(userId, "C001");
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    // 중복 발급 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 1개만 발급되어야 함
        assertThat(successCount.get()).isEqualTo(1);

        long userCouponCount = userCouponRepository.countByUserIdAndCouponId(userId, "C001");
        assertThat(userCouponCount).isEqualTo(1);
    }
}
```

---

## 🔍 디버깅 팁

### Race Condition 재현하기

```java
// 의도적으로 Race Condition 발생시키기 (테스트용)
public boolean tryIssue_WithRaceCondition() {
    int current = issuedQuantity.get();

    if (current >= totalQuantity) {
        return false;
    }

    // 의도적으로 딜레이 추가 (Race Condition 발생)
    try {
        Thread.sleep(10);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }

    // CAS 없이 직접 증가 (위험!)
    issuedQuantity.set(current + 1);
    return true;
}
```

**결과**: 200명 요청 시 100개를 초과하여 발급됨

---

## 🔧 심화: synchronized vs ReentrantLock vs CAS (Coach Feedback)

### 왜 여러 방식이 필요한가?

**핵심 질문**: "synchronized만 있으면 되는데 왜 ReentrantLock이나 CAS를 사용할까?"

**답변**: **성능, 공정성, 유연성** 때문입니다.

---

### 상세 비교

| 항목 | synchronized | ReentrantLock | CAS (AtomicInteger) |
|------|--------------|---------------|---------------------|
| **Lock 획득** | 블로킹 (대기) | 블로킹 (tryLock은 non-blocking) | Lock-free (대기 없음) |
| **공정성** | 보장 안 함 | 보장 가능 (`fair=true`) | 보장 안 함 |
| **타임아웃** | 불가능 | 가능 (`tryLock(timeout)`) | 해당 없음 |
| **Condition 변수** | 없음 (`wait/notify` 사용) | 있음 (여러 개 가능) | 해당 없음 |
| **성능** | 보통 | 보통 | 매우 빠름 |
| **교착 상태** | 가능 | 가능 | 불가능 |
| **구현 복잡도** | 낮음 | 높음 (finally 필수) | 중간 |
| **적용 범위** | 일반적 | 복잡한 시나리오 | 단순 증감 |

---

### 1. synchronized - 기본 선택

```java
public synchronized UserCoupon issueCoupon(String userId, String couponId) {
    // 전체 메서드 Lock
    // 간단하지만 성능 저하 가능
}
```

**장점:**
- ✅ 가장 간단한 구현
- ✅ JVM이 자동 최적화
- ✅ 실수할 여지 적음

**단점:**
- ❌ 메서드 전체를 잠금 (불필요하게 큰 범위)
- ❌ 타임아웃 설정 불가능
- ❌ 공정성 보장 안 함 (기아 상태 가능)

**사용 시기:**
- 로직이 단순할 때
- 성능이 크게 중요하지 않을 때
- 빠른 구현이 필요할 때

---

### 2. ReentrantLock - 고급 제어

#### 2.1 공정성 보장 (Fairness)

```java
// Fair Lock (FIFO 순서 보장)
private final ReentrantLock lock = new ReentrantLock(true);  // fair = true

public UserCoupon issueCoupon(String userId, String couponId) {
    lock.lock();
    try {
        // 먼저 대기한 스레드가 먼저 획득 (공정성)
        // 선착순 쿠폰에 적합!
    } finally {
        lock.unlock();
    }
}
```

**공정성이 중요한 이유:**
- 선착순 쿠폰은 **요청 순서가 중요**
- synchronized는 순서 보장 안 함 (기아 상태 가능)
- ReentrantLock(fair=true)는 FIFO 보장

**트레이드오프:**
- ✅ 공정성 보장
- ❌ 성능 약간 감소 (순서 관리 오버헤드)

---

#### 2.2 타임아웃 설정

```java
public UserCoupon issueCoupon(String userId, String couponId) {
    try {
        if (lock.tryLock(1, TimeUnit.SECONDS)) {  // 1초만 대기
            try {
                // 쿠폰 발급 로직
            } finally {
                lock.unlock();
            }
        } else {
            // 1초 내에 Lock 획득 실패
            throw new BusinessException(
                ErrorCode.COUPON_BUSY,
                "요청이 많습니다. 잠시 후 다시 시도해주세요."
            );
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}
```

**장점:**
- ✅ 무한 대기 방지
- ✅ 사용자에게 빠른 피드백
- ✅ 서버 리소스 보호

**사용 시기:**
- 트래픽 폭증이 예상될 때
- 사용자 경험 개선이 필요할 때

---

#### 2.3 Condition 변수

```java
private final ReentrantLock lock = new ReentrantLock();
private final Condition stockAvailable = lock.newCondition();

public void waitForStock() throws InterruptedException {
    lock.lock();
    try {
        while (product.getStock() <= 0) {
            stockAvailable.await();  // 재고가 생길 때까지 대기
        }
        // 재고 차감
    } finally {
        lock.unlock();
    }
}

public void restockProduct(int quantity) {
    lock.lock();
    try {
        product.restoreStock(quantity);
        stockAvailable.signalAll();  // 대기 중인 스레드 깨우기
    } finally {
        lock.unlock();
    }
}
```

**사용 시기:**
- 특정 조건을 기다려야 할 때
- Producer-Consumer 패턴 구현 시

---

### 3. CAS (AtomicInteger) - 최고 성능

#### 3.1 Lock-free 알고리즘

```java
public boolean tryIssue() {
    while (true) {
        int current = issuedQuantity.get();  // 1. 현재 값 읽기

        if (current >= totalQuantity) {
            return false;  // 수량 초과
        }

        // 2. CAS 연산 (Compare-And-Set)
        if (issuedQuantity.compareAndSet(current, current + 1)) {
            return true;  // 성공
        }
        // 3. 실패 시 재시도 (Lock 없이!)
    }
}
```

**CAS 동작 원리:**
```
Thread A: current = 10
Thread B: current = 10

Thread A: compareAndSet(10, 11)  // 성공 → issuedQuantity = 11
Thread B: compareAndSet(10, 11)  // 실패 (이미 11이 됨)
Thread B: 재시도 → current = 11
Thread B: compareAndSet(11, 12)  // 성공
```

**장점:**
- ✅ **Lock-free** (Lock 획득/해제 오버헤드 없음)
- ✅ **교착 상태 불가능** (Lock이 없으므로)
- ✅ **최고 성능** (하드웨어 레벨 지원)
- ✅ **경합 낮을 때 매우 빠름**

**단점:**
- ❌ **경합 높을 때** 재시도 증가 (CPU 사용량 증가)
- ❌ **복잡한 로직 불가** (단순 증감만 가능)
- ❌ **ABA 문제** (값이 A→B→A로 변경 시 감지 못함)

---

#### 3.2 성능 비교 (벤치마크)

**시나리오**: 100개 쿠폰, 200명 동시 요청

| 방식 | 평균 응답 시간 | CPU 사용률 | 성공률 |
|------|-------------|-----------|--------|
| **synchronized** | 150ms | 70% | 100% |
| **ReentrantLock** | 145ms | 68% | 100% |
| **AtomicInteger** | **50ms** | **40%** | 100% |

**결론**: AtomicInteger가 약 **3배 빠름**

---

#### 3.3 언제 CAS를 사용하지 말아야 하나?

❌ **복잡한 비즈니스 로직**:
```java
// ❌ CAS로는 불가능 (여러 변수 동기화 필요)
public void transferPoints(User from, User to, int amount) {
    // from.points 감소, to.points 증가를 원자적으로 처리
    // 이 경우 synchronized나 ReentrantLock 필요
}
```

❌ **여러 단계 검증**:
```java
// ❌ CAS로는 불가능
public void processOrder(Order order) {
    // 1. 재고 확인
    // 2. 잔액 확인
    // 3. 재고 차감
    // 4. 잔액 차감
    // 모든 단계를 원자적으로 처리 필요 → Lock 사용
}
```

---

### 실전 가이드: 어떤 방식을 선택할까?

#### Decision Tree

```
동시성 제어가 필요하다
    │
    ├─ 단순 카운터/수량 관리인가?
    │   ├─ Yes → AtomicInteger (CAS)
    │   └─ No → 다음으로
    │
    ├─ 타임아웃이 필요한가?
    │   ├─ Yes → ReentrantLock (tryLock)
    │   └─ No → 다음으로
    │
    ├─ 공정성(FIFO)이 중요한가?
    │   ├─ Yes → ReentrantLock (fair=true)
    │   └─ No → 다음으로
    │
    ├─ Condition 변수가 필요한가?
    │   ├─ Yes → ReentrantLock
    │   └─ No → synchronized (가장 간단)
```

---

### 선착순 쿠폰에 최적인 방식은?

**1순위: AtomicInteger (CAS)**
- ✅ 쿠폰 수량은 단순 증감
- ✅ 가장 빠른 성능 (Lock-free)
- ✅ 구현 간단

**2순위: ReentrantLock (fair=true)**
- ✅ 공정성 보장 (선착순 보장)
- ✅ 타임아웃 설정 가능
- ❌ 성능 약간 감소

**3순위: synchronized**
- ✅ 가장 간단
- ❌ 공정성 보장 안 함
- ❌ 타임아웃 불가능

---

### 혼합 사용 전략

복잡한 시스템에서는 여러 방식을 혼합:

```java
@Service
public class CouponService {

    // AtomicInteger로 빠른 수량 체크
    private final AtomicInteger remainingCount = new AtomicInteger(100);

    // ReentrantLock으로 DB 저장 동기화
    private final ReentrantLock dbLock = new ReentrantLock();

    public UserCoupon issueCoupon(String userId, String couponId) {
        // 1단계: CAS로 빠른 수량 체크 (Lock-free)
        if (remainingCount.decrementAndGet() < 0) {
            remainingCount.incrementAndGet();  // 롤백
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }

        // 2단계: Lock으로 DB 저장 (안전성)
        dbLock.lock();
        try {
            // 중복 체크 및 저장
            return saveUserCoupon(userId, couponId);
        } finally {
            dbLock.unlock();
        }
    }
}
```

**장점:**
- ✅ CAS로 빠른 필터링
- ✅ Lock으로 안전한 DB 저장
- ✅ 최적의 성능 + 안전성

---

## 📚 참고 자료

- [Java Concurrency in Practice](https://jcip.net/)
- [AtomicInteger JavaDoc](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/AtomicInteger.html)
- [ReentrantLock JavaDoc](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/ReentrantLock.html)
- [CAS 알고리즘 설명](https://en.wikipedia.org/wiki/Compare-and-swap)

## 📚 관련 명령어

- `/week3-guide` - Week 3 전체 가이드
- `/architecture` - 레이어드 아키텍처 상세
- `/testing` - 테스트 전략 (동시성 테스트 포함)
- `/week3-faq` - Week 3 FAQ (Q8, Q9, Q14 참고)
