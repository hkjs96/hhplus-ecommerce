package io.hhplus.ecommerce.application.usecase.order;

import io.hhplus.ecommerce.domain.order.OrderIdempotency;
import io.hhplus.ecommerce.domain.order.OrderIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멱등성 키 저장 서비스
 *
 * 별도 서비스로 분리하여 REQUIRES_NEW 트랜잭션이 제대로 작동하도록 함
 * (자기 자신의 메서드 호출에서는 프록시가 적용되지 않음)
 */
@Service
@RequiredArgsConstructor
public class IdempotencySaveService {

    private final OrderIdempotencyRepository idempotencyRepository;

    /**
     * 멱등성 키를 PROCESSING 상태로 저장 (별도 트랜잭션)
     *
     * REQUIRES_NEW 사용:
     * - 주문 생성 실패 시에도 멱등성 키는 저장되어야 함
     * - 분산락 leaseTime(60s)이 충분히 길어서 트랜잭션 완료까지 락 유지
     * - 동일 idempotency 키는 분산락으로 직렬화됨
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderIdempotency saveProcessing(OrderIdempotency idempotency) {
        // 먼저 존재 여부 확인 후 없을 때만 INSERT 시도해 중복키 예외를 피한다.
        return idempotencyRepository.findByIdempotencyKey(idempotency.getIdempotencyKey())
                .orElseGet(() -> doSave(idempotency));
    }

    private OrderIdempotency doSave(OrderIdempotency idempotency) {
        try {
            return idempotencyRepository.save(idempotency);
        } catch (DataIntegrityViolationException e) {
            // 동일 키가 거의 동시에 저장된 경우 기존 엔티티 반환
            return idempotencyRepository.findByIdempotencyKey(idempotency.getIdempotencyKey())
                    .orElseThrow(() -> e);
        }
    }

    /**
     * 완료된 멱등성 키 저장 (별도 트랜잭션)
     *
     * 주문 생성 성공 후 COMPLETED 상태로 업데이트
     *
     * 중요: idempotencyKey로 다시 조회하여 새로운 영속성 컨텍스트에서 처리
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCompletedIdempotency(String idempotencyKey, Long createdOrderId, String responsePayload) {
        OrderIdempotency idempotency = idempotencyRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency key not found: " + idempotencyKey));

        idempotency.complete(createdOrderId, responsePayload);
        idempotencyRepository.save(idempotency);
    }

    /**
     * 실패한 멱등성 키 저장 (별도 트랜잭션)
     *
     * 주문 생성 실패 시 트랜잭션 롤백되어도
     * 멱등성 키의 FAILED 상태는 저장되어야 함
     *
     * 중요: idempotencyKey로 다시 조회하여 새로운 영속성 컨텍스트에서 처리
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailedIdempotency(String idempotencyKey, String errorMessage) {
        OrderIdempotency idempotency = idempotencyRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency key not found: " + idempotencyKey));

        idempotency.fail(errorMessage);
        idempotencyRepository.save(idempotency);
    }
}
