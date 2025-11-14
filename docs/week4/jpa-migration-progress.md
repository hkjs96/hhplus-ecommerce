# JPA Migration Progress - Order Domain

**작업일**: 2025.01.12
**목표**: Order 도메인 JPA Repository 구현 및 MySQL Testcontainers 테스트

---

## ✅ 완료된 작업

### 1. Gradle 의존성 추가

```gradle
dependencies {
    // JPA & Database
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'com.mysql:mysql-connector-j'

    // Testcontainers (MySQL 8.0 기반 테스트)
    testImplementation 'org.testcontainers:testcontainers:1.19.3'
    testImplementation 'org.testcontainers:junit-jupiter:1.19.3'
    testImplementation 'org.testcontainers:mysql:1.19.3'
}
```

**변경사항:**
- ❌ H2 Database 제거 (MySQL만 사용)
- ✅ MySQL Connector만 유지
- ✅ Testcontainers MySQL 8.0 사용

### 2. application.yml 설정 (MySQL 전용)

```yaml
spring:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: update  # 개발 시 update, 프로덕션에서는 none
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        highlight_sql: true
        default_batch_fetch_size: 100  # N+1 문제 방지
        dialect: org.hibernate.dialect.MySQLDialect
    show-sql: false

  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/ecommerce?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul
    username: root
    password: password
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
```

### 3. Docker Compose (로컬 MySQL 실행)

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: ecommerce-mysql
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: ecommerce
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
```

**로컬 MySQL 실행 방법:**
```bash
# MySQL 컨테이너 시작
docker-compose up -d

# 애플리케이션 실행
./gradlew bootRun

# MySQL 컨테이너 종료
docker-compose down
```

### 4. JPA Repository 구현

#### JpaOrderRepository
```java
@Repository
@Primary
public interface JpaOrderRepository extends JpaRepository<Order, Long>, OrderRepository {

    @Override
    Optional<Order> findByOrderNumber(String orderNumber);

    @Override
    @Query("SELECT o FROM Order o WHERE o.userId = :userId ORDER BY o.createdAt DESC")
    List<Order> findByUserId(@Param("userId") Long userId);
}
```

**특징:**
- `@Primary`: 기본 Repository로 설정 (InMemoryOrderRepository 대신 사용)
- Spring Data JPA의 Query Method 활용
- Domain의 OrderRepository 인터페이스 구현

#### JpaOrderItemRepository
```java
@Repository
@Primary
public interface JpaOrderItemRepository extends JpaRepository<OrderItem, Long>, OrderItemRepository {

    @Override
    @Query("SELECT oi FROM OrderItem oi WHERE oi.orderId = :orderId")
    List<OrderItem> findByOrderId(@Param("orderId") Long orderId);
}
```

### 5. InMemoryRepository 타입 수정 및 Profile 분리

#### InMemoryOrderRepository
- `Map<String, Order>` → `Map<Long, Order>` 변경
- ID 생성: `AtomicLong` 사용
- `@Profile("inmemory")` 추가 (기본 프로필에서 비활성화)

```java
@Repository
@Profile("inmemory")
public class InMemoryOrderRepository implements OrderRepository {
    private final Map<Long, Order> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Order save(Order order) {
        if (order.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            // Reflection으로 ID 설정 (JPA Entity는 protected setter 없음)
            // ...
        }
        storage.put(order.getId(), order);
        return order;
    }
}
```

### 6. MySQL Testcontainers 테스트 작성

```java
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JpaOrderRepositoryTest {

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private JpaOrderRepository orderRepository;

    @Test
    void saveAndFindById() {
        // Given
        Order order = Order.create("ORD-20250112-001", 1L, 100000L, 10000L);

        // When
        Order savedOrder = orderRepository.save(order);

        // Then
        assertThat(savedOrder.getId()).isNotNull();
        assertThat(savedOrder.getOrderNumber()).isEqualTo("ORD-20250112-001");
    }
}
```

---

## ⚠️ 현재 문제점

### 타입 불일치로 인한 컴파일 에러

다른 도메인들(User, Product, Coupon, Cart)이 아직 String id를 사용하고 있어 OrderService에서 타입 에러 발생:

```java
// OrderService.java
User user = userRepository.findByIdOrThrow(request.getUserId());
// Error: String cannot be converted to Long

Product product = productRepository.findByIdOrThrow(itemReq.getProductId());
// Error: String cannot be converted to Long
```

**영향 범위:**
- OrderService (다른 도메인 사용)
- UserService
- ProductService
- CouponService
- CartService
- 관련 DTO 클래스들

**원인:**
- Order, OrderItem → JPA Entity (Long id)
- User, Product, Coupon, Cart → 아직 String id (InMemory 기준)

---

## 🔧 해결 방안

### 옵션 A: 전체 도메인 일괄 JPA 전환 (권장)

**작업 범위:**
1. **Repository 인터페이스 타입 변경**
   - UserRepository: `findById(String)` → `findById(Long)`
   - ProductRepository
   - CouponRepository
   - CartRepository

2. **InMemory Repository 타입 수정**
   - InMemoryUserRepository: `Map<String, User>` → `Map<Long, User>`
   - InMemoryProductRepository
   - InMemoryCouponRepository
   - InMemoryCartRepository
   - 모두 `AtomicLong idGenerator` 추가

3. **JPA Repository 생성**
   - JpaUserRepository
   - JpaProductRepository
   - JpaCouponRepository
   - JpaUserCouponRepository
   - JpaCartRepository
   - JpaCartItemRepository

4. **Service 레이어 타입 수정**
   - OrderService
   - UserService
   - ProductService
   - CouponService
   - CartService

5. **DTO 타입 수정**
   - Request/Response DTO의 String id → Long id

**예상 작업 파일:**
- Repository 인터페이스: 6개
- InMemory 구현체: 6개
- JPA 구현체: 6개 (신규 생성)
- Service: 5개
- DTO: 약 15개
- **총 약 38개 파일 수정**

**소요 시간:** 약 2-3시간

### 옵션 B: 점진적 전환

**작업 순서:**
1. User 도메인 전환
2. Product 도메인 전환
3. Coupon 도메인 전환
4. Cart 도메인 전환
5. 통합 테스트

**장점:** 작은 단위로 검증 가능
**단점:** 각 단계마다 타입 불일치 해결 필요

---

## 📋 다음 단계 제안

### 1단계: 전체 도메인 타입 통일 (우선)

```bash
# 작업 순서
1. User 도메인
   - UserRepository 인터페이스 Long 타입으로 변경
   - InMemoryUserRepository Long 타입으로 변경
   - JpaUserRepository 생성
   - UserService 타입 수정
   - UserResponse DTO 수정

2. Product 도메인
   - 동일 과정 반복

3. Coupon 도메인
   - 동일 과정 반복

4. Cart 도메인
   - 동일 과정 반복
```

### 2단계: MySQL Testcontainers 통합 테스트

```bash
# 모든 도메인 타입 통일 후
./gradlew test --tests JpaOrderRepositoryTest
./gradlew test --tests JpaUserRepositoryTest
./gradlew test --tests JpaProductRepositoryTest
```

### 3단계: 애플리케이션 실행 및 검증

```bash
# H2 Console 확인
./gradlew bootRun
# http://localhost:8080/h2-console

# API 테스트
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -d '{...}'
```

---

## 💡 학습 포인트

### 1. JPA Repository 설계

**Domain Layer (인터페이스):**
```java
public interface OrderRepository {
    Optional<Order> findById(Long id);
    Order save(Order order);

    // Default method로 공통 로직 제공
    default Order findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }
}
```

**Infrastructure Layer (구현):**
```java
@Repository
@Primary
public interface JpaOrderRepository extends JpaRepository<Order, Long>, OrderRepository {
    // Spring Data JPA가 자동 구현
    // 추가 메서드만 정의
}
```

**의존성 방향:**
```
Domain (OrderRepository Interface)
  ↑ implements
Infrastructure (JpaOrderRepository)
```

### 2. Testcontainers 활용

**장점:**
- 실제 MySQL 환경에서 테스트
- H2와 MySQL의 SQL 문법 차이 검증
- 자동으로 컨테이너 시작/종료

**설정:**
```java
@Container
static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

@DynamicPropertySource
static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
}
```

### 3. Profile 분리

**InMemory (레거시):**
```java
@Repository
@Profile("inmemory")
public class InMemoryOrderRepository implements OrderRepository {
    // Week 3 구현
}
```

**JPA (신규):**
```java
@Repository
@Primary  // 기본으로 사용
public interface JpaOrderRepository extends JpaRepository<Order, Long>, OrderRepository {
    // Week 4 구현
}
```

**활성화 방법:**
```yaml
# application.yml
spring:
  profiles:
    active: default  # JPA 사용

# InMemory 사용 시
spring:
  profiles:
    active: inmemory
```

---

## 📁 파일 구조

```
src/
├── main/
│   └── java/io/hhplus/ecommerce/
│       ├── domain/order/
│       │   ├── Order.java                    # ✅ JPA Entity
│       │   ├── OrderItem.java                # ✅ JPA Entity
│       │   ├── OrderRepository.java          # ✅ Long id
│       │   └── OrderItemRepository.java      # ✅ Long id
│       │
│       └── infrastructure/persistence/order/
│           ├── JpaOrderRepository.java        # ✅ 신규 생성 (@Primary)
│           ├── JpaOrderItemRepository.java    # ✅ 신규 생성 (@Primary)
│           ├── InMemoryOrderRepository.java   # ✅ Long 타입 변경 (@Profile("inmemory"))
│           └── InMemoryOrderItemRepository.java  # ✅ Long 타입 변경 (@Profile("inmemory"))
│
└── test/
    └── java/io/hhplus/ecommerce/infrastructure/persistence/order/
        └── JpaOrderRepositoryTest.java       # ✅ MySQL Testcontainers
```

---

## 🎯 결론

### 현재 상태
- ✅ Order 도메인 JPA Repository 구조 완성
- ✅ Testcontainers 테스트 코드 작성
- ⚠️ 다른 도메인 타입 불일치로 컴파일 에러

### 권장 사항
1. **전체 도메인 타입 통일** (User, Product, Coupon, Cart → Long id)
2. 타입 통일 후 **Testcontainers 통합 테스트 실행**
3. **H2/MySQL 환경 검증**
4. **애플리케이션 실행 및 API 테스트**

### 예상 효과
- 실제 MySQL 환경에서 검증 가능
- H2와 MySQL SQL 문법 차이 조기 발견
- 프로덕션 배포 전 안정성 확보
