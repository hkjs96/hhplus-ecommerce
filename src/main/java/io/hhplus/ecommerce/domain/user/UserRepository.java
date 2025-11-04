package io.hhplus.ecommerce.domain.user;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 Repository 인터페이스 (Domain Layer)
 * Week 3: 인터페이스는 Domain에, 구현체는 Infrastructure에 위치 (DIP 원칙)
 */
public interface UserRepository {

    /**
     * 사용자 ID로 조회
     */
    Optional<User> findById(String id);

    /**
     * 이메일로 조회
     */
    Optional<User> findByEmail(String email);

    /**
     * 모든 사용자 조회
     */
    List<User> findAll();

    /**
     * 사용자 저장 (생성 및 업데이트)
     */
    User save(User user);

    /**
     * 사용자 삭제
     */
    void deleteById(String id);

    /**
     * 사용자 존재 여부 확인
     */
    boolean existsById(String id);

    /**
     * 이메일 중복 확인
     */
    boolean existsByEmail(String email);
}
