# Spring Boot 프로젝트 구조

## 아키텍처 스타일

**Layered Architecture** (계층형 아키텍처)
- Presentation Layer (Controller, DTO)
- Application Layer (Use Case, Service)
- Domain Layer (Entity, Domain Service)
- Infrastructure Layer (Repository, External API)

---

## 디렉토리 구조

```
src/
├── main/
│   ├── java/io/hhplus/ecommerce/
│   │   ├── domain/                    # 도메인 계층 (핵심 비즈니스 로직)
│   │   │   ├── product/
│   │   │   │   ├── Product.java              # Entity
│   │   │   │   ├── ProductRepository.java    # Repository Interface
│   │   │   │   └── ProductService.java       # Domain Service
│   │   │   ├── order/
│   │   │   │   ├── Order.java
│   │   │   │   ├── OrderItem.java
│   │   │   │   ├── OrderStatus.java          # Enum
│   │   │   │   ├── OrderRepository.java
│   │   │   │   └── OrderService.java
│   │   │   ├── cart/
│   │   │   │   ├── Cart.java
│   │   │   │   ├── CartItem.java
│   │   │   │   ├── CartRepository.java
│   │   │   │   └── CartService.java
│   │   │   ├── coupon/
│   │   │   │   ├── Coupon.java
│   │   │   │   ├── UserCoupon.java
│   │   │   │   ├── CouponStatus.java
│   │   │   │   ├── CouponRepository.java
│   │   │   │   └── CouponService.java
│   │   │   ├── user/
│   │   │   │   ├── User.java
│   │   │   │   ├── UserRepository.java
│   │   │   │   └── UserService.java
│   │   │   └── shipping/
│   │   │       ├── Shipping.java
│   │   │       ├── ShippingStatus.java
│   │   │       ├── ShippingRepository.java
│   │   │       └── ShippingService.java
│   │   │
│   │   ├── application/               # 애플리케이션 계층 (유스케이스)
│   │   │   ├── product/
│   │   │   │   ├── ProductUseCase.java
│   │   │   │   └── dto/
│   │   │   │       ├── ProductResponse.java
│   │   │   │       └── PopularProductResponse.java
│   │   │   ├── order/
│   │   │   │   ├── OrderUseCase.java
│   │   │   │   ├── PaymentUseCase.java
│   │   │   │   └── dto/
│   │   │   │       ├── CreateOrderRequest.java
│   │   │   │       ├── OrderResponse.java
│   │   │   │       └── PaymentRequest.java
│   │   │   ├── cart/
│   │   │   │   ├── CartUseCase.java
│   │   │   │   └── dto/
│   │   │   │       ├── AddCartItemRequest.java
│   │   │   │       └── CartResponse.java
│   │   │   └── coupon/
│   │   │       ├── CouponUseCase.java
│   │   │       └── dto/
│   │   │           ├── IssueCouponRequest.java
│   │   │           └── CouponResponse.java
│   │   │
│   │   ├── infrastructure/            # 인프라 계층
│   │   │   ├── persistence/           # DB 구현
│   │   │   │   ├── product/
│   │   │   │   │   └── ProductRepositoryImpl.java
│   │   │   │   ├── order/
│   │   │   │   │   ├── OrderRepositoryImpl.java
│   │   │   │   │   └── OrderJpaRepository.java    # Spring Data JPA
│   │   │   │   └── ...
│   │   │   │
│   │   │   ├── external/              # 외부 API 연동
│   │   │   │   ├── dataplatform/
│   │   │   │   │   ├── DataPlatformClient.java
│   │   │   │   │   ├── DataPlatformConfig.java
│   │   │   │   │   └── dto/
│   │   │   │   │       └── OrderDataRequest.java
│   │   │   │   ├── payment/
│   │   │   │   │   ├── TossPaymentClient.java     # 토스페이 연동
│   │   │   │   │   └── PaymentConfig.java
│   │   │   │   ├── notification/
│   │   │   │   │   ├── NotificationClient.java
│   │   │   │   │   └── EmailService.java
│   │   │   │   └── shipping/
│   │   │   │       └── ShippingTrackerClient.java
│   │   │   │
│   │   │   └── batch/                 # 배치 작업
│   │   │       └── ProductStatisticsScheduler.java
│   │   │
│   │   ├── presentation/              # 프레젠테이션 계층
│   │   │   ├── api/
│   │   │   │   ├── product/
│   │   │   │   │   └── ProductController.java
│   │   │   │   ├── order/
│   │   │   │   │   └── OrderController.java
│   │   │   │   ├── cart/
│   │   │   │   │   └── CartController.java
│   │   │   │   └── coupon/
│   │   │   │       └── CouponController.java
│   │   │   │
│   │   │   └── common/                # 공통 응답 처리
│   │   │       ├── ApiResponse.java
│   │   │       ├── ErrorResponse.java
│   │   │       └── GlobalExceptionHandler.java
│   │   │
│   │   ├── config/                    # 설정
│   │   │   ├── JpaConfig.java
│   │   │   ├── AsyncConfig.java
│   │   │   ├── CacheConfig.java
│   │   │   └── RestTemplateConfig.java
│   │   │
│   │   ├── common/                    # 공통 유틸
│   │   │   ├── exception/
│   │   │   │   ├── BusinessException.java
│   │   │   │   ├── ErrorCode.java
│   │   │   │   ├── InsufficientStockException.java
│   │   │   │   └── CouponSoldOutException.java
│   │   │   └── util/
│   │   │       └── DateUtils.java
│   │   │
│   │   └── EcommerceApplication.java  # Main
│   │
│   └── resources/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-test.yml
│       ├── db/
│       │   └── migration/             # Flyway/Liquibase
│       │       └── V1__init.sql
│       └── static/
│
└── test/
    ├── java/io/hhplus/ecommerce/
    │   ├── domain/
    │   │   ├── product/
    │   │   │   └── ProductServiceTest.java
    │   │   └── order/
    │   │       └── OrderServiceTest.java
    │   ├── application/
    │   │   └── order/
    │   │       └── OrderUseCaseTest.java
    │   ├── infrastructure/
    │   │   └── external/
    │   │       └── DataPlatformClientTest.java
    │   ├── presentation/
    │   │   └── api/
    │   │       └── OrderControllerTest.java
    │   └── integration/               # 통합 테스트
    │       ├── OrderIntegrationTest.java
    │       └── ConcurrencyTest.java
    └── resources/
        └── application-test.yml
```

---

## 계층별 역할

### 1. Domain Layer (도메인 계층)
**역할**: 핵심 비즈니스 로직, 도메인 규칙

**특징**:
- 다른 계층에 의존하지 않음 (순수 Java)
- Entity, Value Object, Domain Service
- Repository는 인터페이스만 정의

**예시**:
```java
@Entity
@Table(name = "products")
public class Product {
    @Id
    private String id;
    private String name;
    private BigDecimal price;
    private Integer stock;

    // 비즈니스 로직
    public void deductStock(int quantity) {
        if (this.stock < quantity) {
            throw new InsufficientStockException(
                "재고 부족: 현재 " + this.stock + "개");
        }
        this.stock -= quantity;
    }

    public boolean isAvailable() {
        return this.stock > 0;
    }
}
```

---

### 2. Application Layer (애플리케이션 계층)
**역할**: 유스케이스 구현, 트랜잭션 관리, 도메인 서비스 조율

**특징**:
- @Service 또는 @UseCase
- 여러 도메인 서비스를 조합
- DTO 변환

**예시**:
```java
@Service
@RequiredArgsConstructor
public class OrderUseCase {
    private final OrderService orderService;
    private final ProductService productService;
    private final CouponService couponService;
    private final PaymentUseCase paymentUseCase;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1. 재고 확인
        productService.validateStock(request.getItems());

        // 2. 쿠폰 검증
        BigDecimal discount = BigDecimal.ZERO;
        if (request.getCouponId() != null) {
            discount = couponService.validateAndCalculateDiscount(
                request.getCouponId(), request.getUserId());
        }

        // 3. 주문 생성
        Order order = orderService.createOrder(
            request.getUserId(),
            request.getItems(),
            discount
        );

        // 4. DTO 변환
        return OrderResponse.from(order);
    }
}
```

---

### 3. Infrastructure Layer (인프라 계층)
**역할**: 기술적 구현 (DB, 외부 API, 메시징)

**특징**:
- Repository 구현체
- 외부 API 클라이언트
- 배치 작업

**예시**:
```java
@Service
@RequiredArgsConstructor
public class DataPlatformClient {
    private final RestTemplate restTemplate;

    @Async("externalApiExecutor")
    @Retry(name = "dataPlatform")
    public void sendOrderData(Order order) {
        OrderDataRequest request = OrderDataRequest.from(order);
        restTemplate.postForObject(
            "https://data-platform.com/api/orders",
            request,
            Void.class
        );
    }

    private void fallback(Order order, Exception ex) {
        log.error("Data platform unavailable. Queuing for retry", ex);
        retryQueue.add(order);  // In-memory queue or Redis
    }
}
```

---

### 4. Presentation Layer (프레젠테이션 계층)
**역할**: HTTP 요청/응답 처리

**특징**:
- @RestController
- 입력 검증 (@Valid)
- 에러 핸들링

**예시**:
```java
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderUseCase orderUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        OrderResponse order = orderUseCase.createOrder(request);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(order));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @PathVariable String orderId) {

        OrderResponse order = orderUseCase.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success(order));
    }
}
```

---

## 공통 응답 형식

```java
@Getter
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorResponse error;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(
            false,
            null,
            new ErrorResponse(errorCode.getCode(), message)
        );
    }
}

@Getter
@AllArgsConstructor
public class ErrorResponse {
    private String code;
    private String message;
}
```

---

## 에러 처리

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientStock(
            InsufficientStockException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ErrorCode.INSUFFICIENT_STOCK, ex.getMessage()));
    }

    @ExceptionHandler(CouponSoldOutException.class)
    public ResponseEntity<ApiResponse<Void>> handleCouponSoldOut(
            CouponSoldOutException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ErrorCode.COUPON_SOLD_OUT, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "서버 오류 발생"));
    }
}
```

---

## Mock 서버 구조 (db.json 대신)

### In-Memory 저장소 사용

```java
@Component
public class InMemoryProductRepository implements ProductRepository {
    private final Map<String, Product> store = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 초기 데이터 설정
        store.put("P001", new Product("P001", "노트북", 890000, 10, "전자제품"));
        store.put("P002", new Product("P002", "키보드", 120000, 50, "주변기기"));
    }

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Product save(Product product) {
        store.put(product.getId(), product);
        return product;
    }
}
```

**장점**:
- JSON Server 불필요
- Spring Boot의 모든 기능 활용 가능
- 실제 구현과 동일한 구조
- 테스트 용이

---

## 의존성 (build.gradle)

```gradle
dependencies {
    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-cache'

    // Database
    runtimeOnly 'com.h2database:h2'  // 개발용
    // runtimeOnly 'mysql:mysql-connector-java'  // 프로덕션

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.mockito:mockito-core'
}
```

---

## 설정 파일 예시

### application.yml

```yaml
spring:
  application:
    name: ecommerce
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create
    show-sql: true
  cache:
    type: simple  # 또는 redis

external:
  api:
    data-platform:
      url: https://data-platform.com/api
      timeout: 3000
    toss-payment:
      url: https://api.tosspayments.com
      secret-key: ${TOSS_SECRET_KEY}
```

---

## 다음 단계

1. ✅ 프로젝트 구조 이해
2. 도메인 모델 구현 (Product, Order 등)
3. Repository 구현 (InMemory 또는 JPA)
4. Use Case 구현
5. Controller 구현
6. 가용성 패턴 적용 (Timeout, Retry, Fallback, Async)
7. 테스트 작성
