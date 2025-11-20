package io.hhplus.ecommerce.infrastructure.external;

import io.hhplus.ecommerce.application.order.dto.PaymentRequest;

/**
 * PG(Payment Gateway) 서비스 인터페이스
 * <p>
 * 외부 PG사 API 호출을 추상화한 인터페이스
 * <p>
 * 실제 PG사 예시:
 * - 토스페이먼츠: https://docs.tosspayments.com/
 * - NicePay: https://developers.nicepay.co.kr/
 * - KG이니시스: https://www.inicis.com/
 * <p>
 * 현재 구현:
 * - MockPGServiceImpl: Mock 구현 (실제 API 없음)
 * <p>
 * 향후 확장:
 * - TossPaymentsServiceImpl: 토스페이먼츠 실제 API 연동
 * - NicePayServiceImpl: NicePay 실제 API 연동
 */
public interface PGService {

    /**
     * 결제 승인 요청
     * <p>
     * 외부 PG사에 결제 승인을 요청합니다.
     * - 실제 PG사: HTTP API 호출 (5초 이내 응답 권장)
     * - Mock 구현: 즉시 응답 (성공/실패 시뮬레이션)
     *
     * @param request 결제 요청 정보
     * @return PG사 응답
     * @throws RuntimeException 네트워크 오류, 타임아웃 등
     */
    PGResponse charge(PaymentRequest request);
}
