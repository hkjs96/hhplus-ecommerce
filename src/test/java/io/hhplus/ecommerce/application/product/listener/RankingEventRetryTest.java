package io.hhplus.ecommerce.application.product.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.event.FailedEvent;
import io.hhplus.ecommerce.domain.event.FailedEvent.FailedEventStatus;
import io.hhplus.ecommerce.domain.event.FailedEventRepository;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.order.PaymentCompletedEvent;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.infrastructure.persistence.product.JpaProductRepository;
import io.hhplus.ecommerce.infrastructure.persistence.user.JpaUserRepository;
import io.hhplus.ecommerce.infrastructure.redis.EventIdempotencyService;
import io.hhplus.ecommerce.infrastructure.redis.ProductRankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 재시도 메커니즘 Integration Test
 *
 * 목적: 이벤트 처리 실패 시 FailedEvent에 저장되어 재시도 가능
 * - Redis 장애 시뮬레이션
 * - FailedEvent DB 저장 검증
 * - 재시도 성공 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class RankingEventRetryTest {

    @TestConfiguration
    static class RankingEventRetryTestConfig {
        @Bean
        RankingEventListener rankingEventListener(
            ProductRankingRepository rankingRepository,
            EventIdempotencyService idempotencyService,
            FailedEventRepository failedEventRepository,
            ObjectMapper objectMapper
        ) {
            return new RankingEventListener(rankingRepository, idempotencyService, failedEventRepository, objectMapper);
        }
    }

    @MockBean  // 장애 시나리오를 주입하기 위해 목으로 대체
    private ProductRankingRepository rankingRepository;

    @Autowired
    private FailedEventRepository failedEventRepository;

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

    @Autowired
    private RankingEventListener rankingEventListener;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private EventIdempotencyService idempotencyService;

    private User testUser;
    private Product testProduct;

    @BeforeEach
    @Transactional
    void setUp() {
        // Use unique identifiers per test run to avoid conflicts
        String uniqueId = java.util.UUID.randomUUID().toString().substring(0, 8);

        User user = User.create("retry-test-" + uniqueId + "@example.com", "재시도테스트유저");
        user.charge(1_000_000L);
        testUser = userRepository.saveAndFlush(user);

        testProduct = productRepository.saveAndFlush(
            Product.create("RETRY-P999-" + uniqueId, "재시도상품", "설명", 10_000L, "전자제품", 100)
        );
    }

    @Test
    @DisplayName("Redis 장애 시 FailedEvent에 저장")
    void Redis장애_FailedEvent저장() throws InterruptedException {
        // Given: Redis incrementScore가 예외를 던지도록 설정
        doThrow(new RuntimeException("Redis connection failed"))
            .when(rankingRepository).incrementScore(anyString(), anyInt());

        // Given: 주문 생성
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        Order savedOrder = template.execute(status -> {
            User user = userRepository.findById(testUser.getId()).orElseThrow();
            Product product = productRepository.findById(testProduct.getId()).orElseThrow();

            Order order = Order.create("ORDER-RETRY-001", user, 30_000L, 0L);
            Order saved = orderRepository.save(order);

            OrderItem item = OrderItem.create(saved, product, 3, 10_000L);
            orderItemRepository.save(item);

            return saved;
        });

        String eventType = "PaymentCompleted";
        String eventId = "order-" + savedOrder.getId();
        idempotencyService.remove(eventType, eventId); // 멱등성 키 초기화

        // When: 이벤트 발행 (Redis 장애) - 실제 이벤트 흐름대로
        template.execute(status -> {
            eventPublisher.publishEvent(new PaymentCompletedEvent(savedOrder));
            return null;
        });

        FailedEvent failedEvent = awaitFailedEvent(eventType, eventId);

        // Then: FailedEvent에 저장되었는지 확인
        assertThat(failedEvent.getEventType()).isEqualTo(eventType);
        assertThat(failedEvent.getEventId()).isEqualTo(eventId);
        assertThat(failedEvent.getStatus()).isEqualTo(FailedEventStatus.PENDING);
        assertThat(failedEvent.getRetryCount()).isEqualTo(0);
        assertThat(failedEvent.getErrorMessage()).contains("Redis connection failed");
    }

    @Test
    @DisplayName("FailedEvent 재시도 성공 시 SUCCESS 상태로 변경")
    void FailedEvent_재시도_성공() throws InterruptedException {
        // Given: Redis 장애 시뮬레이션 후 FailedEvent 생성
        doThrow(new RuntimeException("Redis temporarily down"))
            .when(rankingRepository).incrementScore(anyString(), anyInt());

        TransactionTemplate template = new TransactionTemplate(transactionManager);

        Order savedOrder = template.execute(status -> {
            User user = userRepository.findById(testUser.getId()).orElseThrow();
            Product product = productRepository.findById(testProduct.getId()).orElseThrow();

            Order order = Order.create("ORDER-RETRY-002", user, 50_000L, 0L);
            Order saved = orderRepository.save(order);

            OrderItem item = OrderItem.create(saved, product, 5, 10_000L);
            orderItemRepository.save(item);

            return saved;
        });

        String eventType = "PaymentCompleted";
        String eventId = "order-" + savedOrder.getId();
        idempotencyService.remove(eventType, eventId); // 멱등성 키 초기화

        // 비동기 이벤트 흐름으로 실패 이벤트 생성
        template.execute(status -> {
            eventPublisher.publishEvent(new PaymentCompletedEvent(savedOrder));
            return null;
        });

        FailedEvent failedEvent = awaitFailedEvent(eventType, eventId);

        assertThat(failedEvent.getStatus()).isEqualTo(FailedEventStatus.PENDING);

        // Given: Redis 복구 (정상 동작)
        reset(rankingRepository);
        doNothing().when(rankingRepository).incrementScore(anyString(), anyInt());

        // When: 재시도 시작
        failedEvent.startRetry();
        failedEventRepository.save(failedEvent);

        boolean retrySuccess = rankingEventListener.retryFailedEvent(failedEvent);

        // Then: 재시도 성공
        assertThat(retrySuccess).isTrue();

        // When: 재시도 성공 상태로 변경
        failedEvent.markSuccess();
        failedEventRepository.save(failedEvent);

        // Then: SUCCESS 상태로 변경
        FailedEvent updated = failedEventRepository.findById(failedEvent.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(FailedEventStatus.SUCCESS);
        assertThat(updated.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("재시도 실패 시 PENDING 상태로 되돌아가며 nextRetryAt 갱신 (Exponential Backoff)")
    void 재시도실패_PENDING상태_ExponentialBackoff() {
        // Given: FailedEvent 생성 (PENDING)
        String eventType = "PaymentCompleted";
        String eventId = "order-12345";
        String payload = "{\"orderId\":12345,\"userId\":1}";
        String errorMessage = "First failure";

        idempotencyService.remove(eventType, eventId); // 멱등성 키 초기화 (재시도 테스트 간 간섭 방지)

        FailedEvent failedEvent = FailedEvent.create(eventType, eventId, payload, errorMessage);
        failedEventRepository.save(failedEvent);

        assertThat(failedEvent.getRetryCount()).isEqualTo(0);
        assertThat(failedEvent.getStatus()).isEqualTo(FailedEventStatus.PENDING);

        // When: 첫 번째 재시도 시작
        failedEvent.startRetry();
        failedEventRepository.save(failedEvent);

        assertThat(failedEvent.getRetryCount()).isEqualTo(1);
        assertThat(failedEvent.getStatus()).isEqualTo(FailedEventStatus.RETRYING);

        // When: 재시도 실패
        failedEvent.markRetryFailed("Second failure");
        failedEventRepository.save(failedEvent);

        // Then: PENDING으로 되돌아감, nextRetryAt 갱신 (1분 후)
        assertThat(failedEvent.getStatus()).isEqualTo(FailedEventStatus.PENDING);
        assertThat(failedEvent.getRetryCount()).isEqualTo(1);
        assertThat(failedEvent.getNextRetryAt()).isNotNull();
        assertThat(failedEvent.getNextRetryAt()).isAfter(failedEvent.getCreatedAt());
    }

    @Test
    @DisplayName("최대 재시도 횟수(3) 초과 시 FAILED (DLQ) 상태로 변경")
    void 최대재시도횟수초과_FAILED상태() {
        // Given: FailedEvent 생성
        String eventType = "PaymentCompleted";
        String eventId = "order-99999";
        String payload = "{\"orderId\":99999,\"userId\":1}";

        FailedEvent failedEvent = FailedEvent.create(eventType, eventId, payload, "Initial failure");
        failedEventRepository.save(failedEvent);

        // When: 3번 재시도 실패
        for (int i = 0; i < 3; i++) {
            failedEvent.startRetry();
            failedEvent.markRetryFailed("Retry failure #" + (i + 1));
            failedEventRepository.save(failedEvent);
        }

        // Then: FAILED 상태 (DLQ)
        FailedEvent updated = failedEventRepository.findById(failedEvent.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(FailedEventStatus.FAILED);
        assertThat(updated.getRetryCount()).isEqualTo(3);
        assertThat(updated.getNextRetryAt()).isNull();  // 더 이상 재시도 안 함
    }

    @Test
    @DisplayName("재시도 가능 여부 체크: PENDING + nextRetryAt 경과")
    void 재시도가능여부_체크() throws InterruptedException {
        // Given: FailedEvent 생성 (PENDING)
        String eventType = "PaymentCompleted";
        String eventId = "order-11111";
        String payload = "{\"orderId\":11111}";

        FailedEvent failedEvent = FailedEvent.create(eventType, eventId, payload, "Failure");
        failedEventRepository.save(failedEvent);

        // When: nextRetryAt이 아직 도래하지 않음 (1분 후 설정됨)
        boolean canRetryBefore = failedEvent.canRetry();
        assertThat(canRetryBefore).isFalse();  // 아직 재시도 불가

        // When: nextRetryAt을 과거로 변경 (시간 경과 시뮬레이션)
        failedEvent.startRetry();
        failedEvent.markRetryFailed("Still failing");

        // nextRetryAt을 강제로 과거로 설정
        try {
            java.lang.reflect.Field field = FailedEvent.class.getDeclaredField("nextRetryAt");
            field.setAccessible(true);
            field.set(failedEvent, java.time.LocalDateTime.now().minusMinutes(1));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        failedEventRepository.save(failedEvent);

        // Then: 재시도 가능
        FailedEvent updated = failedEventRepository.findById(failedEvent.getId()).orElseThrow();
        boolean canRetryAfter = updated.canRetry();
        assertThat(canRetryAfter).isTrue();
    }

    private FailedEvent awaitFailedEvent(String eventType, String eventId) throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            Optional<FailedEvent> found = failedEventRepository.findByEventTypeAndEventId(eventType, eventId);
            if (found.isPresent()) {
                return found.get();
            }
            TimeUnit.MILLISECONDS.sleep(100L);
        }
        throw new AssertionError("FailedEvent not found for type=" + eventType + ", id=" + eventId);
    }
}
