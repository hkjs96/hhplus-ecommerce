package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderStatus;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("JpaOrderRepository 통합 테스트 (MySQL Testcontainers)")
class JpaOrderRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ecommerce_test")
            .withUsername("test")
            .withPassword("test");

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
    @Autowired
    private UserRepository userRepository; // Added to save User
    @Autowired
    private EntityManager entityManager; // Added for flushing

    private User testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        testUser = User.createForTest(1L, "test@example.com", "testuser", 0L);
        userRepository.save(testUser);
        entityManager.flush(); // Ensure ID is populated
        entityManager.clear(); // Detach user to avoid conflicts in subsequent operations
    }

    @Test
    @DisplayName("주문 저장 및 조회 성공")
    void saveAndFindById() {
        // Given: 주문 생성
        Order order = Order.create(
            "ORD-20250112-001",
            testUser,
            100000L,
            10000L
        );

        // When: 주문 저장
        Order savedOrder = orderRepository.save(order);

        // Then: 저장된 주문 검증
        assertThat(savedOrder.getId()).isNotNull();
        assertThat(savedOrder.getOrderNumber()).isEqualTo("ORD-20250112-001");
        assertThat(savedOrder.getUserId()).isEqualTo(testUser.getId());
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
            testUser,
            50000L,
            5000L
        );
        orderRepository.save(order);

        // When: 주문 번호로 조회
        Optional<Order> foundOrder = orderRepository.findByOrderNumber("ORD-20250112-002");

        // Then: 조회 성공
        assertThat(foundOrder).isPresent();
        assertThat(foundOrder.get().getUserId()).isEqualTo(testUser.getId());
        assertThat(foundOrder.get().getTotalAmount()).isEqualTo(45000L);
    }

    @Test
    @DisplayName("사용자 ID로 주문 목록 조회 성공 (최신순 정렬)")
    void findByUserId() throws InterruptedException {
        // Given: 동일 사용자의 주문 3개 생성
        User user = userRepository.findById(testUser.getId()).orElseThrow(); // Re-fetch user in new session
        
        Order order1 = Order.create("ORD-20250112-001", user, 10000L, 1000L);
        Order order2 = Order.create("ORD-20250112-002", user, 20000L, 2000L);
        Order order3 = Order.create("ORD-20250112-003", user, 30000L, 3000L);

        orderRepository.save(order1);
        Thread.sleep(10); // 시간 차이를 두기 위해
        orderRepository.save(order2);
        Thread.sleep(10);
        orderRepository.save(order3);

        // When: 사용자 ID로 조회
        List<Order> orders = orderRepository.findByUserId(testUser.getId());

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
        User otherUser = User.createForTest(2L, "other@example.com", "otheruser", 0L);
        userRepository.save(otherUser);
        entityManager.flush();

        Order order1 = Order.create("ORD-20250112-001", testUser, 10000L, 1000L);
        Order order2 = Order.create("ORD-20250112-002", otherUser, 20000L, 2000L);

        orderRepository.save(order1);
        orderRepository.save(order2);

        // When: 사용자 1의 주문 조회
        List<Order> user1Orders = orderRepository.findByUserId(testUser.getId());

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
            testUser,
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