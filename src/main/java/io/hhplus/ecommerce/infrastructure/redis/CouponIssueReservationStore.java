package io.hhplus.ecommerce.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponIssueReservationStore {

    private final RedisTemplate<String, String> redisTemplate;

    public enum ReserveResult {
        RESERVED,
        SOLD_OUT,
        ALREADY_RESERVED,
        ALREADY_ISSUED
    }

    public record ReserveResponse(
        ReserveResult result,
        long remainingAfter
    ) {
    }

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>(
        """
            local remainingKey = KEYS[1]
            local issuedSetKey = KEYS[2]
            local reservationKey = KEYS[3]
            local reservationSetKey = KEYS[4]

            local userId = ARGV[1]
            local initialQuantity = tonumber(ARGV[2])
            local reservationTtlSeconds = tonumber(ARGV[3])

            if redis.call('SISMEMBER', issuedSetKey, userId) == 1 then
              return -4
            end

            if redis.call('EXISTS', reservationKey) == 1 then
              return -3
            end

            local remaining = redis.call('GET', remainingKey)
            if remaining == false then
              redis.call('SET', remainingKey, initialQuantity)
              remaining = tostring(initialQuantity)
            end

            if tonumber(remaining) <= 0 then
              return -1
            end

            local newRemaining = redis.call('DECR', remainingKey)
            redis.call('SET', reservationKey, 'RESERVED', 'EX', reservationTtlSeconds)
            redis.call('SADD', reservationSetKey, userId)
            redis.call('EXPIRE', reservationSetKey, reservationTtlSeconds)
            return newRemaining
            """,
        Long.class
    );

    private static final DefaultRedisScript<Long> CONFIRM_SCRIPT = new DefaultRedisScript<>(
        """
            local issuedSetKey = KEYS[1]
            local reservationKey = KEYS[2]
            local reservationSetKey = KEYS[3]

            local userId = ARGV[1]
            local issuedTtlSeconds = tonumber(ARGV[2])

            if redis.call('GET', reservationKey) ~= 'RESERVED' then
              return 0
            end

            redis.call('DEL', reservationKey)
            redis.call('SREM', reservationSetKey, userId)
            redis.call('SADD', issuedSetKey, userId)
            redis.call('EXPIRE', issuedSetKey, issuedTtlSeconds)
            return 1
            """,
        Long.class
    );

    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT = new DefaultRedisScript<>(
        """
            local remainingKey = KEYS[1]
            local reservationKey = KEYS[2]
            local reservationSetKey = KEYS[3]

            local userId = ARGV[1]

            if redis.call('GET', reservationKey) ~= 'RESERVED' then
              return 0
            end

            redis.call('DEL', reservationKey)
            redis.call('SREM', reservationSetKey, userId)
            redis.call('INCR', remainingKey)
            return 1
            """,
        Long.class
    );

    public ReserveResponse reserve(Long couponId, Long userId, long initialQuantity, Duration reservationTtl) {
        String remainingKey = String.format("coupon:%d:remaining", couponId);
        String issuedSetKey = String.format("coupon:%d:issued", couponId);
        String reservationKey = String.format("coupon:%d:reservation:%d", couponId, userId);
        String reservationSetKey = String.format("coupon:%d:reservations", couponId);

        Long result = redisTemplate.execute(
            RESERVE_SCRIPT,
            List.of(remainingKey, issuedSetKey, reservationKey, reservationSetKey),
            String.valueOf(userId),
            String.valueOf(initialQuantity),
            String.valueOf(reservationTtl.toSeconds())
        );

        if (result == null) {
            throw new IllegalStateException("Redis script execution returned null");
        }

        if (result == -1L) {
            return new ReserveResponse(ReserveResult.SOLD_OUT, 0);
        }
        if (result == -3L) {
            return new ReserveResponse(ReserveResult.ALREADY_RESERVED, -1);
        }
        if (result == -4L) {
            return new ReserveResponse(ReserveResult.ALREADY_ISSUED, -1);
        }
        return new ReserveResponse(ReserveResult.RESERVED, result);
    }

    public boolean confirmIssued(Long couponId, Long userId, Duration issuedTtl) {
        String issuedSetKey = String.format("coupon:%d:issued", couponId);
        String reservationKey = String.format("coupon:%d:reservation:%d", couponId, userId);
        String reservationSetKey = String.format("coupon:%d:reservations", couponId);

        Long result = redisTemplate.execute(
            CONFIRM_SCRIPT,
            List.of(issuedSetKey, reservationKey, reservationSetKey),
            String.valueOf(userId),
            String.valueOf(issuedTtl.toSeconds())
        );
        return result != null && result == 1L;
    }

    public boolean compensateReservation(Long couponId, Long userId) {
        String remainingKey = String.format("coupon:%d:remaining", couponId);
        String reservationKey = String.format("coupon:%d:reservation:%d", couponId, userId);
        String reservationSetKey = String.format("coupon:%d:reservations", couponId);

        Long result = redisTemplate.execute(
            COMPENSATE_SCRIPT,
            List.of(remainingKey, reservationKey, reservationSetKey),
            String.valueOf(userId)
        );
        return result != null && result == 1L;
    }
}
