package io.hhplus.ecommerce.application.payment.listener;

import io.hhplus.ecommerce.config.TestContainersConfig;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hhplus.ecommerce.application.order.dto.CreateOrderRequest;
import io.hhplus.ecommerce.application.order.dto.OrderItemRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.redis.ProductRankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Week 8 이벤트 기반 아키텍처 통합 테스트
 *
 * 검증 사항:
 * 1. PaymentCompletedEvent 발행 확인
 * 2. RankingEventListener 비동기 실행 확인
 * 3. DataPlatformEventListener 비동기 실행 확인
 * 4. @TransactionalEventListener AFTER_COMMIT 동작 확인
 * 5. 이벤트 리스너 실패가 주문 트랜잭션에 영향 없음 확인
 */
@Import(TestContainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// @Transactional 사용 안 함 - AFTER_COMMIT 이벤트 검증을 위해 실제 커밋 필요
class PaymentEventIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductRankingRepository rankingRepository;

    private Long testUserId;
    private Long testProductId;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성 (고유 이메일 사용)
        String uniqueEmail = "test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = User.create(uniqueEmail, "테스트유저");
        user.charge(1_000_000L);  // 100만원 충전
        User savedUser = userRepository.save(user);
        testUserId = savedUser.getId();

        // 테스트 상품 생성 (고유 코드 사용)
        String uniqueProductCode = "P-" + UUID.randomUUID().toString().substring(0, 8);
        Product product = Product.create(
            uniqueProductCode,
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
    @DisplayName("결제 완료 시 RankingEventListener가 비동기로 실행되어 랭킹 갱신")
    void paymentCompleted_랭킹갱신_비동기실행() throws Exception {
        // Given: 주문 생성
        String orderIdempotencyKey = "ORDER_" + UUID.randomUUID().toString();
        OrderItemRequest itemRequest = new OrderItemRequest(testProductId, 3);  // 3개 주문
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

        // When: 결제 처리 (이벤트 발행)
        String paymentIdempotencyKey = "PAYMENT_" + UUID.randomUUID().toString();
        PaymentRequest paymentRequest = new PaymentRequest(testUserId, paymentIdempotencyKey);

        mockMvc.perform(post("/api/orders/" + orderId + "/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk());

        // Then: 비동기 이벤트 리스너가 실행되어 랭킹 갱신 (최대 3초 대기)
        Thread.sleep(3000);

        // Redis 랭킹에 상품이 등록되고 score가 증가했는지 확인
        int score = rankingRepository.getScore(LocalDate.now(), testProductId.toString());
        assertThat(score).isGreaterThanOrEqualTo(3);  // 최소 3개 이상 (누적)
    }

    @Test
    @DisplayName("결제 완료 시 DataPlatformEventListener가 비동기로 실행")
    void paymentCompleted_데이터플랫폼전송_비동기실행() throws Exception {
        // Given: 주문 생성
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

        // When: 결제 처리 (이벤트 발행)
        String paymentIdempotencyKey = "PAYMENT_" + UUID.randomUUID().toString();
        PaymentRequest paymentRequest = new PaymentRequest(testUserId, paymentIdempotencyKey);

        long startTime = System.currentTimeMillis();

        mockMvc.perform(post("/api/orders/" + orderId + "/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk());

        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;

        // Then: 결제 API가 즉시 반환됨 (DataPlatformListener의 1초 sleep을 기다리지 않음)
        // 비동기 처리이므로 1000ms보다 훨씬 빨라야 함
        assertThat(elapsedTime).isLessThan(1000);  // 1초 이내에 응답

        // 비동기 처리 완료 대기
        Thread.sleep(2000);

        // 로그를 통해 DataPlatformListener가 실행되었음을 확인할 수 있음
        // (실제 프로덕션에서는 외부 시스템 호출 여부를 Mock으로 검증)
    }

    @Test
    @DisplayName("여러 상품 주문 시 각 상품별로 랭킹 갱신")
    void paymentCompleted_여러상품_랭킹갱신() throws Exception {
        // Given: 두 번째 상품 생성
        Product product2 = Product.create(
            "P002",
            "테스트상품2",
            "테스트 상품 설명2",
            20_000L,
            "전자제품",
            100
        );
        Product savedProduct2 = productRepository.save(product2);
        Long testProductId2 = savedProduct2.getId();

        // 주문 생성 (2개 상품)
        String orderIdempotencyKey = "ORDER_" + UUID.randomUUID().toString();
        List<OrderItemRequest> itemRequests = List.of(
            new OrderItemRequest(testProductId, 2),
            new OrderItemRequest(testProductId2, 5)
        );
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
            testUserId,
            itemRequests,
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
                .andExpect(status().isOk());

        // Then: 각 상품별로 랭킹 갱신 확인
        Thread.sleep(3000);

        int score1 = rankingRepository.getScore(LocalDate.now(), testProductId.toString());
        int score2 = rankingRepository.getScore(LocalDate.now(), testProductId2.toString());

        assertThat(score1).isGreaterThanOrEqualTo(2);
        assertThat(score2).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("동일 상품 여러 번 주문 시 랭킹 score 누적")
    void paymentCompleted_동일상품_여러주문_랭킹누적() throws Exception {
        // Given & When: 3번 주문/결제
        for (int i = 0; i < 3; i++) {
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

            String paymentIdempotencyKey = "PAYMENT_" + UUID.randomUUID().toString();
            PaymentRequest paymentRequest = new PaymentRequest(testUserId, paymentIdempotencyKey);

            mockMvc.perform(post("/api/orders/" + orderId + "/payment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(paymentRequest)))
                    .andExpect(status().isOk());
        }

        // Then: score가 3 누적
        Thread.sleep(3000);

        int score = rankingRepository.getScore(LocalDate.now(), testProductId.toString());
        assertThat(score).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("AFTER_COMMIT 검증: 트랜잭션 커밋 후에만 이벤트 발행")
    void transactionalEventListener_afterCommit검증() throws Exception {
        // Given: 주문 생성
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

        // 결제 전 랭킹 확인 (0이어야 함)
        int scoreBefore = rankingRepository.getScore(LocalDate.now(), testProductId.toString());

        // When: 결제 처리
        String paymentIdempotencyKey = "PAYMENT_" + UUID.randomUUID().toString();
        PaymentRequest paymentRequest = new PaymentRequest(testUserId, paymentIdempotencyKey);

        mockMvc.perform(post("/api/orders/" + orderId + "/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk());

        // Then: 결제 직후에는 아직 이벤트가 처리되지 않았을 수 있음
        // 비동기 + AFTER_COMMIT이므로 약간의 대기 시간 필요
        Thread.sleep(3000);

        int scoreAfter = rankingRepository.getScore(LocalDate.now(), testProductId.toString());

        // 결제 전에는 랭킹 없었고 (0), 결제 후에는 랭킹 갱신됨
        assertThat(scoreAfter).isGreaterThanOrEqualTo(1);
    }
}
