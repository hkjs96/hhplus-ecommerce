package io.hhplus.ecommerce.presentation.api.order;

import io.hhplus.ecommerce.config.TestContainersConfig;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hhplus.ecommerce.application.order.dto.CreateOrderRequest;
import io.hhplus.ecommerce.application.order.dto.OrderItemRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestContainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional  // 테스트마다 자동 롤백으로 데이터 격리
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
    private OrderRepository orderRepository;

    private Long testUserId;
    private Long testProductId;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성 (잔액 충분)
        User user = User.create("test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com", "테스트유저");
        user.charge(1_000_000L);  // 100만원 충전
        User savedUser = userRepository.save(user);
        testUserId = savedUser.getId();

        // 테스트 상품 생성
        Product product = Product.create(
            "P-" + UUID.randomUUID().toString().substring(0, 8),
            "테스트상품",
            "테스트 상품 설명",
            10_000L,
            "전자제품",
            100
        );
        Product savedProduct = productRepository.save(product);
        testProductId = savedProduct.getId();
    }

    @Test
    @DisplayName("주문 생성 API - 성공")
    void createOrder_성공() throws Exception {
        // Given
        String idempotencyKey = "ORDER_" + UUID.randomUUID().toString();
        OrderItemRequest itemRequest = new OrderItemRequest(testProductId, 2);
        CreateOrderRequest request = new CreateOrderRequest(
            testUserId,
            List.of(itemRequest),
            null,  // 쿠폰 없음
            idempotencyKey
        );

        // When & Then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.orderNumber").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(20000));  // 10,000 * 2

        // Verify: 재고 차감 확인
        Product product = productRepository.findById(testProductId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(98);  // 100 - 2
    }

    @Test
    @DisplayName("주문 생성 API - 재고 부족 실패")
    void createOrder_실패_재고부족() throws Exception {
        // Given
        String idempotencyKey = "ORDER_" + UUID.randomUUID().toString();
        OrderItemRequest itemRequest = new OrderItemRequest(testProductId, 200);  // 재고보다 많은 수량
        CreateOrderRequest request = new CreateOrderRequest(
            testUserId,
            List.of(itemRequest),
            null,
            idempotencyKey
        );

        // When & Then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("P002"));  // 재고 부족 에러
    }

    @Test
    @DisplayName("주문 생성 API - 멱등성 키로 중복 방지")
    void createOrder_멱등성_중복방지() throws Exception {
        // Given
        String idempotencyKey = "ORDER_" + UUID.randomUUID().toString();
        OrderItemRequest itemRequest = new OrderItemRequest(testProductId, 1);
        CreateOrderRequest request = new CreateOrderRequest(
            testUserId,
            List.of(itemRequest),
            null,
            idempotencyKey
        );

        // When: 첫 번째 요청
        String firstResponse = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").exists())
                .andReturn().getResponse().getContentAsString();

        // When: 동일 idempotencyKey로 두 번째 요청
        String secondResponse = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())  // 캐시된 응답 반환
                .andReturn().getResponse().getContentAsString();

        // Then: 동일한 응답 반환 (캐시된 결과)
        assertThat(firstResponse).isEqualTo(secondResponse);

        // Verify: 재고는 한 번만 차감됨
        Product product = productRepository.findById(testProductId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(99);  // 100 - 1 (한 번만 차감)
    }

    @Test
    @DisplayName("결제 처리 API - 성공")
    void processPayment_성공() throws Exception {
        // Given: 먼저 주문 생성
        String orderIdempotencyKey = "ORDER_" + UUID.randomUUID().toString();
        OrderItemRequest itemRequest = new OrderItemRequest(testProductId, 1);
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
            testUserId,
            List.of(itemRequest),
            null,
            orderIdempotencyKey
        );

        String createOrderResponse = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(createOrderResponse).get("orderId").asLong();

        // When: 결제 처리
        String paymentIdempotencyKey = "PAYMENT_" + UUID.randomUUID().toString();
        PaymentRequest paymentRequest = new PaymentRequest(testUserId, paymentIdempotencyKey);

        mockMvc.perform(post("/api/orders/" + orderId + "/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.paidAmount").value(10000));

        // Verify: 사용자 잔액 차감 확인
        User user = userRepository.findById(testUserId).orElseThrow();
        assertThat(user.getBalance()).isEqualTo(990000L);  // 1,000,000 - 10,000

        // Verify: 주문 상태 변경 확인
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(io.hhplus.ecommerce.domain.order.OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("결제 처리 API - 잔액 부족 실패")
    void processPayment_실패_잔액부족() throws Exception {
        // Given: 잔액이 부족한 사용자
        User poorUser = User.create("poor@example.com", "가난한유저");
        poorUser.charge(5000L);  // 5천원만 충전
        User savedPoorUser = userRepository.save(poorUser);

        // 주문 생성
        String orderIdempotencyKey = "ORDER_" + UUID.randomUUID().toString();
        OrderItemRequest itemRequest = new OrderItemRequest(testProductId, 1);  // 10,000원
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
            savedPoorUser.getId(),
            List.of(itemRequest),
            null,
            orderIdempotencyKey
        );

        String createOrderResponse = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(createOrderResponse).get("orderId").asLong();

        // When: 결제 시도 (잔액 부족)
        String paymentIdempotencyKey = "PAYMENT_" + UUID.randomUUID().toString();
        PaymentRequest paymentRequest = new PaymentRequest(savedPoorUser.getId(), paymentIdempotencyKey);

        // Then
        mockMvc.perform(post("/api/orders/" + orderId + "/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAY001"));  // 잔액 부족 에러
    }

    @Test
    @DisplayName("주문 목록 조회 API - 성공")
    void getOrders_성공() throws Exception {
        // Given: 주문 2개 생성
        for (int i = 0; i < 2; i++) {
            String idempotencyKey = "ORDER_" + UUID.randomUUID().toString();
            OrderItemRequest itemRequest = new OrderItemRequest(testProductId, 1);
            CreateOrderRequest request = new CreateOrderRequest(
                testUserId,
                List.of(itemRequest),
                null,
                idempotencyKey
            );

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        // When & Then
        mockMvc.perform(get("/api/orders")
                        .param("userId", testUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").isArray())
                .andExpect(jsonPath("$.orders.length()").value(2))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    @DisplayName("주문 목록 조회 API - 상태별 필터링")
    void getOrders_상태별필터링() throws Exception {
        // Given: 주문 생성 및 결제
        String orderIdempotencyKey = "ORDER_" + UUID.randomUUID().toString();
        OrderItemRequest itemRequest = new OrderItemRequest(testProductId, 1);
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
            testUserId,
            List.of(itemRequest),
            null,
            orderIdempotencyKey
        );

        String createOrderResponse = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(createOrderResponse).get("orderId").asLong();

        // 결제 완료
        String paymentIdempotencyKey = "PAYMENT_" + UUID.randomUUID().toString();
        PaymentRequest paymentRequest = new PaymentRequest(testUserId, paymentIdempotencyKey);
        mockMvc.perform(post("/api/orders/" + orderId + "/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk());

        // When & Then: COMPLETED 상태만 조회
        mockMvc.perform(get("/api/orders")
                        .param("userId", testUserId.toString())
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").isArray())
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.orders[0].status").value("COMPLETED"));
    }
}
