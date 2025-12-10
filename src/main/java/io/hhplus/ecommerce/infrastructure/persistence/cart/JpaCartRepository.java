package io.hhplus.ecommerce.infrastructure.persistence.cart;

import io.hhplus.ecommerce.domain.cart.Cart;
import io.hhplus.ecommerce.domain.cart.CartRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
@Primary
public interface JpaCartRepository extends JpaRepository<Cart, Long>, CartRepository {

    @Override
    Optional<Cart> findByUserId(Long userId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.userId = :userId")
    Optional<Cart> findByUserIdForUpdate(@Param("userId") Long userId);

    @Override
    @SuppressWarnings("unchecked")
    Cart save(Cart cart);
}

