package io.hhplus.ecommerce.application.product.listener;

import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.order.PaymentCompletedEvent;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.redis.EventIdempotencyService;
import io.hhplus.ecommerce.infrastructure.redis.ProductRankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이벤트 멱등성 Integration Test
 *
 * 목적: 동일한 이벤트가 여러 번 발행되어도 랭킹은 한 번만 증가
 * - Redis EventIdempotencyService를 사용한 중복 처리 방지
 * - 재시도 시나리오에서도 멱등성 보장
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class RankingEventIdempotencyTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ProductRankingRepository rankingRepository;

    @Autowired
    private EventIdempotencyService idempotencyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User testUser;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 준비: TransactionTemplate 내부에서 DB 저장 및 커밋
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        template.execute(status -> {
            testUser = userRepository.save(User.create("idem-test@example.com", "멱등성테스트유저"));
            testUser.charge(1_000_000L);

            testProduct = productRepository.save(
                Product.create("IDEM-P001", "멱등성상품", "설명1", 10_000L, "전자제품", 100)
            );

            return null;
        });
        // → 트랜잭션 커밋 완료, testUser와 testProduct는 ID가 할당된 detached 상태

        // Redis 멱등성 데이터 초기화
        String eventType = "PaymentCompleted";
        String eventId = "order-" + 1L;  // 테스트용 고정 ID
        idempotencyService.remove(eventType, eventId);
    }

    @Test
    @DisplayName("동일 이벤트 2번 발행 시 랭킹은 1번만 증가 (멱등성)")
    void 동일이벤트_중복발행_멱등성보장() throws InterruptedException {
        // Given: 주문 생성
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        Order savedOrder = template.execute(status -> {
            // testUser를 트랜잭션 내부에서 재조회 (detached → managed)
            User user = userRepository.findById(testUser.getId()).orElseThrow();
            Product product = productRepository.findById(testProduct.getId()).orElseThrow();

            Order order = Order.create("ORDER-001", user, 30_000L, 0L);
            Order saved = orderRepository.save(order);

            OrderItem item = OrderItem.create(saved, product, 3, 10_000L);
            orderItemRepository.save(item);

            return saved;
        });

        // When: 동일 이벤트 2번 발행
        template.execute(status -> {
            eventPublisher.publishEvent(new PaymentCompletedEvent(savedOrder));
            return null;
        });

        Thread.sleep(2000);  // 첫 번째 처리 대기

        // 두 번째 이벤트 발행 (중복)
        template.execute(status -> {
            eventPublisher.publishEvent(new PaymentCompletedEvent(savedOrder));
            return null;
        });

        Thread.sleep(2000);  // 두 번째 처리 대기

        // Then: score는 3만 증가 (한 번만 처리)
        int score = rankingRepository.getScore(LocalDate.now(), testProduct.getId().toString());
        assertThat(score).isEqualTo(3);  // 중복 처리되지 않음
    }

    @Test
    @DisplayName("동일 이벤트 3번 연속 발행 시 랭킹은 1번만 증가")
    void 동일이벤트_3번발행_1번만처리() throws InterruptedException {
        // Given: 주문 생성
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        Order savedOrder = template.execute(status -> {
            User user = userRepository.findById(testUser.getId()).orElseThrow();
            Product product = productRepository.findById(testProduct.getId()).orElseThrow();

            Order order = Order.create("ORDER-001", user, 50_000L, 0L);
            Order saved = orderRepository.save(order);

            OrderItem item = OrderItem.create(saved, product, 5, 10_000L);
            orderItemRepository.save(item);

            return saved;
        });

        // When: 동일 이벤트 3번 발행
        for (int i = 0; i < 3; i++) {
            template.execute(status -> {
                eventPublisher.publishEvent(new PaymentCompletedEvent(savedOrder));
                return null;
            });
            Thread.sleep(1000);
        }

        Thread.sleep(3000);  // 처리 대기

        // Then: score는 5만 증가 (첫 번째만 처리)
        int score = rankingRepository.getScore(LocalDate.now(), testProduct.getId().toString());
        assertThat(score).isEqualTo(5);
    }

    @Test
    @DisplayName("멱등성 체크 후 실패 → 재시도 시에도 중복 처리 방지")
    void 멱등성체크_실패_재시도_중복방지() throws InterruptedException {
        // Given: 주문 생성
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        Order savedOrder = template.execute(status -> {
            User user = userRepository.findById(testUser.getId()).orElseThrow();
            Product product = productRepository.findById(testProduct.getId()).orElseThrow();

            Order order = Order.create("ORDER-001", user, 20_000L, 0L);
            Order saved = orderRepository.save(order);

            OrderItem item = OrderItem.create(saved, product, 2, 10_000L);
            orderItemRepository.save(item);

            return saved;
        });

        // When: 첫 번째 이벤트 발행 및 처리
        template.execute(status -> {
            eventPublisher.publishEvent(new PaymentCompletedEvent(savedOrder));
            return null;
        });

        Thread.sleep(2000);

        int scoreAfterFirst = rankingRepository.getScore(LocalDate.now(), testProduct.getId().toString());
        assertThat(scoreAfterFirst).isEqualTo(2);

        // When: 두 번째 이벤트 발행 (재시도 시뮬레이션)
        template.execute(status -> {
            eventPublisher.publishEvent(new PaymentCompletedEvent(savedOrder));
            return null;
        });

        Thread.sleep(2000);

        // Then: score는 여전히 2 (중복 처리 안 됨)
        int scoreAfterRetry = rankingRepository.getScore(LocalDate.now(), testProduct.getId().toString());
        assertThat(scoreAfterRetry).isEqualTo(2);
    }

    @Test
    @DisplayName("서로 다른 주문(eventId)은 각각 처리됨")
    void 서로다른이벤트_각각처리() throws InterruptedException {
        // Given: 두 개의 주문 생성
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        Order order1 = template.execute(status -> {
            User user = userRepository.findById(testUser.getId()).orElseThrow();
            Product product = productRepository.findById(testProduct.getId()).orElseThrow();

            Order order = Order.create("ORDER-001", user, 30_000L, 0L);
            Order saved = orderRepository.save(order);

            OrderItem item = OrderItem.create(saved, product, 3, 10_000L);
            orderItemRepository.save(item);

            return saved;
        });

        Order order2 = template.execute(status -> {
            User user = userRepository.findById(testUser.getId()).orElseThrow();
            Product product = productRepository.findById(testProduct.getId()).orElseThrow();

            Order order = Order.create("ORDER-002", user, 50_000L, 0L);
            Order saved = orderRepository.save(order);

            OrderItem item = OrderItem.create(saved, product, 5, 10_000L);
            orderItemRepository.save(item);

            return saved;
        });

        // When: 두 이벤트 발행
        template.execute(status -> {
            eventPublisher.publishEvent(new PaymentCompletedEvent(order1));
            return null;
        });

        Thread.sleep(2000);

        template.execute(status -> {
            eventPublisher.publishEvent(new PaymentCompletedEvent(order2));
            return null;
        });

        Thread.sleep(2000);

        // Then: score는 3 + 5 = 8 (각각 처리)
        int score = rankingRepository.getScore(LocalDate.now(), testProduct.getId().toString());
        assertThat(score).isEqualTo(8);
    }
}
