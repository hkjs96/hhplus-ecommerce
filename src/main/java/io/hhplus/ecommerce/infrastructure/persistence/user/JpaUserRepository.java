package io.hhplus.ecommerce.infrastructure.persistence.user;

import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
