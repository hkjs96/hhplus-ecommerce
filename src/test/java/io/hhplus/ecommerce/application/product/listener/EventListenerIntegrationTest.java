package io.hhplus.ecommerce.application.product.listener;

import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.application.usecase.order.ProcessPaymentUseCase;
import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.redis.ProductRankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

/**
 * Event Listener Integration Test
 *
 * 목적: Phase 1 + Phase 2 통합 검증
 * - EventIdempotencyListener: 멱등성 체크
 * - RankingUpdateEventListener: Redis 랭킹 갱신 + 재시도
 * - @TransactionalEventListener AFTER_COMMIT 동작
 * - @Async 비동기 처리
 *
 * 전략:
 * - ProcessPaymentUseCaseIntegrationTest 패턴 참고
 * - @MockBean 없이 실제 ApplicationEventPublisher 사용
 * - 비동기 처리 대기는 Awaitility 사용
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class EventListenerIntegrationTest {

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductRankingRepository rankingRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private User testUser;
    private Product testProduct1;
    private Product testProduct2;

    @BeforeEach
    void setUp() {
        // 각 테스트마다 고유한 데이터 생성 (UUID 사용)
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);

        // 테스트 사용자 생성, 충전 및 저장
        User user = User.create("event-test-" + uniqueId + "@example.com", "이벤트테스트유저");
        user.charge(1_000_000L);  // 100만원 충전
        testUser = userRepository.save(user);  // charge 후에 저장

        // 테스트 상품 생성
        testProduct1 = productRepository.save(
            Product.create("EVENT-P001-" + uniqueId, "이벤트테스트상품1", "설명1", 10_000L, "전자제품", 100)
        );
        testProduct2 = productRepository.save(
            Product.create("EVENT-P002-" + uniqueId, "이벤트테스트상품2", "설명2", 20_000L, "전자제품", 100)
        );
    }

    @Test
    @DisplayName("결제 완료 후 이벤트 발행 → 랭킹 갱신 (비동기)")
    void paymentCompleted_EventPublished_RankingUpdated() throws InterruptedException {
        // Redis 초기화 (이전 테스트 데이터 제거)
        redisTemplate.delete(
            redisTemplate.keys("ranking:product:orders:daily:*")
        );

        // Given: 주문 생성
        String uniqueOrderNumber = "ORDER-" + UUID.randomUUID().toString().substring(0, 8);
        Order order = Order.create(uniqueOrderNumber, testUser, 30_000L, 0L);
        Order savedOrder = orderRepository.save(order);

        OrderItem item = OrderItem.create(savedOrder, testProduct1, 3, 10_000L);
        orderItemRepository.save(item);

        PaymentRequest request = new PaymentRequest(
            testUser.getId(),
            "PAYMENT-" + UUID.randomUUID()
        );

        // When: 결제 처리 (이벤트 발행)
        processPaymentUseCase.execute(savedOrder.getId(), request);

        // Then: 비동기 처리 대기 후 랭킹 확인
        await().atMost(3, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                int score = rankingRepository.getScore(LocalDate.now(), testProduct1.getId().toString());
                assertThat(score).isEqualTo(3);
            });
    }

    @Test
    @DisplayName("여러 상품 주문 시 각 상품별 랭킹 갱신")
    void multipleProducts_EachRankingUpdated() throws InterruptedException {
        // Redis 초기화
        redisTemplate.delete(
            redisTemplate.keys("ranking:product:orders:daily:*")
        );

        // Given: 2개 상품 주문
        String uniqueOrderNumber = "ORDER-" + UUID.randomUUID().toString().substring(0, 8);
        Order order = Order.create(uniqueOrderNumber, testUser, 50_000L, 0L);
        Order savedOrder = orderRepository.save(order);

        OrderItem item1 = OrderItem.create(savedOrder, testProduct1, 2, 10_000L);
        OrderItem item2 = OrderItem.create(savedOrder, testProduct2, 5, 20_000L);
        orderItemRepository.save(item1);
        orderItemRepository.save(item2);

        PaymentRequest request = new PaymentRequest(
            testUser.getId(),
            "PAYMENT-" + UUID.randomUUID()
        );

        // When: 결제 처리
        processPaymentUseCase.execute(savedOrder.getId(), request);

        // Then: 각 상품별 랭킹 확인
        await().atMost(3, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                int score1 = rankingRepository.getScore(LocalDate.now(), testProduct1.getId().toString());
                int score2 = rankingRepository.getScore(LocalDate.now(), testProduct2.getId().toString());

                assertThat(score1).isEqualTo(2);
                assertThat(score2).isEqualTo(5);
            });
    }

    @Test
    @DisplayName("동일 상품 여러 주문 시 랭킹 score 누적")
    void sameProduct_MultipleOrders_ScoreAccumulated() throws InterruptedException {
        // Redis 초기화
        redisTemplate.delete(
            redisTemplate.keys("ranking:product:orders:daily:*")
        );

        // Given & When: 동일 상품 3번 주문
        for (int i = 1; i <= 3; i++) {
            String uniqueOrderNumber = "ORDER-" + UUID.randomUUID().toString().substring(0, 8);
            Order order = Order.create(uniqueOrderNumber, testUser, 10_000L * i, 0L);
            Order savedOrder = orderRepository.save(order);

            OrderItem item = OrderItem.create(savedOrder, testProduct1, i, 10_000L);
            orderItemRepository.save(item);

            PaymentRequest request = new PaymentRequest(
                testUser.getId(),
                "PAYMENT-" + UUID.randomUUID()
            );

            processPaymentUseCase.execute(savedOrder.getId(), request);
        }

        // Then: score 누적 확인 (1 + 2 + 3 = 6)
        // 여러 주문의 비동기 처리를 위해 대기 시간 증가
        await().atMost(10, TimeUnit.SECONDS)
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                int score = rankingRepository.getScore(LocalDate.now(), testProduct1.getId().toString());
                assertThat(score).isGreaterThanOrEqualTo(6);
            });
    }

    @Test
    @DisplayName("@Async 비동기 처리: 결제 트랜잭션과 랭킹 갱신 분리")
    void asyncProcessing_PaymentAndRankingSeparated() throws InterruptedException {
        // Redis 초기화
        redisTemplate.delete(
            redisTemplate.keys("ranking:product:orders:daily:*")
        );

        // Given: 주문 생성
        String uniqueOrderNumber = "ORDER-" + UUID.randomUUID().toString().substring(0, 8);
        Order order = Order.create(uniqueOrderNumber, testUser, 100_000L, 0L);
        Order savedOrder = orderRepository.save(order);

        OrderItem item = OrderItem.create(savedOrder, testProduct1, 10, 10_000L);
        orderItemRepository.save(item);

        PaymentRequest request = new PaymentRequest(
            testUser.getId(),
            "PAYMENT-" + UUID.randomUUID()
        );

        // When: 결제 처리
        long startTime = System.currentTimeMillis();
        processPaymentUseCase.execute(savedOrder.getId(), request);
        long endTime = System.currentTimeMillis();

        // Then: 결제 처리는 빠르게 완료 (비동기이므로 랭킹 갱신 대기 안 함)
        long duration = endTime - startTime;
        System.out.println("결제 처리 시간: " + duration + "ms");
        assertThat(duration).isLessThan(2000);  // 2초 이내

        // Then: 비동기 처리 대기 후 랭킹 확인
        await().atMost(3, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                int score = rankingRepository.getScore(LocalDate.now(), testProduct1.getId().toString());
                assertThat(score).isEqualTo(10);
            });
    }
}
