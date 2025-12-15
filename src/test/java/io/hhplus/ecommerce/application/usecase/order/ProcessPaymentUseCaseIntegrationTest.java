package io.hhplus.ecommerce.application.usecase.order;

import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

/**
 * ProcessPaymentUseCase Integration Test
 *
 * 목적: UseCase 비즈니스 로직 + Repository(DB) 연동 검증
 * - 결제 처리 성공 시 PaymentCompletedEvent 발행
 * - 잔액 차감 및 재고 차감 검증
 * - 멱등성 키 중복 처리 검증
 *
 * 특징:
 * - @Transactional: 각 테스트 후 자동 롤백
 * - @MockBean ApplicationEventPublisher: 이벤트 리스너 실행 스킵
 * - 실제 DB 연동하여 비즈니스 로직 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@Transactional
class ProcessPaymentUseCaseIntegrationTest {

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

    @MockBean  // ← 이벤트 리스너 실행 스킵
    private ApplicationEventPublisher eventPublisher;

    private User testUser;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성
        testUser = userRepository.save(User.create("test-payment@example.com", "테스트유저"));
        testUser.charge(1_000_000L);  // 100만원 충전

        // 테스트 상품 생성
        testProduct = productRepository.save(
            Product.create("PAY-P001", "테스트상품", "설명", 10_000L, "전자제품", 100)
        );

        // @Transactional이 자동으로 관리하므로 flush() 불필요
    }

    @Test
    @DisplayName("결제 처리 성공 시 PaymentCompletedEvent 발행")
    void execute_결제성공_이벤트발행() {
        // Given: 주문 생성
        Order createdOrder = Order.create("ORDER-001", testUser, 30_000L, 0L);
        final Order savedOrder = orderRepository.save(createdOrder);

        OrderItem item = OrderItem.create(savedOrder, testProduct, 3, 10_000L);
        orderItemRepository.save(item);

        PaymentRequest request = new PaymentRequest(
            testUser.getId(),
            "PAYMENT-" + UUID.randomUUID()
        );

        // When: 결제 처리
        PaymentResponse response = processPaymentUseCase.execute(savedOrder.getId(), request);

        // Then: 결제 성공
        assertThat(response).isNotNull();
        assertThat(response.paidAmount()).isEqualTo(30_000L);

        // Then: PaymentCompletedEvent 발행 검증
        verify(eventPublisher).publishEvent(
            argThat((Object event) ->
                event instanceof PaymentCompletedEvent &&
                ((PaymentCompletedEvent) event).getOrder().getId().equals(savedOrder.getId())
            )
        );
    }

    @Test
    @DisplayName("결제 처리 시 사용자 잔액 차감")
    void execute_잔액차감() {
        // Given: 주문 생성
        Order order = Order.create("ORDER-001", testUser, 30_000L, 0L);
        order = orderRepository.save(order);

        OrderItem item = OrderItem.create(order, testProduct, 3, 10_000L);
        orderItemRepository.save(item);


        PaymentRequest request = new PaymentRequest(
            testUser.getId(),
            "PAYMENT-" + UUID.randomUUID()
        );

        Long balanceBefore = testUser.getBalance();

        // When: 결제 처리
        processPaymentUseCase.execute(order.getId(), request);

        // Then: 잔액 차감 확인
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getBalance()).isEqualTo(balanceBefore - 30_000L);
    }

    @Test
    @DisplayName("결제 처리 시 상품 재고 차감")
    void execute_재고차감() {
        // Given: 주문 생성
        Order order = Order.create("ORDER-001", testUser, 30_000L, 0L);
        order = orderRepository.save(order);

        OrderItem item = OrderItem.create(order, testProduct, 3, 10_000L);
        orderItemRepository.save(item);


        PaymentRequest request = new PaymentRequest(
            testUser.getId(),
            "PAYMENT-" + UUID.randomUUID()
        );

        int stockBefore = testProduct.getStock();

        // When: 결제 처리
        processPaymentUseCase.execute(order.getId(), request);

        // Then: 재고 차감 확인
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(stockBefore - 3);
    }

    @Test
    @DisplayName("동일 멱등성 키로 중복 결제 요청 시 기존 결과 반환")
    void execute_멱등성키중복_기존결과반환() {
        // Given: 주문 생성
        Order order = Order.create("ORDER-001", testUser, 30_000L, 0L);
        order = orderRepository.save(order);

        OrderItem item = OrderItem.create(order, testProduct, 3, 10_000L);
        orderItemRepository.save(item);


        String idempotencyKey = "PAYMENT-" + UUID.randomUUID();
        PaymentRequest request = new PaymentRequest(testUser.getId(), idempotencyKey);

        // When: 첫 번째 결제 처리
        PaymentResponse firstResponse = processPaymentUseCase.execute(order.getId(), request);

        // When: 동일 멱등성 키로 두 번째 요청
        PaymentRequest duplicateRequest = new PaymentRequest(testUser.getId(), idempotencyKey);
        PaymentResponse secondResponse = processPaymentUseCase.execute(order.getId(), duplicateRequest);

        // Then: 동일한 응답 반환
        assertThat(secondResponse.paidAmount()).isEqualTo(firstResponse.paidAmount());

        // Then: 이벤트는 1번만 발행
        verify(eventPublisher).publishEvent(any(PaymentCompletedEvent.class));
    }

    @Test
    @DisplayName("여러 상품 주문 시 각 상품별 재고 차감")
    void execute_여러상품_재고차감() {
        // Given: 두 번째 상품 추가
        Product product2 = productRepository.save(
            Product.create("P002", "상품2", "설명2", 20_000L, "전자제품", 100)
        );

        // Given: 2개 상품 주문
        Order order = Order.create("ORDER-001", testUser, 50_000L, 0L);
        order = orderRepository.save(order);

        OrderItem item1 = OrderItem.create(order, testProduct, 2, 10_000L);
        OrderItem item2 = OrderItem.create(order, product2, 5, 20_000L);
        orderItemRepository.save(item1);
        orderItemRepository.save(item2);


        PaymentRequest request = new PaymentRequest(
            testUser.getId(),
            "PAYMENT-" + UUID.randomUUID()
        );

        int stock1Before = testProduct.getStock();
        int stock2Before = product2.getStock();

        // When: 결제 처리
        processPaymentUseCase.execute(order.getId(), request);

        // Then: 각 상품별 재고 차감
        Product updatedProduct1 = productRepository.findById(testProduct.getId()).orElseThrow();
        Product updatedProduct2 = productRepository.findById(product2.getId()).orElseThrow();

        assertThat(updatedProduct1.getStock()).isEqualTo(stock1Before - 2);
        assertThat(updatedProduct2.getStock()).isEqualTo(stock2Before - 5);
    }
}
