package io.hhplus.ecommerce.infrastructure.persistence.event;

import io.hhplus.ecommerce.domain.event.FailedEvent;
import io.hhplus.ecommerce.domain.event.FailedEvent.FailedEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FailedEventJpaRepository extends JpaRepository<FailedEvent, Long> {

    Optional<FailedEvent> findByEventTypeAndEventId(String eventType, String eventId);

    @Query("SELECT f FROM FailedEvent f " +
           "WHERE f.status = 'PENDING' " +
           "AND f.nextRetryAt <= :now " +
           "ORDER BY f.nextRetryAt ASC")
    List<FailedEvent> findRetryableEvents(@Param("now") LocalDateTime now, org.springframework.data.domain.Pageable pageable);

    long countByStatus(FailedEventStatus status);
}
