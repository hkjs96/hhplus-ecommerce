package io.hhplus.ecommerce.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hhplus.ecommerce.application.order.dto.CreateOrderRequest;
import io.hhplus.ecommerce.application.order.dto.OrderItemRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.infrastructure.redis.ProductRankingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OrderPayment E2E Test
 *
 * 목적: 전체 플로우 검증 (주문 생성 → 결제 → 이벤트 → 랭킹)
 * - HTTP API (MockMvc)
 * - 실제 DB (Testcontainers MySQL)
 * - 실제 Redis (Testcontainers Redis)
 * - 비동기 이벤트 처리 (@Async)
 *
 * 특징:
 * - @Sql로 고정 데이터 준비 (userId=999, productId=888)
 * - 핵심 시나리오 1개만 검증
 * - 전체 스택 통합 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@Sql(scripts = "/test-data-e2e.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/cleanup-e2e.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class OrderPaymentE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRankingRepository rankingRepository;

    @Test
    @DisplayName("E2E: 주문 생성 → 결제 → 랭킹 갱신 전체 플로우")
    void 전체플로우_주문생성_결제_랭킹갱신() throws Exception {
        // Given: SQL로 고정 데이터 준비 (userId=999, productId=888)

        // When 1: 주문 생성 API 호출
        CreateOrderRequest orderRequest = new CreateOrderRequest(
            999L,  // 고정 사용자 ID
            List.of(new OrderItemRequest(888L, 3)),  // 고정 상품 ID, 3개 주문
            null,
            "E2E-ORDER-" + UUID.randomUUID()
        );

        String orderResponse = mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").exists())
            .andExpect(jsonPath("$.totalAmount").value(30000))
            .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(orderResponse).get("orderId").asLong();

        // When 2: 결제 API 호출
        PaymentRequest paymentRequest = new PaymentRequest(
            999L,
            "E2E-PAYMENT-" + UUID.randomUUID()
        );

        mockMvc.perform(post("/api/orders/" + orderId + "/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paidAmount").value(30000));

        // Then: 비동기 이벤트 처리 대기 (랭킹 갱신) - Awaitility로 상태 기반 검증
        LocalDate today = LocalDate.now();
        await().atMost(5, SECONDS)
            .pollInterval(200, MILLISECONDS)
            .untilAsserted(() -> {
                int score = rankingRepository.getScore(today, "888");
                assertThat(score).isGreaterThanOrEqualTo(3);
            });
    }

    @Test
    @DisplayName("E2E: 잔액 부족 시 결제 실패")
    void 전체플로우_잔액부족_결제실패() throws Exception {
        // Given: 사용자 잔액 100만원, 200만원 주문 시도

        // When 1: 고액 주문 생성 (잔액 초과하도록 200개 주문)
        CreateOrderRequest orderRequest = new CreateOrderRequest(
            999L,
            List.of(new OrderItemRequest(888L, 150)),  // 150개 = 1,500,000원 (잔액 1,000,000원 초과)
            null,
            "E2E-ORDER-" + UUID.randomUUID()
        );

        String orderResponse = mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(orderResponse).get("orderId").asLong();

        // When 2: 결제 시도 (잔액 부족)
        PaymentRequest paymentRequest = new PaymentRequest(
            999L,
            "E2E-PAYMENT-" + UUID.randomUUID()
        );

        // Then: 400 Bad Request (잔액 부족)
        mockMvc.perform(post("/api/orders/" + orderId + "/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("E2E: 재고 부족 시 주문 생성 실패")
    void 전체플로우_재고부족_주문실패() throws Exception {
        // Given: 상품 재고 100개

        // When: 200개 주문 시도
        CreateOrderRequest orderRequest = new CreateOrderRequest(
            999L,
            List.of(new OrderItemRequest(888L, 600)),  // 재고보다 많은 수량 (테스트 데이터: 재고 500)
            null,
            "E2E-ORDER-" + UUID.randomUUID()
        );

        // Then: 400 Bad Request (재고 부족)
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("재고가 부족합니다")));
    }
}
