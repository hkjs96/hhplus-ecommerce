package io.hhplus.ecommerce.domain.user;

import io.hhplus.ecommerce.application.user.dto.ChargeBalanceResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 잔액 충전 완료 이벤트
 *
 * 발행 시점: 잔액 충전 트랜잭션 커밋 직후
 *
 * 처리:
 * - 멱등성 키 완료 상태로 업데이트
 * - 충전 통계 집계
 *
 * Phase 2 개선:
 * - 멱등성 완료 처리를 이벤트로 분리하여 비즈니스 로직과 결합도 낮춤
 * - 멱등성 저장 실패가 잔액 충전 트랜잭션에 영향을 주지 않음
 */
@Getter
@AllArgsConstructor
public class BalanceChargedEvent {
    private final String idempotencyKey;
    private final ChargeBalanceResponse chargeResponse;
}
