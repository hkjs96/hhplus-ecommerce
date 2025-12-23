package io.hhplus.ecommerce.infrastructure.persistence.coupon;

import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@Profile("inmemory")
public class InMemoryCouponRepository implements CouponRepository {

    private final Map<Long, Coupon> storage = new ConcurrentHashMap<>();
    private final Map<String, Coupon> couponCodeIndex = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Optional<Coupon> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<Coupon> findByCouponCode(String couponCode) {
        return Optional.ofNullable(couponCodeIndex.get(couponCode));
    }

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            try {
                var idField = Coupon.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(coupon, newId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set ID", e);
            }
        }

        storage.put(coupon.getId(), coupon);
        couponCodeIndex.put(coupon.getCouponCode(), coupon);
        return coupon;
    }

    public void clear() {
        storage.clear();
        couponCodeIndex.clear();
        idGenerator.set(1);
    }
}
