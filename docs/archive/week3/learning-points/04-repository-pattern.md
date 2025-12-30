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

**더 자세한 내용**: [09. Thread-Safe 컬렉션 (ConcurrentHashMap)](./09-concurrent-collections.md)에서 내부 동작 원리, 주요 메서드, 성능 비교를 확인하세요.

---

## 🔧 실전 패턴: findByIdOrThrow() ⭐

### 문제 상황: 반복되는 코드 패턴

**코치 피드백:**
> 반복되는 코드는 공통 메서드로 추출하세요. Repository 레이어에 구현하여 재사용하면 코드 중복을 줄일 수 있습니다.

**반복되는 패턴:**
```java
// CouponService
Coupon coupon = couponRepository.findById(couponId)
    .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

// CartService
Cart cart = cartRepository.findById(cartId)
    .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

// PointService
User user = userRepository.findById(userId)
    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
```

**문제점:**
- ❌ 모든 서비스에서 동일한 패턴 반복
- ❌ 코드 중복 (같은 로직을 여러 곳에 작성)
- ❌ 유지보수 어려움 (ErrorCode 변경 시 모든 곳 수정 필요)

---

### 해결 방법 3가지

#### ✅ Option 1: Repository Custom Method (가장 추천)

**장점:**
- ✅ 각 Repository에 적절한 ErrorCode 내장
- ✅ 가장 간결한 사용법
- ✅ 타입 안전성
- ✅ IDE 자동완성 지원

```java
// Domain Repository Interface
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    // Custom method 추가
    default Coupon findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.COUPON_NOT_FOUND,
                "Coupon not found. couponId: " + id
            ));
    }
}

// 사용
Coupon coupon = couponRepository.findByIdOrThrow(couponId);  // 간결!
```

**적용 예시:**
```java
// ProductRepository
public interface ProductRepository {
    Optional<Product> findById(String id);

    default Product findByIdOrThrow(String id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.PRODUCT_NOT_FOUND,
                "Product not found. productId: " + id
            ));
    }
}

// OrderRepository
public interface OrderRepository {
    Optional<Order> findById(String id);

    default Order findByIdOrThrow(String id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.ORDER_NOT_FOUND,
                "Order not found. orderId: " + id
            ));
    }
}
```

---

#### Option 2: Common Utility Method

**장점:**
- ✅ 중앙 집중식 관리
- ❌ 사용할 때마다 3개 인자 전달 필요

```java
// Common Utility Class
public class RepositoryUtils {

    public static <T, ID> T findByIdOrThrow(
        JpaRepository<T, ID> repository,
        ID id,
        ErrorCode errorCode
    ) {
        return repository.findById(id)
            .orElseThrow(() -> new BusinessException(errorCode));
    }
}

// 사용
Coupon coupon = RepositoryUtils.findByIdOrThrow(
    couponRepository,
    couponId,
    ErrorCode.COUPON_NOT_FOUND
);
```

---

#### Option 3: Base Repository Interface (고급)

**장점:**
- ✅ 모든 Repository가 공통 기능 상속
- ❌ 설계 복잡도 증가

```java
// Base Repository Interface
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {

    default T findByIdOrThrow(ID id, ErrorCode errorCode) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(errorCode));
    }
}

// 각 Repository가 상속
public interface CouponRepository extends BaseRepository<Coupon, Long> {
    // 추가 메서드만 정의
}

// 사용
Coupon coupon = couponRepository.findByIdOrThrow(couponId, ErrorCode.COUPON_NOT_FOUND);
```

---

### 권장: Option 1 선택 이유

1. **간결성**: 메서드 호출이 가장 짧음
2. **타입 안전성**: 각 Repository에 맞는 Entity 타입 보장
3. **ErrorCode 내장**: 각 도메인에 적합한 에러 코드 자동 적용
4. **IDE 지원**: 자동완성으로 쉽게 발견 가능

**적용 대상:**
- [ ] ProductRepository
- [ ] UserRepository
- [ ] OrderRepository
- [ ] CartRepository
- [ ] CouponRepository

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

**이전 학습**: [03. 도메인 모델링](../../../learning-points/03-domain-modeling.md)
**다음 학습**: [05. 동시성 제어](./05-concurrency-control.md)
