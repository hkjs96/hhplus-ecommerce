package io.hhplus.ecommerce.application.user.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceResponse;
import io.hhplus.ecommerce.application.usecase.user.ChargeBalanceIdempotencySaveService;
import io.hhplus.ecommerce.domain.user.BalanceChargedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * 잔액 충전 이벤트 핸들러
 *
 * 책임:
 * - 멱등성 키를 COMPLETED 상태로 업데이트
 * - 실패 시 재시도 큐에 적재 (향후 확장)
 *
 * 주의사항:
 * - @Transactional 어노테이션 사용 금지
 *   (RestrictedTransactionalEventListenerFactory 예외 발생)
 * - ChargeBalanceIdempotencySaveService.saveCompletedIdempotency()가 내부적으로 트랜잭션 처리
 *   (Propagation.REQUIRES_NEW)
 *
 * Phase 2 개선 효과:
 * - 멱등성 완료 처리를 비동기로 분리하여 응답 속도 개선
 * - 멱등성 저장 실패가 잔액 충전에 영향을 주지 않음
 * - 비즈니스 로직과 멱등성 관리의 결합도 감소
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BalanceChargedEventHandler {

    private final ChargeBalanceIdempotencySaveService idempotencySaveService;

    /**
     * 잔액 충전 완료 시 멱등성 완료 처리
     *
     * AFTER_COMMIT: 잔액 충전 트랜잭션이 성공적으로 커밋된 후에만 실행
     * Async: 별도 스레드에서 실행하여 응답 속도 개선
     *
     * 트랜잭션 처리:
     * - @Transactional 어노테이션 없음 (사용 시 예외 발생)
     * - ChargeBalanceIdempotencySaveService.saveCompletedIdempotency()가
     *   REQUIRES_NEW 트랜잭션으로 처리
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBalanceCharged(BalanceChargedEvent event) {
        log.info("잔액 충전 완료 - 멱등성 처리 시작: idempotencyKey={}, userId={}",
                event.getIdempotencyKey(),
                event.getChargeResponse().userId());

        try {
            idempotencySaveService.saveCompletedIdempotency(
                event.getIdempotencyKey(),
                serializeResponse(event.getChargeResponse())
            );

            log.info("멱등성 완료 처리 성공: idempotencyKey={}", event.getIdempotencyKey());

        } catch (Exception e) {
            log.error("멱등성 완료 처리 실패: idempotencyKey={}",
                    event.getIdempotencyKey(), e);
            // 실패 시 재시도 큐에 적재 (Phase 4에서 구현)
            // 잔액 충전은 성공했으므로 예외를 던지지 않음
        }
    }

    /**
     * JSON 직렬화 (응답 → JSON)
     */
    private String serializeResponse(ChargeBalanceResponse response) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("응답 직렬화 실패", e);
        }
    }
}
