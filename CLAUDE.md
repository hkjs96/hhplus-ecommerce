# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot e-commerce reference project for the Hanghe Plus backend curriculum (항해플러스 백엔드 커리큘럼). It's a Java-based application using Spring Boot 3.5.7 with Gradle as the build tool.

**Current Phase:** Week 3 - Layered Architecture Implementation (구현 단계)

**핵심 목표**: 레이어드 아키텍처로 핵심 비즈니스 로직 구현 및 동시성 제어

---

## 📊 Implementation Progress

### Phase 1: Documentation & Design ✅ (Week 2)
- ✅ step1-2: ERD, Sequence Diagrams, API Specification, Requirements (main)
- ✅ step3: Infrastructure + Core Controllers (Product, Cart, Order)
- ✅ step4: Additional Controllers (Coupon, User)
- **Status**: 15 API endpoints with Mock data (ConcurrentHashMap)

### Phase 2: Layered Architecture Implementation 🚧 (Week 3)
- 🚧 **step5**: Domain & Application Layer (진행 중)
  - Domain: Entity, Value Object, Repository Interface, DomainService
  - Application: UseCase implementation
  - Infrastructure: In-Memory Repository 구현
  - Unit Testing (Coverage 70%+)

- ⏳ **step6**: Concurrency Control & Integration (예정)
  - Race Condition 방지 (선착순 쿠폰)
  - 인기 상품 집계 로직
  - 통합 테스트 작성
  - 동시성 제어 분석 문서

---

## Technology Stack

### Current Implementation (Week 3)
- **Language**: Java 17
- **Framework**: Spring Boot 3.5.7
- **Build Tool**: Gradle
- **Architecture**: Layered Architecture (4-Layer)
- **Data Storage**: In-Memory (ConcurrentHashMap, ArrayList) - ⚠️ NO DATABASE
- **Testing**: JUnit 5, Mockito

### Key Dependencies
- Spring Boot Starter (Web, Validation)
- Lombok
- SpringDoc OpenAPI 2.7.0
- JUnit 5 (Testing)

> **⚠️ IMPORTANT**: Week 3는 **DB를 사용하지 않습니다**. 모든 데이터는 인메모리로 관리합니다.

---

## 📋 Week 3 Assignment: Layered Architecture Implementation

### Assignment Objectives
1. **Domain Layer**: ERD 기반 도메인 모델 구현 (Entity, Value Object)
2. **Application Layer**: API 명세를 유스케이스로 구현
3. **Infrastructure Layer**: In-Memory Repository 구현
4. **Concurrency Control**: 선착순 쿠폰 Race Condition 방지
5. **Unit Testing**: 테스트 커버리지 70% 이상

---

## 🚩 STEP 5: Layered Architecture 기본 구현

### 과제 요구사항

#### 1. 도메인 모델 구현
- Week 2의 ERD를 기반으로 Entity 클래스 작성
- Value Object 구현 (Money, Quantity, CouponDiscount 등)
- 비즈니스 규칙을 도메인 모델에 캡슐화

#### 2. 레이어드 아키텍처 구조
```
src/main/java/io/hhplus/ecommerce/
├── domain/              # 핵심 비즈니스 로직 (Entity, Repository Interface, Domain Service)
├── application/         # 유스케이스 (UseCase, DTO)
├── infrastructure/      # 외부 세계와의 통합 (In-Memory Repository 구현체)
└── presentation/        # API 엔드포인트 (Controller)
```

#### 3. 핵심 비즈니스 로직 구현
- **재고 관리**: 재고 조회, 차감, 복구
- **주문/결제**: 주문 생성, 상태 관리, 결제 처리
- **선착순 쿠폰**: 쿠폰 발급, 사용, 만료 처리

#### 4. 단위 테스트
- 각 계층별 단위 테스트 작성
- 테스트 커버리지 70% 이상 달성
- Mock/Stub을 활용한 격리된 테스트

### Pass 조건 (모두 충족 필요)
- [ ] 4계층(Presentation, Application, Domain, Infrastructure)이 명확히 분리되어 있는가?
- [ ] 도메인 모델이 비즈니스 규칙을 포함하고 있는가?
- [ ] Repository 패턴이 적용되어 인터페이스와 구현체가 분리되어 있는가?
- [ ] 핵심 비즈니스 로직(재고/주문/쿠폰)이 정상 동작하는가?
- [ ] 단위 테스트 커버리지가 70% 이상인가?
- [ ] DB 없이 인메모리로 구현되었는가?

### Fail 사유
- 계층 분리 없이 단일 파일에 모든 로직이 작성된 경우
- 비즈니스 로직이 Controller나 Repository에 위치한 경우
- 테스트가 없거나 커버리지가 50% 미만인 경우
- DB를 사용한 경우

---

## 🚩 STEP 6: 동시성 제어 및 고급 기능

### 과제 요구사항

#### 1. 동시성 제어 구현
- 선착순 쿠폰 발급 시 Race Condition 방지
- 선택 가능한 방식:
  - Mutex/Lock (synchronized, ReentrantLock)
  - Semaphore
  - Atomic Operations (AtomicInteger, AtomicReference)
  - Queue 기반 (BlockingQueue)

#### 2. 통합 테스트 작성
- 동시 요청 시나리오 검증
- 멀티 스레드 환경 테스트 (ExecutorService)
- Race Condition 방지 검증

#### 3. 인기 상품 집계 로직
- 조회수/판매량 기반 순위 계산
- 최근 3일 데이터 집계
- Top 5 상품 반환

#### 4. 동시성 제어 분석 문서 작성
- README.md에 동시성 제어 방식 설명
- 선택한 방식의 장단점 분석
- 대안 방식 비교

### Pass 조건 (모두 충족 필요)
- [ ] 선착순 쿠폰 발급 시 Race Condition이 발생하지 않는가?
- [ ] 동시성 테스트가 작성되어 있고 통과하는가?
- [ ] 인기 상품 집계 로직이 효율적으로 구현되었는가?
- [ ] README.md에 동시성 제어 방식에 대한 기술 분석이 포함되어 있는가?

### Fail 사유
- 동시성 제어 없이 Race Condition이 발생하는 경우
- 동시성 검증 테스트가 없는 경우
- README.md에 동시성 제어 분석이 없는 경우

---

## 🏗️ Layered Architecture 상세 설계

### 의존성 방향 (Dependency Rule)

```
Presentation Layer (Controller)
    ↓ depends on
Application Layer (UseCase)
    ↓ depends on
Domain Layer (Entity, Repository Interface, DomainService)
    ↑ implemented by
Infrastructure Layer (In-Memory Repository Impl)
```

**핵심 원칙**: 의존성은 항상 **바깥쪽 → 안쪽**으로만 흐른다.
- Infrastructure는 Domain을 **알지만**, Domain은 Infrastructure를 **모른다**.
- Repository 인터페이스는 **Domain**에, 구현체는 **Infrastructure**에 위치.

---

## 📁 Project Structure (Step 5)

```
src/main/java/io/hhplus/ecommerce/
├── domain/                          # 🔵 Domain Layer
│   ├── product/
│   │   ├── Product.java            # Entity
│   │   ├── Stock.java              # Value Object
│   │   ├── ProductRepository.java  # Repository Interface
│   │   └── ProductService.java     # Domain Service (optional)
│   ├── order/
│   │   ├── Order.java              # Entity (Aggregate Root)
│   │   ├── OrderItem.java          # Entity
│   │   ├── OrderStatus.java        # Enum
│   │   ├── OrderRepository.java    # Repository Interface
│   │   └── OrderService.java       # Domain Service
│   ├── cart/
│   │   ├── Cart.java               # Entity (Aggregate Root)
│   │   ├── CartItem.java           # Entity
│   │   ├── CartRepository.java     # Repository Interface
│   │   └── CartService.java        # Domain Service
│   ├── coupon/
│   │   ├── Coupon.java             # Entity
│   │   ├── UserCoupon.java         # Entity
│   │   ├── CouponDiscount.java     # Value Object
│   │   ├── CouponRepository.java   # Repository Interface
│   │   ├── UserCouponRepository.java
│   │   └── CouponService.java      # Domain Service (선착순 로직)
│   └── user/
│       ├── User.java               # Entity
│       ├── Balance.java            # Value Object
│       ├── UserRepository.java     # Repository Interface
│       └── UserService.java        # Domain Service
│
├── application/                     # 🟢 Application Layer
│   ├── product/
│   │   ├── ProductUseCase.java     # 상품 조회 유스케이스
│   │   ├── PopularProductUseCase.java  # 인기 상품 조회
│   │   └── dto/
│   │       ├── ProductResponse.java
│   │       └── PopularProductResponse.java
│   ├── cart/
│   │   ├── CartUseCase.java        # 장바구니 관리
│   │   └── dto/
│   │       ├── AddCartItemRequest.java
│   │       └── CartResponse.java
│   ├── order/
│   │   ├── OrderUseCase.java       # 주문 생성
│   │   ├── PaymentUseCase.java     # 결제 처리
│   │   └── dto/
│   │       ├── CreateOrderRequest.java
│   │       ├── OrderResponse.java
│   │       └── PaymentResponse.java
│   ├── coupon/
│   │   ├── CouponUseCase.java      # 쿠폰 발급/조회
│   │   └── dto/
│   │       ├── IssueCouponRequest.java
│   │       └── IssueCouponResponse.java
│   └── user/
│       ├── UserUseCase.java        # 사용자 잔액 관리
│       └── dto/
│           ├── BalanceResponse.java
│           └── ChargeBalanceRequest.java
│
├── infrastructure/                  # 🟡 Infrastructure Layer
│   ├── persistence/
│   │   ├── product/
│   │   │   └── InMemoryProductRepository.java  # Repository 구현체
│   │   ├── order/
│   │   │   └── InMemoryOrderRepository.java
│   │   ├── cart/
│   │   │   ├── InMemoryCartRepository.java
│   │   │   └── InMemoryCartItemRepository.java
│   │   ├── coupon/
│   │   │   ├── InMemoryCouponRepository.java
│   │   │   └── InMemoryUserCouponRepository.java
│   │   └── user/
│   │       └── InMemoryUserRepository.java
│   └── config/
│       └── DataInitializer.java    # 초기 데이터 로딩
│
├── presentation/                    # 🔴 Presentation Layer
│   ├── api/
│   │   ├── product/
│   │   │   └── ProductController.java  # UseCase 호출
│   │   ├── cart/
│   │   │   └── CartController.java
│   │   ├── order/
│   │   │   └── OrderController.java
│   │   ├── coupon/
│   │   │   └── CouponController.java
│   │   └── user/
│   │       └── UserController.java
│   └── common/
│       ├── ApiResponse.java
│       ├── ErrorResponse.java
│       └── GlobalExceptionHandler.java
│
├── config/
│   ├── OpenApiConfig.java
│   └── AsyncConfig.java
│
└── common/
    └── exception/
        ├── BusinessException.java
        └── ErrorCode.java
```

---

## 🎯 Implementation Guide

### Step 1: Domain Layer 구현

#### Entity 구현 예시 (Product.java)

```java
package io.hhplus.ecommerce.domain.product;

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

    /**
     * 비즈니스 로직: 재고 차감
     * - Domain Layer에서 비즈니스 규칙 검증
     */
    public void decreaseStock(int quantity) {
        if (stock < quantity) {
            throw new IllegalArgumentException("재고가 부족합니다");
        }
        this.stock -= quantity;
    }

    /**
     * 비즈니스 로직: 재고 복구
     */
    public void restoreStock(int quantity) {
        this.stock += quantity;
    }

    /**
     * 비즈니스 로직: 재고 확인
     */
    public boolean hasStock(int quantity) {
        return stock >= quantity;
    }
}
```

#### Repository Interface (ProductRepository.java)

```java
package io.hhplus.ecommerce.domain.product;

import java.util.List;
import java.util.Optional;

/**
 * Repository 인터페이스는 Domain Layer에 위치
 * 구현체는 Infrastructure Layer에 위치
 */
public interface ProductRepository {
    Optional<Product> findById(String id);
    List<Product> findAll();
    List<Product> findByCategory(String category);
    Product save(Product product);
    void deleteById(String id);
}
```

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

    /**
     * 상품 목록 조회
     */
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

    /**
     * 상품 상세 조회
     */
    public ProductResponse getProduct(String productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        return ProductResponse.from(product);
    }
}
```

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

## 🔒 Concurrency Control Strategies (Step 6)

### 선택 가능한 동시성 제어 방식

#### 1. synchronized (가장 간단)

```java
@Service
public class CouponService {

    // Method-level synchronization
    public synchronized UserCoupon issueCoupon(String userId, String couponId) {
        // 선착순 쿠폰 발급 로직
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        if (coupon.issuedQuantity() >= coupon.totalQuantity()) {
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }

        // 수량 증가 및 발급
        coupon.increaseIssuedQuantity();
        return userCouponRepository.save(new UserCoupon(...));
    }
}
```

**장점**: 구현이 가장 간단함
**단점**: 메서드 전체를 잠금 (성능 저하)

#### 2. ReentrantLock (세밀한 제어)

```java
@Service
public class CouponService {

    private final ReentrantLock lock = new ReentrantLock();

    public UserCoupon issueCoupon(String userId, String couponId) {
        lock.lock();
        try {
            // 선착순 쿠폰 발급 로직
            Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

            if (coupon.issuedQuantity() >= coupon.totalQuantity()) {
                throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
            }

            coupon.increaseIssuedQuantity();
            return userCouponRepository.save(new UserCoupon(...));
        } finally {
            lock.unlock();
        }
    }
}
```

**장점**: tryLock(), timeout 등 세밀한 제어 가능
**단점**: synchronized보다 복잡함

#### 3. AtomicInteger (가장 빠름)

```java
@Getter
public class Coupon {
    private String id;
    private String name;
    private Integer totalQuantity;
    private AtomicInteger issuedQuantity;  // Atomic 사용

    /**
     * CAS (Compare-And-Swap) 기반 동시성 제어
     */
    public boolean tryIssue() {
        while (true) {
            int current = issuedQuantity.get();

            // 수량 초과 체크
            if (current >= totalQuantity) {
                return false;
            }

            // CAS 연산으로 증가 시도
            if (issuedQuantity.compareAndSet(current, current + 1)) {
                return true;
            }
            // 실패하면 재시도 (while loop)
        }
    }
}
```

**장점**: Lock-free, 가장 빠른 성능
**단점**: 복잡한 로직에는 부적합

#### 4. BlockingQueue (순차 처리)

```java
@Service
public class CouponService {

    private final BlockingQueue<CouponIssueRequest> queue = new LinkedBlockingQueue<>();

    @PostConstruct
    public void init() {
        // 별도 스레드에서 큐 처리
        new Thread(() -> {
            while (true) {
                try {
                    CouponIssueRequest request = queue.take();
                    processIssueCoupon(request);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    public void issueCoupon(String userId, String couponId) {
        // 큐에 추가 (비동기 처리)
        queue.offer(new CouponIssueRequest(userId, couponId));
    }

    private void processIssueCoupon(CouponIssueRequest request) {
        // 순차적으로 쿠폰 발급 처리
    }
}
```

**장점**: 순차 처리로 동시성 문제 원천 차단
**단점**: 비동기 처리로 즉시 응답 불가

---

## 🧪 Testing Strategy

### Unit Testing (Step 5)

#### Domain Layer 테스트

```java
@Test
void 재고_차감_성공() {
    // Given
    Product product = new Product("P001", "노트북", "설명", 890000L, 10, "전자제품");

    // When
    product.decreaseStock(3);

    // Then
    assertThat(product.getStock()).isEqualTo(7);
}

@Test
void 재고_부족시_예외_발생() {
    // Given
    Product product = new Product("P001", "노트북", "설명", 890000L, 5, "전자제품");

    // When & Then
    assertThatThrownBy(() -> product.decreaseStock(10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("재고가 부족합니다");
}
```

#### Application Layer 테스트 (Mock 사용)

```java
@ExtendWith(MockitoExtension.class)
class ProductUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductUseCase productUseCase;

    @Test
    void 상품_조회_성공() {
        // Given
        String productId = "P001";
        Product product = new Product(productId, "노트북", "설명", 890000L, 10, "전자제품");
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        // When
        ProductResponse response = productUseCase.getProduct(productId);

        // Then
        assertThat(response.getProductId()).isEqualTo(productId);
        verify(productRepository).findById(productId);
    }

    @Test
    void 상품_없음_예외_발생() {
        // Given
        String productId = "INVALID";
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> productUseCase.getProduct(productId))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }
}
```

### Integration Testing (Step 6)

#### 동시성 테스트

```java
@SpringBootTest
class CouponConcurrencyTest {

    @Autowired
    private CouponUseCase couponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Test
    void 선착순_쿠폰_동시성_테스트() throws InterruptedException {
        // Given: 쿠폰 100개 생성
        String couponId = "C001";
        Coupon coupon = new Coupon(couponId, "10% 할인", 10, 100, 0);
        couponRepository.save(coupon);

        int threadCount = 200;  // 200명이 동시에 요청
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 200명이 동시에 쿠폰 발급 시도
        for (int i = 0; i < threadCount; i++) {
            String userId = "U" + String.format("%03d", i);
            executorService.submit(() -> {
                try {
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

        Coupon result = couponRepository.findById(couponId).orElseThrow();
        assertThat(result.getIssuedQuantity()).isEqualTo(100);
    }
}
```

---

## 📊 Test Coverage Guide

### 커버리지 측정 (Jacoco)

#### build.gradle 설정

```gradle
plugins {
    id 'jacoco'
}

jacoco {
    toolVersion = "0.8.11"
}

test {
    useJUnitPlatform()
    finalizedBy jacocoTestReport
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.70  // 70% 이상
            }
        }
    }
}
```

#### 커버리지 확인

```bash
# 테스트 실행 및 커버리지 리포트 생성
./gradlew test jacocoTestReport

# 커버리지 검증 (70% 미만 시 빌드 실패)
./gradlew jacocoTestCoverageVerification

# 리포트 확인
open build/reports/jacoco/test/html/index.html
```

---

## 🗂️ Data Initialization Strategy

### DataInitializer 구현

```java
package io.hhplus.ecommerce.infrastructure.config;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 시작 시 초기 데이터 로딩
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) {
        initProducts();
        initUsers();
    }

    private void initProducts() {
        productRepository.save(new Product("P001", "노트북", "고성능 게이밍 노트북", 890000L, 10, "전자제품"));
        productRepository.save(new Product("P002", "키보드", "기계식 키보드", 120000L, 20, "주변기기"));
        productRepository.save(new Product("P003", "마우스", "무선 마우스", 45000L, 30, "주변기기"));
        productRepository.save(new Product("P004", "모니터", "27인치 4K 모니터", 350000L, 15, "전자제품"));
        productRepository.save(new Product("P005", "헤드셋", "노이즈 캔슬링 헤드셋", 230000L, 25, "주변기기"));
    }

    private void initUsers() {
        userRepository.save(new User("U001", "김항해", 50000));
        userRepository.save(new User("U002", "이플러스", 100000));
        userRepository.save(new User("U003", "박백엔드", 30000));
    }
}
```

---

## ✅ Step 5 Implementation Checklist

### Domain Layer
- [ ] Product Entity (재고 차감/복구 메서드)
- [ ] User Entity (잔액 충전/차감 메서드)
- [ ] Coupon Entity (발급 수량 검증)
- [ ] UserCoupon Entity
- [ ] Cart & CartItem Entity
- [ ] Order & OrderItem Entity
- [ ] Repository Interfaces (domain 패키지에 위치)

### Application Layer
- [ ] ProductUseCase (목록/상세 조회)
- [ ] CartUseCase (추가/조회/수정/삭제)
- [ ] OrderUseCase (주문 생성/조회)
- [ ] PaymentUseCase (결제 처리)
- [ ] CouponUseCase (발급/조회)
- [ ] UserUseCase (잔액 조회/충전)
- [ ] DTO 클래스 (Request, Response)

### Infrastructure Layer
- [ ] InMemoryProductRepository
- [ ] InMemoryUserRepository
- [ ] InMemoryCouponRepository
- [ ] InMemoryUserCouponRepository
- [ ] InMemoryCartRepository
- [ ] InMemoryOrderRepository
- [ ] DataInitializer (초기 데이터 로딩)

### Presentation Layer
- [ ] Controller 리팩토링 (ConcurrentHashMap 제거)
- [ ] UseCase 의존성 주입
- [ ] Mock 데이터 제거

### Testing
- [ ] Domain Layer 단위 테스트
- [ ] Application Layer 단위 테스트 (Mock 사용)
- [ ] Repository 단위 테스트
- [ ] 테스트 커버리지 70% 이상 달성

---

## ✅ Step 6 Implementation Checklist

### Concurrency Control
- [ ] 동시성 제어 방식 선택 (synchronized, ReentrantLock, Atomic, Queue 중 택1)
- [ ] 선착순 쿠폰 발급 Race Condition 방지 구현
- [ ] 재고 차감 동시성 제어 (optional)

### Popular Products Aggregation
- [ ] 인기 상품 집계 로직 구현 (최근 3일, Top 5)
- [ ] 판매량 기반 순위 계산
- [ ] PopularProductUseCase 구현

### Integration Testing
- [ ] 동시성 테스트 (ExecutorService, CountDownLatch)
- [ ] 200명 동시 요청 시나리오 테스트
- [ ] 정확히 100개만 발급 검증

### Documentation
- [ ] README.md에 동시성 제어 방식 설명
- [ ] 선택한 방식의 장단점 분석
- [ ] 대안 방식 비교 (최소 2가지)
- [ ] 코드 예시 포함

---

## 🔍 Common Pitfalls to Avoid

### Architecture
- ❌ Controller에 비즈니스 로직 작성
- ❌ Repository 구현체를 Domain에 위치
- ❌ UseCase에서 다른 UseCase 직접 호출 (DomainService 사용)
- ✅ 의존성 방향 준수 (Presentation → Application → Domain ← Infrastructure)

### Concurrency
- ❌ 동시성 제어 없이 쿠폰 발급
- ❌ Thread-unsafe 컬렉션 사용 (HashMap, ArrayList)
- ✅ ConcurrentHashMap, AtomicInteger 사용
- ✅ synchronized 또는 Lock 적용

### Testing
- ❌ 테스트 없이 구현
- ❌ 통합 테스트만 작성 (단위 테스트 누락)
- ✅ 각 계층별 단위 테스트 작성
- ✅ Mock을 활용한 격리된 테스트

### Data Management
- ❌ DB 라이브러리 사용 (JPA, Hibernate)
- ❌ 영속성 어노테이션 사용 (@Entity, @Table)
- ✅ 순수 Java 클래스로 Entity 구현
- ✅ In-Memory 컬렉션으로 저장

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

## 🎓 Success Criteria (Week 3)

### Step 5 Success Criteria
- [ ] 4계층 분리가 명확함
- [ ] Repository 인터페이스와 구현체가 분리됨
- [ ] 비즈니스 로직이 Domain Layer에 위치
- [ ] 모든 데이터가 인메모리로 관리됨
- [ ] 단위 테스트 커버리지 70% 이상

### Step 6 Success Criteria
- [ ] 선착순 쿠폰 Race Condition 방지
- [ ] 동시성 테스트 통과
- [ ] 인기 상품 집계 로직 구현
- [ ] README.md에 동시성 분석 포함

---

## Configuration

Application configuration is in `src/main/resources/application.yml`.

### Key Configurations
- **Logging**: DEBUG level for development
- **Async**: Thread pool for asynchronous tasks
- **OpenAPI**: Swagger UI configuration

---

## 📝 Next Steps

1. **Week 4 (Database Integration)**: H2/MySQL 연동, JPA Entity, Spring Data JPA
2. **Week 5 (Advanced Features)**: 외부 API 연동, Async/Fallback, 인기 상품 배치
3. **Week 6 (Performance)**: 캐싱, 인덱스 최적화, 부하 테스트
