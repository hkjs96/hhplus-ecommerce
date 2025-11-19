package io.hhplus.ecommerce.infrastructure.external;

import io.hhplus.ecommerce.domain.payment.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 외부 데이터 전송 서비스
 * <p>
 * DB 트랜잭션 커밋 후 외부 API 호출
 * - @TransactionalEventListener(phase = AFTER_COMMIT): 트랜잭션 커밋 후 실행
 * - 외부 API 실패해도 결제 트랜잭션은 롤백되지 않음
 * - 실패 시 재시도 로직 또는 보상 트랜잭션 구현 가능 (추후)
 * <p>
 * 동시성 제어: 외부 API는 트랜잭션 밖에서 실행
 * - 5명 관점: 김데이터(O), 박트래픽(△:Async), 이금융(△:보상TX), 최아키텍트(O), 정스타트업(O)
 * - 최종 선택: 동기 처리 (AFTER_COMMIT)
 *   · 트랜잭션 분리로 DB Lock 시간 최소화
 *   · 외부 API 실패 시 결제는 완료 상태 유지
 *   · 향후 @Async + 메시지 큐로 확장 가능
 * <p>
 * 참고: 비동기 처리는 @Async + @EnableAsync로 확장 가능
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalDataTransmissionService {

    /**
     * 결제 완료 후 외부 데이터 전송
     * <p>
     * TransactionPhase.AFTER_COMMIT: DB 트랜잭션 커밋 후 실행
     * - 결제 트랜잭션과 분리되어 외부 API 실패 시에도 결제는 완료 상태
     * - 외부 API: 데이터 플랫폼 전송 (현재는 로그만 출력)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("=== External Data Transmission START ===");
        log.info("Transaction committed. Now calling external API...");
        log.info("Order ID: {}", event.getOrderId());
        log.info("User ID: {}", event.getUserId());
        log.info("Paid Amount: {}", event.getPaidAmount());
        log.info("Paid At: {}", event.getPaidAt());

        try {
            // TODO: 실제 외부 API 호출 구현
            // - RestTemplate, WebClient, Feign Client 등 사용
            // - 데이터 플랫폼, 분석 시스템, 알림 서비스 등
            // - 예시:
            //   restTemplate.postForEntity(
            //       "https://data-platform.example.com/api/payments",
            //       PaymentDataDto.from(event),
            //       Void.class
            //   );

            // 현재는 성공으로 간주
            log.info("External API call SUCCESS (mocked)");
            log.info("=== External Data Transmission END (SUCCESS) ===");

        } catch (Exception e) {
            // 외부 API 실패해도 결제는 이미 완료 상태
            // 재시도 로직, Dead Letter Queue, 보상 트랜잭션 등 구현 가능
            log.error("External API call FAILED. Payment is still completed. orderId: {}, error: {}",
                event.getOrderId(), e.getMessage(), e);
            log.error("=== External Data Transmission END (FAILED) ===");

            // TODO: 실패 처리 전략
            // 1. 재시도 (Retry with exponential backoff)
            // 2. Dead Letter Queue (메시지 큐에 저장)
            // 3. 보상 트랜잭션 (실패 이력 저장, 관리자 알림)
            // 4. Circuit Breaker (외부 API 장애 대응)
        }
    }
}
