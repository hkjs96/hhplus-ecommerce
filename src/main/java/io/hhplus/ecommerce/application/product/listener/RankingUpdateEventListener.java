package io.hhplus.ecommerce.application.product.listener;

import io.hhplus.ecommerce.domain.event.FailedEvent;
import io.hhplus.ecommerce.domain.event.FailedEventRepository;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.PaymentCompletedEvent;
import io.hhplus.ecommerce.infrastructure.redis.ProductRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.HashMap;
import java.util.Map;

/**
 * 랭킹 갱신 전용 리스너
 *
 * 책임: Redis Sorted Set 랭킹 갱신만 담당 (Single Responsibility)
 * - 주문 완료 시 상품별 판매량 score 증가
 *
 * 비동기 처리:
 * - @Async: 별도 스레드에서 실행
 * - Redis 장애가 주문 트랜잭션에 영향 없음
 *
 * 재시도 메커니즘 (Phase 2 완료):
 * - @Retryable: Redis 일시적 장애 시 자동 재시도
 * - Exponential Backoff: 1초 → 2초 → 4초
 * - 최대 3회 재시도
 * - 재시도 실패 시: DLQ (FailedEvent)에 저장
 *
 * 8주차 코치 피드백 반영:
 * - "예외를 던져야 재시도 작동, 로그만 남기고 잡아먹으면 무력화" ✅
 * - "리스너는 가벼움 (응집도 ↑)" ✅
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RankingUpdateEventListener {

    private final ProductRankingRepository rankingRepository;
    private final FailedEventRepository failedEventRepository;
    private final ObjectMapper objectMapper;
    private final io.hhplus.ecommerce.infrastructure.redis.EventIdempotencyService idempotencyService;

    /**
     * 랭킹 갱신 처리 (비동기 + 재시도)
     *
     * @param event PaymentCompletedEvent
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("rankingExecutor")  // 전용 executor
    @Retryable(
        retryFor = {RedisConnectionFailureException.class, QueryTimeoutException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void updateRanking(PaymentCompletedEvent event) {
        String eventType = "PaymentCompleted";
        String eventId = "order-" + event.getOrder().getId();

        try {
            // 1. 멱등성 선점: SET NX로 원자적으로 처리 여부 결정
            // EventIdempotencyListener와 독립적으로 동작 (@Async이므로 다른 스레드)
            boolean isFirstProcessing = idempotencyService.markAsProcessed(eventType, eventId);

            // 2. 중복 이벤트면 중단 (사이드이펙트 실행 안 함)
            if (!isFirstProcessing) {
                log.info("랭킹 갱신 중복 처리 방지 (멱등성 선점 실패): orderId={}", event.getOrder().getId());
                return;
            }

            // 3. 핵심 로직: 랭킹 갱신 (최초 처리인 경우에만)
            for (OrderItem item : event.getOrder().getOrderItems()) {
                rankingRepository.incrementScore(
                    item.getProduct().getId().toString(),
                    item.getQuantity()
                );
            }

            log.info("랭킹 갱신 완료: orderId={}, itemCount={}",
                event.getOrder().getId(),
                event.getOrder().getOrderItems().size());

        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            // Redis 일시적 장애: 예외를 던져 @Retryable 작동
            log.warn("Redis 일시적 장애, 재시도 예정: orderId={}", event.getOrder().getId(), e);
            throw e;  // @Retryable이 이 예외를 catch해서 재시도

        } catch (Exception e) {
            // 복구 불가 에러: DLQ로 이동 (재시도하지 않음)
            log.error("복구 불가 에러, DLQ로 이동: orderId={}", event.getOrder().getId(), e);
            saveToDLQ(event, e.getMessage());
        }
    }

    /**
     * DLQ (Dead Letter Queue)에 저장
     * - 재시도 불가능한 에러
     * - 재시도 횟수 초과
     */
    private void saveToDLQ(PaymentCompletedEvent event, String errorMessage) {
        try {
            String eventType = "PaymentCompleted";
            String eventId = "order-" + event.getOrder().getId();

            // 이벤트를 JSON으로 직렬화
            Map<String, Object> payload = new HashMap<>();
            payload.put("orderId", event.getOrder().getId());
            payload.put("userId", event.getOrder().getUserId());
            payload.put("totalAmount", event.getOrder().getTotalAmount());

            String payloadJson = objectMapper.writeValueAsString(payload);

            // FailedEvent 생성 및 저장
            FailedEvent failedEvent = FailedEvent.create(eventType, eventId, payloadJson, errorMessage);
            failedEventRepository.save(failedEvent);

            log.info("DLQ 저장 완료: eventId={}", eventId);

        } catch (JsonProcessingException e) {
            log.error("DLQ 저장 중 JSON 직렬화 실패: orderId={}", event.getOrder().getId(), e);
        } catch (Exception e) {
            log.error("DLQ 저장 실패: orderId={}", event.getOrder().getId(), e);
        }
    }
}
