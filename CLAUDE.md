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

#### 1. 아키텍처 분리 ✅
- [ ] **4계층 분리**: Presentation, Application, Domain, Infrastructure가 명확히 분리
- [ ] **의존성 방향**: Domain이 Infrastructure를 의존하지 않음
- [ ] **패키지 구조**: 각 계층이 별도 패키지로 구성됨

#### 2. 도메인 모델 설계 ✅
- [ ] **Entity 구현**: Product, Order, Coupon, User 등 ERD 기반 Entity 작성
- [ ] **비즈니스 로직**: Entity 내부에 비즈니스 규칙 메서드 존재
  - 예: `Product.decreaseStock()`, `User.charge()`, `Coupon.isAvailable()`
- [ ] **Value Object**: Money, Quantity 등 값 객체 활용 (선택)

#### 3. Repository 패턴 ✅
- [ ] **인터페이스 위치**: Repository 인터페이스가 Domain Layer에 위치
- [ ] **구현체 위치**: 구현체가 Infrastructure Layer에 위치
- [ ] **In-Memory 구현**: ConcurrentHashMap으로 데이터 관리
- [ ] **DB 미사용**: JPA, Hibernate 등 DB 라이브러리 사용하지 않음

#### 4. UseCase 구현 ✅
- [ ] **유스케이스 분리**: API 명세가 UseCase 메서드로 구현됨
- [ ] **단일 책임**: 각 UseCase는 하나의 비즈니스 흐름만 담당
- [ ] **DTO 사용**: Request/Response DTO로 데이터 전달

#### 5. 핵심 비즈니스 로직 ✅
- [ ] **재고 관리**: 재고 조회, 차감, 복구 로직 정상 동작
- [ ] **주문/결제**: 주문 생성 및 결제 프로세스 정상 동작
- [ ] **선착순 쿠폰**: 쿠폰 발급, 사용, 만료 로직 정상 동작

#### 6. 테스트 커버리지 ✅
- [ ] **커버리지 70% 이상**: Jacoco 리포트 기준
- [ ] **단위 테스트**: Domain, Application Layer 테스트 작성
- [ ] **Mock 활용**: Mockito로 의존성 격리

---

### Fail 사유 (하나라도 해당 시 불합격)

#### 아키텍처 Fail ❌
- ❌ **계층 미분리**: 단일 파일에 Controller + Service + Repository 로직이 혼재
- ❌ **의존성 역전**: Domain이 Infrastructure를 직접 의존 (import)
- ❌ **책임 혼재**: Controller에 비즈니스 로직 작성

#### 구현 Fail ❌
- ❌ **비즈니스 로직 위치**: Controller나 Repository에 비즈니스 규칙 작성
- ❌ **DB 사용**: JPA, Hibernate, @Entity 어노테이션 사용
- ❌ **Mock 데이터**: Controller에 하드코딩된 Mock 데이터 (Week 2 방식)

#### 테스트 Fail ❌
- ❌ **테스트 부재**: 테스트 코드가 전혀 없음
- ❌ **낮은 커버리지**: 50% 미만의 테스트 커버리지
- ❌ **통합 테스트만**: 단위 테스트 없이 통합 테스트만 존재

---

### 핵심 역량 및 평가 포인트

#### 1. 레이어드 아키텍처 이해도 🏗️
**평가 기준:**
- 각 계층의 책임을 명확히 이해하고 구현했는가?
- 의존성 방향을 올바르게 유지했는가?

**토론 주제:**
- "왜 Repository 인터페이스를 Domain에 두었나요?"
- "UseCase와 DomainService의 차이는 무엇인가요?"
- "Controller에서 직접 Repository를 호출하면 안 되는 이유는?"

#### 2. 비즈니스 로직 배치 📍
**평가 기준:**
- 비즈니스 규칙이 Entity 내부에 캡슐화되었는가?
- Anemic Domain Model을 피했는가?

**토론 주제:**
- "재고 차감 로직을 어디에 구현했나요? 그 이유는?"
- "할인 계산 로직은 어느 계층에 있나요?"

#### 3. Repository 패턴 이해 🗄️
**평가 기준:**
- 인터페이스와 구현체가 분리되었는가?
- In-Memory 구현이 올바르게 작동하는가?

**토론 주제:**
- "Repository와 DAO의 차이는 무엇인가요?"
- "ConcurrentHashMap을 선택한 이유는?"

#### 4. 테스트 가능한 설계 🧪
**평가 기준:**
- Mock을 활용한 격리된 테스트가 가능한가?
- 각 계층별로 테스트가 분리되어 있는가?

**토론 주제:**
- "Domain Layer 테스트에서 Mock이 필요한가요?"
- "통합 테스트와 단위 테스트의 비율은 어떻게 가져갔나요?"

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

#### 1. 동시성 제어 구현 ✅
- [ ] **Race Condition 방지**: 200명이 동시 요청해도 정확히 100개만 발급
- [ ] **동시성 제어 방식 선택**: synchronized, ReentrantLock, Atomic, Queue 중 택1
- [ ] **일관성 보장**: 쿠폰 발급 수량이 정확히 일치

#### 2. 통합 테스트 작성 ✅
- [ ] **동시성 테스트**: ExecutorService + CountDownLatch 활용
- [ ] **시나리오 검증**: 200명 요청 → 100명 성공, 100명 실패
- [ ] **테스트 통과**: 100% 성공률로 동시성 테스트 통과

#### 3. 인기 상품 집계 ✅
- [ ] **집계 로직**: 최근 3일 판매량 기준 Top 5 계산
- [ ] **효율성**: O(N log N) 이하의 시간 복잡도
- [ ] **API 응답**: period, rank, salesCount, revenue 포함

#### 4. 동시성 제어 문서화 ✅
- [ ] **README.md**: 동시성 제어 방식 설명 포함
- [ ] **선택 이유**: 해당 방식을 선택한 근거 작성
- [ ] **대안 비교**: 최소 2가지 다른 방식과 비교 분석
- [ ] **코드 예시**: 핵심 동시성 제어 코드 포함

---

### Fail 사유 (하나라도 해당 시 불합격)

#### 동시성 제어 Fail ❌
- ❌ **Race Condition 발생**: 200명 요청 시 100개를 초과하여 발급
- ❌ **동시성 제어 부재**: synchronized, Lock, Atomic 등 어떠한 제어도 없음
- ❌ **불안정한 결과**: 테스트 실행마다 발급 수량이 달라짐

#### 테스트 Fail ❌
- ❌ **테스트 부재**: 동시성 검증 테스트가 없음
- ❌ **단순 테스트**: 단일 스레드 테스트만 존재
- ❌ **테스트 실패**: 동시성 테스트가 통과하지 못함

#### 문서화 Fail ❌
- ❌ **문서 없음**: README.md에 동시성 제어 분석이 없음
- ❌ **설명 부족**: 어떤 방식을 사용했는지만 언급 (이유 없음)
- ❌ **대안 비교 없음**: 다른 방식과의 비교 분석 누락

---

### 핵심 역량 및 평가 포인트

#### 1. 동시성 제어 이해도 🔒
**평가 기준:**
- Race Condition이 무엇인지 이해하는가?
- 선택한 동시성 제어 방식을 정확히 설명할 수 있는가?

**토론 주제:**
- "synchronized와 ReentrantLock의 차이는 무엇인가요?"
- "AtomicInteger가 ConcurrentHashMap보다 빠른 이유는?"
- "BlockingQueue 방식의 장단점은 무엇인가요?"

#### 2. 통합 테스트 설계 🧪
**평가 기준:**
- ExecutorService를 올바르게 활용했는가?
- CountDownLatch의 역할을 이해하는가?

**토론 주제:**
- "200명의 동시 요청을 어떻게 시뮬레이션했나요?"
- "테스트 실패 시 어떻게 디버깅했나요?"

#### 3. 인기 상품 집계 효율성 📊
**평가 기준:**
- 최근 3일 데이터만 필터링하는가?
- 정렬 알고리즘의 시간 복잡도를 이해하는가?

**토론 주제:**
- "매번 정렬하는 것이 효율적인가요? 대안은?"
- "실시간 집계와 배치 집계 중 어떤 방식을 선택했나요?"

#### 4. 기술 문서 작성 능력 📝
**평가 기준:**
- 기술적 선택의 근거를 명확히 제시하는가?
- 트레이드오프를 이해하고 설명하는가?

**토론 주제:**
- "README.md에 어떤 내용을 포함했나요?"
- "다른 개발자가 읽고 이해하기 쉽게 작성했나요?"

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

## 📡 API Response Specification

### 주요 API 응답 형식 (Week 3 구현 시 참고)

#### 1. 인기 상품 조회 (GET /products/top)

**Response:**
```json
{
  "success": true,
  "data": {
    "period": "3days",
    "products": [
      {
        "rank": 1,
        "productId": "P001",
        "name": "노트북",
        "salesCount": 150,
        "revenue": 133500000
      }
    ]
  }
}
```

**필수 필드:**
- `period`: "3days" (고정값)
- `rank`: 순위 (1~5)
- `salesCount`: 판매 수량
- `revenue`: 매출액

**집계 방식**:
- 최근 3일간 판매량 기준 Top 5
- 실시간 쿼리 (초기 구현)
- 향후 성능 이슈 시 배치/캐시로 개선

---

#### 2. 주문 생성 (POST /orders)

**Request:**
```json
{
  "userId": "user123",
  "items": [
    {
      "productId": "P001",
      "quantity": 2
    }
  ],
  "couponId": "COUPON_10"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "orderId": "ORDER-20240115-001",
    "items": [
      {
        "productId": "P001",
        "name": "노트북",
        "quantity": 2,
        "unitPrice": 890000,
        "subtotal": 1780000
      }
    ],
    "subtotalAmount": 1900000,
    "discountAmount": 190000,
    "totalAmount": 1710000,
    "status": "PENDING"
  }
}
```

**필수 필드:**
- `items[]`: 주문 상품 상세 (name, unitPrice, subtotal 포함)
- `subtotalAmount`: 상품 합계 금액
- `discountAmount`: 할인 금액
- `totalAmount`: 최종 결제 금액
- `status`: "PENDING" | "COMPLETED"

---

#### 3. 결제 처리 (POST /orders/{orderId}/payment)

**Request:**
```json
{
  "userId": "user123"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "orderId": "ORDER-20240115-001",
    "paidAmount": 1710000,
    "remainingBalance": 290000,
    "status": "SUCCESS",
    "dataTransmission": "SUCCESS"
  }
}
```

**필수 필드:**
- `paidAmount`: 결제된 금액
- `remainingBalance`: 결제 후 남은 잔액
- `status`: "SUCCESS" | "FAILED"
- `dataTransmission`: "SUCCESS" | "FAILED" | "PENDING"

**중요**: 외부 전송 실패(`dataTransmission: "FAILED"`)여도 주문은 정상 완료 처리

---

#### 4. 쿠폰 발급 (POST /coupons/{couponId}/issue)

**Request:**
```json
{
  "userId": "user123"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "userCouponId": "UC-20240115-001",
    "couponName": "10% 할인쿠폰",
    "discountRate": 10,
    "expiresAt": "2024-12-31T23:59:59Z",
    "remainingQuantity": 95
  }
}
```

**필수 필드:**
- `userCouponId`: 발급된 쿠폰 ID (사용자별 고유)
- `remainingQuantity`: 남은 쿠폰 수량 (선착순 확인용)

---

#### 5. 보유 쿠폰 조회 (GET /users/{userId}/coupons)

**Response:**
```json
{
  "success": true,
  "data": {
    "coupons": [
      {
        "userCouponId": "UC-20240115-001",
        "couponName": "10% 할인쿠폰",
        "discountRate": 10,
        "status": "AVAILABLE",
        "expiresAt": "2024-12-31T23:59:59Z"
      }
    ]
  }
}
```

**status 타입:**
- `AVAILABLE`: 사용 가능
- `USED`: 사용됨
- `EXPIRED`: 만료됨

---

## 🚨 Error Codes Reference

### ErrorCode Enum 또는 Constants 클래스

```java
package io.hhplus.ecommerce.common.exception;

public class ErrorCode {

    // 상품 관련 (Product)
    public static final String PRODUCT_NOT_FOUND = "P001";      // 상품을 찾을 수 없음
    public static final String INSUFFICIENT_STOCK = "P002";     // 재고 부족

    // 주문 관련 (Order)
    public static final String INVALID_QUANTITY = "O001";       // 잘못된 수량 (0 이하)
    public static final String ORDER_NOT_FOUND = "O002";        // 주문을 찾을 수 없음
    public static final String INVALID_ORDER_STATUS = "O003";   // 주문 상태가 올바르지 않음

    // 결제 관련 (Payment)
    public static final String INSUFFICIENT_BALANCE = "PAY001"; // 잔액 부족
    public static final String PAYMENT_FAILED = "PAY002";       // 결제 처리 실패

    // 쿠폰 관련 (Coupon)
    public static final String COUPON_SOLD_OUT = "C001";        // 쿠폰 수량 소진
    public static final String INVALID_COUPON = "C002";         // 유효하지 않은 쿠폰
    public static final String EXPIRED_COUPON = "C003";         // 만료된 쿠폰
    public static final String ALREADY_ISSUED = "C004";         // 이미 발급받은 쿠폰 (1인 1매)

    // 사용자 관련 (User)
    public static final String USER_NOT_FOUND = "U001";         // 사용자를 찾을 수 없음
    public static final String INVALID_CHARGE_AMOUNT = "U002";  // 잘못된 충전 금액
}
```

### BusinessException 클래스 예시

```java
package io.hhplus.ecommerce.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final String errorCode;
    private final String message;

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
    }

    // 편의 메서드
    public static BusinessException of(String errorCode, String message) {
        return new BusinessException(errorCode, message);
    }
}
```

### 사용 예시

```java
// Domain Layer에서 사용
public void decreaseStock(int quantity) {
    if (stock < quantity) {
        throw new BusinessException(
            ErrorCode.INSUFFICIENT_STOCK,
            String.format("재고가 부족합니다. (요청: %d, 재고: %d)", quantity, stock)
        );
    }
    this.stock -= quantity;
}

// UseCase에서 사용
public ProductResponse getProduct(String productId) {
    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.PRODUCT_NOT_FOUND,
            "상품을 찾을 수 없습니다. productId: " + productId
        ));

    return ProductResponse.from(product);
}
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

    public void decreaseStock(int quantity) {
        if (stock < quantity) {
            throw new IllegalArgumentException("재고가 부족합니다");
        }
        this.stock -= quantity;
    }

    public void restoreStock(int quantity) {
        this.stock += quantity;
    }

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

## ❓ FAQ (자주 묻는 질문)

### Q1. TDD로 개발해야 하나요?
**A:** TDD는 권장사항이지만 필수는 아닙니다.
- ✅ **테스트 커버리지 70% 이상**이 핵심 평가 기준입니다.
- ✅ 구현 후 테스트를 작성해도 무방합니다.
- 💡 TDD를 시도해보면 설계 개선에 도움이 됩니다.

**TDD 프로세스 (선택):**
1. 실패하는 테스트 작성 (Red)
2. 최소한의 코드로 테스트 통과 (Green)
3. 리팩토링 (Refactor)

**테스트 커버리지의 실용적 접근 (로이코치님 조언):**
- 🎯 **핵심 비즈니스 로직**: 완성도 최대화 (90%+ 목표)
  - 예: 재고 차감, 쿠폰 발급, 결제 처리
- ⚖️ **일반 서비스 코드**: 적절한 수준 (70-80%)
  - 예: CRUD, 단순 조회 로직
- ⚠️ **주의**: 테스트 커버리지에 맞추려다 의미 없는 테스트를 작성하지 말 것

**핵심 비즈니스 로직 파악 방법:**
1. 도메인 규칙이 포함된 로직 (재고 부족 검증, 쿠폰 수량 제한)
2. 돈/수량이 관련된 로직 (결제, 포인트, 재고)
3. Race Condition이 발생할 수 있는 로직 (선착순 쿠폰)

---

### Q2. 의존성 주입(DI)을 직접 구현해야 하나요?
**A:** 아니요, Spring의 DI를 사용하세요.
- ✅ `@RequiredArgsConstructor` (Lombok) 사용 권장
- ✅ 생성자 주입 방식 사용
- ❌ 필드 주입(`@Autowired`)은 테스트하기 어려움

**올바른 DI 예시:**
```java
@Service
@RequiredArgsConstructor  // Lombok이 생성자 자동 생성
public class ProductUseCase {
    private final ProductRepository productRepository;  // final로 선언
    // 생성자 자동 생성됨
}
```

---

### Q3. UseCase란 무엇인가요?
**A:** 사용자가 특정 목표를 달성하기 위해 시스템과 상호작용하는 완전한 시나리오입니다.

**UseCase의 본질 (로이코치님 조언):**
- 📋 **유즈케이스 = 요구사항의 단위** (아키텍처 패턴과 무관)
- 🎯 단순히 "상품 조회"가 아니라 "고객이 구매 결정을 내리기 위한 모든 정보 제공"
- 🔄 여러 도메인을 조합하여 완전한 비즈니스 플로우 구성

**실제 예시: 상품 상세 조회 UseCase**
```java
@Service
@RequiredArgsConstructor
public class ProductDetailUseCase {
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final StockRepository stockRepository;
    private final ShippingRepository shippingRepository;

    public ProductDetailResponse getProductDetail(String productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        // 재고 정보 조회
        Integer stockQuantity = stockRepository.getAvailableStock(productId);

        // 평점/리뷰 통계
        ReviewStats stats = reviewRepository.getStatsByProduct(productId);

        // 배송 예정일 계산
        LocalDate estimatedDelivery = shippingRepository.calculateDeliveryDate(productId);

        // 추천 상품 조회
        List<Product> recommendations = productRepository.findRecommendations(productId);

        return ProductDetailResponse.of(
            product,
            stockQuantity,
            stats,
            estimatedDelivery,
            recommendations
        );
    }
}
```

**중요:**
- ❌ 단순 CRUD가 아니라 완전한 비즈니스 시나리오
- ✅ API 명세를 유스케이스로 구현 (1 API = 1 UseCase 메서드)
- ✅ 코드는 Service가 아니라 **UseCase 클래스**로 작성

---

### Q4. DomainService와 UseCase의 차이는 무엇인가요?
**A:** 역할과 위치가 다릅니다.

| 항목 | DomainService | UseCase |
|------|--------------|---------|
| **위치** | Domain Layer | Application Layer |
| **역할** | 여러 Entity를 조합한 도메인 로직 | API 요청을 처리하는 워크플로우 |
| **예시** | `OrderService.validateOrder()` | `OrderUseCase.createOrder()` |
| **의존성** | Entity, Value Object만 의존 | DomainService, Repository 의존 |

**예시:**
```java
// DomainService (Domain Layer)
@Service
public class OrderService {
    public void validateOrder(Order order, List<Product> products) {
        // 도메인 규칙 검증
    }
}

// UseCase (Application Layer)
@Service
public class OrderUseCase {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderService orderService;  // DomainService 사용

    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1. 데이터 조회 (Repository)
        // 2. 비즈니스 로직 (DomainService)
        // 3. 데이터 저장 (Repository)
        // 4. DTO 변환
    }
}
```

---

### Q4. Anemic Domain Model은 무엇인가요?
**A:** 비즈니스 로직 없이 getter/setter만 있는 Entity를 말합니다.

**Anemic (나쁨) ❌:**
```java
public class Product {
    private String id;
    private Integer stock;

    // getter/setter만 존재
}

// Service에 비즈니스 로직
public class ProductService {
    public void decreaseStock(Product product, int quantity) {
        if (product.getStock() < quantity) {
            throw new Exception("재고 부족");
        }
        product.setStock(product.getStock() - quantity);
    }
}
```

**Rich Domain Model (좋음) ✅:**
```java
public class Product {
    private String id;
    private Integer stock;

    // 비즈니스 로직을 Entity 내부에 캡슐화
    public void decreaseStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException("재고 부족");
        }
        this.stock -= quantity;
    }
}

// Service는 단순히 호출만
public class ProductService {
    public void decreaseStock(Product product, int quantity) {
        product.decreaseStock(quantity);  // Entity의 메서드 호출
    }
}
```

---

### Q5. Entity에 Lombok을 사용해도 되나요?
**A:** 네, 사용 권장합니다.
- ✅ `@Getter`: getter 자동 생성
- ✅ `@AllArgsConstructor`: 모든 필드를 받는 생성자 생성
- ❌ `@Setter`: 사용 지양 (불변성을 위해)
- ❌ `@Data`: 너무 많은 기능 포함 (지양)

**권장 사용법:**
```java
@Getter
@AllArgsConstructor
public class Product {
    private String id;
    private String name;
    private Integer stock;

    // setter 대신 비즈니스 메서드 제공
    public void decreaseStock(int quantity) {
        this.stock -= quantity;
    }
}
```

---

### Q6. 테스트 커버리지 70%는 어떻게 계산하나요?
**A:** Jacoco로 자동 계산합니다.
```bash
# 테스트 실행 및 커버리지 측정
./gradlew test jacocoTestReport

# 리포트 확인
open build/reports/jacoco/test/html/index.html
```

**커버리지 계산 기준:**
- **라인 커버리지**: 전체 코드 라인 대비 실행된 라인 비율
- **브랜치 커버리지**: if/else 분기 실행 비율

**70% 달성 팁:**
- Domain Layer (Entity 메서드) 테스트: 필수
- Application Layer (UseCase) 테스트: 필수
- Infrastructure Layer (Repository): 선택 (단순 CRUD는 생략 가능)
- Presentation Layer (Controller): 선택 (통합 테스트로 대체 가능)

---

### Q7. Mock과 Stub의 차이는 무엇인가요?
**A:** 검증 방식이 다릅니다.

| 항목 | Mock | Stub |
|------|------|------|
| **목적** | 행위 검증 (메서드 호출 확인) | 상태 검증 (반환값 확인) |
| **사용** | `verify()` 사용 | `when().thenReturn()` 사용 |

**예시:**
```java
@Test
void 상품_조회_성공() {
    // Stub: 반환값 설정
    when(productRepository.findById("P001"))
        .thenReturn(Optional.of(product));

    // 실행
    ProductResponse response = productUseCase.getProduct("P001");

    // 상태 검증
    assertThat(response.getProductId()).isEqualTo("P001");

    // Mock: 행위 검증
    verify(productRepository).findById("P001");
}
```

---

### Q8. ConcurrentHashMap과 synchronized 중 어떤 것을 사용해야 하나요?
**A:** 상황에 따라 다릅니다.

| 방식 | 장점 | 단점 | 사용 시기 |
|------|------|------|----------|
| **ConcurrentHashMap** | 높은 동시성, Lock-free | 복잡한 연산 불가 | 단순 CRUD |
| **synchronized** | 간단한 구현 | 전체 메서드 잠금 | 간단한 비즈니스 로직 |
| **AtomicInteger** | 가장 빠름, Lock-free | 단순 증감만 가능 | 카운터, 수량 관리 |

**권장:**
- **Repository (데이터 저장)**: ConcurrentHashMap 사용
- **쿠폰 발급 (수량 제어)**: AtomicInteger + CAS 사용

---

### Q9. 인기 상품 집계를 매번 계산하는 것이 비효율적이지 않나요?
**A:** Week 3에서는 단순 구현이 목표입니다.
- ✅ **초기 구현**: 실시간 쿼리 (매번 계산)
- 🔄 **향후 개선**: 배치 스케줄러 + 캐시 (Week 5)

**Week 3 구현:**
```java
public List<PopularProductResponse> getTopProducts() {
    // 매번 전체 주문을 조회하여 집계 (단순하지만 느림)
    return orderRepository.findAll().stream()
        .filter(order -> order.getCreatedAt().isAfter(threeDaysAgo))
        .flatMap(order -> order.getItems().stream())
        .collect(Collectors.groupingBy(
            OrderItem::getProductId,
            Collectors.summingInt(OrderItem::getQuantity)
        ))
        .entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(5)
        .map(this::toResponse)
        .collect(Collectors.toList());
}
```

**Week 5 개선 (참고):**
- 배치 스케줄러: 5분마다 집계
- Redis 캐시: 집계 결과 저장
- Fallback: 캐시 실패 시 실시간 계산

---

### Q10. 레이어별로 DTO를 분리해야 하나요?
**A:** 원칙적으로는 분리하는 것이 맞지만, 실용적으로 접근하세요.

**원칙 (로이코치님 조언):**
- 📌 **레이어별로 관심사와 변경 이유가 다르기 때문에 레이어는 자신만의 DTO를 가져야 함**
- 📌 **소프트웨어 핵심 원칙: 변경 이유가 다른 것은 분리한다**

**실용적 접근:**
- ✅ **도메인 모델이 안정적이면** 여러 레이어에서 사용 가능
- ✅ **Week 3에서는** Domain Entity를 여러 레이어에서 사용해도 무방
- ⚠️ **실무에서는** 레이어별 DTO 분리 권장

**DTO 재사용 전략:**
```java
// 공통 필드를 Composition으로 재사용
public class ProductBaseDto {
    private String productId;
    private String name;
    private Long price;
}

// API별 전용 DTO (단일 책임 원칙)
public class ProductListResponse {
    private ProductBaseDto product;  // 컴포지션
    private Integer stock;
}

public class ProductDetailResponse {
    private ProductBaseDto product;  // 컴포지션
    private List<Review> reviews;
    private Integer avgRating;
}
```

**균형 찾기:**
- 🎯 **단일 책임 원칙 (SRP)**: API마다 전용 DTO
- 🔄 **DRY 원칙**: 공통 부분은 컴포지션으로 재사용
- ⚖️ 두 원칙의 균형을 찾는 것이 중요

---

### Q11. Mock API를 왜 만드나요?
**A:** 협업 시 병목을 줄이고 작업의 병렬성을 높이기 위함입니다.

**Mock API의 목적 (로이코치님 조언):**
1. 🤝 **협업 병목 제거**: 백엔드 완성 전에 프론트/모바일 개발 시작
2. ⚡ **작업 병렬성**: 팀원들이 동시에 작업 가능
3. 🧪 **테스트 가능성**: 가짜 응답 데이터로 UI 테스트

**Week 2 → Week 3 변환 전략:**
```
Week 2 (Mock):
OrderController
  ├── ConcurrentHashMap에 하드코딩된 Mock 데이터
  └── 간단한 CRUD 로직

Week 3 (Layered Architecture):
OrderController                    (Presentation)
  └── OrderUseCase                 (Application)
        ├── OrderService           (Domain)
        ├── ProductRepository      (Domain Interface)
        └── InMemoryOrderRepository (Infrastructure)
```

**중요:**
- ✅ Mock을 잘 정의하고, 이것을 그대로 활용하여 실제 기능으로 전환
- ✅ Controller 이름 유지: `OrderController` (O), `MockOrderController` (X)
- ✅ ConcurrentHashMap을 Repository로 이동시켜 재사용

---

### Q12. Entity에 비즈니스 로직을 두는 이유는 무엇인가요?
**A:** 객체의 능동성, 테스트 용이성, 로직 분산 때문입니다.

**Entity에 로직을 두는 이유 (로이코치님 조언):**
1. 🎯 **객체의 능동성**: Entity가 스스로 행동하도록 (Rich Domain Model)
2. 🧪 **테스트 용이성**: Entity 메서드만 단독으로 테스트 가능
3. 📦 **로직 분산**: Service 로직 간소화 (God Service 방지)

**비교:**
```java
// Anemic Domain Model (❌ 나쁨)
public class Product {
    private Integer stock;
    public void setStock(Integer stock) { this.stock = stock; }
    public Integer getStock() { return stock; }
}

@Service
public class ProductService {
    public void decreaseStock(Product product, int quantity) {
        // Service에 모든 로직이 집중
        if (product.getStock() < quantity) {
            throw new BusinessException("재고 부족");
        }
        if (quantity <= 0) {
            throw new BusinessException("수량은 0보다 커야 함");
        }
        product.setStock(product.getStock() - quantity);
    }
}

// Rich Domain Model (✅ 좋음)
public class Product {
    private Integer stock;

    // Entity가 스스로 행동 (능동성)
    public void decreaseStock(int quantity) {
        validateQuantity(quantity);
        validateStock(quantity);
        this.stock -= quantity;
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("수량은 0보다 커야 함");
        }
    }

    private void validateStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException("재고 부족");
        }
    }
}

@Service
public class ProductService {
    public void decreaseStock(Product product, int quantity) {
        product.decreaseStock(quantity);  // 단순 위임
    }
}
```

**테스트 용이성:**
```java
// Entity 메서드만 단독 테스트 (의존성 없음)
@Test
void 재고_차감_성공() {
    Product product = new Product("P001", "노트북", 10);
    product.decreaseStock(3);
    assertThat(product.getStock()).isEqualTo(7);
}
```

---

### Q13. Week 3에서 동시성 제어를 고민해야 하나요?
**A:** Step 5에서는 고민하지 않아도 됩니다. Step 6에서만 고민하세요.

**Week 3 동시성 제어 범위 (로이코치님 조언):**
- ❌ **Step 5**: 동시성 제어 고민 불필요
  - ConcurrentHashMap만으로 충분
  - 레이어드 아키텍처 구현에 집중
- ✅ **Step 6**: 선착순 쿠폰 발급만 동시성 제어
  - synchronized, ReentrantLock, AtomicInteger 중 택1
  - Race Condition 방지 필수

**ConcurrentHashMap 활용:**
```java
@Repository
public class InMemoryProductRepository implements ProductRepository {
    // Thread-safe 컬렉션 (Step 5에서 충분)
    private final Map<String, Product> storage = new ConcurrentHashMap<>();

    @Override
    public Product save(Product product) {
        storage.put(product.getId(), product);
        return product;
    }
}
```

---

### Q14. step5와 step6를 하나의 PR로 제출해도 되나요?
**A:** 권장하지 않습니다.
- ✅ **step5 PR**: 레이어드 아키텍처 기본 구현
- ✅ **step6 PR**: step5 기반 위에 동시성 제어 추가

**이유:**
- 리뷰가 용이함 (작은 단위)
- 문제 발생 시 롤백 쉬움
- 점진적 개선 경험

---

---

### Q15. 입력값 유효성 검증은 어디서 해야 하나요?
**A:** Controller에서 먼저 검증하고, 비즈니스 규칙은 Entity에서 검증하세요.

**검증 레이어 (로이코치님 조언):**
```
입력값 검증 흐름:
Controller > Service > Entity > DB

1. Controller: 형식 검증 (@Valid, @NotNull 등)
2. Entity: 비즈니스 규칙 검증 (재고 부족, 수량 제한 등)
```

**예시:**
```java
// Controller: 형식 검증
@PostMapping("/orders")
public ApiResponse<OrderResponse> createOrder(
    @Valid @RequestBody CreateOrderRequest request  // @Valid로 형식 검증
) {
    return ApiResponse.success(orderUseCase.createOrder(request));
}

// Request DTO: 형식 검증 어노테이션
public class CreateOrderRequest {
    @NotBlank(message = "사용자 ID는 필수입니다")
    private String userId;

    @NotEmpty(message = "주문 상품은 최소 1개 이상이어야 합니다")
    private List<OrderItemRequest> items;
}

// Entity: 비즈니스 규칙 검증
public class Product {
    public void decreaseStock(int quantity) {
        // 비즈니스 규칙 검증
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
        this.stock -= quantity;
    }
}
```

**검증 분리 원칙:**
- ✅ Controller: 형식, Null 체크, 범위 검증
- ✅ Entity: 비즈니스 규칙 검증

---

### Q16. Week 3에서 캐시를 구현해야 하나요?
**A:** 아니요, Week 3에서는 캐시를 고민하지 않아도 됩니다.

**이유 (로이코치님 조언):**
- 📌 **Week 3는 인메모리 구현**: DB도 사용하지 않음
- 📌 모든 데이터가 이미 메모리에 있기 때문에 캐시가 불필요
- 📌 캐시는 Week 5 이후 DB 도입 시 고려

**Week 3 Focus:**
- ✅ 레이어드 아키텍처 구현
- ✅ In-Memory Repository (ConcurrentHashMap)
- ✅ 동시성 제어 (Step 6)
- ❌ 캐시 (불필요)

---

### Q17. 유비쿼터스 언어란 무엇인가요?
**A:** 팀원 모두가 사용하는 공통 언어입니다.

**유비쿼터스 언어의 중요성 (로이코치님 조언):**
- 📋 개발자, 기획자, 디자이너가 모두 같은 용어 사용
- 📋 코드에도 동일한 용어 반영
- 📋 커뮤니케이션 비용 감소

**예시:**
```
기획서: "사용자가 상품을 장바구니에 담는다"
↓
코드:
CartUseCase.addItemToCart(userId, productId)  // ✅ 좋음
CartUseCase.insert(userId, productId)         // ❌ 나쁨 (다른 용어)
```

**적용 방법:**
1. 기획서/요구사항의 용어를 그대로 코드에 사용
2. 클래스명, 메서드명, 변수명에 비즈니스 용어 반영
3. 팀 내 용어집 정리 (Glossary)

**예시:**
- "주문" → `Order`, `OrderUseCase`
- "장바구니" → `Cart`, `CartItem`
- "선착순 쿠폰" → `FirstComeCoupon`, `issueCoupon()`

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
