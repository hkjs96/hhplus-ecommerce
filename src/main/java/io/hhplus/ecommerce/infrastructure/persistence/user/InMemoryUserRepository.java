package io.hhplus.ecommerce.infrastructure.persistence.user;

import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@Profile("inmemory")
public class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> storage = new ConcurrentHashMap<>();
    private final Map<String, User> emailIndex = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(emailIndex.get(email));
    }

    @Override
    public User save(User user) {
        // ID가 없으면 새로 생성 (신규 저장)
        if (user.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            // Reflection으로 ID 설정 (JPA Entity는 protected setter가 없음)
            try {
                var idField = User.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(user, newId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set ID", e);
            }
        }

        storage.put(user.getId(), user);
        emailIndex.put(user.getEmail(), user);
        return user;
    }

    public void clear() {
        storage.clear();
        emailIndex.clear();
        idGenerator.set(1);
    }
}
