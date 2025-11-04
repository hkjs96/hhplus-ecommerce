package io.hhplus.ecommerce.infrastructure.persistence.coupon;

import io.hhplus.ecommerce.domain.coupon.CouponStatus;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 사용자 쿠폰 In-Memory Repository 구현체 (Infrastructure Layer)
 * Week 3: ConcurrentHashMap 기반 Thread-safe 저장소
 *
 * DIP (Dependency Inversion Principle):
 * - Domain의 UserCouponRepository 인터페이스를 구현
 * - Infrastructure가 Domain에 의존 (Domain은 Infrastructure를 모름)
 */
@Repository
public class InMemoryUserCouponRepository implements UserCouponRepository {

    // Thread-safe 인메모리 저장소
    private final Map<String, UserCoupon> storage = new ConcurrentHashMap<>();
    // 중복 발급 체크용 인덱스 (userId + couponId)
    private final Map<String, String> userCouponIndex = new ConcurrentHashMap<>();

    @Override
    public Optional<UserCoupon> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<UserCoupon> findByUserId(String userId) {
        return storage.values().stream()
            .filter(userCoupon -> userId.equals(userCoupon.getUserId()))
            .collect(Collectors.toList());
    }

    @Override
    public List<UserCoupon> findByUserIdAndStatus(String userId, CouponStatus status) {
        return storage.values().stream()
            .filter(userCoupon -> userId.equals(userCoupon.getUserId()))
            .filter(userCoupon -> status == userCoupon.getStatus())
            .collect(Collectors.toList());
    }

    @Override
    public boolean existsByUserIdAndCouponId(String userId, String couponId) {
        String key = makeKey(userId, couponId);
        return userCouponIndex.containsKey(key);
    }

    @Override
    public List<UserCoupon> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        storage.put(userCoupon.getId(), userCoupon);
        // 중복 발급 체크용 인덱스 업데이트
        String key = makeKey(userCoupon.getUserId(), userCoupon.getCouponId());
        userCouponIndex.put(key, userCoupon.getId());
        return userCoupon;
    }

    @Override
    public void deleteById(String id) {
        UserCoupon userCoupon = storage.remove(id);
        if (userCoupon != null) {
            String key = makeKey(userCoupon.getUserId(), userCoupon.getCouponId());
            userCouponIndex.remove(key);
        }
    }

    @Override
    public boolean existsById(String id) {
        return storage.containsKey(id);
    }

    /**
     * 복합 키 생성 (userId + couponId)
     */
    private String makeKey(String userId, String couponId) {
        return userId + ":" + couponId;
    }
}
