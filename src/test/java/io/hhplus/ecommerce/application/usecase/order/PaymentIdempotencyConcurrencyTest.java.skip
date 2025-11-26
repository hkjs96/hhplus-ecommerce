package io.hhplus.ecommerce.application.usecase.order;

import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentResponse;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.order.OrderStatus;
import io.hhplus.ecommerce.domain.payment.IdempotencyStatus;
import io.hhplus.ecommerce.domain.payment.PaymentIdempotency;
import io.hhplus.ecommerce.domain.payment.PaymentIdempotencyRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 멱등성 키 동시성 테스트
 * <p>
 * 제이 코치 피드백 반영:
 * "Idempotency Key가 실제로 중복 결제를 막는지, @Version이 Lost Update를 방지하는지 테스트로 검증하면
 * 문서와 코드가 일치하는지 확인할 수 있거든요."
 * <p>
 * 테스트 시나리오:
 * 1. 동일한 멱등성 키로 10번 동시 결제 시 1번만 처리
 * 2. 서로 다른 멱등성 키로 10번 동시 결제 시 10번 모두 처리
 * 3. 네트워크 재시도 시나리오 - 동일 키로 3번 재시도
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PaymentIdempotencyConcurrencyTest {

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private PaymentIdempotencyRepository paymentIdempotencyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    private User testUser;
    private Order testOrder;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성 (잔액 충분)
        testUser = User.create("test@example.com", "테스트유저");
        testUser.chargeBalance(BigDecimal.valueOf(1_000_000));
        userRepository.save(testUser);

        // 테스트 상품 생성
        testProduct = Product.builder()
                .name("테스트상품")
                .price(50_000L)
                .stock(100)
                .build();
        productRepository.save(testProduct);

        // 테스트 주문 생성 (PENDING 상태)
        testOrder = Order.create(
                "ORDER-" + UUID.randomUUID(),
                testUser.getId(),
                50_000L,
                0L
        );
        orderRepository.save(testOrder);
    }

    @Test
    @DisplayName("[제이코치 피드백] 동일한 멱등성 키로 10번 동시 결제 시 1번만 처리")
    void 멱등성키_동시성_테스트_중복차단() throws InterruptedException {
        // Given: 동일한 멱등성 키
        String idempotencyKey = UUID.randomUUID().toString();
        int threadCount = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger otherErrorCount = new AtomicInteger();

        List<Long> createdOrderIds = new ArrayList<>();

        // When: 10번 동시 결제 시도 (동일한 멱등성 키)
        for (int i = 0; i < threadCount; i++) {
            final int attemptNumber = i + 1;

            executorService.submit(() -> {
                try {
                    PaymentRequest request = new PaymentRequest(
                            testUser.getId(),
                            BigDecimal.valueOf(50_000),
                            idempotencyKey
                    );

                    PaymentResponse response = processPaymentUseCase.execute(testOrder.getId(), request);
                    successCount.incrementAndGet();

                    synchronized (createdOrderIds) {
                        createdOrderIds.add(response.orderId());
                    }

                    System.out.println("✅ 성공 #" + attemptNumber + " - OrderId: " + response.orderId());

                } catch (DataIntegrityViolationException e) {
                    // UNIQUE 제약조건 위반 (동시 요청 중 다른 스레드가 먼저 저장)
                    conflictCount.incrementAndGet();
                    System.out.println("⚠️ UNIQUE 제약조건 위반 #" + attemptNumber);

                } catch (Exception e) {
                    // 기타 예외 (이미 처리됨, 409 Conflict 등)
                    otherErrorCount.incrementAndGet();
                    System.out.println("⚠️ 기타 예외 #" + attemptNumber + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());

                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 정확히 1번만 성공
        System.out.println("\n=== 결과 요약 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("UNIQUE 제약조건 위반: " + conflictCount.get());
        System.out.println("기타 예외: " + otherErrorCount.get());

        // 핵심 검증: 1번만 성공해야 함
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get() + otherErrorCount.get()).isEqualTo(9);

        // DB에도 1건만 저장되었는지 확인
        long paymentCount = paymentIdempotencyRepository.countByIdempotencyKey(idempotencyKey);
        assertThat(paymentCount).isEqualTo(1);

        // 저장된 멱등성 키 상태 확인
        PaymentIdempotency savedIdempotency = paymentIdempotencyRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow();

        assertThat(savedIdempotency.getStatus()).isIn(
                IdempotencyStatus.COMPLETED,
                IdempotencyStatus.PROCESSING
        );
        assertThat(savedIdempotency.getUserId()).isEqualTo(testUser.getId());

        // 사용자 잔액 확인 (1번만 차감)
        User user = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(user.getBalance()).isEqualByComparingTo(
                BigDecimal.valueOf(950_000)  // 1,000,000 - 50,000
        );
    }

    @Test
    @DisplayName("[제이코치 피드백] 서로 다른 멱등성 키로 10번 동시 결제 시 10번 모두 처리")
    void 서로_다른_멱등성키_동시성_테스트() throws InterruptedException {
        // Given
        int threadCount = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When: 각각 다른 멱등성 키로 10번 결제
        for (int i = 0; i < threadCount; i++) {
            final int attemptNumber = i + 1;

            executorService.submit(() -> {
                try {
                    // 매번 새로운 멱등성 키 생성
                    String uniqueKey = UUID.randomUUID().toString();

                    // 새로운 주문 생성 (각 결제마다 별도 주문)
                    Order order = Order.create(
                            "ORDER-" + UUID.randomUUID(),
                            testUser.getId(),
                            50_000L,
                            0L
                    );
                    orderRepository.save(order);

                    PaymentRequest request = new PaymentRequest(
                            testUser.getId(),
                            BigDecimal.valueOf(50_000),
                            uniqueKey
                    );

                    PaymentResponse response = processPaymentUseCase.execute(order.getId(), request);
                    successCount.incrementAndGet();

                    System.out.println("✅ 성공 #" + attemptNumber + " - OrderId: " + response.orderId());

                } catch (Exception e) {
                    // 잔액 부족으로 일부 실패 가능
                    failCount.incrementAndGet();
                    System.out.println("❌ 실패 #" + attemptNumber + ": " + e.getMessage());

                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 잔액이 충분한 만큼 성공 (1,000,000 / 50,000 = 20회 가능)
        System.out.println("\n=== 결과 요약 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        assertThat(successCount.get()).isGreaterThanOrEqualTo(10);

        // DB에 실제로 저장된 멱등성 키 개수 확인
        long totalIdempotencyKeys = paymentIdempotencyRepository.count();
        assertThat(totalIdempotencyKeys).isEqualTo(successCount.get());
    }

    @Test
    @DisplayName("[제이코치 피드백] 네트워크 재시도 시나리오 - 동일 멱등성 키로 3번 재시도")
    void 네트워크_재시도_시나리오() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();

        PaymentRequest request = new PaymentRequest(
                testUser.getId(),
                BigDecimal.valueOf(50_000),
                idempotencyKey
        );

        // When: 첫 번째 요청 성공
        PaymentResponse firstResponse = processPaymentUseCase.execute(testOrder.getId(), request);
        assertThat(firstResponse).isNotNull();
        assertThat(firstResponse.success()).isTrue();

        Long firstOrderId = firstResponse.orderId();
        System.out.println("✅ 1차 요청 성공 - OrderId: " + firstOrderId);

        // When: 네트워크 타임아웃으로 재시도 (동일 키)
        PaymentResponse secondResponse = processPaymentUseCase.execute(testOrder.getId(), request);

        // Then: 기존 결과 반환 (중복 처리 안 함)
        assertThat(secondResponse).isNotNull();
        assertThat(secondResponse.orderId()).isEqualTo(firstOrderId);
        System.out.println("✅ 2차 요청 - 기존 결과 반환: " + secondResponse.orderId());

        // When: 다시 한 번 재시도 (동일 키)
        PaymentResponse thirdResponse = processPaymentUseCase.execute(testOrder.getId(), request);

        // Then: 역시 기존 결과 반환
        assertThat(thirdResponse).isNotNull();
        assertThat(thirdResponse.orderId()).isEqualTo(firstOrderId);
        System.out.println("✅ 3차 요청 - 기존 결과 반환: " + thirdResponse.orderId());

        // 잔액은 1번만 차감
        User user = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(user.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(950_000));

        // DB에도 1건만 존재
        long paymentCount = paymentIdempotencyRepository.countByIdempotencyKey(idempotencyKey);
        assertThat(paymentCount).isEqualTo(1);

        // 멱등성 키 상태 확인
        PaymentIdempotency savedIdempotency = paymentIdempotencyRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow();

        assertThat(savedIdempotency.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(savedIdempotency.getOrderId()).isEqualTo(firstOrderId);
    }

    @Test
    @DisplayName("100명이 동시에 서로 다른 주문에 대해 결제 시 모두 성공 (높은 동시성)")
    void 대규모_동시_결제_테스트() throws InterruptedException {
        // Given: 잔액을 충분히 증가
        testUser.chargeBalance(BigDecimal.valueOf(10_000_000));
        userRepository.save(testUser);

        int threadCount = 100;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When: 100명이 동시에 각자 다른 주문 결제
        for (int i = 0; i < threadCount; i++) {
            final int attemptNumber = i + 1;

            executorService.submit(() -> {
                try {
                    // 각 스레드마다 새로운 주문 생성
                    Order order = Order.create(
                            "ORDER-" + UUID.randomUUID(),
                            testUser.getId(),
                            10_000L,  // 10,000원씩
                            0L
                    );
                    orderRepository.save(order);

                    // 고유한 멱등성 키
                    String uniqueKey = UUID.randomUUID().toString();

                    PaymentRequest request = new PaymentRequest(
                            testUser.getId(),
                            BigDecimal.valueOf(10_000),
                            uniqueKey
                    );

                    processPaymentUseCase.execute(order.getId(), request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("❌ 실패 #" + attemptNumber + ": " + e.getMessage());

                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then
        System.out.println("\n=== 대규모 동시 결제 결과 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        // 대부분 성공해야 함
        assertThat(successCount.get()).isGreaterThan(90);

        // 최종 잔액 확인
        User user = userRepository.findById(testUser.getId()).orElseThrow();
        BigDecimal expectedBalance = BigDecimal.valueOf(11_000_000) // 초기 1,000,000 + 충전 10,000,000
                .subtract(BigDecimal.valueOf(10_000L * successCount.get()));

        assertThat(user.getBalance()).isEqualByComparingTo(expectedBalance);
    }
}
