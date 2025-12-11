package io.hhplus.ecommerce.application.usecase.user;

import io.hhplus.ecommerce.domain.user.ChargeBalanceIdempotency;
import io.hhplus.ecommerce.domain.user.ChargeBalanceIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 잔액 충전 멱등성 키 저장 서비스
 *
 * 별도 서비스로 분리하여 REQUIRES_NEW 트랜잭션이 제대로 작동하도록 함
 * (자기 자신의 메서드 호출에서는 프록시가 적용되지 않음)
 */
@Service
@RequiredArgsConstructor
public class ChargeBalanceIdempotencySaveService {

    private final ChargeBalanceIdempotencyRepository idempotencyRepository;

    /**
     * 완료된 멱등성 키 저장 (별도 트랜잭션)
     *
     * 잔액 충전 성공 후 COMPLETED 상태로 업데이트
     *
     * 중요: idempotencyKey로 다시 조회하여 새로운 영속성 컨텍스트에서 처리
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCompletedIdempotency(String idempotencyKey, String responsePayload) {
        ChargeBalanceIdempotency idempotency = idempotencyRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency key not found: " + idempotencyKey));

        idempotency.complete(responsePayload);
        idempotencyRepository.save(idempotency);
    }
}
