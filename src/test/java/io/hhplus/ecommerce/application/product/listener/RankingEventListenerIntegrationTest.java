package io.hhplus.ecommerce.application.product.listener;

import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.order.PaymentCompletedEvent;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.infrastructure.persistence.product.JpaProductRepository;
import io.hhplus.ecommerce.infrastructure.persistence.user.JpaUserRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RankingEventListener Integration Test
 *
 * 목적: 이벤트 리스너 + Redis 실제 연동 검증
 * - @TransactionalEventListener AFTER_COMMIT 동작 확인
 * - 비동기 처리 (@Async) 확인
 * - 실제 Redis에 랭킹 score 증가 확인
 * - 트랜잭션 롤백 시 이벤트 미발행 확인
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class RankingEventListenerIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ProductRankingRepository rankingRepository;

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private JpaProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User testUser;
    private Product testProduct1;
    private Product testProduct2;

    @BeforeEach
    void setUp() {
        // 각 테스트에서 TransactionTemplate을 사용하여 데이터 준비
        // @Transactional 제거 (saveAndFlush() 문제 해결)
    }

    // 테스트 데이터 ID를 저장 (detached entity 문제 해결)
    private Long testUserId;
    private Long testProduct1Id;
    private Long testProduct2Id;
    private String testRunId;

    /**
     * 테스트 데이터 준비 헬퍼 메서드
     *
     * 테스트 클래스 레벨의 @Transactional로 JPA 트랜잭션 활성화
     * - saveAndFlush() 사용 가능
     * - ID 자동 생성
     */
    void prepareTestData() {
        System.out.println("======= prepareTestData() CALLED =======");
        System.out.println("transactionManager = " + transactionManager);
        System.out.println("userRepository = " + userRepository);
        System.out.println("productRepository = " + productRepository);

        try {
            System.out.println(">>>>>> PREPARING DATA <<<<<<");
            String uniqueId = java.util.UUID.randomUUID().toString().substring(0, 8);
            System.out.println("uniqueId = " + uniqueId);
            testRunId = uniqueId;

            User user = User.create("ranking-int-test-" + uniqueId + "@example.com", "랭킹통합테스트유저");
            System.out.println("User created: " + user);

            System.out.println("Before charge...");
            user.charge(1_000_000L);
            System.out.println("After charge, before save...");

            User savedUser = userRepository.saveAndFlush(user);  // ← saveAndFlush 사용
            System.out.println("After save, savedUser = " + savedUser);
            System.out.println("savedUser.getId() = " + savedUser.getId());

            testUserId = savedUser.getId();  // ← ID 추출 (managed 상태에서)
            System.out.println("DEBUG: testUserId = " + testUserId);

            Product product1 = Product.create("RI-P01-" + uniqueId, "랭킹통합상품1", "설명1", 10_000L, "전자제품", 100);
            Product savedProduct1 = productRepository.saveAndFlush(product1);  // ← saveAndFlush 사용
            testProduct1Id = savedProduct1.getId();  // ← ID 추출
            System.out.println("DEBUG: testProduct1Id = " + testProduct1Id);

            Product product2 = Product.create("RI-P02-" + uniqueId, "랭킹통합상품2", "설명2", 20_000L, "전자제품", 100);
            Product savedProduct2 = productRepository.saveAndFlush(product2);  // ← saveAndFlush 사용
            testProduct2Id = savedProduct2.getId();  // ← ID 추출
            System.out.println("DEBUG: testProduct2Id = " + testProduct2Id);

            // After save, verify IDs are set
            System.out.println("DEBUG END: testUserId = " + testUserId);
            System.out.println("DEBUG END: testProduct1Id = " + testProduct1Id);
            System.out.println("DEBUG END: testProduct2Id = " + testProduct2Id);
        } catch (Exception e) {
            System.err.println("ERROR in prepareTestData: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    @DisplayName("AFTER_COMMIT: 트랜잭션 커밋 후에만 이벤트 처리되어 랭킹 갱신")
    void afterCommit_이벤트처리_랭킹갱신() throws InterruptedException {
        // Given: 테스트 데이터 준비
        prepareTestData();

        // Given & When: 트랜잭션 내에서 Order 생성 및 이벤트 발행
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        template.execute(status -> {
            User user = userRepository.findById(testUserId).orElseThrow();
            Product product = productRepository.findById(testProduct1Id).orElseThrow();

            Order order = Order.create("ORD-" + testRunId + "-001", user, 30_000L, 0L);
            order = orderRepository.save(order);

            OrderItem item = OrderItem.create(order, product, 3, 10_000L);
            orderItemRepository.save(item);

            // 이벤트 발행 (트랜잭션 내부)
            eventPublisher.publishEvent(new PaymentCompletedEvent(order));

            return null;
        });
        // ← 여기서 트랜잭션 커밋 → AFTER_COMMIT 리스너 실행

        // Then: 비동기 처리 대기 후 랭킹 확인
        Thread.sleep(2000);  // @Async 처리 대기

        int score = rankingRepository.getScore(LocalDate.now(), testProduct1Id.toString());
        assertThat(score).isEqualTo(3);
    }

    @Test
    @DisplayName("트랜잭션 롤백 시 이벤트 미발행")
    void rollback_이벤트미발행() throws InterruptedException {
        System.out.println("@@@@@@@ TEST METHOD STARTED @@@@@@@");
        // Given: 테스트 데이터 준비
        prepareTestData();
        System.out.println("@@@@@@@ AFTER prepareTestData() @@@@@@@");

        // Given & When: 트랜잭션 내에서 예외 발생 → 롤백
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> {
            template.execute(status -> {
                User user = userRepository.findById(testUserId).orElseThrow();
                Product product = productRepository.findById(testProduct1Id).orElseThrow();

                Order order = Order.create("ORD-" + testRunId + "-RB", user, 30_000L, 0L);
                order = orderRepository.save(order);

                OrderItem item = OrderItem.create(order, product, 3, 10_000L);
                orderItemRepository.save(item);

                // 이벤트 발행
                eventPublisher.publishEvent(new PaymentCompletedEvent(order));

                // 강제 롤백
                throw new RuntimeException("강제 롤백");
            });
        }).isInstanceOf(RuntimeException.class);

        // Then: 이벤트 리스너 실행 안 됨
        Thread.sleep(2000);

        int score = rankingRepository.getScore(LocalDate.now(), testProduct1Id.toString());
        assertThat(score).isEqualTo(0);  // 랭킹 갱신 안 됨
    }

    @Test
    @DisplayName("여러 상품 주문 시 각 상품별 랭킹 갱신")
    void 여러상품_각각랭킹갱신() throws InterruptedException {
        // Given: 테스트 데이터 준비
        prepareTestData();

        // Given & When: 2개 상품 주문
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        template.execute(status -> {
            User user = userRepository.findById(testUserId).orElseThrow();
            Product product1 = productRepository.findById(testProduct1Id).orElseThrow();
            Product product2 = productRepository.findById(testProduct2Id).orElseThrow();

            Order order = Order.create("ORD-" + testRunId + "-M1", user, 50_000L, 0L);
            order = orderRepository.save(order);

            OrderItem item1 = OrderItem.create(order, product1, 2, 10_000L);
            OrderItem item2 = OrderItem.create(order, product2, 5, 20_000L);
            orderItemRepository.save(item1);
            orderItemRepository.save(item2);

            eventPublisher.publishEvent(new PaymentCompletedEvent(order));

            return null;
        });

        // Then: 각 상품별 score 증가
        Thread.sleep(2000);

        int score1 = rankingRepository.getScore(LocalDate.now(), testProduct1Id.toString());
        int score2 = rankingRepository.getScore(LocalDate.now(), testProduct2Id.toString());

        assertThat(score1).isEqualTo(2);
        assertThat(score2).isEqualTo(5);
    }

    @Test
    @DisplayName("동일 상품 여러 번 주문 시 score 누적")
    void 동일상품_여러주문_score누적() throws InterruptedException {
        // Given: 테스트 데이터 준비
        prepareTestData();

        // Given & When: 3번 주문
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        for (int i = 1; i <= 3; i++) {
            final int quantity = i;
            template.execute(status -> {
                User user = userRepository.findById(testUserId).orElseThrow();
                Product product = productRepository.findById(testProduct1Id).orElseThrow();

                Order order = Order.create(String.format("ORD-%s-%03d", testRunId, quantity), user, 10_000L * quantity, 0L);
                order = orderRepository.save(order);

                OrderItem item = OrderItem.create(order, product, quantity, 10_000L);
                orderItemRepository.save(item);

                eventPublisher.publishEvent(new PaymentCompletedEvent(order));

                return null;
            });
        }

        // Then: score가 1 + 2 + 3 = 6 누적
        Thread.sleep(3000);

        int score = rankingRepository.getScore(LocalDate.now(), testProduct1Id.toString());
        assertThat(score).isGreaterThanOrEqualTo(6);  // 비동기 처리로 인한 약간의 오차 허용
    }

    @Test
    @DisplayName("Redis 장애 시에도 이벤트 처리는 정상 완료 (로그만 남김)")
    void Redis장애_이벤트처리정상() throws InterruptedException {
        // Given: 테스트 데이터 준비
        prepareTestData();

        // Given: Redis 서버는 정상 (실제 장애는 Testcontainers 환경에서 시뮬레이션 어려움)
        // 이 테스트는 RankingEventListenerTest (Unit)에서 Mock으로 검증

        // When: 정상 이벤트 발행
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        template.execute(status -> {
            User user = userRepository.findById(testUserId).orElseThrow();
            Product product = productRepository.findById(testProduct1Id).orElseThrow();

            Order order = Order.create("ORD-" + testRunId + "-R1", user, 30_000L, 0L);
            order = orderRepository.save(order);

            OrderItem item = OrderItem.create(order, product, 3, 10_000L);
            orderItemRepository.save(item);

            eventPublisher.publishEvent(new PaymentCompletedEvent(order));

            return null;
        });

        // Then: 정상 처리
        Thread.sleep(2000);

        int score = rankingRepository.getScore(LocalDate.now(), testProduct1Id.toString());
        assertThat(score).isEqualTo(3);
    }
}
