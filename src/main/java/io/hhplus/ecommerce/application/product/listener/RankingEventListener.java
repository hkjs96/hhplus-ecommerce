package io.hhplus.ecommerce.application.product.listener;

import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.PaymentCompletedEvent;
import io.hhplus.ecommerce.infrastructure.redis.ProductRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 랭킹 갱신 이벤트 리스너
 *
 * 핵심 원칙 (제이 코치 QnA 기반):
 * 1. DB 트랜잭션 커밋 후 실행 (@TransactionalEventListener AFTER_COMMIT)
 * 2. 비동기 처리 (@Async) - Redis 장애가 주문에 영향 X
 * 3. 실패 시 로그만 남김 (주문은 이미 완료)
 *
 * 흐름:
 * [결제 완료] → [DB 커밋] → [이벤트 발행] → [비동기 랭킹 갱신]
 *
 * 격리 원칙:
 * - 랭킹 갱신 실패가 주문 성공에 영향을 주면 안 됨
 * - Redis 장애 시에도 주문은 정상 처리됨
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RankingEventListener {

    private final ProductRankingRepository rankingRepository;

    /**
     * 결제 완료 시 랭킹 갱신 (비동기)
     *
     * 특징:
     * - TransactionPhase.AFTER_COMMIT: DB 커밋 후 실행
     * - @Async: 별도 스레드에서 실행 (주문 트랜잭션과 격리)
     * - Redis 장애 시 로그만 남김
     *
     * @param event PaymentCompletedEvent (Order 포함)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        try {
            // 각 상품별로 판매량 증가
            for (OrderItem item : event.getOrder().getOrderItems()) {
                String productId = item.getProductId().toString();
                int quantity = item.getQuantity();

                // ZINCRBY - 원자적 score 증가
                rankingRepository.incrementScore(productId, quantity);

                log.debug("랭킹 갱신: orderId={}, productId={}, quantity={}",
                    event.getOrder().getId(), productId, quantity);
            }

            log.info("랭킹 갱신 완료: orderId={}, itemCount={}",
                event.getOrder().getId(),
                event.getOrder().getOrderItems().size());

        } catch (Exception e) {
            // Redis 장애 시에도 주문은 이미 완료됨
            // 실패 로그만 남기고, 주문 트랜잭션에 영향 없음
            log.error("랭킹 갱신 실패 (주문 정상 처리됨): orderId={}",
                event.getOrder().getId(), e);

            // TODO: 재시도 로직 or 알림
            // - Kafka DLQ로 재처리
            // - Slack 알림
            // - 메트릭 수집 (Prometheus)
        }
    }
}
