package io.hhplus.ecommerce.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 이벤트 멱등성 보장 서비스
 *
 * 목적:
 * - 동일한 이벤트가 여러 번 처리되는 것을 방지
 * - Redis Set을 사용하여 처리된 이벤트 ID 기록
 *
 * 사용 방법:
 * 1. 이벤트 처리 전에 isProcessed() 체크
 * 2. 처리 완료 후 markAsProcessed() 호출
 *
 * TTL:
 * - 처리된 이벤트 ID는 7일 후 자동 삭제
 * - 메모리 효율성과 충분한 멱등성 기간 보장
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventIdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String KEY_PREFIX = "event:processed:";
    private static final long TTL_DAYS = 7;

    /**
     * 이벤트가 이미 처리되었는지 확인
     *
     * @param eventType 이벤트 타입 (예: "PaymentCompleted")
     * @param eventId 이벤트 고유 ID (예: "order-123")
     * @return 이미 처리되었으면 true, 아니면 false
     */
    public boolean isProcessed(String eventType, String eventId) {
        String key = buildKey(eventType, eventId);
        Boolean exists = redisTemplate.hasKey(key);

        if (Boolean.TRUE.equals(exists)) {
            log.debug("이벤트 중복 처리 감지: type={}, id={}", eventType, eventId);
            return true;
        }

        return false;
    }

    /**
     * 이벤트를 처리 완료로 표시
     *
     * @param eventType 이벤트 타입
     * @param eventId 이벤트 고유 ID
     * @return 최초 처리이면 true, 이미 처리되었으면 false
     */
    public boolean markAsProcessed(String eventType, String eventId) {
        String key = buildKey(eventType, eventId);

        // SET NX (존재하지 않을 때만 설정)
        Boolean success = redisTemplate.opsForValue()
            .setIfAbsent(key, "1", Duration.ofDays(TTL_DAYS));

        if (Boolean.TRUE.equals(success)) {
            log.debug("이벤트 처리 기록: type={}, id={}", eventType, eventId);
            return true;
        } else {
            log.warn("이벤트 중복 처리 시도 방지: type={}, id={}", eventType, eventId);
            return false;
        }
    }

    /**
     * 이벤트 처리 기록 삭제 (테스트용)
     *
     * @param eventType 이벤트 타입
     * @param eventId 이벤트 고유 ID
     */
    public void remove(String eventType, String eventId) {
        String key = buildKey(eventType, eventId);
        redisTemplate.delete(key);
    }

    /**
     * Redis 키 생성
     *
     * 형식: event:processed:{eventType}:{eventId}
     * 예: event:processed:PaymentCompleted:order-123
     */
    private String buildKey(String eventType, String eventId) {
        return KEY_PREFIX + eventType + ":" + eventId;
    }
}
