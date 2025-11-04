package io.hhplus.ecommerce.domain.product;

import java.util.List;
import java.util.Optional;

/**
 * 상품 Repository 인터페이스 (Domain Layer)
 * Week 3: 인터페이스는 Domain에, 구현체는 Infrastructure에 위치 (DIP 원칙)
 *
 * Repository 패턴:
 * - 데이터 저장소에 대한 추상화
 * - 구현체는 In-Memory, JPA 등으로 교체 가능
 */
public interface ProductRepository {

    /**
     * 상품 ID로 조회
     */
    Optional<Product> findById(String id);

    /**
     * 모든 상품 조회
     */
    List<Product> findAll();

    /**
     * 카테고리별 상품 조회
     */
    List<Product> findByCategory(String category);

    /**
     * 상품 저장 (생성 및 업데이트)
     */
    Product save(Product product);

    /**
     * 상품 삭제
     */
    void deleteById(String id);

    /**
     * 상품 존재 여부 확인
     */
    boolean existsById(String id);
}
