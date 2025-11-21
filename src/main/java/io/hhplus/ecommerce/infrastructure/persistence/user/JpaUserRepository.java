package io.hhplus.ecommerce.infrastructure.persistence.user;

import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import jakarta.persistence.LockModeType;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Primary
public interface JpaUserRepository extends JpaRepository<User, Long>, UserRepository {

    // Explicitly declare methods to resolve ambiguity with UserRepository
    @Override
    Optional<User> findById(Long id);

    @Override
    User save(User user);

    @Override
    Optional<User> findByEmail(String email);

    /**
     * Pessimistic Write Lock (SELECT FOR UPDATE)
     * - 잔액 업데이트 시 사용 (charge, deduct)
     * - Lost Update 방지
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithLock(@Param("id") Long id);
}
