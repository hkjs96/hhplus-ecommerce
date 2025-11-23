# STEP 5-6 코치 피드백 개선 가이드
## 제이코치님 피드백 반영 사항

> **피드백 날짜**: 2025-11-23
> **대상**: Week 3-4 (Step 5-6) 동시성 제어 구현

---

## 📋 목차

1. [피드백 요약](#피드백-요약)
2. [개선 사항 1: 누락된 테스트 추가](#개선-사항-1-누락된-테스트-추가)
3. [개선 사항 2: 성능 측정 및 문서화](#개선-사항-2-성능-측정-및-문서화)
4. [개선 사항 3: 락 타임아웃 설정](#개선-사항-3-락-타임아웃-설정)
5. [개선 사항 4: 낙관적 락 재시도 로직](#개선-사항-4-낙관적-락-재시도-로직)
6. [개선 사항 5: 대규모 부하 테스트](#개선-사항-5-대규모-부하-테스트)
7. [체크리스트](#체크리스트)

---

## 🎯 피드백 요약

### ✅ 잘한 점

1. **5가지 동시성 문제 식별**
   - 재고 차감 (Over-selling)
   - 선착순 쿠폰 (초과 발급)
   - 결제 중복 (이중 차감)
   - 잔액 손실 (Lost Update)
   - 상태 전이 (건너뛰기)

2. **하이브리드 락 전략**
   - Hot Spot(재고/쿠폰): 비관적 락
   - 충돌 드문 곳(잔액/상태): 낙관적 락

3. **Idempotency Key 패턴 도입**
   - 결제 중복 방지 (UNIQUE 제약조건)

4. **원자적 SQL 업데이트**
   - `UPDATE users SET balance = balance + 50000`

5. **3개 동시성 테스트 작성**
   - IssueCouponConcurrencyTest
   - OrderConcurrencyTest
   - CartItemConcurrencyTest

### 🔧 개선 필요 사항

1. **테스트 부족**: 5가지 문제 중 2가지 미검증
   - ❌ 결제 중복 (Idempotency Key) 테스트 없음
   - ❌ 잔액 손실 (Optimistic Lock) 테스트 없음

2. **성능 측정 부족**: "TPS 30% 하락" 등 실측치 미기재
   - Before/After 비교 데이터 부족

3. **락 타임아웃 미설정**: 무한 대기 가능성
   - `javax.persistence.lock.timeout` 미설정

4. **재시도 로직 불명확**: 낙관적 락 실패 시 처리
   - 재시도 횟수, 백오프 전략, 최종 실패 처리 불명확

5. **부하 테스트 미실행**: 20명 → 100명, 1000명 규모 필요
   - Lock Contention 실제 영향도 미측정

---

## 🧪 개선 사항 1: 누락된 테스트 추가

### 1.1 결제 중복(Idempotency Key) 테스트

#### 테스트 시나리오
```
동일한 멱등성 키로 10번 동시 결제 요청
→ 1번만 처리되어야 함
→ UNIQUE 제약조건으로 중복 차단
```

#### 구현 코드

```java
package io.hhplus.ecommerce.application.payment;

import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.payment.Payment;
import io.hhplus.ecommerce.domain.payment.PaymentRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestContainersConfig.class)
class PaymentIdempotencyTest {

    @Autowired
    private PaymentUseCase paymentUseCase;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    private User testUser;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성 (잔액 충분)
        testUser = User.builder()
                .name("테스트유저")
                .email("test@example.com")
                .balance(BigDecimal.valueOf(1_000_000))
                .build();
        userRepository.save(testUser);

        // 테스트 주문 생성
        testOrder = Order.builder()
                .userId(testUser.getId())
                .totalAmount(BigDecimal.valueOf(50_000))
                .build();
        orderRepository.save(testOrder);
    }

    @Test
    @DisplayName("동일한 멱등성 키로 10번 동시 결제 시 1번만 처리")
    void 멱등성키_동시성_테스트() throws InterruptedException {
        // Given: 동일한 멱등성 키
        String idempotencyKey = UUID.randomUUID().toString();
        int threadCount = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When: 10번 동시 결제 시도 (동일한 멱등성 키)
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    PaymentRequest request = PaymentRequest.builder()
                            .orderId(testOrder.getId())
                            .amount(BigDecimal.valueOf(50_000))
                            .idempotencyKey(idempotencyKey)
                            .build();

                    paymentUseCase.processPayment(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    // UNIQUE 제약조건 위반 또는 이미 처리됨 예외
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 정확히 1번만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);

        // DB에도 1건만 저장되었는지 확인
        long paymentCount = paymentRepository.countByIdempotencyKey(idempotencyKey);
        assertThat(paymentCount).isEqualTo(1);

        // 사용자 잔액 확인 (1번만 차감)
        User user = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(user.getBalance()).isEqualByComparingTo(
                BigDecimal.valueOf(950_000)  // 1,000,000 - 50,000
        );
    }

    @Test
    @DisplayName("서로 다른 멱등성 키로 10번 동시 결제 시 10번 모두 처리")
    void 서로_다른_멱등성키_동시성_테스트() throws InterruptedException {
        // Given
        int threadCount = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();

        // When: 각각 다른 멱등성 키로 10번 결제
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    // 매번 새로운 멱등성 키 생성
                    String uniqueKey = UUID.randomUUID().toString();

                    PaymentRequest request = PaymentRequest.builder()
                            .orderId(testOrder.getId())
                            .amount(BigDecimal.valueOf(50_000))
                            .idempotencyKey(uniqueKey)
                            .build();

                    paymentUseCase.processPayment(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    // 잔액 부족으로 일부 실패 가능
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 잔액이 충분한 만큼 성공 (1,000,000 / 50,000 = 20회 가능)
        assertThat(successCount.get()).isGreaterThanOrEqualTo(10);

        // DB에 실제로 저장된 결제 건수 확인
        long totalPayments = paymentRepository.count();
        assertThat(totalPayments).isEqualTo(successCount.get());
    }

    @Test
    @DisplayName("네트워크 재시도 시나리오 - 동일 멱등성 키로 3번 재시도")
    void 네트워크_재시도_시나리오() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();

        PaymentRequest request = PaymentRequest.builder()
                .orderId(testOrder.getId())
                .amount(BigDecimal.valueOf(50_000))
                .idempotencyKey(idempotencyKey)
                .build();

        // When: 첫 번째 요청 성공
        PaymentResponse firstResponse = paymentUseCase.processPayment(request);
        assertThat(firstResponse.isSuccess()).isTrue();

        // When: 네트워크 타임아웃으로 재시도 (동일 키)
        PaymentResponse secondResponse = paymentUseCase.processPayment(request);

        // Then: 기존 결제 정보 반환 (중복 처리 안 함)
        assertThat(secondResponse.getPaymentId()).isEqualTo(firstResponse.getPaymentId());

        // 잔액은 1번만 차감
        User user = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(user.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(950_000));

        // DB에도 1건만 존재
        long paymentCount = paymentRepository.countByIdempotencyKey(idempotencyKey);
        assertThat(paymentCount).isEqualTo(1);
    }
}
```

#### Payment Entity 수정 (UNIQUE 제약조건)

```java
package io.hhplus.ecommerce.domain.payment;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_idempotency_key", columnList = "idempotency_key")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_payment_idempotency_key",
            columnNames = {"idempotency_key"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * 멱등성 키 (Idempotency Key)
     *
     * UNIQUE 제약조건으로 중복 결제 방지
     * - 클라이언트가 생성한 UUID
     * - 네트워크 재시도 시 동일 키 사용
     * - DB 레벨에서 중복 차단
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static Payment create(Long orderId, BigDecimal amount, String idempotencyKey) {
        return Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .idempotencyKey(idempotencyKey)
                .status(PaymentStatus.COMPLETED)
                .build();
    }
}
```

#### PaymentRepository 수정

```java
package io.hhplus.ecommerce.domain.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 멱등성 키로 결제 조회
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * 멱등성 키로 결제 개수 조회 (테스트용)
     */
    long countByIdempotencyKey(String idempotencyKey);
}
```

#### PaymentUseCase 수정

```java
package io.hhplus.ecommerce.application.payment;

import io.hhplus.ecommerce.domain.payment.Payment;
import io.hhplus.ecommerce.domain.payment.PaymentRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    /**
     * 결제 처리 (Idempotency Key 기반)
     *
     * 동작 순서:
     * 1. 멱등성 키로 기존 결제 조회
     * 2. 이미 처리된 경우 기존 결과 반환
     * 3. 신규 결제인 경우 처리 진행
     * 4. UNIQUE 제약조건으로 DB 레벨 중복 방지
     */
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("결제 처리 시작: orderId={}, idempotencyKey={}",
                request.getOrderId(), request.getIdempotencyKey());

        // 1. 멱등성 키로 기존 결제 조회
        Optional<Payment> existingPayment = paymentRepository
                .findByIdempotencyKey(request.getIdempotencyKey());

        if (existingPayment.isPresent()) {
            // 2. 이미 처리된 경우 기존 결과 반환
            log.info("이미 처리된 결제: paymentId={}", existingPayment.get().getId());
            return PaymentResponse.from(existingPayment.get());
        }

        // 3. 사용자 조회
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        // 4. 잔액 차감
        user.deductBalance(request.getAmount());

        // 5. 결제 생성 (UNIQUE 제약조건으로 중복 방지)
        Payment payment = Payment.create(
                request.getOrderId(),
                request.getAmount(),
                request.getIdempotencyKey()
        );

        try {
            paymentRepository.save(payment);
            log.info("결제 완료: paymentId={}", payment.getId());

        } catch (DataIntegrityViolationException e) {
            // UNIQUE 제약조건 위반 (동시 요청 중 다른 스레드가 먼저 저장)
            log.warn("멱등성 키 중복: {}", request.getIdempotencyKey());

            // 다시 조회하여 기존 결과 반환
            Payment savedPayment = paymentRepository
                    .findByIdempotencyKey(request.getIdempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("결제 조회 실패"));

            return PaymentResponse.from(savedPayment);
        }

        return PaymentResponse.from(payment);
    }
}
```

---

### 1.2 잔액 손실(Optimistic Lock) 테스트

#### 테스트 시나리오
```
사용자 A의 잔액 100,000원
동시에 10명이 각각 10,000원씩 차감 시도
→ 낙관적 락으로 Lost Update 방지
→ 최종 잔액 0원
```

#### 구현 코드

```java
package io.hhplus.ecommerce.application.user;

import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestContainersConfig.class)
class UserBalanceOptimisticLockTest {

    @Autowired
    private UserUseCase userUseCase;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성 (잔액 100,000원)
        testUser = User.builder()
                .name("테스트유저")
                .email("test@example.com")
                .balance(BigDecimal.valueOf(100_000))
                .build();
        userRepository.save(testUser);
    }

    @Test
    @DisplayName("10명이 동시에 10,000원씩 차감 시 최종 잔액 0원")
    void 낙관적락_잔액차감_동시성_테스트() throws InterruptedException {
        // Given
        int threadCount = 10;
        BigDecimal deductAmount = BigDecimal.valueOf(10_000);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger retryCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When: 10명이 동시에 10,000원씩 차감
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    userUseCase.deductBalanceWithRetry(
                            testUser.getId(),
                            deductAmount
                    );
                    successCount.incrementAndGet();

                } catch (ObjectOptimisticLockingFailureException e) {
                    // 재시도 후에도 실패 (최대 재시도 횟수 초과)
                    retryCount.incrementAndGet();
                    failCount.incrementAndGet();

                } catch (Exception e) {
                    // 기타 예외 (잔액 부족 등)
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 모두 성공 (재시도 포함)
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(0);

        // 재시도가 발생했는지 확인 (낙관적 락 충돌)
        assertThat(retryCount.get()).isGreaterThan(0);

        // 최종 잔액 0원
        User user = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(user.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("잔액 50,000원일 때 10명이 10,000원씩 차감 시 5명만 성공")
    void 낙관적락_잔액부족_동시성_테스트() throws InterruptedException {
        // Given: 잔액 50,000원으로 설정
        testUser.setBalance(BigDecimal.valueOf(50_000));
        userRepository.save(testUser);

        int threadCount = 10;
        BigDecimal deductAmount = BigDecimal.valueOf(10_000);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    userUseCase.deductBalanceWithRetry(
                            testUser.getId(),
                            deductAmount
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

        // Then: 정확히 5명만 성공
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        // 최종 잔액 0원
        User user = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(user.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("충전과 차감 동시 발생 시 Lost Update 방지")
    void 낙관적락_충전과차감_동시_테스트() throws InterruptedException {
        // Given
        int threadCount = 20;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger chargeCount = new AtomicInteger();
        AtomicInteger deductCount = new AtomicInteger();

        // When: 충전 10번, 차감 10번 동시 실행
        for (int i = 0; i < 10; i++) {
            // 충전 (10,000원씩)
            executorService.submit(() -> {
                try {
                    userUseCase.chargeBalanceWithRetry(
                            testUser.getId(),
                            BigDecimal.valueOf(10_000)
                    );
                    chargeCount.incrementAndGet();
                } catch (Exception e) {
                    // 재시도 실패
                } finally {
                    latch.countDown();
                }
            });

            // 차감 (10,000원씩)
            executorService.submit(() -> {
                try {
                    userUseCase.deductBalanceWithRetry(
                            testUser.getId(),
                            BigDecimal.valueOf(10_000)
                    );
                    deductCount.incrementAndGet();
                } catch (Exception e) {
                    // 잔액 부족으로 실패 가능
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 최종 잔액 = 초기 잔액 + (충전 횟수 - 차감 횟수) * 10,000
        int expectedBalance = 100_000 + (chargeCount.get() - deductCount.get()) * 10_000;

        User user = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(user.getBalance()).isEqualByComparingTo(
                BigDecimal.valueOf(expectedBalance)
        );

        // Lost Update가 발생하지 않았음을 확인
        // (낙관적 락이 없으면 일부 업데이트가 소실될 수 있음)
    }
}
```

#### User Entity 수정 (@Version 추가)

```java
package io.hhplus.ecommerce.domain.user;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    /**
     * 낙관적 락을 위한 버전 필드
     *
     * - 업데이트할 때마다 자동으로 증가
     * - 충돌 감지 시 ObjectOptimisticLockingFailureException 발생
     * - 재시도 로직으로 처리
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 잔액 차감
     *
     * @throws IllegalArgumentException 잔액 부족
     */
    public void deductBalance(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    String.format("잔액 부족: 현재 %s, 차감 시도 %s", this.balance, amount)
            );
        }

        this.balance = this.balance.subtract(amount);
    }

    /**
     * 잔액 충전
     */
    public void chargeBalance(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다");
        }

        this.balance = this.balance.add(amount);
    }

    // Setter for test
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
```

#### UserUseCase 수정 (재시도 로직 추가)

```java
package io.hhplus.ecommerce.application.user;

import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserUseCase {

    private final UserRepository userRepository;

    private static final int MAX_RETRY_COUNT = 10;  // 최대 재시도 횟수
    private static final long RETRY_DELAY_MS = 50;   // 재시도 간격 (50ms)

    /**
     * 잔액 차감 (재시도 로직 포함)
     *
     * 낙관적 락 충돌 시:
     * 1. 최대 10번까지 재시도
     * 2. 50ms씩 대기 후 재시도 (Exponential Backoff 가능)
     * 3. 최대 재시도 초과 시 예외 발생
     */
    public void deductBalanceWithRetry(Long userId, BigDecimal amount) {
        int retryCount = 0;

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                deductBalance(userId, amount);
                return;  // 성공 시 즉시 반환

            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;

                log.warn("낙관적 락 충돌 발생: userId={}, retryCount={}/{}",
                        userId, retryCount, MAX_RETRY_COUNT);

                if (retryCount >= MAX_RETRY_COUNT) {
                    log.error("최대 재시도 횟수 초과: userId={}", userId);
                    throw new IllegalStateException(
                            "잔액 차감 실패: 최대 재시도 횟수 초과", e
                    );
                }

                // 재시도 전 대기 (Exponential Backoff)
                try {
                    Thread.sleep(RETRY_DELAY_MS * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("재시도 대기 중 인터럽트 발생", ie);
                }
            }
        }
    }

    @Transactional
    public void deductBalance(Long userId, BigDecimal amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        user.deductBalance(amount);
        userRepository.save(user);
    }

    /**
     * 잔액 충전 (재시도 로직 포함)
     */
    public void chargeBalanceWithRetry(Long userId, BigDecimal amount) {
        int retryCount = 0;

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                chargeBalance(userId, amount);
                return;

            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;

                if (retryCount >= MAX_RETRY_COUNT) {
                    throw new IllegalStateException(
                            "잔액 충전 실패: 최대 재시도 횟수 초과", e
                    );
                }

                try {
                    Thread.sleep(RETRY_DELAY_MS * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("재시도 대기 중 인터럽트 발생", ie);
                }
            }
        }
    }

    @Transactional
    public void chargeBalance(Long userId, BigDecimal amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        user.chargeBalance(amount);
        userRepository.save(user);
    }
}
```

---

## 📊 개선 사항 2: 성능 측정 및 문서화

### 2.1 JMeter 성능 측정 가이드

#### JMeter 설치 및 설정

```bash
# JMeter 다운로드 (macOS/Linux)
brew install jmeter

# 또는 직접 다운로드
# https://jmeter.apache.org/download_jmeter.cgi
```

#### 테스트 계획 작성

```xml
<!-- pessimistic-lock-test.jmx -->
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Pessimistic Lock Test">
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
    </TestPlan>

    <hashTree>
      <!-- Thread Group: 100명 동시 사용자 -->
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Users">
        <intProp name="ThreadGroup.num_threads">100</intProp>
        <intProp name="ThreadGroup.ramp_time">10</intProp>
        <longProp name="ThreadGroup.duration">60</longProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
      </ThreadGroup>

      <hashTree>
        <!-- HTTP Request: 주문 생성 -->
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Create Order">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8080</stringProp>
          <stringProp name="HTTPSampler.path">/api/orders</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>

          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="" elementType="HTTPArgument">
                <boolProp name="HTTPArgument.always_encode">false</boolProp>
                <stringProp name="Argument.value">{
  "productId": 1,
  "quantity": 1
}</stringProp>
                <stringProp name="Argument.metadata">=</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>

          <stringProp name="HTTPSampler.contentEncoding">UTF-8</stringProp>
          <stringProp name="HTTPSampler.implementation">HttpClient4</stringProp>

          <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="HTTP Header Manager">
            <collectionProp name="HeaderManager.headers">
              <elementProp name="" elementType="Header">
                <stringProp name="Header.name">Content-Type</stringProp>
                <stringProp name="Header.value">application/json</stringProp>
              </elementProp>
            </collectionProp>
          </HeaderManager>
        </HTTPSamplerProxy>

        <!-- Summary Report -->
        <ResultCollector guiclass="SummaryReport" testclass="ResultCollector" testname="Summary Report"/>

        <!-- View Results Tree -->
        <ResultCollector guiclass="ViewResultsFullVisualizer" testclass="ResultCollector" testname="View Results Tree"/>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

#### 실행 명령어

```bash
# GUI 모드로 실행 (테스트 계획 작성용)
jmeter

# CLI 모드로 실행 (성능 측정용)
jmeter -n -t pessimistic-lock-test.jmx -l results.jtl -e -o report/

# 결과 확인
open report/index.html
```

### 2.2 성능 측정 보고서 템플릿

```markdown
# 동시성 제어 성능 측정 보고서

## 1. 측정 환경

### 하드웨어
- CPU: Intel Core i7-12700K (12 Cores)
- RAM: 32GB DDR4
- SSD: 1TB NVMe

### 소프트웨어
- OS: macOS Sonoma 14.2
- Java: OpenJDK 17
- Spring Boot: 3.5.7
- MySQL: 8.0
- HikariCP Max Pool Size: 50

### 테스트 설정
- Tool: JMeter 5.6
- 동시 사용자: 100명
- Ramp-up: 10초
- Duration: 60초
- 총 요청 수: 약 6,000회

## 2. 시나리오별 성능 측정

### 시나리오 1: 재고 차감 (비관적 락)

#### Before (락 없음)
| 지표 | 값 |
|-----|---|
| 평균 응답 시간 | 85ms |
| 최대 응답 시간 | 320ms |
| TPS | 1,176 req/s |
| 에러율 | 8.5% (재고 음수 발생) |
| DB CPU 사용률 | 45% |

#### After (비관적 락)
| 지표 | 값 | 변화율 |
|-----|---|--------|
| 평균 응답 시간 | 142ms | **+67% (증가)** |
| 최대 응답 시간 | 890ms | **+178% (증가)** |
| TPS | 704 req/s | **-40% (감소)** |
| 에러율 | 0% | **-100% (개선)** |
| DB CPU 사용률 | 78% | +73% (증가) |

**분석**:
- ✅ 데이터 정합성 100% 보장 (재고 음수 발생 0건)
- ❌ TPS 40% 감소 (Lock Contention으로 인한 대기 증가)
- ❌ 응답 시간 67% 증가
- ⚠️ DB CPU 사용률 증가 (Lock 대기 시간)

### 시나리오 2: 잔액 차감 (낙관적 락)

#### Before (락 없음)
| 지표 | 값 |
|-----|---|
| 평균 응답 시간 | 62ms |
| 최대 응답 시간 | 185ms |
| TPS | 1,612 req/s |
| 에러율 | 12.3% (Lost Update 발생) |
| DB CPU 사용률 | 38% |

#### After (낙관적 락 + 재시도)
| 지표 | 값 | 변화율 |
|-----|---|--------|
| 평균 응답 시간 | 58ms | **-6% (개선)** |
| 최대 응답 시간 | 420ms | **+127% (증가)** |
| TPS | 1,724 req/s | **+7% (개선)** |
| 에러율 | 0% | **-100% (개선)** |
| 평균 재시도 횟수 | 1.8회 | - |
| DB CPU 사용률 | 42% | +11% (증가) |

**분석**:
- ✅ 데이터 정합성 100% 보장
- ✅ TPS 7% 향상 (비관적 락 대비 2.4배)
- ✅ 평균 응답 시간 개선
- ⚠️ 최대 응답 시간 증가 (재시도로 인한 일부 지연)
- 📊 평균 재시도 1.8회 (충돌 빈도 낮음)

### 시나리오 3: 선착순 쿠폰 (비관적 락)

#### Before (락 없음)
| 지표 | 값 |
|-----|---|
| 평균 응답 시간 | 78ms |
| 최대 응답 시간 | 245ms |
| TPS | 1,282 req/s |
| 에러율 | 15.2% (초과 발급) |

#### After (비관적 락)
| 지표 | 값 | 변화율 |
|-----|---|--------|
| 평균 응답 시간 | 125ms | **+60% (증가)** |
| 최대 응답 시간 | 780ms | **+218% (증가)** |
| TPS | 800 req/s | **-38% (감소)** |
| 에러율 | 0% | **-100% (개선)** |

**분석**:
- ✅ 초과 발급 0건 (정확히 100개만 발급)
- ❌ Hot Spot으로 인한 심한 Lock Contention
- ❌ TPS 38% 감소
- 💡 개선 방안: Redis Distributed Lock 도입 필요

## 3. 종합 분석

### 락 전략별 특성

| 전략 | 장점 | 단점 | 권장 시나리오 |
|-----|------|------|--------------|
| **비관적 락** | • 데이터 정합성 보장<br>• 충돌 시 즉시 실패 | • TPS 30-40% 감소<br>• Lock Contention<br>• DB 부하 증가 | • 재고 차감<br>• 선착순 쿠폰<br>• 충돌 빈번한 Hot Spot |
| **낙관적 락** | • TPS 향상 (7%)<br>• DB 부하 낮음<br>• 동시성 높음 | • 재시도 오버헤드<br>• 최대 응답 시간 증가 | • 잔액 관리<br>• 상태 전이<br>• 충돌 드문 경우 |

### 권장 사항

1. **Hot Spot (재고/쿠폰)**: 비관적 락 또는 Redis 분산락
   - 충돌이 빈번하므로 낙관적 락의 재시도 오버헤드가 큼
   - 데이터 정합성이 최우선

2. **일반적인 경우 (잔액/상태)**: 낙관적 락 + 재시도
   - 충돌이 드물어 재시도 오버헤드 낮음
   - 성능 우위 (TPS 향상)

3. **대규모 트래픽**: Redis 분산락 + 캐시
   - DB 부하 최소화
   - 멀티 인스턴스 환경 대응

## 4. 다음 단계

- [ ] Redis Distributed Lock 도입 (선착순 쿠폰)
- [ ] HikariCP Pool Size 튜닝 (Lock 대기 시간 감소)
- [ ] 쿼리 최적화 (N+1 문제 해결)
- [ ] 캐시 도입 (조회 성능 향상)
- [ ] 1000명 규모 부하 테스트 실행
```

---

## ⏱️ 개선 사항 3: 락 타임아웃 설정

### 3.1 비관적 락 타임아웃 설정

#### ProductRepository 수정

```java
package io.hhplus.ecommerce.domain.product;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 비관적 락으로 상품 조회 (타임아웃 설정)
     *
     * 타임아웃 설정:
     * - javax.persistence.lock.timeout: 3000ms (3초)
     * - 3초 내에 락을 획득하지 못하면 PessimisticLockException 발생
     * - 무한 대기 방지
     *
     * @param id 상품 ID
     * @return 상품 (락 획득된 상태)
     * @throws jakarta.persistence.PessimisticLockException 락 획득 실패
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "javax.persistence.lock.timeout", value = "3000")
    })
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    /**
     * 비관적 락으로 상품 조회 (NOWAIT)
     *
     * NOWAIT 옵션:
     * - timeout = 0: 락 획득 즉시 실패
     * - 대기 없이 바로 예외 발생
     * - 빠른 실패가 필요한 경우 사용
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "javax.persistence.lock.timeout", value = "0")  // NOWAIT
    })
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLockNoWait(@Param("id") Long id);

    /**
     * 비관적 락으로 상품 조회 (SKIP LOCKED)
     *
     * SKIP LOCKED 옵션:
     * - timeout = -2: 락이 걸린 행은 건너뛰기
     * - MySQL 8.0+, PostgreSQL 9.5+ 지원
     * - 순서가 중요하지 않은 큐 처리에 유용
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "javax.persistence.lock.timeout", value = "-2")  // SKIP LOCKED
    })
    @Query("SELECT p FROM Product p WHERE p.category = :category")
    List<Product> findByCategoryWithLockSkipLocked(@Param("category") String category);
}
```

#### OrderUseCase 수정 (타임아웃 예외 처리)

```java
package io.hhplus.ecommerce.application.order;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import jakarta.persistence.PessimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderUseCase {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    /**
     * 주문 생성 (비관적 락 + 타임아웃 처리)
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        try {
            // 1. 비관적 락으로 상품 조회 (3초 타임아웃)
            Product product = productRepository.findByIdWithLock(request.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품 없음"));

            // 2. 재고 차감
            product.decreaseStock(request.getQuantity());

            // 3. 주문 생성
            Order order = Order.create(request.getUserId(), product, request.getQuantity());
            orderRepository.save(order);

            log.info("주문 생성 완료: orderId={}", order.getId());
            return OrderResponse.from(order);

        } catch (PessimisticLockException e) {
            // 락 획득 타임아웃 (3초 초과)
            log.error("락 획득 타임아웃: productId={}", request.getProductId(), e);
            throw new IllegalStateException(
                    "현재 주문이 집중되어 처리할 수 없습니다. 잠시 후 다시 시도해주세요.", e
            );
        }
    }

    /**
     * 주문 생성 (NOWAIT 전략)
     *
     * 락 획득 실패 시 즉시 실패
     * - 대기 시간 없이 빠른 피드백
     * - 사용자에게 즉시 "품절" 안내 가능
     */
    @Transactional
    public OrderResponse createOrderNoWait(CreateOrderRequest request) {
        try {
            Product product = productRepository.findByIdWithLockNoWait(request.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품 없음"));

            product.decreaseStock(request.getQuantity());

            Order order = Order.create(request.getUserId(), product, request.getQuantity());
            orderRepository.save(order);

            return OrderResponse.from(order);

        } catch (PessimisticLockException e) {
            log.warn("락 획득 즉시 실패 (NOWAIT): productId={}", request.getProductId());
            throw new IllegalStateException(
                    "해당 상품은 현재 다른 사용자가 구매 중입니다. 잠시 후 다시 시도해주세요.", e
            );
        }
    }
}
```

#### 타임아웃 설정 비교

| 옵션 | timeout 값 | 동작 | 사용 시나리오 |
|-----|-----------|------|--------------|
| **기본** | (미설정) | 무한 대기 | ❌ 권장하지 않음 (데드락 위험) |
| **타임아웃** | 3000 (3초) | 3초 후 예외 발생 | ✅ 일반적인 경우 (권장) |
| **NOWAIT** | 0 | 즉시 예외 발생 | ✅ 빠른 실패가 필요한 경우 |
| **SKIP LOCKED** | -2 | 락 걸린 행 건너뛰기 | ✅ 큐 처리, 순서 무관한 작업 |

---

## 🔄 개선 사항 4: 낙관적 락 재시도 로직

### 4.1 재시도 전략 상세 구현

위의 "1.2 잔액 손실(Optimistic Lock) 테스트"에서 이미 구현되어 있습니다.

### 4.2 재시도 전략 비교

| 전략 | 설명 | 장점 | 단점 |
|-----|------|------|------|
| **Fixed Delay** | 고정 시간 대기 (50ms) | 단순함 | 충돌 빈번 시 비효율적 |
| **Exponential Backoff** | 지수적 증가 (50ms → 100ms → 200ms) | 충돌 분산 효과 | 최대 대기 시간 증가 |
| **Random Jitter** | 랜덤 시간 추가 | 충돌 회피 | 예측 불가능한 지연 |

#### Exponential Backoff 구현 예시

```java
@Service
@RequiredArgsConstructor
public class UserUseCase {

    private static final int MAX_RETRY_COUNT = 10;
    private static final long INITIAL_DELAY_MS = 50;

    public void deductBalanceWithExponentialBackoff(Long userId, BigDecimal amount) {
        int retryCount = 0;

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                deductBalance(userId, amount);
                return;

            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;

                if (retryCount >= MAX_RETRY_COUNT) {
                    throw new IllegalStateException("최대 재시도 횟수 초과", e);
                }

                // Exponential Backoff: 50ms → 100ms → 200ms → 400ms ...
                long delayMs = INITIAL_DELAY_MS * (long) Math.pow(2, retryCount - 1);

                // Random Jitter 추가 (0~25% 랜덤 추가)
                long jitter = (long) (delayMs * 0.25 * Math.random());
                long totalDelay = delayMs + jitter;

                log.info("재시도 대기: {}ms (retry {}/{})", totalDelay, retryCount, MAX_RETRY_COUNT);

                try {
                    Thread.sleep(totalDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("재시도 대기 중 인터럽트", ie);
                }
            }
        }
    }
}
```

---

## 🚀 개선 사항 5: 대규모 부하 테스트

### 5.1 K6 부하 테스트 스크립트

```javascript
// k6-load-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 커스텀 메트릭
const successCounter = new Counter('successful_orders');
const errorCounter = new Counter('failed_orders');
const errorRate = new Rate('error_rate');
const orderDuration = new Trend('order_duration');

// 테스트 설정
export const options = {
  stages: [
    { duration: '30s', target: 100 },   // Ramp-up: 0 → 100명
    { duration: '2m', target: 100 },    // Stay: 100명 유지
    { duration: '30s', target: 500 },   // Peak: 100 → 500명
    { duration: '1m', target: 500 },    // Stay: 500명 유지
    { duration: '30s', target: 0 },     // Ramp-down: 500 → 0명
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],   // 95% 요청이 500ms 이하
    error_rate: ['rate<0.01'],          // 에러율 1% 이하
  },
};

export default function () {
  const url = 'http://localhost:8080/api/orders';

  const payload = JSON.stringify({
    productId: 1,
    quantity: 1,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    timeout: '10s',
  };

  const res = http.post(url, payload, params);

  // 응답 검증
  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });

  if (success) {
    successCounter.add(1);
  } else {
    errorCounter.add(1);
  }

  errorRate.add(!success);
  orderDuration.add(res.timings.duration);

  sleep(1);  // 1초 대기
}

export function handleSummary(data) {
  return {
    'k6-summary.json': JSON.stringify(data),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}
```

#### 실행 명령어

```bash
# K6 설치
brew install k6

# 테스트 실행
k6 run k6-load-test.js

# 결과를 Grafana로 시각화
k6 run --out influxdb=http://localhost:8086/k6 k6-load-test.js
```

---

## ✅ 체크리스트

### 누락된 테스트 추가
- [ ] 결제 중복(Idempotency Key) 테스트 작성 및 통과
- [ ] 잔액 손실(Optimistic Lock) 테스트 작성 및 통과
- [ ] 테스트 커버리지 확인 (5가지 문제 모두 검증)

### 성능 측정 및 문서화
- [ ] JMeter로 비관적 락 성능 측정 (Before/After)
- [ ] JMeter로 낙관적 락 성능 측정 (Before/After)
- [ ] 성능 보고서 작성 (TPS, 응답 시간, 에러율)
- [ ] 락 전략별 권장 사항 문서화

### 락 타임아웃 설정
- [ ] ProductRepository에 @QueryHints 추가
- [ ] CouponRepository에 @QueryHints 추가
- [ ] 타임아웃 예외 처리 로직 추가
- [ ] NOWAIT, SKIP LOCKED 옵션 테스트

### 낙관적 락 재시도 로직
- [ ] UserUseCase에 재시도 로직 추가
- [ ] 재시도 횟수, 백오프 전략 설정
- [ ] 최대 재시도 초과 시 예외 처리
- [ ] Exponential Backoff + Random Jitter 구현

### 대규모 부하 테스트
- [ ] K6 스크립트 작성
- [ ] 100명, 500명, 1000명 규모 테스트 실행
- [ ] Lock Contention 영향도 측정
- [ ] LOAD_TEST_EXECUTION_GUIDE.md 업데이트

---

## 🎯 다음 단계

1. **즉시 개선 (1-2일)**
   - 누락된 테스트 2개 추가
   - 락 타임아웃 설정

2. **단기 개선 (3-5일)**
   - 성능 측정 및 보고서 작성
   - 재시도 로직 최적화

3. **중기 개선 (1-2주)**
   - K6 대규모 부하 테스트
   - Redis Distributed Lock 도입

---

**📚 제이코치님 피드백을 반영하여 더 견고한 동시성 제어를 구현하세요!** 💪
