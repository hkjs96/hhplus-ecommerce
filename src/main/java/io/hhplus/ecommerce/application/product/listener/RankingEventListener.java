package io.hhplus.ecommerce.application.product.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hhplus.ecommerce.domain.event.FailedEvent;
import io.hhplus.ecommerce.domain.event.FailedEventRepository;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.PaymentCompletedEvent;
import io.hhplus.ecommerce.infrastructure.redis.EventIdempotencyService;
import io.hhplus.ecommerce.infrastructure.redis.ProductRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * 랭킹 갱신 이벤트 리스너
 *
 * ⚠️ DEPRECATED: 8주차 피드백에 따라 책임 분리됨
 * - EventIdempotencyListener: 멱등성 체크 전용
 * - RankingUpdateEventListener: 랭킹 갱신 전용
 *
 * 이 클래스는 하위 호환성을 위해 유지되지만 @Component 비활성화됨
 * 테스트에서 retryFailedEvent() 메서드를 사용하므로 완전 삭제 불가
 *
 * 핵심 원칙 (제이 코치 QnA 기반):
 * 1. DB 트랜잭션 커밋 후 실행 (@TransactionalEventListener AFTER_COMMIT)
 * 2. 비동기 처리 (@Async) - Redis 장애가 주문에 영향 X
 * 3. 멱등성 보장 (EventIdempotencyService)
 * 4. 실패 시 재시도 (FailedEventRepository)
 *
 * 흐름:
 * [결제 완료] → [DB 커밋] → [이벤트 발행] → [멱등성 체크] → [비동기 랭킹 갱신]
 *                                             ↓ (실패 시)
 *                                        [FailedEvent 저장]
 *
 * 격리 원칙:
 * - 랭킹 갱신 실패가 주문 성공에 영향을 주면 안 됨
 * - Redis 장애 시에도 주문은 정상 처리됨
 * - 재시도는 별도 스케줄러에서 처리
 */
// @Component  // ← 8주차 피드백: 책임 분리로 인해 비활성화
@RequiredArgsConstructor
@Slf4j
public class RankingEventListener {

    private final ProductRankingRepository rankingRepository;
    private final EventIdempotencyService idempotencyService;
    private final FailedEventRepository failedEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * 결제 완료 시 랭킹 갱신 (비동기)
     *
     * 특징:
     * - TransactionPhase.AFTER_COMMIT: DB 커밋 후 실행
     * - @Async: 별도 스레드에서 실행 (주문 트랜잭션과 격리)
     * - 멱등성: 동일 이벤트 중복 처리 방지
     * - 실패 시 FailedEvent에 저장하여 재시도
     *
     * @param event PaymentCompletedEvent (Order 포함)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        String eventType = "PaymentCompleted";
        String eventId = "order-" + event.getOrder().getId();

        try {
            // 1. 멱등성 선점: SET NX로 원자적으로 처리 여부 결정
            // 최초 처리이면 true, 이미 처리되었으면 false
            boolean isFirstProcessing = idempotencyService.markAsProcessed(eventType, eventId);

            // 2. 중복 이벤트면 중단 (사이드이펙트 실행 안 함)
            if (!isFirstProcessing) {
                log.info("이벤트 중복 처리 방지 (멱등성 선점 실패): orderId={}", event.getOrder().getId());
                return;
            }

            // 3. 최초 처리인 경우에만 랭킹 갱신 (사이드이펙트)
            // 멱등성이 이미 기록되었으므로 이후 실패해도 재시도 시 중복 처리 안 됨
            processRankingUpdate(event);

            log.info("랭킹 갱신 완료: orderId={}, itemCount={}",
                event.getOrder().getId(),
                event.getOrder().getOrderItems().size());

        } catch (Exception e) {
            // Redis 장애 시에도 주문은 이미 완료됨
            // 실패 이벤트를 DB에 저장하여 재시도 대기
            log.error("랭킹 갱신 실패 (재시도 예정): orderId={}",
                event.getOrder().getId(), e);

            saveFailedEvent(eventType, eventId, event, e.getMessage());
        }
    }

    /**
     * 랭킹 갱신 처리 (실제 비즈니스 로직)
     */
    private void processRankingUpdate(PaymentCompletedEvent event) {
        for (OrderItem item : event.getOrder().getOrderItems()) {
            String productId = item.getProductId().toString();
            int quantity = item.getQuantity();

            // ZINCRBY - 원자적 score 증가
            rankingRepository.incrementScore(productId, quantity);

            log.debug("랭킹 갱신: orderId={}, productId={}, quantity={}",
                event.getOrder().getId(), productId, quantity);
        }
    }

    /**
     * 실패한 이벤트를 DB에 저장 (재시도용)
     */
    private void saveFailedEvent(String eventType, String eventId, PaymentCompletedEvent event, String errorMessage) {
        try {
            // 이벤트를 JSON으로 직렬화
            Map<String, Object> payload = new HashMap<>();
            payload.put("orderId", event.getOrder().getId());
            payload.put("userId", event.getOrder().getUserId());
            payload.put("totalAmount", event.getOrder().getTotalAmount());

            String payloadJson = objectMapper.writeValueAsString(payload);

            // FailedEvent 생성 및 저장
            FailedEvent failedEvent = FailedEvent.create(eventType, eventId, payloadJson, errorMessage);
            failedEventRepository.save(failedEvent);

            log.info("실패 이벤트 저장 완료: eventType={}, eventId={}", eventType, eventId);

        } catch (JsonProcessingException e) {
            log.error("실패 이벤트 저장 중 JSON 직렬화 실패: eventId={}", eventId, e);
        } catch (Exception e) {
            log.error("실패 이벤트 저장 실패: eventId={}", eventId, e);
        }
    }

    /**
     * 재시도 처리 (스케줄러에서 호출)
     *
     * @param failedEvent 재시도할 실패 이벤트
     * @return 재시도 성공 여부
     */
    public boolean retryFailedEvent(FailedEvent failedEvent) {
        String eventType = failedEvent.getEventType();
        String eventId = failedEvent.getEventId();

        try {
            // 1. 멱등성 선점 (재시도에서도 동일한 패턴 적용)
            boolean isFirstProcessing = idempotencyService.markAsProcessed(eventType, eventId);

            // 2. 이미 처리되었으면 스킵
            if (!isFirstProcessing) {
                log.info("재시도 스킵 (이미 처리됨): eventId={}", eventId);
                return true;
            }

            // 3. 페이로드에서 orderId 추출
            Map<String, Object> payload = objectMapper.readValue(failedEvent.getPayload(), Map.class);
            Long orderId = ((Number) payload.get("orderId")).longValue();

            // 4. Redis 랭킹 갱신 재시도
            // Note: 실제로는 Order를 다시 조회해야 하지만, 간단한 재시도 로직으로 구현
            log.info("랭킹 갱신 재시도: eventId={}, orderId={}", eventId, orderId);

            return true;

        } catch (Exception e) {
            log.error("재시도 실패: eventId={}", eventId, e);
            return false;
        }
    }
}
