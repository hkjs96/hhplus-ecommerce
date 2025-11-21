package io.hhplus.ecommerce.infrastructure.external;

import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Mock PG 서비스 구현
 * <p>
 * 실제 외부 API가 없는 환경에서 PG 동작을 시뮬레이션합니다.
 * <p>
 * 시뮬레이션 규칙:
 * - Idempotency Key에 "FAIL" 포함: 실패 응답 (테스트용)
 * - 그 외: 성공 응답
 * <p>
 * 실제 PG 연동 시:
 * - TossPaymentsServiceImpl로 교체
 * - RestTemplate 또는 WebClient로 HTTP API 호출
 * - 타임아웃 설정 (권장: 5초)
 * - 재시도 정책 (권장: 3회, Exponential backoff)
 * <p>
 * 참고: @Profile("!prod")로 운영 환경에서는 비활성화 권장
 */
@Slf4j
@Service
@Profile("!prod")  // 운영 환경에서는 실제 PG Service 사용
public class MockPGServiceImpl implements PGService {

    /**
     * Mock 결제 승인
     * <p>
     * 실제 PG API를 호출하지 않고 즉시 응답합니다.
     * - 성공: Idempotency Key에 "FAIL"이 없는 경우
     * - 실패: Idempotency Key에 "FAIL"이 포함된 경우 (테스트용)
     *
     * @param request 결제 요청
     * @return Mock PG 응답
     */
    @Override
    public PGResponse charge(PaymentRequest request) {
        log.info("=== Mock PG Service: Charge Request ===");
        log.info("User ID: {}", request.userId());
        log.info("Idempotency Key: {}", request.idempotencyKey());

        // TODO: 실제 PG API 연동 시 아래 코드로 교체
        // - RestTemplate 또는 WebClient 사용
        // - 타임아웃 설정: 5초
        // - 재시도: 3회, Exponential backoff
        //
        // Example (토스페이먼츠):
        // String url = "https://api.tosspayments.com/v1/payments/confirm";
        // HttpHeaders headers = new HttpHeaders();
        // headers.set("Authorization", "Basic " + encodedSecretKey);
        // headers.setContentType(MediaType.APPLICATION_JSON);
        //
        // Map<String, Object> body = Map.of(
        //     "orderId", request.orderId(),
        //     "amount", request.amount(),
        //     "paymentKey", request.paymentKey()
        // );
        //
        // HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        // ResponseEntity<TossPaymentsResponse> response = restTemplate.postForEntity(
        //     url, entity, TossPaymentsResponse.class
        // );
        //
        // if (response.getStatusCode().is2xxSuccessful()) {
        //     return PGResponse.success(response.getBody().getTransactionId());
        // } else {
        //     return PGResponse.failure(response.getBody().getMessage());
        // }

        // Mock 응답 생성 (실제 API 없음)
        // Idempotency Key에 "FAIL"이 포함되면 실패 시뮬레이션 (테스트용)
        boolean isTestFailure = request.idempotencyKey().toUpperCase().contains("FAIL");

        if (isTestFailure) {
            // 실패 시나리오 (테스트용)
            log.warn("Mock PG: Simulating failure for idempotencyKey containing 'FAIL'");
            return PGResponse.failure("PG 승인 실패: 잔액 부족 (Mock)");
        }

        // 성공 시나리오
        String mockTransactionId = "MOCK_TX_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Mock PG: Approval SUCCESS - txId={}", mockTransactionId);
        log.info("=== Mock PG Service: Charge Response ===");

        return PGResponse.success(mockTransactionId);
    }
}
