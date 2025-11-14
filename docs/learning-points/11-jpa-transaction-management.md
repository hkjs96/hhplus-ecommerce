# 11. JPA & Transaction Management (Week 4)

## 📌 핵심 개념

**Week 4 목표**: Week 3의 In-Memory Repository를 JPA 기반 데이터베이스로 전환하면서 **비즈니스 로직은 유지**

---

## 🎯 Week 4 과제 범위

### 주요 작업
1. ✅ **JPA Entity 변환** (Week 3 Domain Entity → JPA Entity)
2. ✅ **Spring Data JPA Repository** (In-Memory → JpaRepository)
3. ✅ **Transaction Management** (@Transactional 적용)
4. ✅ **Database 설정** (MySQL)

### Pass 조건
- [ ] JPA Entity로 변환 (비즈니스 로직 유지!)
- [ ] Spring Data JPA Repository 활용
- [ ] @Transactional 적절히 적용
- [ ] In-Memory Repository 제거
- [ ] 테스트 커버리지 70% 이상 유지

### Fail 사유
- ❌ In-Memory 유지 (JPA 미사용)
- ❌ Entity에서 비즈니스 로직 제거 (Anemic Domain Model)
- ❌ @Transactional 부재 또는 잘못된 위치 적용

---

## 🔄 Week 3 → Week 4 전환

### Before (Week 3): 순수 Java Entity + In-Memory

```java
// Week 3: 순수 Java 클래스
public class Product {
    private String id;
    private String name;
    private Integer stock;
    private Long price;
    private String category;

    public Product(String id, String name, Integer stock, Long price, String category) {
        this.id = id;
        this.name = name;
        this.stock = stock;
        this.price = price;
        this.category = category;
    }

    // 비즈니스 로직 (중요!)
    public void decreaseStock(int quantity) {
        validateQuantity(quantity);
        validateStock(quantity);
        this.stock -= quantity;
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        }
    }

    private void validateStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
    }
}
```

```java
// Week 3: In-Memory Repository
@Repository
public class InMemoryProductRepository implements ProductRepository {
    private final Map<String, Product> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Product save(Product product) {
        storage.put(product.getId(), product);
        return product;
    }
}
```

---

### After (Week 4): JPA Entity + Spring Data JPA

```java
// Week 4: JPA Entity (비즈니스 로직 유지!)
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA 필수
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false, length = 50)
    private String category;

    // 생성자 (정적 팩토리 메서드)
    public static Product create(String name, Integer stock, Long price, String category) {
        Product product = new Product();
        product.name = name;
        product.stock = stock;
        product.price = price;
        product.category = category;
        return product;
    }

    // ⭐ 비즈니스 로직은 그대로 유지!
    public void decreaseStock(int quantity) {
        validateQuantity(quantity);
        validateStock(quantity);
        this.stock -= quantity;
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        }
    }

    private void validateStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
    }
}
```

```java
// Week 4: Spring Data JPA Repository
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // 메서드 네이밍 쿼리
    List<Product> findByCategory(String category);

    // Custom method (코치 피드백 반영)
    default Product findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.PRODUCT_NOT_FOUND,
                "Product not found. productId: " + id
            ));
    }
}
```

---

## 🚨 중요: Anemic Domain Model 방지

### ❌ Fail 예시: 비즈니스 로직 제거

```java
// ❌ 잘못된 예시: Anemic Domain Model
@Entity
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Integer stock;
    private Long price;

    // getter/setter만 존재 (비즈니스 로직 없음!)
}

// Service에서 비즈니스 로직 처리 (잘못된 방법)
@Service
public class ProductService {
    public void decreaseStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId).orElseThrow();

        // ❌ Service에서 비즈니스 로직 직접 처리
        if (product.getStock() < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
        product.setStock(product.getStock() - quantity);
    }
}
```

### ✅ Pass 예시: 비즈니스 로직 유지

```java
// ✅ 올바른 예시: Rich Domain Model
@Entity
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Integer stock;
    private Long price;

    // ✅ Entity에 비즈니스 로직 유지
    public void decreaseStock(int quantity) {
        validateStock(quantity);
        this.stock -= quantity;
    }

    private void validateStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
    }
}

// Service는 워크플로우만 조율
@Service
@Transactional
public class OrderUseCase {
    public OrderResponse createOrder(CreateOrderRequest request) {
        Product product = productRepository.findByIdOrThrow(request.getProductId());

        // ✅ Entity 메서드 호출 (비즈니스 로직 위임)
        product.decreaseStock(request.getQuantity());

        return OrderResponse.from(orderRepository.save(order));
    }
}
```

---

## 🔧 Transaction Management

### @Transactional 적용 위치

**핵심 원칙**: Application Layer (UseCase/Service)에만 적용

```
✅ Application Layer (UseCase)
   ↓ @Transactional 적용
❌ Controller
❌ Domain Entity
❌ Repository
```

---

### UseCase에 @Transactional 적용

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 기본값: 읽기 전용
public class OrderUseCase {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    // 쓰기 작업: readOnly = false
    @Transactional  // readOnly=false (기본값 오버라이드)
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findByIdOrThrow(request.getUserId());

        // 2. 상품 조회
        Product product = productRepository.findByIdOrThrow(request.getProductId());

        // 3. 재고 차감 (Entity 메서드 → Dirty Checking 적용)
        product.decreaseStock(request.getQuantity());

        // 4. 주문 생성
        Order order = Order.create(request);

        // 5. 저장 (트랜잭션 커밋 시 자동 UPDATE)
        return OrderResponse.from(orderRepository.save(order));
    }

    // 읽기 전용 메서드: 클래스 레벨 @Transactional(readOnly=true) 사용
    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findByIdOrThrow(orderId);
        return OrderResponse.from(order);
    }

    public List<ProductResponse> getProducts() {
        return productRepository.findAll().stream()
            .map(ProductResponse::from)
            .toList();
    }
}
```

---

### Dirty Checking (변경 감지)

**개념**: 영속성 컨텍스트 내에서 Entity 변경 시, 트랜잭션 커밋 시점에 자동 UPDATE

```java
@Transactional
public void decreaseProductStock(Long productId, int quantity) {
    Product product = productRepository.findById(productId).orElseThrow();

    // Entity 메서드 호출 (상태 변경)
    product.decreaseStock(quantity);

    // ✅ productRepository.save() 호출 불필요!
    // 트랜잭션 커밋 시 자동으로 UPDATE 쿼리 실행
}
```

**주의:**
- ❌ @Transactional 없으면 Dirty Checking 작동 안 함
- ❌ readOnly=true이면 UPDATE 쿼리 실행 안 됨

---

### @Transactional 옵션

```java
@Transactional(
    readOnly = false,           // 읽기 전용 여부 (기본: false)
    isolation = Isolation.DEFAULT,  // 격리 수준
    propagation = Propagation.REQUIRED,  // 전파 방식
    timeout = 5,                // 타임아웃 (초)
    rollbackFor = Exception.class  // 롤백 예외
)
public void complexOperation() {
    // ...
}
```

**Week 4에서 사용할 옵션:**
- `readOnly=true`: 읽기 전용 메서드 (성능 최적화)
- `readOnly=false`: 쓰기 작업 (기본값)

---

## 💾 Database 설정

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ecommerce?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: your_password

  jpa:
    hibernate:
      ddl-auto: create  # 개발: create, 프로덕션: validate
    show-sql: true  # SQL 로그 출력
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: true  # SQL 포매팅
        use_sql_comments: true  # 주석 추가
    defer-datasource-initialization: true  # data.sql 실행 (ddl-auto 이후)

logging:
  level:
    org.hibernate.SQL: DEBUG  # SQL 로그
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE  # 파라미터 바인딩
```

**MySQL 접속 정보:**
- Host: `localhost`
- Port: `3306`
- Database: `ecommerce`
- Username: `root`
- Password: 각자 설정한 비밀번호

---

### ddl-auto 옵션

| 옵션 | 설명 | 사용 환경 |
|------|------|----------|
| **create** | 기존 테이블 삭제 후 생성 | 개발 (초기) |
| **create-drop** | create + 종료 시 삭제 | 테스트 |
| **update** | 변경 사항만 반영 | 개발 (중후반) |
| **validate** | 스키마 검증만 | 프로덕션 |
| **none** | 아무것도 안 함 | 프로덕션 (권장) |

**Week 4 권장**: `create` (초기 개발) → `update` (개발 중) → `validate` (프로덕션)

---

## 🧪 JPA 테스트 전략

### @DataJpaTest (Repository 테스트)

```java
@DataJpaTest  // JPA 관련 Bean만 로딩
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void 상품_저장_및_조회() {
        // Given
        Product product = Product.create("노트북", 10, 890000L, "전자제품");

        // When
        Product saved = productRepository.save(product);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("노트북");

        // 조회 검증
        Product found = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("노트북");
    }

    @Test
    void 카테고리로_상품_조회() {
        // Given
        productRepository.save(Product.create("노트북", 10, 890000L, "전자제품"));
        productRepository.save(Product.create("키보드", 20, 120000L, "주변기기"));
        productRepository.save(Product.create("모니터", 15, 350000L, "전자제품"));

        // When
        List<Product> electronics = productRepository.findByCategory("전자제품");

        // Then
        assertThat(electronics).hasSize(2);
        assertThat(electronics)
            .extracting("name")
            .containsExactlyInAnyOrder("노트북", "모니터");
    }
}
```

---

### @SpringBootTest + @Transactional (통합 테스트)

```java
@SpringBootTest
@Transactional  // 각 테스트 후 롤백
class OrderIntegrationTest {

    @Autowired
    private OrderUseCase orderUseCase;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // 초기 데이터 설정
        productRepository.save(Product.create("노트북", 10, 890000L, "전자제품"));
        userRepository.save(User.create("테스트유저", 1000000L));
    }

    @Test
    void 주문_생성_후_재고_차감_확인() {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
            .userId(1L)
            .productId(1L)
            .quantity(3)
            .build();

        // When
        OrderResponse response = orderUseCase.createOrder(request);

        // Then
        assertThat(response.getOrderId()).isNotNull();

        // 재고 차감 확인 (Dirty Checking)
        Product product = productRepository.findById(1L).orElseThrow();
        assertThat(product.getStock()).isEqualTo(7);  // 10 - 3 = 7
    }
}
```

---

## ✅ Week 4 체크리스트

### JPA Entity 변환
- [ ] @Entity, @Table, @Id, @Column 어노테이션 적용
- [ ] @NoArgsConstructor(access = PROTECTED) 추가
- [ ] 비즈니스 로직 메서드 유지 (decreaseStock, validate 등)
- [ ] 정적 팩토리 메서드 또는 생성자 제공

### Spring Data JPA Repository
- [ ] JpaRepository 상속
- [ ] 커스텀 쿼리 메서드 작성 (findByCategory 등)
- [ ] findByIdOrThrow() 메서드 추가 (코치 피드백)
- [ ] InMemory Repository 제거

### Transaction Management
- [ ] UseCase에 @Transactional(readOnly=true) 클래스 레벨 적용
- [ ] 쓰기 메서드에 @Transactional 오버라이드
- [ ] Dirty Checking 활용 (save() 호출 최소화)

### Database 설정
- [ ] application.yml 설정 (MySQL)
- [ ] ddl-auto, show-sql, dialect 설정
- [ ] 초기 데이터 로딩 (ApplicationRunner 또는 data.sql)

### Testing
- [ ] @DataJpaTest로 Repository 테스트
- [ ] @SpringBootTest + @Transactional로 통합 테스트
- [ ] 테스트 커버리지 70% 이상 유지

---

## 🚨 Common Pitfalls (자주 하는 실수)

### JPA Entity
- ❌ 비즈니스 로직 제거 (Anemic Domain Model)
- ❌ @NoArgsConstructor 누락 (JPA 필수)
- ❌ setter 남발 (캡슐화 위반)

### Transaction
- ❌ Controller에 @Transactional 적용
- ❌ Entity에 @Transactional 적용
- ❌ readOnly=true인데 UPDATE 시도

### Testing
- ❌ @Transactional 없이 통합 테스트 (데이터 오염)
- ❌ Dirty Checking 미검증
- ❌ N+1 문제 미발견

---

## 📚 참고 자료

- [Spring Data JPA Documentation](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Hibernate User Guide](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html)
- CLAUDE.md - Week 4 Implementation Guide

---

**이전 학습**: [10. 테스트 전략 (Week 3)](./10-testing-strategies-week3.md)
**다음 학습**: Week 5 - 외부 API 연동 & 비동기 처리
