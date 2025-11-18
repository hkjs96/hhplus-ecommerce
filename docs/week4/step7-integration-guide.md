# Week 4 - STEP 7: DB 통합 과제 가이드

> **과제 기간**: 2025-XX-XX ~ 2025-XX-XX
> **제출 방식**: PR + 코드 리뷰

---

## 📋 과제 개요

Week 3에서 In-Memory 기반으로 구현한 레이어드 아키텍처를 **실제 데이터베이스(MySQL)**와 연동합니다.

**핵심 목표**:
1. JPA를 활용한 데이터베이스 통합
2. 비즈니스 유즈케이스 완성 (Application Layer)
3. 실제 DB를 사용한 통합 테스트 작성

---

## ✅ 체크리스트

### 1. 환경 설정
- [ ] MySQL 설치 및 데이터베이스 생성
- [ ] `application.yml`에 MySQL 연결 정보 설정
- [ ] `build.gradle`에 JPA 의존성 추가

### 2. Entity 변환
- [ ] Week 3의 도메인 모델을 JPA Entity로 변환
- [ ] `@Entity`, `@Table`, `@Id`, `@Column` 어노테이션 추가
- [ ] 연관 관계 매핑 (`@OneToMany`, `@ManyToOne`)
- [ ] **비즈니스 로직 메서드는 그대로 유지**

### 3. Repository 구현
- [ ] `JpaRepository`를 상속한 Repository 인터페이스 작성
- [ ] 커스텀 쿼리 메서드 작성 (필요 시)
- [ ] In-Memory Repository 구현체 제거

### 4. Application Layer 완성
- [ ] 비즈니스 유즈케이스를 UseCase 클래스로 구현
- [ ] `@Transactional` 적용
- [ ] 읽기 전용 메서드는 `readOnly=true` 설정

### 5. 통합 테스트 작성
- [ ] Repository 테스트 (`@DataJpaTest`)
- [ ] UseCase 통합 테스트 (`@SpringBootTest`)
- [ ] 동시성 테스트 MySQL 환경에서 통과 확인
- [ ] 트랜잭션 롤백 동작 검증

### 6. ERD 개선 (선택)
- [ ] 기존 ERD 검토 및 개선점 파악
- [ ] 정규화 수준 검토 (1NF ~ 3NF)
- [ ] 연관 관계 명확히 정의

---

## 🛠️ 구현 가이드

### Step 1: MySQL 환경 구성

#### 1.1. MySQL 설치 (Windows/Mac/Linux)

**Windows:**
```bash
# MySQL Installer 다운로드 및 설치
# https://dev.mysql.com/downloads/installer/
```

**Mac (Homebrew):**
```bash
brew install mysql
brew services start mysql
```

**Linux (Ubuntu):**
```bash
sudo apt update
sudo apt install mysql-server
sudo systemctl start mysql
```

#### 1.2. 데이터베이스 생성

```sql
-- MySQL 접속
mysql -u root -p

-- 데이터베이스 생성
CREATE DATABASE ecommerce CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 사용자 생성 및 권한 부여 (선택)
CREATE USER 'hhplus'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON ecommerce.* TO 'hhplus'@'localhost';
FLUSH PRIVILEGES;
```

#### 1.3. application.yml 설정

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ecommerce?serverTimezone=Asia/Seoul
    username: root  # 또는 hhplus
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: validate  # 운영: validate, 개발: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        default_batch_fetch_size: 100
    open-in-view: false

logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.type.descriptor.sql.BasicBinder: trace
```

#### 1.4. build.gradle 의존성

```gradle
dependencies {
    // JPA
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // MySQL Driver
    runtimeOnly 'com.mysql:mysql-connector-j'

    // H2 (테스트용)
    testRuntimeOnly 'com.h2:h2'
}
```

---

### Step 2: Entity 변환

#### 2.1. Product Entity 예시

**Week 3 (순수 Java 클래스):**
```java
@Getter
@AllArgsConstructor
public class Product {
    private String id;
    private String name;
    private Long price;
    private Integer stock;

    public void decreaseStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
        this.stock -= quantity;
    }
}
```

**Week 4 (JPA Entity):**
```java
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // String → Long 변경

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private Integer stock;

    @Column(length = 50)
    private String category;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ⭐ 비즈니스 로직은 그대로 유지
    public void decreaseStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
        this.stock -= quantity;
    }

    public void restoreStock(int quantity) {
        this.stock += quantity;
    }

    // 생성 메서드
    public static Product create(String name, Long price, Integer stock, String category) {
        Product product = new Product();
        product.name = name;
        product.price = price;
        product.stock = stock;
        product.category = category;
        return product;
    }
}
```

**주요 변경 사항:**
1. `@Entity` 추가
2. PK를 String → Long으로 변경 (Auto Increment)
3. `@NoArgsConstructor` 추가 (JPA 필수)
4. `@CreatedDate`, `@LastModifiedDate` 추가 (Auditing)
5. **비즈니스 로직 메서드는 그대로 유지**

---

#### 2.2. 연관 관계 매핑 (Order - OrderItem)

```java
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false)
    private Long subtotalAmount;

    @Column(nullable = false)
    private Long totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 연관 관계 편의 메서드
    public void addOrderItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    // 비즈니스 로직
    public void complete() {
        if (status != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.COMPLETED;
    }
}
```

---

### Step 3: Repository 구현

#### 3.1. Domain Repository 인터페이스 (Domain Layer)

```java
package io.hhplus.ecommerce.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Optional<Product> findById(Long id);
    List<Product> findAll();
    List<Product> findByCategory(String category);
    Product save(Product product);
    void deleteById(Long id);

    // Week 3에서 배운 패턴
    default Product findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.PRODUCT_NOT_FOUND,
                "상품을 찾을 수 없습니다. productId: " + id
            ));
    }
}
```

#### 3.2. JPA Repository 구현체 (Infrastructure Layer)

```java
package io.hhplus.ecommerce.infrastructure.persistence.product;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaProductRepository extends JpaRepository<Product, Long>, ProductRepository {

    // 메서드 네이밍 쿼리 (자동 생성)
    List<Product> findByCategory(String category);

    // 커스텀 쿼리 (JPQL)
    @Query("SELECT p FROM Product p WHERE p.stock > 0")
    List<Product> findAvailableProducts();
}
```

#### 3.3. In-Memory Repository 제거

```bash
# 제거할 파일들
src/main/java/io/hhplus/ecommerce/infrastructure/persistence/product/InMemoryProductRepository.java
src/main/java/io/hhplus/ecommerce/infrastructure/persistence/order/InMemoryOrderRepository.java
# ... 기타 InMemory 구현체들
```

---

### Step 4: Application Layer 완성

#### 4.1. ProductUseCase

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 기본 readOnly
public class ProductUseCase {

    private final ProductRepository productRepository;

    public List<ProductResponse> getProducts(String category) {
        List<Product> products;

        if (category != null && !category.isEmpty()) {
            products = productRepository.findByCategory(category);
        } else {
            products = productRepository.findAll();
        }

        return products.stream()
            .map(ProductResponse::from)
            .toList();
    }

    public ProductResponse getProduct(Long productId) {
        Product product = productRepository.findByIdOrThrow(productId);
        return ProductResponse.from(product);
    }
}
```

#### 4.2. OrderUseCase

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderUseCase {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional  // 쓰기 작업은 readOnly=false
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findByIdOrThrow(request.getUserId());

        // 2. 상품 조회 및 재고 차감
        List<OrderItem> orderItems = new ArrayList<>();
        long subtotal = 0L;

        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findByIdOrThrow(itemRequest.getProductId());

            // 재고 차감 (Dirty Checking으로 자동 UPDATE)
            product.decreaseStock(itemRequest.getQuantity());

            OrderItem orderItem = OrderItem.create(product, itemRequest.getQuantity());
            orderItems.add(orderItem);
            subtotal += orderItem.getSubtotal();
        }

        // 3. 주문 생성
        Order order = Order.create(user, orderItems, subtotal);
        Order savedOrder = orderRepository.save(order);

        return OrderResponse.from(savedOrder);
    }
}
```

---

### Step 5: 통합 테스트 작성

#### 5.1. Repository 테스트 (@DataJpaTest)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)  // 실제 MySQL 사용
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("상품 저장 및 조회")
    void 상품_저장_및_조회() {
        // Given
        Product product = Product.create("노트북", 890000L, 10, "전자제품");

        // When
        Product saved = productRepository.save(product);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("노트북");
    }
}
```

#### 5.2. UseCase 통합 테스트 (@SpringBootTest)

```java
@SpringBootTest
@Transactional  // 각 테스트 후 롤백
class OrderUseCaseIntegrationTest {

    @Autowired
    private OrderUseCase orderUseCase;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    private Long productId;
    private Long userId;

    @BeforeEach
    void setUp() {
        Product product = productRepository.save(
            Product.create("노트북", 890000L, 10, "전자제품")
        );
        productId = product.getId();

        User user = userRepository.save(User.create("김항해", 2000000L));
        userId = user.getId();
    }

    @Test
    @DisplayName("주문 생성 및 재고 차감")
    void 주문_생성_및_재고_차감() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
            userId,
            List.of(new OrderItemRequest(productId, 2)),
            null
        );

        // When
        OrderResponse response = orderUseCase.createOrder(request);

        // Then
        assertThat(response.getOrderId()).isNotNull();

        // 재고 확인
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(8);  // 10 - 2 = 8
    }

    @Test
    @DisplayName("재고 부족 시 롤백 확인")
    void 재고_부족_시_롤백() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
            userId,
            List.of(new OrderItemRequest(productId, 20)),  // 재고보다 많음
            null
        );

        // When & Then
        assertThatThrownBy(() -> orderUseCase.createOrder(request))
            .isInstanceOf(BusinessException.class);

        // 재고가 롤백되어 원래대로 유지
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(10);
    }
}
```

#### 5.3. 동시성 테스트 (MySQL 환경)

```java
@SpringBootTest
class CouponConcurrencyIntegrationTest {

    @Autowired
    private CouponUseCase couponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Test
    @DisplayName("선착순 쿠폰 발급 동시성 테스트")
    void 선착순_쿠폰_발급_동시성_테스트() throws InterruptedException {
        // Given
        Coupon coupon = Coupon.create("C001", "10% 할인", 10, 100);
        couponRepository.save(coupon);

        int threadCount = 200;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    String userId = "U" + String.format("%03d", index);
                    couponUseCase.issueCoupon(userId, "C001");
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(100);
    }
}
```

---

## 🚨 주의사항

### 1. 비즈니스 로직 유지

**❌ 나쁨 (Anemic Domain Model):**
```java
@Entity
public class Product {
    private Long id;
    private Integer stock;
    // getter/setter만 존재
}

// Service에 비즈니스 로직
@Service
public class ProductService {
    public void decreaseStock(Product product, int quantity) {
        product.setStock(product.getStock() - quantity);  // 검증 없음
    }
}
```

**✅ 좋음 (Rich Domain Model):**
```java
@Entity
public class Product {
    private Long id;
    private Integer stock;

    // Entity에 비즈니스 로직 유지
    public void decreaseStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
        this.stock -= quantity;
    }
}
```

---

### 2. 트랜잭션 위치

**❌ 나쁨:**
```java
// Controller에 @Transactional (너무 넓은 경계)
@RestController
@Transactional
public class OrderController { }

// Entity에 @Transactional (계층 혼재)
@Entity
@Transactional
public class Order { }
```

**✅ 좋음:**
```java
// UseCase(Service)에만 @Transactional
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderUseCase {

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 트랜잭션 경계
    }
}
```

---

### 3. 지연 로딩 (Lazy Loading)

**기본 전략: 지연 로딩 사용**
```java
@Entity
public class Order {
    @ManyToOne(fetch = FetchType.LAZY)  // 지연 로딩
    private User user;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderItem> items;
}
```

**N+1 문제 해결:**
```java
// Fetch Join
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.id = :id")
Optional<Order> findByIdWithItems(@Param("id") Long id);

// 또는 Batch Size 설정
spring.jpa.properties.hibernate.default_batch_fetch_size=100
```

---

### 4. OSIV 비활성화 권장

```yaml
spring:
  jpa:
    open-in-view: false  # OSIV 비활성화
```

**이유:**
- Controller에서 지연 로딩 방지
- N+1 문제 조기 발견
- 트랜잭션 경계 명확화

---

## 📚 참고 자료

### 공식 문서
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Hibernate ORM Documentation](https://hibernate.org/orm/documentation/)
- [MySQL 8.0 Reference Manual](https://dev.mysql.com/doc/refman/8.0/en/)

### 추천 강의
- [자바 ORM 표준 JPA 프로그래밍 - 김영한](https://www.inflearn.com/course/ORM-JPA-Basic)
- [실전! 스프링 데이터 JPA - 김영한](https://www.inflearn.com/course/스프링-데이터-JPA-실전)

### Week 3 복습
- `.claude/commands/architecture.md`: Repository 패턴, 레이어 분리
- `.claude/commands/testing.md`: 테스트 전략
- `.claude/commands/concurrency.md`: 동시성 제어

---

## 💡 자주 묻는 질문 (FAQ)

### Q1. H2와 MySQL 중 어떤 것을 사용해야 하나요?

**A:** 둘 다 사용하세요.
- **개발/테스트**: H2 In-Memory (빠른 테스트)
- **통합 테스트**: MySQL (실제 환경 검증)

```yaml
# application.yml (개발)
spring:
  profiles:
    active: local

---
# application-local.yml (H2)
spring:
  datasource:
    url: jdbc:h2:mem:testdb

---
# application-dev.yml (MySQL)
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ecommerce
```

---

### Q2. 양방향 연관 관계를 사용해야 하나요?

**A:** 단방향을 먼저 고려하세요.

**단방향 (권장):**
```java
@Entity
public class Order {
    @OneToMany
    @JoinColumn(name = "order_id")
    private List<OrderItem> items;
}
```

**양방향 (필요 시):**
```java
@Entity
public class Order {
    @OneToMany(mappedBy = "order")
    private List<OrderItem> items;
}

@Entity
public class OrderItem {
    @ManyToOne
    private Order order;
}
```

---

### Q3. DDL Auto 옵션은 무엇을 사용해야 하나요?

**A:** 환경에 따라 다릅니다.

| 옵션 | 사용 환경 | 설명 |
|------|----------|------|
| `create` | 로컬 개발 | 매번 DROP → CREATE |
| `create-drop` | 테스트 | 종료 시 DROP |
| `update` | 개발 | 변경 사항만 반영 (위험) |
| `validate` | 운영 | 검증만 (변경 없음) |
| `none` | 운영 | 아무것도 안 함 |

**권장:**
- 로컬 개발: `update` 또는 `create`
- 통합 테스트: `create-drop`
- 운영: `validate`

---

## 🎯 제출 전 최종 체크리스트

### 코드
- [ ] JPA Entity 변환 완료
- [ ] In-Memory Repository 제거
- [ ] `@Transactional` 적절히 적용
- [ ] 비즈니스 로직 메서드 유지

### 테스트
- [ ] Repository 테스트 작성
- [ ] UseCase 통합 테스트 작성
- [ ] 동시성 테스트 MySQL 환경에서 통과
- [ ] 테스트 커버리지 70% 이상

### 문서
- [ ] ERD 개선 (선택)
- [ ] README.md 업데이트

### 환경
- [ ] MySQL 연동 확인
- [ ] 애플리케이션 정상 실행
- [ ] 테스트 전체 통과

---

## 다음 단계

STEP 8에서는 **DB 최적화**를 수행합니다:
- 조회 성능 저하 가능성 식별
- 쿼리 실행계획(EXPLAIN) 분석
- 인덱스 설계 및 최적화 보고서 작성

→ `docs/week4/step8-optimization-report-template.md` 참조

---

## 제출 방법

1. **브랜치 생성**: `week4-step7-db-integration`
2. **커밋**: 기능별로 커밋 분리
   - `feat: JPA Entity 변환`
   - `feat: JPA Repository 구현`
   - `test: 통합 테스트 작성`
3. **PR 생성**: `main` 브랜치로 PR
4. **자가 리뷰**: 체크리스트 확인
5. **제출**: PR 링크 제출

---

**화이팅! 🚀**
