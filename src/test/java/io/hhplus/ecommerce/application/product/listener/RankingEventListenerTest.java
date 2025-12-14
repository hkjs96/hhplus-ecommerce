package io.hhplus.ecommerce.application.product.listener;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.PaymentCompletedEvent;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.infrastructure.redis.ProductRankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RankingEventListener Unit Test
 *
 * 목적: 비즈니스 로직만 검증 (Mock 사용, DB/Redis 독립)
 * - 이벤트 수신 시 각 상품별 score 증가
 * - 여러 상품 주문 시 각각 랭킹 갱신
 * - 동일 상품 여러 번 주문 시 score 누적
 * - Redis 장애 시 예외 처리
 */
@ExtendWith(MockitoExtension.class)
class RankingEventListenerTest {

    @Mock
    private ProductRankingRepository rankingRepository;

    @InjectMocks
    private RankingEventListener listener;

    private User testUser;
    private Product testProduct1;
    private Product testProduct2;

    @BeforeEach
    void setUp() {
        // Mock 도메인 객체 생성
        testUser = User.create("test@example.com", "테스트유저");
        testProduct1 = Product.create("P001", "상품1", "설명1", 10_000L, "전자제품", 100);
        testProduct2 = Product.create("P002", "상품2", "설명2", 20_000L, "전자제품", 100);

        // Reflection으로 ID 설정 (테스트용)
        setId(testUser, 1L);
        setId(testProduct1, 1L);
        setId(testProduct2, 2L);
    }

    @Test
    @DisplayName("결제 완료 이벤트 수신 시 각 상품별 랭킹 score 증가")
    void handlePaymentCompleted_단일상품_랭킹갱신() {
        // Given: 1개 상품, 3개 수량 주문
        Order order = Order.create("ORDER-001", testUser, 30_000L, 0L);
        OrderItem item = OrderItem.create(order, testProduct1, 3, 10_000L);

        List<OrderItem> items = new ArrayList<>();
        items.add(item);
        setOrderItems(order, items);

        PaymentCompletedEvent event = new PaymentCompletedEvent(order);

        // When: 이벤트 처리
        listener.handlePaymentCompleted(event);

        // Then: 상품1의 score가 3 증가
        verify(rankingRepository, times(1)).incrementScore(
            eq("1"),
            eq(3)
        );
    }

    @Test
    @DisplayName("여러 상품 주문 시 각 상품별로 랭킹 갱신")
    void handlePaymentCompleted_여러상품_각각랭킹갱신() {
        // Given: 2개 상품 주문 (상품1: 3개, 상품2: 5개)
        Order order = Order.create("ORDER-001", testUser, 70_000L, 0L);
        OrderItem item1 = OrderItem.create(order, testProduct1, 3, 10_000L);
        OrderItem item2 = OrderItem.create(order, testProduct2, 5, 20_000L);

        List<OrderItem> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);
        setOrderItems(order, items);

        PaymentCompletedEvent event = new PaymentCompletedEvent(order);

        // When: 이벤트 처리
        listener.handlePaymentCompleted(event);

        // Then: 각 상품별 score 증가
        verify(rankingRepository).incrementScore(eq("1"), eq(3));
        verify(rankingRepository).incrementScore(eq("2"), eq(5));
        verifyNoMoreInteractions(rankingRepository);
    }

    @Test
    @DisplayName("동일 상품 여러 번 주문 시 score 누적")
    void handlePaymentCompleted_동일상품_여러주문_score누적() {
        // Given: 동일 상품을 3번 주문 (각각 1개, 2개, 3개)
        for (int i = 1; i <= 3; i++) {
            Order order = Order.create("ORDER-00" + i, testUser, 10_000L * i, 0L);
            OrderItem item = OrderItem.create(order, testProduct1, i, 10_000L);

            List<OrderItem> items = new ArrayList<>();
            items.add(item);
            setOrderItems(order, items);

            PaymentCompletedEvent event = new PaymentCompletedEvent(order);

            // When: 이벤트 처리
            listener.handlePaymentCompleted(event);
        }

        // Then: score가 1 + 2 + 3 = 6 증가 (3번 호출)
        verify(rankingRepository, times(1)).incrementScore(eq("1"), eq(1));
        verify(rankingRepository, times(1)).incrementScore(eq("1"), eq(2));
        verify(rankingRepository, times(1)).incrementScore(eq("1"), eq(3));
    }

    @Test
    @DisplayName("Redis 장애 시 예외를 로그로만 처리 (주문 영향 없음)")
    void handlePaymentCompleted_Redis장애_예외처리() {
        // Given: Redis 장애 시뮬레이션
        Order order = Order.create("ORDER-001", testUser, 30_000L, 0L);
        OrderItem item = OrderItem.create(order, testProduct1, 3, 10_000L);

        List<OrderItem> items = new ArrayList<>();
        items.add(item);
        setOrderItems(order, items);

        PaymentCompletedEvent event = new PaymentCompletedEvent(order);

        doThrow(new RuntimeException("Redis connection failed"))
            .when(rankingRepository).incrementScore(anyString(), anyInt());

        // When: 이벤트 처리 (예외 발생)
        listener.handlePaymentCompleted(event);

        // Then: 예외가 외부로 전파되지 않음 (로그만 남김)
        verify(rankingRepository).incrementScore(eq("1"), eq(3));
    }

    @Test
    @DisplayName("빈 주문 아이템 리스트인 경우 랭킹 갱신 없음")
    void handlePaymentCompleted_빈주문아이템_랭킹갱신없음() {
        // Given: 주문 아이템이 없는 주문
        Order order = Order.create("ORDER-001", testUser, 0L, 0L);
        setOrderItems(order, new ArrayList<>());

        PaymentCompletedEvent event = new PaymentCompletedEvent(order);

        // When: 이벤트 처리
        listener.handlePaymentCompleted(event);

        // Then: Repository 호출 없음
        verifyNoInteractions(rankingRepository);
    }

    // ===== Helper Methods =====

    private void setId(Object entity, Long id) {
        try {
            java.lang.reflect.Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID", e);
        }
    }

    private void setOrderItems(Order order, List<OrderItem> items) {
        try {
            java.lang.reflect.Field field = Order.class.getDeclaredField("orderItems");
            field.setAccessible(true);
            field.set(order, items);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set orderItems", e);
        }
    }
}
