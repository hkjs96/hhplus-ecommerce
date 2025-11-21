package io.hhplus.ecommerce.infrastructure.external;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * PG사 응답 DTO
 * <p>
 * 실제 외부 API 응답을 모방한 DTO
 * - 실제 PG사: 토스페이먼츠, NicePay, KG이니시스 등
 * - 현재: Mock 구현 (실제 API 없음)
 */
@Getter
@AllArgsConstructor
public class PGResponse {

    /**
     * 성공 여부
     */
    private final boolean success;

    /**
     * PG사 트랜잭션 ID
     * 예: "TOSS_TX_20251120_123456"
     */
    private final String transactionId;

    /**
     * 응답 메시지
     */
    private final String message;

    /**
     * 성공 응답 생성
     */
    public static PGResponse success(String transactionId) {
        return new PGResponse(true, transactionId, "PG 승인 성공");
    }

    /**
     * 실패 응답 생성
     */
    public static PGResponse failure(String message) {
        return new PGResponse(false, null, message);
    }
}
