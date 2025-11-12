package io.hhplus.ecommerce.infrastructure.persistence.coupon;

import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * InMemory UserCoupon Repository (Legacy)
 */
@Repository
@Profile("inmemory")
public class InMemoryUserCouponRepository implements UserCouponRepository {

    private final Map<Long, UserCoupon> storage = new ConcurrentHashMap<>();
    private final Map<String, Long> userCouponIndex = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return storage.values().stream()
            .filter(userCoupon -> userId.equals(userCoupon.getUserId()))
            .toList();
    }

    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        String key = makeKey(userId, couponId);
        return userCouponIndex.containsKey(key);
    }

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        if (userCoupon.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            try {
                var idField = UserCoupon.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(userCoupon, newId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set ID", e);
            }
        }

        storage.put(userCoupon.getId(), userCoupon);
        String key = makeKey(userCoupon.getUserId(), userCoupon.getCouponId());
        userCouponIndex.put(key, userCoupon.getId());
        return userCoupon;
    }

    private String makeKey(Long userId, Long couponId) {
        return userId + ":" + couponId;
    }

    public void clear() {
        storage.clear();
        userCouponIndex.clear();
        idGenerator.set(1);
    }
}
