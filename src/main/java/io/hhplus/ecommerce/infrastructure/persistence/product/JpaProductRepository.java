package io.hhplus.ecommerce.infrastructure.persistence.product;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
public interface JpaProductRepository extends JpaRepository<Product, Long>, ProductRepository {

    // Explicitly declare methods to resolve ambiguity with ProductRepository
    @Override
    Optional<Product> findById(Long id);

    @Override
    Product save(Product product);

    @Override
    List<Product> findAll();

    @Override
    Optional<Product> findByProductCode(String productCode);
}
