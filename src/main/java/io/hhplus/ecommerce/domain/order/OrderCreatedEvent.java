package io.hhplus.ecommerce.domain.order;

import io.hhplus.ecommerce.application.order.dto.CreateOrderResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 주문 생성 완료 이벤트
 *
 * 발행 시점: 주문 생성 트랜잭션 커밋 직후
 *
 * 처리:
 * - 멱등성 키 완료 상태로 업데이트
 * - 주문 생성 통계 집계
 *
 * Phase 2 개선:
 * - 멱등성 완료 처리를 이벤트로 분리하여 비즈니스 로직과 결합도 낮춤
 * - 멱등성 저장 실패가 주문 생성 트랜잭션에 영향을 주지 않음
 */
@Getter
@AllArgsConstructor
public class OrderCreatedEvent {
    private final String idempotencyKey;
    private final CreateOrderResponse orderResponse;
}
