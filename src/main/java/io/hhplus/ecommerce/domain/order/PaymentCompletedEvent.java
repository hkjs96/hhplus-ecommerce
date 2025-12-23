package io.hhplus.ecommerce.domain.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 결제 완료 도메인 이벤트
 *
 * 용도:
 * - 결제 완료 시 다른 도메인(랭킹)에 알림
 * - 비동기 처리를 통한 도메인 간 결합도 감소
 *
 * 처리:
 * - RankingEventListener가 이 이벤트를 받아 랭킹 갱신
 * - TransactionalEventListener로 DB 커밋 후 실행
 */
@Getter
@AllArgsConstructor
public class PaymentCompletedEvent {
    private final Order order;
}
