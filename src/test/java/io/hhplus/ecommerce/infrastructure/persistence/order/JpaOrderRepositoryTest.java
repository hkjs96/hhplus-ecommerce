package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JpaOrderRepository 통합 테스트 (MySQL Testcontainers)
 *
 * @DataJpaTest:
 * - JPA 관련 컴포넌트만 로드 (경량 테스트)
 * - 각 테스트 메서드마다 트랜잭션 롤백 (테스트 격리)
 *
 * @Testcontainers:
 * - MySQL 8.0 Docker 컨테이너를 자동으로 시작/종료
 * - 실제 MySQL 환경에서 테스트 (H2가 아닌 실제 DB)
 *
 * @AutoConfigureTestDatabase(replace = NONE):
 * - Spring Boot의 기본 H2 설정을 사용하지 않고 Testcontainers MySQL 사용
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("JpaOrderRepository 통합 테스트 (MySQL Testcontainers)")
class JpaOrderRepositoryTest {

    /**
     * MySQL 8.0 Testcontainer
     *
     * - 테스트 클래스 실행 시 자동으로 MySQL 8.0 Docker 컨테이너 시작
     * - 모든 테스트가 완료되면 자동으로 컨테이너 종료
     * - 테스트 간 데이터 격리는 @Transactional로 보장 (각 테스트마다 롤백)
     */
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ecommerce_test")
            .withUsername("test")
            .withPassword("test");

    /**
     * Testcontainers의 MySQL 접속 정보를 Spring에 동적으로 주입
     *
     * - Testcontainers가 랜덤 포트로 MySQL을 시작하므로
     * - 런타임에 동적으로 접속 정보를 설정해야 함
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");  // 테스트용 스키마 자동 생성
    }

    @Autowired
    private JpaOrderRepository orderRepository;

    @Test
    @DisplayName("주문 저장 및 조회 성공")
    void saveAndFindById() {
        // Given: 주문 생성
        Order order = Order.create(
            "ORD-20250112-001",
            1L,
            100000L,
            10000L
        );

        // When: 주문 저장
        Order savedOrder = orderRepository.save(order);

        // Then: 저장된 주문 검증
        assertThat(savedOrder.getId()).isNotNull();
        assertThat(savedOrder.getOrderNumber()).isEqualTo("ORD-20250112-001");
        assertThat(savedOrder.getUserId()).isEqualTo(1L);
        assertThat(savedOrder.getSubtotalAmount()).isEqualTo(100000L);
        assertThat(savedOrder.getDiscountAmount()).isEqualTo(10000L);
        assertThat(savedOrder.getTotalAmount()).isEqualTo(90000L);
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedOrder.getCreatedAt()).isNotNull();

        // When: ID로 조회
        Optional<Order> foundOrder = orderRepository.findById(savedOrder.getId());

        // Then: 조회 성공
        assertThat(foundOrder).isPresent();
        assertThat(foundOrder.get().getOrderNumber()).isEqualTo("ORD-20250112-001");
    }

    @Test
    @DisplayName("주문 번호로 조회 성공")
    void findByOrderNumber() {
        // Given: 주문 저장
        Order order = Order.create(
            "ORD-20250112-002",
            1L,
            50000L,
            5000L
        );
        orderRepository.save(order);

        // When: 주문 번호로 조회
        Optional<Order> foundOrder = orderRepository.findByOrderNumber("ORD-20250112-002");

        // Then: 조회 성공
        assertThat(foundOrder).isPresent();
        assertThat(foundOrder.get().getUserId()).isEqualTo(1L);
        assertThat(foundOrder.get().getTotalAmount()).isEqualTo(45000L);
    }

    @Test
    @DisplayName("사용자 ID로 주문 목록 조회 성공 (최신순 정렬)")
    void findByUserId() throws InterruptedException {
        // Given: 동일 사용자의 주문 3개 생성
        Long userId = 1L;

        Order order1 = Order.create("ORD-20250112-001", userId, 10000L, 1000L);
        Order order2 = Order.create("ORD-20250112-002", userId, 20000L, 2000L);
        Order order3 = Order.create("ORD-20250112-003", userId, 30000L, 3000L);

        orderRepository.save(order1);
        Thread.sleep(10); // 시간 차이를 두기 위해
        orderRepository.save(order2);
        Thread.sleep(10);
        orderRepository.save(order3);

        // When: 사용자 ID로 조회
        List<Order> orders = orderRepository.findByUserId(userId);

        // Then: 3개 조회, 최신순 정렬
        assertThat(orders).hasSize(3);
        assertThat(orders.get(0).getOrderNumber()).isEqualTo("ORD-20250112-003"); // 최신
        assertThat(orders.get(1).getOrderNumber()).isEqualTo("ORD-20250112-002");
        assertThat(orders.get(2).getOrderNumber()).isEqualTo("ORD-20250112-001"); // 가장 오래됨
    }

    @Test
    @DisplayName("다른 사용자의 주문은 조회되지 않음")
    void findByUserId_DifferentUser() {
        // Given: 서로 다른 사용자의 주문 생성
        Order order1 = Order.create("ORD-20250112-001", 1L, 10000L, 1000L);
        Order order2 = Order.create("ORD-20250112-002", 2L, 20000L, 2000L);

        orderRepository.save(order1);
        orderRepository.save(order2);

        // When: 사용자 1의 주문 조회
        List<Order> user1Orders = orderRepository.findByUserId(1L);

        // Then: 사용자 1의 주문만 조회됨
        assertThat(user1Orders).hasSize(1);
        assertThat(user1Orders.get(0).getOrderNumber()).isEqualTo("ORD-20250112-001");
    }

    @Test
    @DisplayName("주문 상태 변경 후 저장 성공")
    void updateOrderStatus() {
        // Given: 주문 생성 및 저장
        Order order = Order.create(
            "ORD-20250112-001",
            1L,
            100000L,
            10000L
        );
        Order savedOrder = orderRepository.save(order);
        Long orderId = savedOrder.getId();

        // When: 주문 완료 처리
        savedOrder.complete();
        orderRepository.save(savedOrder);

        // Then: 상태 변경 확인
        Order updatedOrder = orderRepository.findById(orderId).get();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(updatedOrder.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("findByIdOrThrow - 존재하지 않는 주문 조회 시 예외 발생")
    void findByIdOrThrow_NotFound() {
        // Given: 존재하지 않는 주문 ID
        Long nonExistentId = 999L;

        // When & Then: 예외 발생
        org.junit.jupiter.api.Assertions.assertThrows(
            io.hhplus.ecommerce.common.exception.BusinessException.class,
            () -> orderRepository.findByIdOrThrow(nonExistentId)
        );
    }

    @Test
    @DisplayName("findByOrderNumberOrThrow - 존재하지 않는 주문 번호 조회 시 예외 발생")
    void findByOrderNumberOrThrow_NotFound() {
        // Given: 존재하지 않는 주문 번호
        String nonExistentOrderNumber = "ORD-99999999-999";

        // When & Then: 예외 발생
        org.junit.jupiter.api.Assertions.assertThrows(
            io.hhplus.ecommerce.common.exception.BusinessException.class,
            () -> orderRepository.findByOrderNumberOrThrow(nonExistentOrderNumber)
        );
    }
}
