# 4. Repository 패턴 (Repository Pattern)

## 📌 핵심 개념

**Repository**: 도메인 객체의 컬렉션처럼 사용할 수 있는 인터페이스를 제공하며, 데이터 저장소에 대한 접근을 캡슐화하는 패턴

---

## 🎯 Repository 패턴의 목적

### 1. 도메인과 데이터 저장소 분리
```
Domain Layer (Business Logic)
      ↓ uses
Repository Interface (계약)
      ↑ implements
Infrastructure Layer (기술 구현)
```

### 2. 테스트 용이성
- Mock Repository로 쉽게 테스트
- 데이터베이스 없이 Domain 테스트 가능

### 3. 데이터 저장 방식 변경 용이
- In-Memory → JPA → MongoDB
- Domain 코드 수정 없이 구현체만 교체

---

## 🏗️ 인터페이스와 구현체 분리

### Repository Interface (Domain Layer)
```java
package io.hhplus.ecommerce.domain.product;

import java.util.List;
import java.util.Optional;

/**
 * Repository 인터페이스는 Domain Layer에 위치
 * - Domain이 필요한 메서드만 정의
 * - 기술 세부사항 없음 (JPA, SQL 등)
 */
public interface ProductRepository {
    Optional<Product> findById(String id);
    List<Product> findAll();
    List<Product> findByCategory(String category);
    Product save(Product product);
    void deleteById(String id);
}
```

### Repository 구현체 (Infrastructure Layer)
```java
package io.hhplus.ecommerce.infrastructure.persistence.product;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-Memory Repository 구현체
 * - Infrastructure Layer에 위치
 * - ConcurrentHashMap으로 Thread-safe 보장
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
        return List.copyOf(storage.values());
    }

    @Override
    public List<Product> findByCategory(String category) {
        return storage.values().stream()
            .filter(p -> p.getCategory().equals(category))
            .toList();
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

## 🧪 테스트 용이성

### UseCase 테스트 (Mock Repository 사용)
```java
@ExtendWith(MockitoExtension.class)
class ProductUseCaseTest {

    @Mock
    private ProductRepository productRepository;  // Mock

    @InjectMocks
    private ProductUseCase productUseCase;

    @Test
    void 상품_조회_성공() {
        // Given
        String productId = "P001";
        Product product = new Product(productId, "노트북", 10, 890000L);
        when(productRepository.findById(productId))
            .thenReturn(Optional.of(product));

        // When
        ProductResponse response = productUseCase.getProduct(productId);

        // Then
        assertThat(response.getProductId()).isEqualTo(productId);
        assertThat(response.getName()).isEqualTo("노트북");

        // 행위 검증
        verify(productRepository).findById(productId);
    }
}
```

---

## 💾 ConcurrentHashMap 활용

### Why ConcurrentHashMap?

| 컬렉션 | Thread-Safe | 성능 | Week 3 적합성 |
|--------|-------------|------|---------------|
| HashMap | ❌ | ⚡⚡⚡ | ❌ (동시성 문제) |
| Hashtable | ✅ | ⚡ | ❌ (느림) |
| Collections.synchronizedMap() | ✅ | ⚡⚡ | △ (괜찮음) |
| **ConcurrentHashMap** | ✅ | ⚡⚡⚡ | ✅ (최적) |

**ConcurrentHashMap 장점:**
- ✅ Thread-safe (여러 스레드 동시 접근 가능)
- ✅ Lock-free 읽기 (읽기 성능 우수)
- ✅ 세그먼트 단위 락 (쓰기 성능 우수)

### 로이코치님 조언
> "ConcurrentHashMap을 사용하면 어느 정도 동시성을 보장합니다."

---

## 📋 Repository vs DAO

### 비교

| 항목 | Repository | DAO |
|------|-----------|-----|
| **개념** | 도메인 객체 컬렉션 | 데이터 접근 객체 |
| **관점** | 도메인 중심 | 데이터베이스 중심 |
| **메서드명** | findById, findAll | selectById, selectAll |
| **위치** | Domain Layer (Interface) | Infrastructure Layer |
| **목적** | 도메인 모델 지원 | CRUD 지원 |

### Repository (도메인 중심)
```java
public interface ProductRepository {
    Optional<Product> findById(String id);  // 도메인 용어
    List<Product> findAvailableProducts();  // 비즈니스 의미
}
```

### DAO (데이터베이스 중심)
```java
public interface ProductDao {
    ProductEntity selectById(String id);  // DB 용어
    List<ProductEntity> selectAll();      // 기술 용어
}
```

---

## 🔄 데이터 초기화 (DataInitializer)

### DataInitializer 구현
```java
package io.hhplus.ecommerce.infrastructure.config;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 시작 시 초기 데이터 로딩
 * ApplicationRunner: run() 메서드가 애플리케이션 시작 후 자동 실행됨
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== 초기 데이터 로딩 시작 ===");

        initProducts();
        initUsers();

        log.info("=== 초기 데이터 로딩 완료 ===");
    }

    private void initProducts() {
        productRepository.save(new Product("P001", "노트북", 10, 890000L, "전자제품"));
        productRepository.save(new Product("P002", "키보드", 20, 120000L, "주변기기"));
        productRepository.save(new Product("P003", "마우스", 30, 45000L, "주변기기"));
        productRepository.save(new Product("P004", "모니터", 15, 350000L, "전자제품"));
        productRepository.save(new Product("P005", "헤드셋", 25, 230000L, "주변기기"));

        log.info("상품 {} 개 로딩 완료", 5);
    }

    private void initUsers() {
        userRepository.save(new User("U001", "김항해", 500000L));
        userRepository.save(new User("U002", "이플러스", 1000000L));
        userRepository.save(new User("U003", "박백엔드", 300000L));

        log.info("사용자 {} 명 로딩 완료", 3);
    }
}
```

---

## ✅ Pass 기준

### Repository 패턴 적용
- [ ] Repository 인터페이스가 Domain Layer에 위치
- [ ] Repository 구현체가 Infrastructure Layer에 위치
- [ ] ConcurrentHashMap으로 Thread-safe 보장

### 코드 품질
- [ ] Domain이 Infrastructure를 의존하지 않음
- [ ] 메서드명이 도메인 용어 사용 (findById, findAll)
- [ ] DataInitializer로 초기 데이터 로딩

### 테스트
- [ ] Mock Repository로 UseCase 테스트 가능
- [ ] Repository 단위 테스트 작성

---

## ❌ Fail 사유

### Repository Fail
- ❌ Repository 인터페이스가 Infrastructure에 위치
- ❌ Domain이 Repository 구현체를 직접 의존
- ❌ HashMap 사용 (Thread-unsafe)

### 네이밍 Fail
- ❌ selectById, insertProduct 등 기술 용어 사용
- ❌ DAO와 Repository 혼용
- ❌ 일관성 없는 네이밍

---

## 🎯 학습 체크리스트

### 이론 이해
- [ ] Repository 패턴의 목적을 설명할 수 있다
- [ ] Repository와 DAO의 차이를 설명할 수 있다
- [ ] 인터페이스와 구현체를 분리하는 이유를 설명할 수 있다

### 실전 적용
- [ ] Repository 인터페이스를 Domain에 정의할 수 있다
- [ ] In-Memory Repository를 구현할 수 있다
- [ ] Mock Repository로 테스트를 작성할 수 있다

### 토론 주제
- "Repository 인터페이스를 왜 Domain에 두나요?"
- "ConcurrentHashMap을 선택한 이유는?"
- "DataInitializer는 어느 계층에 위치해야 하나요?"

---

## 💡 실전 팁

### Week 2 Mock → Week 3 Repository 전환
```java
// Week 2 (Controller에 ConcurrentHashMap)
@RestController
public class ProductController {
    private final Map<String, Product> products = new ConcurrentHashMap<>();  // ❌

    @GetMapping("/products/{id}")
    public Product getProduct(@PathVariable String id) {
        return products.get(id);
    }
}

// Week 3 (Repository 패턴)
@RestController
@RequiredArgsConstructor
public class ProductController {
    private final ProductUseCase productUseCase;  // ✅

    @GetMapping("/products/{id}")
    public ApiResponse<ProductResponse> getProduct(@PathVariable String id) {
        return ApiResponse.success(productUseCase.getProduct(id));
    }
}

// Repository (Infrastructure Layer)
@Repository
public class InMemoryProductRepository implements ProductRepository {
    private final Map<String, Product> storage = new ConcurrentHashMap<>();  // ✅
    // ...
}
```

---

**이전 학습**: [03. 도메인 모델링](./03-domain-modeling.md)
**다음 학습**: [05. 동시성 제어](./05-concurrency-control.md)
