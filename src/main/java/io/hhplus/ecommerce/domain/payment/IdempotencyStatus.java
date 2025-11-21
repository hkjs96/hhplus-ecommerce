package io.hhplus.ecommerce.domain.payment;

/**
 * 결제 멱등성 키 상태
 * <p>
 * PROCESSING: 처리 중 (동시 요청 방지)
 * COMPLETED: 완료 (결과 반환)
 * FAILED: 실패 (재시도 가능)
 */
public enum IdempotencyStatus {
    PROCESSING,  // 처리 중
    COMPLETED,   // 완료
    FAILED       // 실패
}
