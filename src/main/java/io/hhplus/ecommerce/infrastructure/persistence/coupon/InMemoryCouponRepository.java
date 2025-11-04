package io.hhplus.ecommerce.infrastructure.persistence.coupon;

import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 쿠폰 In-Memory Repository 구현체 (Infrastructure Layer)
 * Week 3: ConcurrentHashMap 기반 Thread-safe 저장소
 *
 * DIP (Dependency Inversion Principle):
 * - Domain의 CouponRepository 인터페이스를 구현
 * - Infrastructure가 Domain에 의존 (Domain은 Infrastructure를 모름)
 */
@Repository
public class InMemoryCouponRepository implements CouponRepository {

    // Thread-safe 인메모리 저장소
    private final Map<String, Coupon> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<Coupon> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Coupon> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<Coupon> findActiveCoupons() {
        LocalDateTime now = LocalDateTime.now();
        return storage.values().stream()
            .filter(coupon -> coupon.isValid(now))
            .collect(Collectors.toList());
    }

    @Override
    public Coupon save(Coupon coupon) {
        storage.put(coupon.getId(), coupon);
        return coupon;
    }

    @Override
    public void deleteById(String id) {
        storage.remove(id);
    }

    @Override
    public boolean existsById(String id) {
        return storage.containsKey(id);
    }
}
