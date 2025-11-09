package io.hhplus.ecommerce.infrastructure.persistence.coupon;

import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryUserCouponRepository implements UserCouponRepository {

    private final Map<String, UserCoupon> storage = new ConcurrentHashMap<>();
    private final Map<String, String> userCouponIndex = new ConcurrentHashMap<>();

    @Override
    public List<UserCoupon> findByUserId(String userId) {
        return storage.values().stream()
            .filter(userCoupon -> userId.equals(userCoupon.getUserId()))
            .toList();
    }

    @Override
    public boolean existsByUserIdAndCouponId(String userId, String couponId) {
        String key = makeKey(userId, couponId);
        return userCouponIndex.containsKey(key);
    }

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        storage.put(userCoupon.getId(), userCoupon);
        String key = makeKey(userCoupon.getUserId(), userCoupon.getCouponId());
        userCouponIndex.put(key, userCoupon.getId());
        return userCoupon;
    }

    private String makeKey(String userId, String couponId) {
        return userId + ":" + couponId;
    }
}
