package io.hhplus.ecommerce.application.usecase.order;

import io.hhplus.ecommerce.application.order.dto.CreateOrderRequest;
import io.hhplus.ecommerce.application.order.dto.CreateOrderResponse;
import io.hhplus.ecommerce.application.order.dto.OrderItemRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.application.usecase.user.ChargeBalanceUseCase;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceRequest;
import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 동시성 테스트 (분산락 적용)
 *
 * 100명이 동시에 같은 상품을 주문하고 결제할 때
 * 분산락이 정확히 재고를 제어하는지 검증합니다.
 */
@SpringBootTest
@Import(TestContainersConfig.class)
class PaymentConcurrencyWithDistributedLockTest {

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private ChargeBalanceUseCase chargeBalanceUseCase;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.product.JpaProductRepository jpaProductRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.user.JpaUserRepository jpaUserRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.cart.JpaCartItemRepository jpaCartItemRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.cart.JpaCartRepository jpaCartRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.order.JpaOrderItemRepository jpaOrderItemRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.order.JpaOrderRepository jpaOrderRepository;

    private Product testProduct;
    private List<User> testUsers;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리 (Foreign Key 순서 고려)
        jpaOrderItemRepository.deleteAll();
        jpaOrderRepository.deleteAll();
        jpaCartItemRepository.deleteAll();
        jpaCartRepository.deleteAll();
        jpaProductRepository.deleteAll();
        jpaUserRepository.deleteAll();

        // 테스트 상품 생성 (재고 100개)
        testProduct = Product.create(
                "TP" + (System.currentTimeMillis() % 100000),  // "TP12345" = 7자
                "테스트 상품",
                "테스트 상품 설명",
                10000L,
                "TEST",
                100
        );
        productRepository.save(testProduct);

        // 테스트 사용자 100명 생성 (각 100,000원 잔액)
        testUsers = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            User user = User.create(
                    "test-user-" + i + "@test.com",
                    "test-user-" + i
            );
            // 잔액 충전
            user.charge(100000L);
            userRepository.save(user);
            testUsers.add(user);
        }
    }

    @Test
    @DisplayName("100명이 동시 결제 시 재고 100개가 정확히 차감됨 (분산락)")
    void 분산락_결제_동시성_테스트_100명() throws InterruptedException {
        // Given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When: 100명이 동시에 주문 생성 → 결제
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    User user = testUsers.get(index);

                    // 1. 주문 생성
                    String orderIdempotencyKey = "ORDER_" + user.getId() + "_" + UUID.randomUUID().toString();
                    CreateOrderRequest orderRequest = new CreateOrderRequest(
                            user.getId(),
                            List.of(new OrderItemRequest(testProduct.getId(), 1)),
                            null,
                            orderIdempotencyKey
                    );
                    CreateOrderResponse orderResponse = createOrderUseCase.execute(orderRequest);

                    // 2. 결제 처리 (분산락 적용)
                    PaymentRequest paymentRequest = new PaymentRequest(
                            user.getId(),
                            UUID.randomUUID().toString()
                    );
                    processPaymentUseCase.execute(orderResponse.orderId(), paymentRequest);

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    System.err.println("Payment failed for user " + index + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 정확히 100개 결제 성공, 재고 0개
        System.out.println("Test result: successCount=" + successCount.get() + ", failCount=" + failCount.get());
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(0);

        Product product = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(product.getStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("재고 50개일 때 100명 요청 시 정확히 50개만 성공 (분산락)")
    void 분산락_결제_동시성_테스트_재고부족() throws InterruptedException {
        // Given: 재고 50개로 설정 (새로운 Product 생성)
        Product limitedStockProduct = Product.create(
                "LS" + (System.currentTimeMillis() % 100000),  // "LS12345" = 7자
                "테스트 상품 (재고 부족)",
                "테스트 상품 설명",
                10000L,
                "TEST",
                50  // 재고 50개
        );
        productRepository.save(limitedStockProduct);

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When: 100명이 동시에 주문 생성 → 결제
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    User user = testUsers.get(index);

                    // 1. 주문 생성
                    String orderIdempotencyKey = "ORDER_" + user.getId() + "_" + UUID.randomUUID().toString();
                    CreateOrderRequest orderRequest = new CreateOrderRequest(
                            user.getId(),
                            List.of(new OrderItemRequest(limitedStockProduct.getId(), 1)),
                            null,
                            orderIdempotencyKey
                    );
                    CreateOrderResponse orderResponse = createOrderUseCase.execute(orderRequest);

                    // 2. 결제 처리 (분산락 적용)
                    PaymentRequest paymentRequest = new PaymentRequest(
                            user.getId(),
                            UUID.randomUUID().toString()
                    );
                    processPaymentUseCase.execute(orderResponse.orderId(), paymentRequest);

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

        // Then: 정확히 50개만 성공, 50개 실패
        assertThat(successCount.get()).isEqualTo(50);
        assertThat(failCount.get()).isEqualTo(50);

        Product product = productRepository.findById(limitedStockProduct.getId()).orElseThrow();
        assertThat(product.getStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("잔액 충전 동시성 테스트 (분산락)")
    void 분산락_잔액충전_동시성_테스트() throws InterruptedException {
        // Given
        User user = testUsers.get(0);
        Long initialBalance = user.getBalance();

        int threadCount = 10;
        long chargeAmount = 10000L;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When: 같은 사용자가 10번 동시 충전
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    String chargeIdempotencyKey = "CHARGE_" + user.getId() + "_" + UUID.randomUUID().toString();
                    ChargeBalanceRequest request = new ChargeBalanceRequest(chargeAmount, chargeIdempotencyKey);
                    chargeBalanceUseCase.execute(user.getId(), request);
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

        // Then: 모두 성공, 정확히 100,000원 증가
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(0);

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getBalance()).isEqualTo(initialBalance + (chargeAmount * 10));
    }
}
