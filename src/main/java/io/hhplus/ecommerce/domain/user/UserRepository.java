package io.hhplus.ecommerce.domain.user;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(String id);

    User save(User user);
}
