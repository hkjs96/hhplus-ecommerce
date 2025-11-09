# E-Commerce Backend System

항해플러스 백엔드 커리큘럼 - 이커머스 시스템 (Week 4: Database Integration & Optimization)

---

## 📋 프로젝트 개요

**핵심 목표**: 레이어드 아키텍처 기반의 데이터베이스 통합 및 성능 최적화

단일 서버 환경에서 동시성 제어, 장애 대응, 성능 최적화를 고려한 REST API 설계 및 구현

---

## 🎯 4주차 목표

### Step 7: Database Integration (필수)
- ✅ **JPA Entity 변환**: Week 3 도메인 모델을 JPA Entity로 변환
- ✅ **Repository 구현**: JPA Repository + JDBC Template 혼합 사용
- ✅ **Transaction 관리**: @Transactional 적용 및 격리 수준 설정
- ✅ **외부 시스템 연동**: Outbox 패턴으로 안정적인 데이터 전송
- ✅ **통합 테스트**: Testcontainers 기반 실제 MySQL 테스트
- ✅ **쿼리 로깅**: p6spy로 바인딩 파라미터 확인

### Step 8: Database Optimization (필수)
- ✅ **성능 병목 식별**: Slow Query 로그, EXPLAIN 분석
- ✅ **인덱스 설계**: Single, Composite, Covering Index 적용
- ✅ **쿼리 최적화**: N+1 문제 해결, JOIN 최적화
- ✅ **최적화 보고서**: Before/After 성능 비교 문서화

---

## 🏗️ 시스템 아키텍처

### Layered Architecture

```
┌─────────────────────────────────────────┐
│     Presentation Layer (API)            │
│  ┌──────────────────────────────────┐   │
│  │  Controllers (REST Endpoints)    │   │
│  │  - ProductController             │   │
│  │  - OrderController               │   │
│  │  - CartController                │   │
│  │  - CouponController              │   │
│  │  - UserController                │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
              ⬇
┌─────────────────────────────────────────┐
│     Application Layer (Use Cases)       │
│  ┌──────────────────────────────────┐   │
│  │  UseCases (Business Flows)       │   │
│  │  - OrderUseCase                  │   │
│  │  - PaymentUseCase                │   │
│  │  - CouponUseCase                 │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
              ⬇
┌─────────────────────────────────────────┐
│     Domain Layer (Business Logic)       │
│  ┌──────────────────────────────────┐   │
│  │  Domain Services & Entities      │   │
│  │  - Product, Stock                │   │
│  │  - Order, OrderItem              │   │
│  │  - Cart, CartItem                │   │
│  │  - Coupon, UserCoupon            │   │
│  │  - User                          │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
              ⬇
┌─────────────────────────────────────────┐
│   Infrastructure Layer (Persistence)    │
│  ┌──────────────────────────────────┐   │
│  │  Repositories & External APIs    │   │
│  │  - JPA Repositories              │   │
│  │  - Redis Cache                   │   │
│  │  - External Data Platform Client │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
              ⬇
┌─────────────────────────────────────────┐
│          Database & Cache               │
│   MySQL  │  Redis  │  External API      │
└─────────────────────────────────────────┘
```

---

## 🗂️ 문서 구조

프로젝트의 모든 설계 문서는 `docs/` 폴더에 체계적으로 정리되어 있습니다.

```
docs/
├── api/                          # API 설계 문서
│   ├── requirements.md           # 요구사항 명세서
│   ├── api-specification.md      # API 명세서 (15개 엔드포인트)
│   └── error-codes.md            # 에러 코드 표준
│
├── diagrams/                     # 다이어그램
│   ├── erd.md                    # ERD (DBML, Mermaid)
│   └── sequence-diagrams.md      # 시퀀스 다이어그램 (API별)
│
├── week4/                        # Week 4 구현 가이드 ⭐
│   ├── step7-integration-guide.md          # DB 통합 환경 설정
│   ├── step7-implementation-examples.md    # 실전 코드 예시
│   └── step8-optimization-report-template.md  # 최적화 보고서
│
├── feedback/                     # 코치 피드백
│   └── week4/
│       └── coach-park-jisu-feedback.md
│
└── PROJECT_STRUCTURE.md          # 프로젝트 구조 가이드
```

### 📍 주요 문서 바로가기

| 문서 | 설명 | 링크 |
|------|------|------|
| **Step 7 통합 가이드** | MySQL 환경 설정 및 Entity 변환 | [step7-integration-guide.md](docs/week4/step7-integration-guide.md) |
| **Step 7 코드 예시** | Repository, Outbox, Transaction 구현 | [step7-implementation-examples.md](docs/week4/step7-implementation-examples.md) |
| **Step 8 최적화 템플릿** | 성능 병목 분석 및 보고서 작성 | [step8-optimization-report-template.md](docs/week4/step8-optimization-report-template.md) |
| **API 명세서** | REST API 엔드포인트 상세 | [api-specification.md](docs/api/api-specification.md) |
| **ERD** | 데이터베이스 설계 (10개 테이블) | [erd.md](docs/diagrams/erd.md) |
| **코치 피드백** | Week 4 코치 피드백 정리 | [coach-park-jisu-feedback.md](docs/feedback/week4/coach-park-jisu-feedback.md) |

---

## 🔑 핵심 기능 (4가지)

### 1. 상품 관리 📦
- **상품 조회**: 목록, 상세, 인기 상품 Top 5
- **재고 관리**: Stock 테이블 분리, 재고 이력 추적 (StockHistory)
- **동시성 제어**: Optimistic Lock (@Version)

### 2. 주문/결제 💳
- **장바구니**: 상품 추가, 조회, 수정, 삭제
- **주문 생성**: 재고 검증, 쿠폰 적용
- **포인트 결제**: 내부 포인트 시스템 (PG 없음)
- **재고 차감**: 결제 완료 **후** 차감 (Optimistic Lock)
- **동시성 제어**: Pessimistic Lock (포인트), Optimistic Lock (재고)

### 3. 쿠폰 시스템 🎟️
- **선착순 발급**: Optimistic Lock으로 정확한 수량 제어
- **1인 1매 제한**: DB Unique Constraint
- **쿠폰 사용**: 결제 시점에 적용

### 4. 외부 연동 🔗
- **비동기 전송**: 주문 완료 후 외부 데이터 플랫폼으로 전송
- **Timeout & Retry**: 3초 타임아웃, 최대 3회 재시도 (1분 → 5분 → 30분)
- **Fallback**: Outbox 패턴 (재시도 큐)

---

## 🛠️ 기술 스택

### Backend
- **Language**: Java 17
- **Framework**: Spring Boot 3.5.7
- **Build**: Gradle

### Database & ORM
- **RDBMS**: MySQL 8.0
- **ORM**: JPA (Hibernate)
- **Direct Query**: JDBC Template (복잡한 쿼리용)
- **Migration**: SQL Scripts (DDL)

### Testing
- **Unit Test**: JUnit 5, Mockito
- **Integration Test**: Testcontainers (MySQL 8.0)
- **Coverage**: Jacoco (94% line coverage)

### Monitoring & Debugging
- **Query Logging**: p6spy (바인딩 파라미터 확인)
- **Slow Query**: MySQL Slow Query Log
- **Performance Analysis**: EXPLAIN, EXPLAIN ANALYZE
- **Index Analysis**: Percona Toolkit (pt-duplicate-key-checker, pt-query-digest)

### 동시성 제어
- **Pessimistic Lock**: `SELECT ... FOR UPDATE` (포인트 차감)
- **Optimistic Lock**: `@Version` (재고 차감, 쿠폰 발급)
- **DB Unique Constraint**: 1인 1매 쿠폰 보장

### 가용성 패턴
- **Timeout**: 3초 (외부 API)
- **Retry**: Exponential Backoff + Outbox 패턴
- **Fallback**: 빈 배열 반환 (서비스 중단 방지)
- **Async**: `@Async` (비동기 외부 전송)

### Development Tools
- **Docker**: MySQL 8.0 컨테이너
- **Docker Compose**: 개발 환경 구성

---

## 📊 데이터베이스 설계

### 테이블 구조 (10개)

| 테이블 | 역할 | 주요 컬럼 | 비고 |
|--------|------|-----------|------|
| **products** | 상품 정보 | id, name, price, category | - |
| **stock** | 재고 현황 | product_id, quantity, version | Optimistic Lock |
| **stock_history** | 재고 변동 이력 | type, quantity_before, quantity_after | FK 없음 (조회 최적화) |
| **carts** | 장바구니 | user_id | 사용자당 1개 |
| **cart_items** | 장바구니 상품 | cart_id, product_id, quantity | - |
| **orders** | 주문 | user_id, total_amount, status | PENDING, COMPLETED |
| **order_items** | 주문 상세 | order_id, product_id, quantity | - |
| **coupons** | 쿠폰 마스터 | total_quantity, issued_quantity, version | Optimistic Lock |
| **user_coupons** | 사용자 쿠폰 | user_id, coupon_id, status | Unique (user_id, coupon_id) |
| **users** | 사용자 | email, balance | balance = 포인트 잔액 |

### 인덱스 전략
```sql
-- 상품 조회
CREATE INDEX idx_products_category ON products(category);

-- 재고 조회
CREATE UNIQUE INDEX uidx_stock_product_warehouse ON stock(product_id, warehouse_id);

-- 재고 이력 (FK 없이 인덱스만)
CREATE INDEX idx_stock_history_product_id ON stock_history(product_id);
CREATE INDEX idx_stock_history_reference ON stock_history(reference_type, reference_id);

-- 주문 조회
CREATE INDEX idx_orders_user_status ON orders(user_id, status);
CREATE INDEX idx_orders_paid_at ON orders(paid_at);

-- 쿠폰 조회
CREATE INDEX idx_user_coupons_user_status ON user_coupons(user_id, status);
CREATE UNIQUE INDEX uidx_user_coupons_user_coupon ON user_coupons(user_id, coupon_id);
```

**상세 ERD**: [docs/diagrams/erd.md](docs/diagrams/erd.md)

---

## 🔄 핵심 플로우

### 1. 주문 생성 및 결제 플로우

```
1. 장바구니 조회 (MySQL)
   ↓
2. 재고 검증 (MySQL stock 테이블)
   ↓
3. 쿠폰 검증 (선택, MySQL user_coupons)
   ↓
4. 주문 생성 (status=PENDING)
   ↓
5. 결제 처리
   - 포인트 차감 (Pessimistic Lock)
   - 재고 차감 (Optimistic Lock) ← 결제 성공 후
   - 재고 이력 기록 (stock_history)
   - 쿠폰 사용 처리
   ↓
6. 주문 상태 업데이트 (status=COMPLETED)
   ↓
7. 외부 데이터 전송 (@Async, Non-blocking)
   - 성공: 완료
   - 실패: Outbox 테이블에 저장 → 재시도 워커가 처리
```

**상세 시퀀스 다이어그램**: [docs/diagrams/sequence-diagrams.md](docs/diagrams/sequence-diagrams.md)

### 2. 선착순 쿠폰 발급 플로우

```
1. 쿠폰 조회 (total_quantity, issued_quantity, version)
   ↓
2. 중복 발급 체크 (user_coupons)
   ↓
3. 쿠폰 발급 (Optimistic Lock)
   - UPDATE coupons SET issued_quantity = issued_quantity + 1, version = version + 1
     WHERE version = ? AND issued_quantity < total_quantity
   ↓
4. 사용자 쿠폰 생성 (Unique Constraint: user_id + coupon_id)
   - 성공: 발급 완료
   - Unique 제약 위반: 쿠폰 수량 롤백 + 에러 반환
```

---

## 🚨 에러 코드 체계

### HTTP Status Code 매핑

| Status | 상황 | 예시 |
|--------|------|------|
| **200 OK** | 성공 (조회, 수정) | 장바구니 조회, 포인트 충전 |
| **201 Created** | 생성 성공 | 주문 생성, 쿠폰 발급 |
| **400 Bad Request** | 잘못된 요청 | 재고 부족, 잔액 부족, 유효하지 않은 쿠폰 |
| **404 Not Found** | 리소스 없음 | 주문 없음, 사용자 없음 |
| **409 Conflict** | 충돌 | 쿠폰 소진, 동시성 충돌 (Optimistic Lock) |
| **500 Internal Server Error** | 서버 오류 | 예상치 못한 오류 |

### 비즈니스 에러 코드

```java
// 상품 관련
PRODUCT_NOT_FOUND           // P001: 상품을 찾을 수 없습니다
INSUFFICIENT_STOCK          // P002: 재고가 부족합니다

// 주문 관련
EMPTY_CART                  // O001: 장바구니가 비어있습니다
ORDER_NOT_FOUND             // O002: 주문을 찾을 수 없습니다
INVALID_QUANTITY            // O003: 유효하지 않은 수량입니다

// 결제 관련
INSUFFICIENT_BALANCE        // PAY001: 잔액이 부족합니다
PAYMENT_FAILED              // PAY002: 결제에 실패했습니다
STOCK_DEDUCTION_FAILED      // PAY003: 재고 차감 실패 (재시도 필요)

// 쿠폰 관련
COUPON_NOT_FOUND            // C001: 쿠폰을 찾을 수 없습니다
COUPON_SOLD_OUT             // C002: 쿠폰이 모두 소진되었습니다
INVALID_COUPON              // C003: 유효하지 않은 쿠폰입니다
EXPIRED_COUPON              // C004: 만료된 쿠폰입니다
ALREADY_ISSUED              // C005: 이미 발급받은 쿠폰입니다
COUPON_ISSUE_FAILED         // C006: 쿠폰 발급 실패 (동시성 충돌)

// 사용자 관련
USER_NOT_FOUND              // U001: 사용자를 찾을 수 없습니다
INVALID_AMOUNT              // U002: 유효하지 않은 금액입니다
```

**상세 에러 코드**: [docs/api/error-codes.md](docs/api/error-codes.md)

---

## 🎯 동시성 제어 전략

### Pessimistic Lock (비관적 락)

**사용처**: 포인트 충전, 포인트 차감

**이유**:
- 정확성이 최우선 (포인트 불일치 허용 불가)
- 충돌 빈도가 낮음 (성능 영향 최소)

**구현**:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT u FROM User u WHERE u.id = :id")
User findByIdWithLock(@Param("id") String id);
```

```sql
SELECT * FROM users WHERE id = ? FOR UPDATE;
UPDATE users SET balance = balance - ? WHERE id = ?;
```

### Optimistic Lock (낙관적 락)

**사용처**: 재고 차감, 쿠폰 발급

**이유**:
- 높은 동시성 처리 (성능 우선)
- 충돌 시 재시도 가능

**구현**:
```java
@Entity
public class Stock {
    @Version
    private Long version;
}
```

```sql
UPDATE stock
SET quantity = quantity - ?, version = version + 1
WHERE product_id = ? AND version = ? AND quantity >= ?;
```

**충돌 처리**:
- 재고 차감 실패 시 포인트 복구 후 409 Conflict 반환
- 쿠폰 발급 실패 시 409 Conflict 반환 (클라이언트 재시도)

### DB Unique Constraint

**사용처**: 1인 1매 쿠폰 제한

**구현**:
```sql
CREATE UNIQUE INDEX uidx_user_coupons_user_coupon
ON user_coupons(user_id, coupon_id);
```

**충돌 처리**:
- DuplicateKeyException 발생 시 쿠폰 발급 수량 롤백

---

## 🛡️ 가용성 패턴

### 1. Timeout ⏱️

**적용**: 모든 외부 API 호출

```java
@Bean
public RestTemplate restTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);  // 3초
    factory.setReadTimeout(3000);     // 3초
    return new RestTemplate(factory);
}
```

### 2. Retry 🔄

**적용**: 외부 데이터 플랫폼 전송 실패 시

**전략**: Exponential Backoff
- 1차 실패: 1분 후 재시도
- 2차 실패: 5분 후 재시도
- 3차 실패: 30분 후 재시도
- 3회 모두 실패: 영구 실패 (알림 발송)

```java
@Scheduled(fixedDelay = 60000) // 1분마다 실행
public void retryFailedMessages() {
    List<OutboxMessage> pendingMessages = outboxRepository.findPending();

    for (OutboxMessage message : pendingMessages) {
        if (message.getRetryCount() < 3) {
            // 재시도 로직
        } else {
            // 영구 실패 처리
        }
    }
}
```

### 3. Fallback 🛡️

**적용**: 인기 상품 조회 (실시간 쿼리)

**전략**:
- 정상: MySQL 쿼리 결과 반환
- 쿼리 실패/타임아웃: 빈 배열 반환 (서비스 중단 방지)

```java
public List<PopularProductDTO> getPopularProducts() {
    try {
        return productRepository.findTopProducts(LocalDateTime.now().minusDays(3), 5);
    } catch (Exception e) {
        log.error("Failed to fetch popular products", e);
        return Collections.emptyList(); // Fallback
    }
}
```

### 4. Async (비동기 처리) ⚡

**적용**: 외부 데이터 플랫폼 전송

**이유**:
- 주문 완료 시간 단축 (외부 API 응답 대기 불필요)
- 외부 API 장애가 주문 성공에 영향 없음

```java
@Async
public CompletableFuture<Void> sendOrderData(Order order) {
    try {
        externalApiClient.sendOrder(transformToExternalFormat(order));
    } catch (Exception e) {
        // Outbox 테이블에 저장 (재시도 큐)
        outboxRepository.save(new OutboxMessage(order));
    }
    return CompletableFuture.completedFuture(null);
}
```

---

## 📝 API 엔드포인트

### 상품

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | `/api/products` | 상품 목록 조회 | - |
| GET | `/api/products/{productId}` | 상품 상세 조회 | - |
| GET | `/api/products/top` | 인기 상품 Top 5 | - |

### 장바구니

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/cart/items` | 장바구니 추가 | ✅ |
| GET | `/api/cart` | 장바구니 조회 | ✅ |
| PUT | `/api/cart/items` | 장바구니 수정 | ✅ |
| DELETE | `/api/cart/items` | 장바구니 삭제 | ✅ |

### 주문/결제

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/orders` | 주문 생성 | ✅ |
| POST | `/api/orders/{orderId}/payment` | 결제 처리 | ✅ |
| GET | `/api/orders/{orderId}` | 주문 조회 | ✅ |

### 쿠폰

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/coupons/{couponId}/issue` | 쿠폰 발급 | ✅ |
| GET | `/api/users/{userId}/coupons` | 보유 쿠폰 조회 | ✅ |

### 사용자

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | `/api/users/{userId}/balance` | 포인트 조회 | ✅ |
| POST | `/api/users/{userId}/balance/charge` | 포인트 충전 | ✅ |

**상세 API 명세**: [docs/api/api-specification.md](docs/api/api-specification.md)

---

## 🧪 테스트 전략 (선택 구현)

### 1. 컨트롤러 단위 테스트
- `@WebMvcTest` 활용
- Mock 서비스 주입
- API 엔드포인트 응답 검증

### 2. 서비스 통합 테스트
- `@SpringBootTest` 활용
- H2 In-Memory DB 사용
- 트랜잭션 롤백

### 3. 동시성 테스트
- `ExecutorService`로 멀티 스레드 시뮬레이션
- 재고 차감, 쿠폰 발급 동시성 검증

---

## 🚀 실행 방법

### 사전 요구사항
- Java 17 이상
- Docker & Docker Compose
- Gradle 8.0 이상

### 1. MySQL 환경 구성 (Docker)

```bash
# Docker Compose로 MySQL 8.0 실행
docker-compose up -d

# 데이터베이스 생성
docker exec -it hhplus-mysql mysql -uroot -ppassword -e "CREATE DATABASE IF NOT EXISTS ecommerce;"

# DDL 실행 (스키마 생성)
docker exec -i hhplus-mysql mysql -uroot -ppassword ecommerce < docs/sql/schema.sql
```

### 2. 애플리케이션 빌드 및 실행

```bash
# 의존성 설치 및 빌드
./gradlew clean build

# 애플리케이션 실행
./gradlew bootRun

# 또는 JAR 실행
java -jar build/libs/ecommerce-0.0.1-SNAPSHOT.jar
```

### 3. API 문서 확인

```
Swagger UI: http://localhost:8080/swagger-ui/index.html
```

### 4. 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# 테스트 커버리지 리포트 생성
./gradlew test jacocoTestReport

# 리포트 확인
open build/reports/jacoco/test/html/index.html
```

### 5. 쿼리 로깅 확인

```bash
# p6spy 로그 확인 (바인딩 파라미터 포함)
tail -f logs/spy.log
```

---

## 📚 학습 포인트

### Week 4에서 중점적으로 학습한 내용

#### 1. **JPA Entity 설계**
- Week 3 도메인 모델을 JPA Entity로 변환
- `@Entity`, `@Table`, `@Column` 매핑
- 양방향 연관관계 설정 및 주의사항
- Lombok 사용 시 주의사항 (`@Data`, `@ToString` 순환 참조)

#### 2. **Repository 패턴 구현**
- JPA Repository 인터페이스 정의
- `findByIdOrThrow()` default method 패턴
- JDBC Template 혼합 사용 (복잡한 쿼리)
- Testcontainers로 실제 MySQL 테스트

#### 3. **Transaction 관리**
- `@Transactional` 적용 범위 및 격리 수준
- 트랜잭션 경계 설정 (Service Layer)
- 외부 API 호출은 트랜잭션 밖에서 처리
- 보상 트랜잭션 (재고 차감 실패 시 포인트 복구)

#### 4. **외부 시스템 연동**
- Outbox 패턴으로 안정적인 데이터 전송
- 실패 시 재시도 로직 (Exponential Backoff)
- 비동기 처리 (`@Async`)

#### 5. **쿼리 성능 최적화**
- EXPLAIN으로 실행 계획 분석
- N+1 문제 감지 및 해결 (Fetch Join, Batch Size)
- 인덱스 설계 (Single, Composite, Covering)
- p6spy로 쿼리 로깅 및 바인딩 파라미터 확인
- Percona Toolkit으로 중복 인덱스 분석

#### 6. **테스트 전략**
- Testcontainers로 실제 DB 환경 테스트
- `@Transactional` 활용한 테스트 격리
- 동시성 테스트 (ExecutorService + CountDownLatch)
- 의미 있는 assertion (단순 null 체크 지양)

---

## 🔍 주요 설계 결정 (Design Decisions)

### 1. 재고 테이블 분리 (Product vs Stock)

**결정**: 상품(Product)과 재고(Stock)를 별도 테이블로 분리

**이유**:
- 재고 이력 추적 용이 (StockHistory 테이블)
- 다중 창고 확장 가능 (warehouse_id 필드)
- 재고 불일치 디버깅 용이

### 2. 포인트 시스템 (PG 없음)

**결정**: 외부 PG 연동 없이 내부 포인트 시스템만 구현

**이유**:
- Week 2는 설계 단계 (PG 연동은 Week 3+)
- 핵심 로직(동시성, 가용성)에 집중
- 사용자는 미리 포인트를 충전하여 사용

### 3. 재고 차감 시점 (결제 완료 후)

**결정**: 재고 차감은 결제 완료 **후**에 수행

**이유**:
- 결제 실패 시 재고 복원 불필요
- 트랜잭션 범위 최소화
- 데이터 일관성 보장

### 4. 쿠폰 적용 시점 (결제 단계)

**결정**: 쿠폰은 주문 생성 시 검증만 하고, 실제 사용 처리는 결제 완료 시

**이유**:
- 결제 실패 시 쿠폰 복원 불필요
- 주문 생성과 결제를 분리하여 유연성 확보

### 5. 인기 상품 조회 (실시간 쿼리)

**결정**: 배치 집계 대신 실시간 쿼리로 단순화 (피드백 반영)

**이유**:
- Week 2 수준에서는 단순한 접근 권장
- 복잡도 감소, 기술 학습 목표에 집중
- 필요 시 추후 캐시/배치로 최적화 가능

```sql
-- 실시간 쿼리 (최근 3일)
SELECT p.id, p.name, SUM(oi.quantity) as sales_count
FROM products p
JOIN order_items oi ON p.id = oi.product_id
JOIN orders o ON oi.order_id = o.id
WHERE o.status = 'COMPLETED' AND o.paid_at >= NOW() - INTERVAL 3 DAY
GROUP BY p.id ORDER BY sales_count DESC LIMIT 5;
```

### 6. StockHistory FK 제약조건 없음

**결정**: stock_history 테이블은 FK 제약조건 없이 인덱스만 설정

**이유**:
- 조회 전용 테이블 (감사 목적)
- FK 락으로 인한 성능 저하 방지
- 애플리케이션 레벨에서 데이터 무결성 보장

---

## 📋 체크리스트

### Week 3: Layered Architecture ✅
- [x] 4계층 분리 (Presentation, Application, Domain, Infrastructure)
- [x] Domain Entity 구현 (비즈니스 로직 캡슐화)
- [x] Repository 패턴 (인터페이스 Domain, 구현체 Infrastructure)
- [x] UseCase 구현 (Application Layer)
- [x] In-Memory Repository (ConcurrentHashMap)
- [x] 동시성 제어 (synchronized, ReentrantLock)
- [x] 단위 테스트 (커버리지 94%)

### Week 4 Step 7: Database Integration ✅
- [x] **JPA Entity 변환**: Week 3 도메인 모델 → JPA Entity
- [x] **Repository 구현**: JPA Repository + JDBC Template 혼합
- [x] **Transaction 관리**: @Transactional 적용
- [x] **외부 시스템 연동**: Outbox 패턴 구현
- [x] **통합 테스트**: Testcontainers 기반 MySQL 테스트
- [x] **쿼리 로깅**: p6spy 설정 완료
- [x] **Docker 환경**: docker-compose.yml 구성

### Week 4 Step 8: Database Optimization 🚧
- [ ] **Slow Query 식별**: 성능 병목 지점 파악
- [ ] **EXPLAIN 분석**: 실행 계획 분석 및 문서화
- [ ] **인덱스 설계**: Composite Index, Covering Index 적용
- [ ] **N+1 문제 해결**: Fetch Join, Batch Size 적용
- [ ] **쿼리 최적화**: JOIN 최적화, Subquery 개선
- [ ] **최적화 보고서**: Before/After 성능 비교 작성

### 코치 피드백 반영 ✅
- [x] **findByIdOrThrow() 패턴**: Repository default method 추가
- [x] **검증 레이어 분리**: Controller/UseCase/Entity 검증 명확화
- [x] **동시성 제어 비교**: synchronized vs ReentrantLock vs CAS 문서화
- [x] **테스트 품질 개선**: 의미 있는 assertion, Edge case 추가
- [x] **문서화**: Week 4 가이드 및 코드 예시 작성

---

## 🙏 참고 자료

### JPA & Hibernate
- [Hibernate User Guide](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html)
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [N+1 Problem Solutions](https://vladmihalcea.com/n-plus-1-query-problem/)

### Database Optimization
- [Use The Index, Luke](https://use-the-index-luke.com/) - 인덱스 설계 가이드
- [MySQL Performance Tuning](https://dev.mysql.com/doc/refman/8.0/en/optimization.html)
- [Percona Toolkit Documentation](https://docs.percona.com/percona-toolkit/)

### Testing
- [Testcontainers Documentation](https://testcontainers.com/)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)

### Resilience Patterns
- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)

### Concurrency Control
- [Optimistic Locking vs Pessimistic Locking](https://stackoverflow.com/questions/129329/optimistic-vs-pessimistic-locking)

---

## 📞 Contact

프로젝트 관련 문의: [GitHub Issues](https://github.com/hkjs96/hhplus-ecommerce/issues)

---

## 📄 License

This project is licensed under the MIT License.

---

**항해플러스 백엔드 커리큘럼 Week 4** - Database Integration & Optimization
