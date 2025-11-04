package io.hhplus.ecommerce.infrastructure.persistence.product;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 상품 In-Memory Repository 구현체 (Infrastructure Layer)
 * Week 3: ConcurrentHashMap 기반 Thread-safe 저장소
 *
 * DIP (Dependency Inversion Principle):
 * - Domain의 ProductRepository 인터페이스를 구현
 * - Infrastructure가 Domain에 의존 (Domain은 Infrastructure를 모름)
 */
@Repository
public class InMemoryProductRepository implements ProductRepository {

    // Thread-safe 인메모리 저장소
    private final Map<String, Product> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<Product> findByCategory(String category) {
        return storage.values().stream()
            .filter(product -> category.equals(product.getCategory()))
            .collect(Collectors.toList());
    }

    @Override
    public Product save(Product product) {
        storage.put(product.getId(), product);
        return product;
    }

    @Override
    public void deleteById(String id) {
        storage.remove(id);
    }

    @Override
    public boolean existsById(String id) {
        return storage.containsKey(id);
    }
}
