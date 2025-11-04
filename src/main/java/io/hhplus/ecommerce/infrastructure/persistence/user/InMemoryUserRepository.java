package io.hhplus.ecommerce.infrastructure.persistence.user;

import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자 In-Memory Repository 구현체 (Infrastructure Layer)
 * Week 3: ConcurrentHashMap 기반 Thread-safe 저장소
 *
 * DIP (Dependency Inversion Principle):
 * - Domain의 UserRepository 인터페이스를 구현
 * - Infrastructure가 Domain에 의존 (Domain은 Infrastructure를 모름)
 */
@Repository
public class InMemoryUserRepository implements UserRepository {

    // Thread-safe 인메모리 저장소
    private final Map<String, User> storage = new ConcurrentHashMap<>();
    // 이메일 인덱스 (빠른 조회를 위한 보조 인덱스)
    private final Map<String, String> emailIndex = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String userId = emailIndex.get(email);
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(userId));
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public User save(User user) {
        storage.put(user.getId(), user);
        emailIndex.put(user.getEmail(), user.getId());
        return user;
    }

    @Override
    public void deleteById(String id) {
        User user = storage.remove(id);
        if (user != null) {
            emailIndex.remove(user.getEmail());
        }
    }

    @Override
    public boolean existsById(String id) {
        return storage.containsKey(id);
    }

    @Override
    public boolean existsByEmail(String email) {
        return emailIndex.containsKey(email);
    }
}
