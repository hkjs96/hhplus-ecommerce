package io.hhplus.ecommerce.infrastructure.persistence.product;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * InMemory Product Repository (Legacy)
 */
@Repository
@Profile("inmemory")
public class InMemoryProductRepository implements ProductRepository {

    private final Map<Long, Product> storage = new ConcurrentHashMap<>();
    private final Map<String, Product> productCodeIndex = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<Product> findByProductCode(String productCode) {
        return Optional.ofNullable(productCodeIndex.get(productCode));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            try {
                var idField = Product.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(product, newId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set ID", e);
            }
        }

        storage.put(product.getId(), product);
        productCodeIndex.put(product.getProductCode(), product);
        return product;
    }

    public void clear() {
        storage.clear();
        productCodeIndex.clear();
        idGenerator.set(1);
    }
}
