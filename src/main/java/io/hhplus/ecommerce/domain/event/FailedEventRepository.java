package io.hhplus.ecommerce.domain.event;

import java.util.List;
import java.util.Optional;

/**
 * 실패한 이벤트 저장소 인터페이스
 */
public interface FailedEventRepository {

    /**
     * 실패한 이벤트 저장
     */
    FailedEvent save(FailedEvent failedEvent);

    /**
     * ID로 조회
     */
    Optional<FailedEvent> findById(Long id);

    /**
     * eventType과 eventId로 조회
     */
    Optional<FailedEvent> findByEventTypeAndEventId(String eventType, String eventId);

    /**
     * 재시도 가능한 이벤트 목록 조회
     *
     * 조건:
     * - status = PENDING
     * - nextRetryAt이 현재 시각 이전
     *
     * @param limit 조회 개수
     */
    List<FailedEvent> findRetryableEvents(int limit);

    /**
     * 상태별 개수 조회 (모니터링용)
     */
    long countByStatus(FailedEvent.FailedEventStatus status);
}
