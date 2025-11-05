package io.hhplus.ecommerce.application.order;

import io.hhplus.ecommerce.application.order.dto.*;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.persistence.coupon.InMemoryCouponRepository;
import io.hhplus.ecommerce.infrastructure.persistence.coupon.InMemoryUserCouponRepository;
import io.hhplus.ecommerce.infrastructure.persistence.order.InMemoryOrderItemRepository;
import io.hhplus.ecommerce.infrastructure.persistence.order.InMemoryOrderRepository;
import io.hhplus.ecommerce.infrastructure.persistence.product.InMemoryProductRepository;
import io.hhplus.ecommerce.infrastructure.persistence.user.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private CouponRepository couponRepository;
    private UserCouponRepository userCouponRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        orderItemRepository = new InMemoryOrderItemRepository();
        productRepository = new InMemoryProductRepository();
        userRepository = new InMemoryUserRepository();
        couponRepository = new InMemoryCouponRepository();
        userCouponRepository = new InMemoryUserCouponRepository();

        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                productRepository,
                userRepository,
                couponRepository,
                userCouponRepository
        );
    }

    @Test
    @DisplayName("주문 생성 성공")
    void createOrder_성공() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");
        user.charge(1000000L);
        userRepository.save(user);

        Product product = Product.create("P001", "노트북", "고성능", 890000L, "전자제품", 10);
        productRepository.save(product);

        OrderItemRequest itemRequest = new OrderItemRequest("P001", 2);
        CreateOrderRequest request = new CreateOrderRequest(userId, List.of(itemRequest), null);

        // When
        CreateOrderResponse response = orderService.createOrder(request);

        // Then
        assertThat(response.getOrderId()).isNotNull();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getSubtotalAmount()).isEqualTo(1780000L);
        assertThat(response.getDiscountAmount()).isEqualTo(0L);
        assertThat(response.getTotalAmount()).isEqualTo(1780000L);
        assertThat(response.getStatus()).isEqualTo("PENDING");

        Product savedProduct = productRepository.findById("P001").orElseThrow();
        assertThat(savedProduct.getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("주문 생성 실패 - 존재하지 않는 사용자")
    void createOrder_실패_존재하지않는사용자() {
        // Given
        String userId = "INVALID";
        OrderItemRequest itemRequest = new OrderItemRequest("P001", 2);
        CreateOrderRequest request = new CreateOrderRequest(userId, List.of(itemRequest), null);

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("주문 생성 실패 - 존재하지 않는 상품")
    void createOrder_실패_존재하지않는상품() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");
        userRepository.save(user);

        OrderItemRequest itemRequest = new OrderItemRequest("INVALID", 2);
        CreateOrderRequest request = new CreateOrderRequest(userId, List.of(itemRequest), null);

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("주문 생성 실패 - 재고 부족")
    void createOrder_실패_재고부족() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");
        userRepository.save(user);

        Product product = Product.create("P001", "노트북", "고성능", 890000L, "전자제품", 5);
        productRepository.save(product);

        OrderItemRequest itemRequest = new OrderItemRequest("P001", 10);
        CreateOrderRequest request = new CreateOrderRequest(userId, List.of(itemRequest), null);

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);
    }

    @Test
    @DisplayName("주문 생성 성공 - 쿠폰 적용")
    void createOrder_성공_쿠폰적용() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");
        user.charge(1000000L);
        userRepository.save(user);

        Product product = Product.create("P001", "노트북", "고성능", 890000L, "전자제품", 10);
        productRepository.save(product);

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create("C001", "10% 할인", 10, 100, now, now.plusDays(7));
        couponRepository.save(coupon);

        UserCoupon userCoupon = UserCoupon.create("UC001", userId, "C001", coupon.getExpiresAt());
        userCouponRepository.save(userCoupon);

        OrderItemRequest itemRequest = new OrderItemRequest("P001", 2);
        CreateOrderRequest request = new CreateOrderRequest(userId, List.of(itemRequest), "C001");

        // When
        CreateOrderResponse response = orderService.createOrder(request);

        // Then
        assertThat(response.getSubtotalAmount()).isEqualTo(1780000L);
        assertThat(response.getDiscountAmount()).isEqualTo(178000L);
        assertThat(response.getTotalAmount()).isEqualTo(1602000L);
    }

    @Test
    @DisplayName("결제 처리 성공")
    void processPayment_성공() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");
        user.charge(2000000L);
        userRepository.save(user);

        Product product = Product.create("P001", "노트북", "고성능", 890000L, "전자제품", 10);
        productRepository.save(product);

        OrderItemRequest itemRequest = new OrderItemRequest("P001", 2);
        CreateOrderRequest createRequest = new CreateOrderRequest(userId, List.of(itemRequest), null);
        CreateOrderResponse createResponse = orderService.createOrder(createRequest);

        PaymentRequest paymentRequest = new PaymentRequest(userId);

        // When
        PaymentResponse response = orderService.processPayment(createResponse.getOrderId(), paymentRequest);

        // Then
        assertThat(response.getOrderId()).isEqualTo(createResponse.getOrderId());
        assertThat(response.getPaidAmount()).isEqualTo(1780000L);
        assertThat(response.getRemainingBalance()).isEqualTo(220000L);
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getPaidAt()).isNotNull();

        User savedUser = userRepository.findById(userId).orElseThrow();
        assertThat(savedUser.getBalance()).isEqualTo(220000L);

        Product savedProduct = productRepository.findById("P001").orElseThrow();
        assertThat(savedProduct.getStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("결제 처리 실패 - 존재하지 않는 주문")
    void processPayment_실패_존재하지않는주문() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");
        userRepository.save(user);

        PaymentRequest paymentRequest = new PaymentRequest(userId);

        // When & Then
        assertThatThrownBy(() -> orderService.processPayment("INVALID", paymentRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("결제 처리 실패 - 잔액 부족")
    void processPayment_실패_잔액부족() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");
        user.charge(500000L);
        userRepository.save(user);

        Product product = Product.create("P001", "노트북", "고성능", 890000L, "전자제품", 10);
        productRepository.save(product);

        OrderItemRequest itemRequest = new OrderItemRequest("P001", 2);
        CreateOrderRequest createRequest = new CreateOrderRequest(userId, List.of(itemRequest), null);
        CreateOrderResponse createResponse = orderService.createOrder(createRequest);

        PaymentRequest paymentRequest = new PaymentRequest(userId);

        // When & Then
        assertThatThrownBy(() -> orderService.processPayment(createResponse.getOrderId(), paymentRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("결제 처리 실패 - 이미 완료된 주문")
    void processPayment_실패_이미완료된주문() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");
        user.charge(2000000L);
        userRepository.save(user);

        Product product = Product.create("P001", "노트북", "고성능", 890000L, "전자제품", 10);
        productRepository.save(product);

        OrderItemRequest itemRequest = new OrderItemRequest("P001", 2);
        CreateOrderRequest createRequest = new CreateOrderRequest(userId, List.of(itemRequest), null);
        CreateOrderResponse createResponse = orderService.createOrder(createRequest);

        PaymentRequest paymentRequest = new PaymentRequest(userId);
        orderService.processPayment(createResponse.getOrderId(), paymentRequest);

        // When & Then
        assertThatThrownBy(() -> orderService.processPayment(createResponse.getOrderId(), paymentRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS);
    }
}
