package io.hhplus.ecommerce.performance;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.infrastructure.persistence.order.JpaOrderItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * N+1 문제 검증 테스트
 *
 * 이 테스트의 목적:
 * 1. 양방향 연관관계가 제대로 설정되었는지 확인
 * 2. Batch Size가 동작하는지 확인
 * 3. 실제 발생하는 SQL 쿼리 개수 확인
 *
 * 실행 방법:
 * - application.yml에서 show-sql: true 설정 (이미 되어있음)
 * - 로그 레벨: org.hibernate.SQL: DEBUG
 * - 테스트 실행 후 콘솔에 출력되는 SQL 쿼리 개수 확인
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class N1ProblemVerificationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JpaOrderItemRepository orderItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @Transactional
    @DisplayName("N+1 문제 검증: Order 조회 시 OrderItem이 Batch로 로딩되는지 확인")
    void verifyBatchFetchingForOrderItems() {
        // Given: 여러 개의 Order와 OrderItem이 있을 때
        log.info("========== 테스트 시작 ==========");
        log.info("Step 1: 모든 Order 조회 (첫 번째 쿼리)");

        List<Order> orders = orderRepository.findAll();
        log.info("조회된 Order 개수: {}", orders.size());

        log.info("\nStep 2: 각 Order의 OrderItem 접근 (Batch Fetch 확인)");
        log.info("⚠️ 콘솔에서 SQL 쿼리 개수를 세어보세요!");
        log.info("- N+1 문제가 있으면: 1 (Order 조회) + N (각 Order마다 OrderItem 조회) = 1+N개 쿼리");
        log.info("- Batch Fetch가 동작하면: 1 (Order 조회) + 적은 수의 Batch 쿼리");
        log.info("------------------------------------------------------");

        // When: 각 Order의 OrderItem에 접근
        for (Order order : orders) {
            List<OrderItem> items = order.getOrderItems();
            log.info("Order ID: {}, OrderItem 개수: {}", order.getId(), items.size());

            // OrderItem의 Product에도 접근 (추가 Lazy Loading)
            for (OrderItem item : items) {
                Product product = item.getProduct();
                log.info("  - Product: {} (ID: {})", product.getName(), product.getId());
            }
        }

        log.info("========== 테스트 종료 ==========\n");
        log.info("📊 결과 분석:");
        log.info("1. 위 로그에서 'select' 키워드가 나온 횟수를 세어보세요");
        log.info("2. Order 개수가 10개인데 SELECT 쿼리가 11개(1 + 10)면 N+1 문제 존재");
        log.info("3. SELECT 쿼리가 2~3개 정도면 Batch Fetch 성공!");
        log.info("   (1: Order 조회, 1: OrderItem Batch 조회, 1: Product Batch 조회)");
    }

    @Test
    @Transactional
    @DisplayName("N+1 해결 전후 비교: OrderItem 조회 시 쿼리 개수 확인")
    void compareQueryCountBeforeAndAfter() {
        log.info("========== OrderItem 조회 패턴 비교 ==========");

        // 패턴 1: ID로 개별 조회 (기존 방식 - 간접 참조)
        log.info("\n[기존 방식] OrderItem을 ID로 개별 조회");
        List<OrderItem> items = orderItemRepository.findAll();
        log.info("조회된 OrderItem 개수: {}", items.size());

        log.info("\n각 OrderItem에서 Product 정보 가져오기:");
        for (OrderItem item : items) {
            // getProduct()는 이제 Entity를 반환 (Lazy Loading)
            Product product = item.getProduct();
            log.info("OrderItem ID: {}, Product: {} (재고: {})",
                item.getId(), product.getName(), product.getStock());
        }

        log.info("\n📊 위 로그에서 SELECT 쿼리가 몇 개 발생했나요?");
        log.info("- Batch Size 100 설정으로 인해 Product는 한 번에 최대 100개씩 로딩됩니다");
    }

    @Test
    @DisplayName("실전 시나리오: 사용자의 주문 목록 조회")
    void realWorldScenario_getUserOrders() {
        log.info("========== 실전 시나리오: 사용자 주문 조회 ==========");

        // Given: 특정 사용자의 주문 조회
        Long userId = 1L;

        log.info("\nStep 1: User의 모든 Order 조회");
        List<Order> userOrders = orderRepository.findByUserId(userId);
        log.info("사용자 {}의 주문 개수: {}", userId, userOrders.size());

        log.info("\nStep 2: 각 주문의 상세 정보 출력 (OrderItem + Product)");
        for (Order order : userOrders) {
            log.info("\n주문번호: {}, 총액: {}원", order.getOrderNumber(), order.getTotalAmount());

            for (OrderItem item : order.getOrderItems()) {
                log.info("  - {} x {} = {}원",
                    item.getProduct().getName(),
                    item.getQuantity(),
                    item.getSubtotal());
            }
        }

        log.info("\n========== 쿼리 분석 ==========");
        log.info("✅ 양방향 연관관계 + Batch Fetch가 동작하면:");
        log.info("   1. SELECT orders WHERE user_id = ? (1번)");
        log.info("   2. SELECT order_items WHERE order_id IN (?, ?, ...) (1번, Batch)");
        log.info("   3. SELECT products WHERE id IN (?, ?, ...) (1번, Batch)");
        log.info("   총 3개의 쿼리로 모든 데이터 로딩!");

        log.info("\n❌ N+1 문제가 있으면:");
        log.info("   주문 10개 × 각 주문마다 OrderItem 조회 = 11개 쿼리");
        log.info("   + 각 OrderItem마다 Product 조회 = 수십 개 추가 쿼리");
    }
}
