package io.hhplus.ecommerce.infrastructure.persistence.event;

import io.hhplus.ecommerce.domain.event.FailedEvent;
import io.hhplus.ecommerce.domain.event.FailedEvent.FailedEventStatus;
import io.hhplus.ecommerce.domain.event.FailedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FailedEventRepositoryImpl implements FailedEventRepository {

    private final FailedEventJpaRepository jpaRepository;

    @Override
    public FailedEvent save(FailedEvent failedEvent) {
        return jpaRepository.save(failedEvent);
    }

    @Override
    public Optional<FailedEvent> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<FailedEvent> findByEventTypeAndEventId(String eventType, String eventId) {
        return jpaRepository.findByEventTypeAndEventId(eventType, eventId);
    }

    @Override
    public List<FailedEvent> findRetryableEvents(int limit) {
        return jpaRepository.findRetryableEvents(
            LocalDateTime.now(),
            PageRequest.of(0, limit)
        );
    }

    @Override
    public long countByStatus(FailedEventStatus status) {
        return jpaRepository.countByStatus(status);
    }
}
