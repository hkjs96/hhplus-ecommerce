package io.hhplus.ecommerce.infrastructure.persistence.coupon;

import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryCouponRepository implements CouponRepository {

    private final Map<String, Coupon> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<Coupon> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Coupon save(Coupon coupon) {
        storage.put(coupon.getId(), coupon);
        return coupon;
    }
}
