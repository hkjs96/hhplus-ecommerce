package io.hhplus.ecommerce.presentation.api.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hhplus.ecommerce.application.order.dto.CreateOrderRequest;
import io.hhplus.ecommerce.application.order.dto.OrderItemRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private io.hhplus.ecommerce.domain.order.OrderRepository orderRepository;

    @Autowired
    private io.hhplus.ecommerce.domain.order.OrderItemRepository orderItemRepository;

    private Long testUserId;
    private Long testProduct1Id;
    private Long testProduct2Id;

    @BeforeEach
    void setUp() {
        // Clean up data
        if (orderRepository instanceof io.hhplus.ecommerce.infrastructure.persistence.order.InMemoryOrderRepository) {
            ((io.hhplus.ecommerce.infrastructure.persistence.order.InMemoryOrderRepository) orderRepository).clear();
        }
        if (orderItemRepository instanceof io.hhplus.ecommerce.infrastructure.persistence.order.InMemoryOrderItemRepository) {
            ((io.hhplus.ecommerce.infrastructure.persistence.order.InMemoryOrderItemRepository) orderItemRepository).clear();
        }

        User user = User.create("test@example.com", "김항해");
        user.charge(5000000L);
        User savedUser = userRepository.save(user);
        testUserId = savedUser.getId();

        Product product1 = Product.create("P001", "노트북", "고성능 게이밍 노트북", 1500000L, "전자제품", 50);
        Product product2 = Product.create("P002", "마우스", "무선 게이밍 마우스", 80000L, "전자제품", 100);
        Product savedProduct1 = productRepository.save(product1);
        Product savedProduct2 = productRepository.save(product2);
        testProduct1Id = savedProduct1.getId();
        testProduct2Id = savedProduct2.getId();
    }

    @Test
    @DisplayName("주문 생성 API - 성공")
    void createOrder_성공() throws Exception {
        // Given
        OrderItemRequest item1 = new OrderItemRequest(testProduct1Id, 1);
        OrderItemRequest item2 = new OrderItemRequest(testProduct2Id, 2);
        CreateOrderRequest request = new CreateOrderRequest(testUserId, List.of(item1, item2), null);

        // When & Then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.userId").value(testUserId))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.subtotalAmount").value(1660000L))
                .andExpect(jsonPath("$.discountAmount").value(0L))
                .andExpect(jsonPath("$.totalAmount").value(1660000L))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("주문 생성 API - 쿠폰 적용 성공")
    void createOrder_쿠폰적용_성공() throws Exception {
        // Given - Setup coupon
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create("C001", "10% 할인 쿠폰", 10, 100, now, now.plusDays(7));
        Coupon savedCoupon = couponRepository.save(coupon);
        Long couponId = savedCoupon.getId();

        UserCoupon userCoupon = UserCoupon.create(testUserId, couponId, savedCoupon.getExpiresAt());
        userCouponRepository.save(userCoupon);

        OrderItemRequest item = new OrderItemRequest(testProduct1Id, 1);
        CreateOrderRequest request = new CreateOrderRequest(testUserId, List.of(item), couponId);

        // When & Then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotalAmount").value(1500000L))
                .andExpect(jsonPath("$.discountAmount").value(150000L))
                .andExpect(jsonPath("$.totalAmount").value(1350000L));
    }

    @Test
    @DisplayName("주문 생성 API - 존재하지 않는 사용자")
    void createOrder_실패_존재하지않는사용자() throws Exception {
        // Given
        OrderItemRequest item = new OrderItemRequest(testProduct1Id, 1);
        CreateOrderRequest request = new CreateOrderRequest(99999L, List.of(item), null);

        // When & Then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("주문 생성 API - 재고 부족")
    void createOrder_실패_재고부족() throws Exception {
        // Given
        OrderItemRequest item = new OrderItemRequest(testProduct1Id, 100); // Stock is only 50
        CreateOrderRequest request = new CreateOrderRequest(testUserId, List.of(item), null);

        // When & Then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("P002"));
    }

    @Test
    @DisplayName("결제 처리 API - 성공")
    void processPayment_성공() throws Exception {
        // Given - Create order first
        OrderItemRequest item = new OrderItemRequest(testProduct1Id, 1);
        CreateOrderRequest createRequest = new CreateOrderRequest(testUserId, List.of(item), null);

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("orderId").asText();

        PaymentRequest paymentRequest = new PaymentRequest(testUserId);

        // When & Then - Process payment
        mockMvc.perform(post("/api/orders/" + orderId + "/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.paidAmount").value(1500000L))
                .andExpect(jsonPath("$.remainingBalance").value(3500000L))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paidAt").exists());

        // Verify stock decreased
        Product product = productRepository.findById(testProduct1Id).orElseThrow();
        assertThat(product.getStock()).isEqualTo(49);
    }

    @Test
    @DisplayName("결제 처리 API - 존재하지 않는 주문")
    void processPayment_실패_존재하지않는주문() throws Exception {
        // Given
        PaymentRequest paymentRequest = new PaymentRequest(testUserId);

        // When & Then
        mockMvc.perform(post("/api/orders/INVALID_ORDER_ID/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("O002"));
    }

    @Test
    @DisplayName("결제 처리 API - 잔액 부족")
    void processPayment_실패_잔액부족() throws Exception {
        // Given - Create user with low balance
        User poorUser = User.create("poor@example.com", "가난한항해");
        poorUser.charge(100000L);
        User savedPoorUser = userRepository.save(poorUser);
        Long poorUserId = savedPoorUser.getId();

        // Create order
        OrderItemRequest item = new OrderItemRequest(testProduct1Id, 1);
        CreateOrderRequest createRequest = new CreateOrderRequest(poorUserId, List.of(item), null);

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("orderId").asText();

        PaymentRequest paymentRequest = new PaymentRequest(poorUserId);

        // When & Then - Payment fails due to insufficient balance
        mockMvc.perform(post("/api/orders/" + orderId + "/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAY001"));
    }

    @Test
    @DisplayName("결제 처리 API - 이미 완료된 주문")
    void processPayment_실패_이미완료된주문() throws Exception {
        // Given - Create and pay for order
        OrderItemRequest item = new OrderItemRequest(testProduct2Id, 1);
        CreateOrderRequest createRequest = new CreateOrderRequest(testUserId, List.of(item), null);

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("orderId").asText();

        PaymentRequest paymentRequest = new PaymentRequest(testUserId);

        // First payment - success
        mockMvc.perform(post("/api/orders/" + orderId + "/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk());

        // When & Then - Second payment fails
        mockMvc.perform(post("/api/orders/" + orderId + "/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("O003"));
    }

    @Test
    @DisplayName("주문 내역 조회 API - 성공")
    void getOrders_성공() throws Exception {
        // Given - Create 2 orders
        OrderItemRequest item = new OrderItemRequest(testProduct1Id, 1);
        CreateOrderRequest createRequest = new CreateOrderRequest(testUserId, List.of(item), null);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        // When & Then
        mockMvc.perform(get("/api/orders")
                        .param("userId", String.valueOf(testUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").isArray())
                .andExpect(jsonPath("$.orders.length()").value(2))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.orders[0].userId").value(testUserId))
                .andExpect(jsonPath("$.orders[0].items").isArray())
                .andExpect(jsonPath("$.orders[0].status").exists());
    }

    @Test
    @DisplayName("주문 내역 조회 API - 상태 필터링 (PENDING)")
    void getOrders_상태필터링_PENDING() throws Exception {
        // Given - Create 2 orders, pay for 1
        OrderItemRequest item = new OrderItemRequest(testProduct2Id, 1);
        CreateOrderRequest createRequest = new CreateOrderRequest(testUserId, List.of(item), null);

        // Order 1 - Pay
        MvcResult result1 = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String orderId1 = objectMapper.readTree(result1.getResponse().getContentAsString())
                .get("orderId").asText();

        PaymentRequest paymentRequest = new PaymentRequest(testUserId);
        mockMvc.perform(post("/api/orders/" + orderId1 + "/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk());

        // Order 2 - No payment (PENDING)
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        // When & Then - Filter by PENDING
        mockMvc.perform(get("/api/orders")
                        .param("userId", String.valueOf(testUserId))
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").isArray())
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.orders[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("주문 내역 조회 API - 상태 필터링 (COMPLETED)")
    void getOrders_상태필터링_COMPLETED() throws Exception {
        // Given - Create and pay for 2 orders
        OrderItemRequest item = new OrderItemRequest(testProduct2Id, 1);
        CreateOrderRequest createRequest = new CreateOrderRequest(testUserId, List.of(item), null);

        for (int i = 0; i < 2; i++) {
            MvcResult result = mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andReturn();

            String orderId = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("orderId").asText();

            PaymentRequest paymentRequest = new PaymentRequest(testUserId);
            mockMvc.perform(post("/api/orders/" + orderId + "/payment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(paymentRequest)))
                    .andExpect(status().isOk());
        }

        // When & Then - Filter by COMPLETED
        mockMvc.perform(get("/api/orders")
                        .param("userId", String.valueOf(testUserId))
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").isArray())
                .andExpect(jsonPath("$.orders.length()").value(2))
                .andExpect(jsonPath("$.orders[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.orders[1].status").value("COMPLETED"));
    }

    @Test
    @DisplayName("주문 내역 조회 API - 주문 없음")
    void getOrders_주문없음() throws Exception {
        // Given - New user with no orders
        User newUser = User.create("new@example.com", "새로운항해");
        User savedNewUser = userRepository.save(newUser);
        Long newUserId = savedNewUser.getId();

        // When & Then
        mockMvc.perform(get("/api/orders")
                        .param("userId", String.valueOf(newUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").isArray())
                .andExpect(jsonPath("$.orders.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("주문 내역 조회 API - 존재하지 않는 사용자")
    void getOrders_실패_존재하지않는사용자() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/orders")
                        .param("userId", String.valueOf(99999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("주문 내역 조회 API - 잘못된 주문 상태")
    void getOrders_실패_잘못된상태() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/orders")
                        .param("userId", String.valueOf(testUserId))
                        .param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON002"));
    }
}
