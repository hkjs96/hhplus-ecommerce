# Week 4 - STEP 7: DB 설계 개선 및 구현

## 과제 개요

**목표**: 도메인 기능과 비즈니스 로직을 조합하여 서비스의 비즈니스 유즈케이스를 작성하고, RDBMS(MySQL)와 연동하여 데이터 입출력을 구현합니다.

**핵심 작업**:
1. (선택) 기존 ERD 개선 및 테이블 구조 재설계
2. Infrastructure Layer를 In-Memory → MySQL로 전환
3. Application Layer 유즈케이스 완성
4. 기능별 통합 테스트 작성

---

## 🎯 과제 목표

### 1. 데이터베이스 통합
- Week 3의 In-Memory Repository를 MySQL 기반 JPA Repository로 전환
- 실제 데이터베이스와 연동하여 CRUD 동작 검증
- 트랜잭션 관리 및 영속성 컨텍스트 이해

### 2. 통합 테스트 작성
- 단위 테스트를 넘어서 **실제 데이터베이스를 사용한 통합 테스트** 작성
- 비즈니스 유즈케이스 전체 플로우 검증
- 데이터 격리 및 테스트 환경 구성

### 3. 레이어 간 협업
- Presentation → Application → Domain → Infrastructure 전체 흐름 검증
- 트랜잭션 경계 설정 및 롤백 동작 확인

---

## 📋 PASS/FAIL 기준

### ✅ PASS 조건

#### 1. 테이블 설계 개선 (선택)
- [ ] 비즈니스 요구사항을 반영한 ERD 작성
- [ ] 정규화 수준이 적절한가? (1NF ~ 3NF)
- [ ] 연관 관계가 명확하게 정의되어 있는가?
- [ ] 인덱스 설계 초안이 포함되어 있는가?

#### 2. Application Layer 작성
- [ ] 비즈니스 유즈케이스가 UseCase 클래스로 구현됨
- [ ] 각 UseCase가 명확한 단일 책임을 가지는가?
- [ ] DTO를 사용하여 레이어 간 데이터 전달
- [ ] 비즈니스 검증 로직이 적절히 위치

#### 3. Infrastructure Layer 작성
- [ ] JPA Repository 인터페이스 작성
- [ ] 커스텀 쿼리 메서드 구현 (필요 시)
- [ ] In-Memory Repository 제거
- [ ] MySQL 기반으로 동작

#### 4. 통합 테스트 작성
- [ ] Infrastructure 레이어를 포함한 통합 테스트 작성
- [ ] 핵심 기능의 전체 플로우가 테스트로 검증됨
- [ ] 기존 동시성 테스트가 MySQL 환경에서도 통과
- [ ] `@SpringBootTest` + 실제 DB 사용

#### 5. 트랜잭션 관리
- [ ] `@Transactional`이 UseCase에 적절히 적용
- [ ] 읽기 전용 작업은 `readOnly=true` 설정
- [ ] 예외 발생 시 롤백 동작 검증

---

### ❌ FAIL 사유

#### 구현 부족
- ❌ In-Memory Repository가 여전히 남아있음
- ❌ MySQL이 아닌 H2 In-Memory 모드만 사용
- ❌ JPA 대신 JDBC Template 직접 사용
- ❌ Application Layer가 없고 Controller에서 직접 Repository 호출

#### 테스트 부족
- ❌ 통합 테스트가 전혀 없음
- ❌ 단위 테스트만 있고 실제 DB 연동 검증 없음
- ❌ 동시성 테스트가 MySQL 환경에서 실패

#### 트랜잭션 관리 실패
- ❌ `@Transactional`이 없거나 잘못된 위치에 적용
- ❌ 예외 발생 시 롤백되지 않음
- ❌ 트랜잭션 격리 수준 이해 부족

---

## 🧠 핵심 역량 및 평가 포인트

### 1. 레이어드 아키텍처 완성도 🏗️

**평가 기준:**
- 각 레이어의 책임이 명확하게 분리되어 있는가?
- Presentation → Application → Domain → Infrastructure 의존성 방향이 올바른가?
- Domain Layer가 Infrastructure에 의존하지 않는가?

**토론 주제:**
- "Application Layer와 Domain Layer의 책임 분리는 어떻게 했나요?"
- "UseCase에서 여러 Repository를 조합하는 경우, 트랜잭션은 어떻게 관리했나요?"
- "DTO를 사용하는 이유는 무엇인가요? Entity를 직접 반환하면 안 되나요?"

---

### 2. 데이터베이스 통합 역량 🗄️

**평가 기준:**
- JPA를 올바르게 활용하여 데이터베이스와 연동했는가?
- 연관 관계 매핑이 적절한가? (양방향 vs 단방향)
- 지연 로딩(Lazy)과 즉시 로딩(Eager)을 이해하고 선택했는가?

**토론 주제:**
- "지연 로딩을 기본으로 사용한 이유는 무엇인가요?"
- "N+1 문제를 경험했나요? 어떻게 해결했나요?"
- "Fetch Join과 @EntityGraph의 차이는 무엇인가요?"

---

### 3. 통합 테스트 설계 역량 🧪

**평가 기준:**
- 실제 데이터베이스를 사용한 통합 테스트를 작성했는가?
- 테스트 간 데이터 격리가 보장되는가? (`@Transactional` 활용)
- 핵심 비즈니스 플로우가 통합 테스트로 검증되는가?

**토론 주제:**
- "통합 테스트와 단위 테스트의 차이는 무엇인가요?"
- "`@DataJpaTest`와 `@SpringBootTest`의 차이는 무엇인가요?"
- "Testcontainers를 사용했나요? 사용하지 않았다면 이유는?"

---

### 4. 트랜잭션 관리 이해도 🔄

**평가 기준:**
- `@Transactional`을 적절한 계층에 적용했는가?
- 트랜잭션 경계를 올바르게 설정했는가?
- 예외 발생 시 롤백 동작을 검증했는가?

**토론 주제:**
- "`@Transactional`을 어느 계층에 적용했나요? 그 이유는?"
- "`readOnly=true`를 언제 사용하나요?"
- "트랜잭션 격리 수준(Isolation Level)은 무엇인가요?"
- "RuntimeException과 Checked Exception의 롤백 동작 차이는?"

---

## 🛠️ 구현 가이드

### 1. MySQL 환경 구성

#### application.yml 설정

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ecommerce?serverTimezone=Asia/Seoul
    username: root
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
        default_batch_fetch_size: 100  # N+1 방지
    open-in-view: false  # OSIV 비활성화 권장

logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.type.descriptor.sql.BasicBinder: trace
```

---

### 2. JPA Entity 구현

#### Product Entity 예시

```java
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

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

    // 비즈니스 로직 (Week 3와 동일하게 유지)
    public void decreaseStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_STOCK,
                String.format("재고가 부족합니다. (요청: %d, 재고: %d)", quantity, stock)
            );
        }
        this.stock -= quantity;
    }

    public void restoreStock(int quantity) {
        this.stock += quantity;
    }

    // 생성 메서드
    public static Product create(String name, String description, Long price,
                                   Integer stock, String category) {
        Product product = new Product();
        product.name = name;
        product.description = description;
        product.price = price;
        product.stock = stock;
        product.category = category;
        return product;
    }
}
```

#### 연관 관계 매핑 예시 (Order - OrderItem)

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
    private Long discountAmount;

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

### 3. Spring Data JPA Repository

#### Repository 인터페이스

```java
@Repository
public interface JpaProductRepository extends JpaRepository<Product, Long>, ProductRepository {

    // 메서드 네이밍 쿼리 (자동 생성)
    List<Product> findByCategory(String category);

    // 커스텀 쿼리 (JPQL)
    @Query("SELECT p FROM Product p WHERE p.stock > 0")
    List<Product> findAvailableProducts();

    // Native Query (필요 시)
    @Query(value = "SELECT * FROM products WHERE price BETWEEN :minPrice AND :maxPrice",
           nativeQuery = true)
    List<Product> findByPriceRange(@Param("minPrice") Long minPrice,
                                     @Param("maxPrice") Long maxPrice);

    // Fetch Join (N+1 방지)
    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.items WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);
}
```

#### Domain Repository 인터페이스 (Domain Layer)

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

    // findByIdOrThrow() - Week 3에서 배운 패턴
    default Product findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.PRODUCT_NOT_FOUND,
                "상품을 찾을 수 없습니다. productId: " + id
            ));
    }
}
```

---

### 4. Application Layer (UseCase)

#### ProductUseCase 예시

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 기본 readOnly
public class ProductUseCase {

    private final ProductRepository productRepository;

    // 조회 전용 (readOnly=true 기본값 사용)
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

    // 조회 전용
    public ProductResponse getProduct(Long productId) {
        Product product = productRepository.findByIdOrThrow(productId);
        return ProductResponse.from(product);
    }
}
```

#### OrderUseCase 예시 (트랜잭션 관리)

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

    // 조회 전용 (readOnly=true 기본값 사용)
    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findByIdOrThrow(orderId);
        return OrderResponse.from(order);
    }
}
```

---

### 5. 통합 테스트 작성

#### Repository 테스트 (@DataJpaTest)

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
        Product product = Product.create("노트북", "고성능 게이밍 노트북", 890000L, 10, "전자제품");

        // When
        Product saved = productRepository.save(product);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("노트북");

        // 조회
        Product found = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("노트북");
        assertThat(found.getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("카테고리별 조회")
    void 카테고리별_조회() {
        // Given
        productRepository.save(Product.create("노트북", "설명", 890000L, 10, "전자제품"));
        productRepository.save(Product.create("키보드", "설명", 120000L, 20, "주변기기"));
        productRepository.save(Product.create("마우스", "설명", 45000L, 30, "주변기기"));

        // When
        List<Product> peripherals = productRepository.findByCategory("주변기기");

        // Then
        assertThat(peripherals).hasSize(2);
        assertThat(peripherals).extracting("name")
            .containsExactlyInAnyOrder("키보드", "마우스");
    }
}
```

#### UseCase 통합 테스트 (@SpringBootTest)

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
        // 테스트 데이터 준비
        Product product = productRepository.save(
            Product.create("노트북", "설명", 890000L, 10, "전자제품")
        );
        productId = product.getId();

        User user = userRepository.save(User.create("김항해", 2000000L));
        userId = user.getId();
    }

    @Test
    @DisplayName("주문 생성 및 재고 차감 통합 테스트")
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
        assertThat(response.getTotalAmount()).isEqualTo(1780000L);  // 890000 * 2

        // 재고 확인
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(8);  // 10 - 2 = 8
    }

    @Test
    @DisplayName("재고 부족 시 주문 실패 및 롤백")
    void 재고_부족_시_주문_실패_및_롤백() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
            userId,
            List.of(new OrderItemRequest(productId, 20)),  // 재고보다 많은 수량
            null
        );

        // When & Then
        assertThatThrownBy(() -> orderUseCase.createOrder(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);

        // 재고가 롤백되어 원래대로 유지되는지 확인
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(10);  // 롤백되어 그대로
    }
}
```

#### 동시성 테스트 (MySQL 환경)

```java
@SpringBootTest
class CouponConcurrencyIntegrationTest {

    @Autowired
    private CouponUseCase couponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @BeforeEach
    void setUp() {
        // 쿠폰 100개 생성
        Coupon coupon = Coupon.create("C001", "10% 할인 쿠폰", 10, 100);
        couponRepository.save(coupon);
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 동시성 테스트 (MySQL)")
    void 선착순_쿠폰_발급_동시성_테스트() throws InterruptedException {
        // Given
        String couponId = "C001";
        int threadCount = 200;  // 200명이 동시에 요청

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 200명이 동시에 쿠폰 발급 시도
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    String userId = "U" + String.format("%03d", index);
                    couponUseCase.issueCoupon(userId, couponId);
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

        // Then: 정확히 100개만 발급되어야 함
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(100);

        // DB 확인
        List<UserCoupon> issuedCoupons = userCouponRepository.findByCouponId(couponId);
        assertThat(issuedCoupons).hasSize(100);
    }
}
```

---

## ✅ 체크리스트

### Database 연동
- [ ] MySQL 설치 및 데이터베이스 생성
- [ ] application.yml에 MySQL 연결 정보 설정
- [ ] JPA Entity 변환 (비즈니스 로직 유지)
- [ ] Spring Data JPA Repository 구현
- [ ] In-Memory Repository 제거

### Application Layer
- [ ] 비즈니스 유즈케이스를 UseCase 클래스로 구현
- [ ] `@Transactional` 적절히 적용
- [ ] 읽기 전용 작업은 `readOnly=true` 설정

### 통합 테스트
- [ ] Repository 테스트 작성 (`@DataJpaTest`)
- [ ] UseCase 통합 테스트 작성 (`@SpringBootTest`)
- [ ] 동시성 테스트 MySQL 환경에서 통과
- [ ] 트랜잭션 롤백 동작 검증

### ERD 개선 (선택)
- [ ] 비즈니스 요구사항 반영
- [ ] 정규화 수준 검토 (1NF ~ 3NF)
- [ ] 연관 관계 명확히 정의
- [ ] 인덱스 설계 초안 포함

---

## 🚨 주의사항

### 1. OSIV(Open Session In View) 비활성화 권장

```yaml
spring:
  jpa:
    open-in-view: false  # OSIV 비활성화
```

**이유:**
- Controller에서 지연 로딩 방지 (N+1 문제 조기 발견)
- 트랜잭션 경계를 명확히 (UseCase에서만)
- 프로덕션 환경에서 성능 저하 방지

---

### 2. 양방향 연관 관계 주의

**양방향보다 단방향을 우선 고려:**
```java
// ❌ 양방향 (복잡도 증가)
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

// ✅ 단방향 (단순, 명확)
@Entity
public class Order {
    @OneToMany
    @JoinColumn(name = "order_id")
    private List<OrderItem> items;
}

@Entity
public class OrderItem {
    // Order 참조 없음
}
```

**양방향이 필요한 경우:**
- 연관 관계 편의 메서드 작성
- `toString()`, `equals()`, `hashCode()`에서 순환 참조 주의

---

### 3. Cascade 옵션 신중히 사용

```java
// ⚠️ CascadeType.ALL 사용 시 주의
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
private List<OrderItem> items;
```

**안전한 Cascade 전략:**
- `CascadeType.PERSIST`: 저장만 전파
- `CascadeType.REMOVE`: 삭제만 전파
- `orphanRemoval = true`: 고아 객체 자동 제거

---

### 4. 테스트 데이터 격리

```java
@SpringBootTest
@Transactional  // 각 테스트 후 롤백
class IntegrationTest {
    // 각 테스트가 독립적으로 실행됨
}
```

**대안:**
- `@DirtiesContext`: 전체 컨텍스트 재시작 (느림)
- `@Sql`: SQL 스크립트로 데이터 초기화

---

## 🧪 테스트 환경 구성

### Docker Compose를 활용한 테스트 환경

**docker-compose.yml 예시:**

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: ecommerce-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root_password
      MYSQL_DATABASE: ecommerce
      MYSQL_USER: hhplus
      MYSQL_PASSWORD: your_password
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci

volumes:
  mysql_data:
```

**실행 방법:**

```bash
# Docker Compose 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f mysql

# 중지
docker-compose down

# 데이터 포함 완전 삭제
docker-compose down -v
```

---

### Testcontainers를 활용한 통합 테스트

**build.gradle 의존성 추가:**

```gradle
dependencies {
    // Testcontainers
    testImplementation 'org.testcontainers:testcontainers:1.19.3'
    testImplementation 'org.testcontainers:mysql:1.19.3'
    testImplementation 'org.testcontainers:junit-jupiter:1.19.3'
}
```

**테스트 베이스 클래스:**

```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("ecommerce_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
}
```

**테스트 작성:**

```java
class OrderUseCaseIntegrationTest extends IntegrationTestBase {

    @Autowired
    private OrderUseCase orderUseCase;

    @Test
    @DisplayName("주문 생성 통합 테스트 (Testcontainers)")
    void 주문_생성_통합_테스트() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(...);

        // When
        OrderResponse response = orderUseCase.createOrder(request);

        // Then
        assertThat(response.getOrderId()).isNotNull();
    }
}
```

**장점:**
- ✅ 실제 MySQL 컨테이너 사용 (운영 환경과 동일)
- ✅ 테스트 격리 보장 (각 테스트마다 새 컨테이너)
- ✅ CI/CD 파이프라인에서도 동작

**공식 문서**: [Testcontainers](https://testcontainers.com/)

---

## 🔍 쿼리 로깅 및 디버깅

### 1. Hibernate show_sql

**application.yml:**

```yaml
spring:
  jpa:
    show-sql: true  # SQL 출력
    properties:
      hibernate:
        format_sql: true  # SQL 포맷팅
        use_sql_comments: true  # SQL 주석 (어떤 Entity에서 발생했는지)
```

**출력 예시:**

```sql
Hibernate:
    /* select
        generatedAlias0
    from
        Product as generatedAlias0 */
    select
        product0_.id as id1_0_,
        product0_.name as name2_0_,
        product0_.price as price3_0_
    from
        products product0_
```

---

### 2. p6spy를 활용한 실제 쿼리 로깅

**build.gradle:**

```gradle
dependencies {
    implementation 'com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.9.0'
}
```

**application.yml:**

```yaml
decorator:
  datasource:
    p6spy:
      enable-logging: true
      multiline: true
      logging: slf4j
```

**출력 예시 (바인딩 파라미터 포함):**

```sql
#1704441600000 | took 2ms | statement | connection 0 |
SELECT p.id, p.name, p.price
FROM products p
WHERE p.category = 'electronics'
  AND p.stock > 0

-- 실제 실행된 쿼리 (파라미터 바인딩 완료)
SELECT p.id, p.name, p.price
FROM products p
WHERE p.category = '전자제품'
  AND p.stock > 0
```

**장점:**
- ✅ 실제 실행된 쿼리 확인 (바인딩 파라미터 포함)
- ✅ 쿼리 실행 시간 측정
- ✅ N+1 문제 쉽게 발견

---

## ⚠️ JPA Entity 주의사항

### 1. Lombok 어노테이션 주의

#### ❌ 나쁨: @Data, @ToString

```java
@Entity
@Data  // ❌ toString(), equals(), hashCode() 자동 생성 (위험)
public class Order {
    @Id
    private Long id;

    @OneToMany(mappedBy = "order")
    private List<OrderItem> items;  // StackOverflowError 위험!
}

@Entity
@Data
public class OrderItem {
    @Id
    private Long id;

    @ManyToOne
    private Order order;  // 순환 참조!
}
```

**문제점:**
- `toString()` 호출 시 순환 참조로 `StackOverflowError` 발생
- `equals()`, `hashCode()`가 연관 엔티티를 포함하여 N+1 문제 유발

---

#### ✅ 좋음: @Getter + 필요한 것만

```java
@Entity
@Getter
@NoArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "order")
    private List<OrderItem> items;

    // toString() 직접 구현 (연관 엔티티 제외)
    @Override
    public String toString() {
        return "Order{id=" + id + "}";
    }
}
```

**권장 사항:**
- ✅ `@Getter`: 사용 가능
- ✅ `@NoArgsConstructor`: JPA 필수
- ⚠️ `@ToString`: 연관 엔티티 제외 (`@ToString(exclude = {"items"})`)
- ❌ `@Data`: 절대 사용 금지
- ❌ `@EqualsAndHashCode`: 신중히 사용 (PK만 포함 권장)

---

### 2. Fetch 타입 (EAGER vs LAZY)

#### ❌ 나쁨: EAGER (즉시 로딩)

```java
@Entity
public class Order {
    @ManyToOne(fetch = FetchType.EAGER)  // ❌ N+1 문제 발생
    private User user;

    @OneToMany(mappedBy = "order", fetch = FetchType.EAGER)  // ❌ 매우 위험
    private List<OrderItem> items;
}
```

**문제점:**
- N+1 문제 발생
- 불필요한 데이터까지 모두 조회
- 성능 저하

---

#### ✅ 좋음: LAZY (지연 로딩)

```java
@Entity
public class Order {
    @ManyToOne(fetch = FetchType.LAZY)  // ✅ 지연 로딩
    private User user;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)  // ✅ 지연 로딩
    private List<OrderItem> items;
}
```

**기본 전략:**
- `@ManyToOne`: 기본값 EAGER → **명시적으로 LAZY 설정**
- `@OneToMany`: 기본값 LAZY (그대로 사용)
- `@OneToOne`: 기본값 EAGER → **명시적으로 LAZY 설정**
- `@ManyToMany`: 기본값 LAZY (그대로 사용)

**N+1 문제 해결:**

```java
// Fetch Join 사용
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.id = :id")
Optional<Order> findByIdWithItems(@Param("id") Long id);

// 또는 Batch Size 설정
spring.jpa.properties.hibernate.default_batch_fetch_size=100
```

---

## 🗄️ PK 선정 전략

### UUID vs Long (Auto Increment)

#### UUID 사용 시 주의사항

**❌ 나쁨: 순서가 없는 UUID**

```java
@Entity
public class Order {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;  // 성능 저하!
}
```

**문제점:**
- 인덱스 성능 저하 (B-Tree 재정렬 빈번)
- 디스크 I/O 증가
- 메모리 사용량 증가 (16 bytes vs 8 bytes)

**참고 자료:**
- [UUIDs are Popular, but Bad for Performance](https://www.percona.com/blog/uuids-are-popular-but-bad-for-performance-lets-discuss/)
- [Store UUID in an Optimized Way](https://www.percona.com/blog/store-uuid-optimized-way/)

---

**✅ 좋음: Long (Auto Increment)**

```java
@Entity
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // 순차적, 성능 우수
}
```

**장점:**
- 순차적 증가로 인덱스 성능 우수
- 8 bytes로 작은 크기
- 클러스터링 인덱스 효율적

**권장:**
- 단일 DB 환경: **Long (Auto Increment)** 사용
- 분산 환경: TSID (Time-Sorted Unique Identifier) 고려

---

## 📚 참고 자료

### 필수 참고 자료
- [Database System Concepts](https://www.db-book.com/)
- [Use The Index, Luke!](https://use-the-index-luke.com/)
- [High Performance MySQL](https://www.oreilly.com/library/view/high-performance-mysql/9781492080503/)

### 추천 학습 자료
- [SQL Performance Explained](https://sql-performance-explained.com/)
- [자바 ORM 표준 JPA 프로그래밍 - 김영한](https://www.inflearn.com/course/ORM-JPA-Basic)
- [실전! 스프링 데이터 JPA - 김영한](https://www.inflearn.com/course/스프링-데이터-JPA-실전)

### 공식 문서
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Hibernate ORM Documentation](https://hibernate.org/orm/documentation/)
- [MySQL 8.0 Reference Manual](https://dev.mysql.com/doc/refman/8.0/en/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)

### 도구 및 서비스
- [MySQL Workbench](https://www.mysql.com/products/workbench/) - MySQL GUI 도구
- [DataGrip](https://www.jetbrains.com/datagrip/) - JetBrains DB 도구
- [Testcontainers](https://testcontainers.com/) - 통합 테스트 컨테이너

### Week 3 복습
- `@.claude/commands/architecture.md`: Repository 패턴, 레이어 분리
- `@.claude/commands/testing.md`: 테스트 전략, F.I.R.S.T 원칙
- `@.claude/commands/concurrency.md`: 동시성 제어 패턴

---

## 🎓 성공적인 과제 제출을 위한 팁

1. **Week 3 비즈니스 로직 유지**: Entity의 메서드는 그대로 유지하세요.
2. **트랜잭션 경계 명확히**: UseCase에만 `@Transactional` 적용.
3. **통합 테스트 충실히**: 실제 DB 사용하여 전체 플로우 검증.
4. **N+1 문제 주의**: Fetch Join 또는 `default_batch_fetch_size` 설정.
5. **동시성 테스트 통과**: Week 3에서 작성한 동시성 제어가 MySQL에서도 동작하는지 확인.

---

## 다음 단계

STEP 8에서는 **DB 최적화**를 수행합니다:
- 조회 성능 저하 가능성 식별
- 쿼리 실행계획(Explain) 분석
- 인덱스 설계 및 쿼리 재구성

→ `@.claude/commands/week4-step8.md` 참조
