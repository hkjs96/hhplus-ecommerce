---
description: Step 5-6 구현 가이드 및 코드 예시
---

# Implementation Guide (Step 5-6)

> Week 3 레이어드 아키텍처 구현을 위한 단계별 가이드

## 🎯 Implementation Guide

### Step 1: Domain Layer 구현

#### Entity 구현 예시 (Product.java)

```java
package io.hhplus.ecommerce.domain.product;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Product {
    private String id;
    private String name;
    private String description;
    private Long price;
    private Integer stock;
    private String category;

    public void decreaseStock(int quantity) {
        validateQuantity(quantity);
        validateStock(quantity);
        this.stock -= quantity;
    }

    public void restoreStock(int quantity) {
        validateQuantity(quantity);
        this.stock += quantity;
    }

    public boolean hasStock(int quantity) {
        return stock >= quantity;
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY, "수량은 0보다 커야 합니다.");
        }
    }

    private void validateStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_STOCK,
                String.format("재고가 부족합니다. (요청: %d, 재고: %d)", quantity, stock)
            );
        }
    }
}
```

#### Repository Interface (ProductRepository.java)

```java
package io.hhplus.ecommerce.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Optional<Product> findById(String id);
    List<Product> findAll();
    List<Product> findByCategory(String category);
    Product save(Product product);
    void deleteById(String id);
}
```

---

### Step 2: Infrastructure Layer 구현

#### In-Memory Repository 구현 (InMemoryProductRepository.java)

```java
package io.hhplus.ecommerce.infrastructure.persistence.product;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
        return List.copyOf(storage.values());
    }

    @Override
    public List<Product> findByCategory(String category) {
        return storage.values().stream()
            .filter(p -> p.getCategory().equals(category))
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
}
```

---

### Step 3: Application Layer 구현

#### UseCase 구현 (ProductUseCase.java)

```java
package io.hhplus.ecommerce.application.product;

import io.hhplus.ecommerce.application.product.dto.ProductResponse;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductUseCase {

    private final ProductRepository productRepository;

    public List<ProductResponse> getProducts(String category, String sort) {
        List<Product> products;

        // 카테고리 필터링
        if (category != null && !category.isEmpty()) {
            products = productRepository.findByCategory(category);
        } else {
            products = productRepository.findAll();
        }

        // 정렬 (생략)

        return products.stream()
            .map(ProductResponse::from)
            .collect(Collectors.toList());
    }

    public ProductResponse getProduct(String productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.PRODUCT_NOT_FOUND,
                "상품을 찾을 수 없습니다. productId: " + productId
            ));

        return ProductResponse::from(product);
    }
}
```

#### DTO (ProductResponse.java)

```java
package io.hhplus.ecommerce.application.product.dto;

import io.hhplus.ecommerce.domain.product.Product;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductResponse {
    private String productId;
    private String name;
    private String description;
    private Long price;
    private Integer stock;
    private String category;

    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            product.getCategory()
        );
    }
}
```

---

### Step 4: Presentation Layer 구현

#### Controller 리팩토링 (ProductController.java)

```java
package io.hhplus.ecommerce.presentation.api.product;

import io.hhplus.ecommerce.application.product.ProductUseCase;
import io.hhplus.ecommerce.application.product.dto.ProductListResponse;
import io.hhplus.ecommerce.application.product.dto.ProductResponse;
import io.hhplus.ecommerce.presentation.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/products")
@Tag(name = "1. 상품", description = "상품 조회 API")
@RequiredArgsConstructor  // Lombok으로 생성자 주입
public class ProductController {

    // ConcurrentHashMap 제거!
    private final ProductUseCase productUseCase;  // UseCase 주입

    @GetMapping
    public ApiResponse<ProductListResponse> getProducts(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String sort
    ) {
        log.info("GET /products - category: {}, sort: {}", category, sort);

        List<ProductResponse> products = productUseCase.getProducts(category, sort);
        ProductListResponse response = new ProductListResponse(products, products.size());

        return ApiResponse.success(response);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> getProduct(@PathVariable String productId) {
        log.info("GET /products/{}", productId);

        ProductResponse product = productUseCase.getProduct(productId);
        return ApiResponse.success(product);
    }
}
```

---

## 🛠️ Development Commands

### Building the Project
```bash
./gradlew build
```

### Running the Application
```bash
./gradlew bootRun
```

### Running Tests
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests io.hhplus.ecommerce.domain.product.ProductTest

# Run with coverage
./gradlew test jacocoTestReport

# Verify coverage (70% threshold)
./gradlew jacocoTestCoverageVerification
```

### Cleaning Build Artifacts
```bash
./gradlew clean
```

---

## 📚 Reference Materials

### Architecture Patterns
- [Martin Fowler - Layered Architecture](https://martinfowler.com/bliki/PresentationDomainDataLayering.html)
- [DDD - Eric Evans](https://www.domainlanguage.com/ddd/)
- [Clean Architecture - Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

### Concurrency
- [Java Concurrency in Practice](https://jcip.net/)
- [Oracle - Java Concurrency Tutorial](https://docs.oracle.com/javase/tutorial/essential/concurrency/)

### Testing
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)

---

## 📚 관련 명령어

- `/week3-guide` - Week 3 전체 가이드 및 체크리스트
- `/architecture` - 레이어드 아키텍처 및 프로젝트 구조
- `/concurrency` - 동시성 제어 패턴 4가지
- `/testing` - 테스트 전략 및 Jacoco
- `/week3-faq` - Week 3 FAQ 18개
