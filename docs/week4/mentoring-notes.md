# Week 4 평일 코치님 멘토링 노트

**핵심 주제**:
- 데이터 처리와 DB 설계
- 인메모리 구현체와 JPA의 장단점
- 인덱스와 정렬 데이터(Ordered Data) 활용 및 비용 고려

---

## 📋 목차

1. [데이터베이스 설계 및 DDL](#1-데이터베이스-설계-및-ddl)
2. [인덱스 설계 전략](#2-인덱스-설계-전략)
3. [인메모리 vs JPA](#3-인메모리-vs-jpa)
4. [동시성 제어 테스트](#4-동시성-제어-테스트)
5. [JPA 활용 전략](#5-jpa-활용-전략)
6. [캐시 전략](#6-캐시-전략)
7. [액션 아이템](#7-액션-아이템)

---

## 1. 데이터베이스 설계 및 DDL

### 핵심 포인트

#### 마이그레이션 파일은 필수가 아니다
- ✅ **충분한 수준**: "DDL로 테이블을 생성했고, 인덱스가 존재한다"
- ✅ **중요한 것**: 데이터베이스의 DDL과 Entity 매핑이 정상적으로 되는지 검증
- ❌ **불필요**: 복잡한 마이그레이션 스크립트 작성

**로이코치님 조언**:
> "마이그레이션 파일은 필수는 아님. DDL로 테이블을 생성했고, 인덱스가 존재한다 정도로 충분."

#### DDL과 Entity 매핑 검증이 핵심
```java
// Entity 정의
@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer stock;
}
```

**검증 사항**:
- [ ] DDL로 생성한 테이블 구조와 Entity 매핑이 일치하는가?
- [ ] 컬럼 타입, Nullable, 제약조건이 정확한가?
- [ ] FK(Foreign Key) 관계가 올바르게 설정되었는가?

### 실무 관점

**데이터베이스 기본기를 모르는 경우가 많음**
- SQL 기본 문법은 알지만, DDL 설계 경험 부족
- Entity만 작성하고 실제 DB 테이블 구조 확인 안 함
- DDL ↔ Entity 매핑 불일치로 런타임 에러 발생

**운영 DB 설계 프로세스**:
1. 개발자: CREATE TABLE DDL 작성
2. 개발자: Entity 매핑 구현
3. 개발자: DDL과 Entity 매핑 검증
4. DBA/운영팀: 리뷰 및 승인

---

## 2. 인덱스 설계 전략

### 핵심 원칙: 실시간 쿼리 성능 향상이 최우선

**로이코치님 조언**:
> "인덱스를 많이 만들면 성능 저하가 있다는 말은 조회 쿼리 성능 향상 관점이 빠진 이야기임. 실시간 서비스 쿼리 성능 향상이 가장 중요."

#### 인덱스 개수에 대한 오해

**잘못된 인식**:
- ❌ "인덱스가 많으면 INSERT/UPDATE 성능 저하"
- ❌ "인덱스는 최소한으로 유지해야 함"

**올바른 관점**:
- ✅ **실시간 쿼리 성능 향상에 기여한다면 인덱스 50개라도 괜찮음**
- ✅ **조회 성능 vs DML 부하의 트레이드오프를 데이터로 증명**
- ✅ **인덱스 비용 < 실시간 쿼리 성능 향상 가치 → 인덱스 추가**

**예시**:
```sql
-- 실시간 주문 조회 (초당 1000건)
SELECT * FROM orders WHERE user_id = ? AND status = ?;

-- 인덱스 없으면: Full Table Scan (100ms)
-- 인덱스 있으면: Index Seek (5ms)

-- 결론: DML 비용 증가(1-2ms) << 조회 성능 향상(95ms)
```

### 인덱스 종류별 선택 기준

#### 1. 단일 인덱스 vs 복합 인덱스

**선택 기준**:
- **카디널리티(데이터 중복도)**
- **실제 쿼리 패턴**
- **정렬 후 추가 필터링 비용**

**예시: 주문 조회**
```sql
-- 쿼리 패턴
SELECT * FROM orders
WHERE user_id = ? AND status = ?
ORDER BY created_at DESC;

-- 선택지 1: 단일 인덱스
CREATE INDEX idx_user_id ON orders(user_id);
-- 문제: user_id로 필터링 후 status 추가 필터링 비용 발생

-- 선택지 2: 복합 인덱스 (등치 조건 선행)
CREATE INDEX idx_user_status ON orders(user_id, status);
-- 장점: user_id와 status 둘 다 인덱스로 필터링

-- 선택지 3: 복합 인덱스 (정렬 포함)
CREATE INDEX idx_user_status_created ON orders(user_id, status, created_at);
-- 장점: 정렬까지 인덱스로 해결 (커버링 인덱스 가능)
```

**로이코치님 조언**:
> "등치 조건 컬럼을 복합 인덱스의 선행 컬럼으로 배치하는 것을 고려 중."

#### 2. 커버링 인덱스

**정의**: 쿼리에 필요한 모든 컬럼을 인덱스에 포함시켜 테이블 접근 없이 조회

**로이코치님 조언**:
> "커버링 인덱스 유무보다는 옵티마이저가 결정하는 방식대로 따르는 것이 중요함."

**활용 시나리오**:
```sql
-- 주문 목록 조회 (간단한 정보만)
SELECT order_id, user_id, status, created_at
FROM orders
WHERE user_id = ?;

-- 커버링 인덱스
CREATE INDEX idx_user_cover ON orders(user_id, order_id, status, created_at);

-- 장점: 테이블 접근 없이 인덱스만으로 조회 (성능 향상)
-- 단점: 인덱스 크기 증가, DML 비용 증가
```

**주의사항**:
- ⚠️ **성능 저하는 커버링 인덱스 문제가 아니라 데이터 탐색 비용이 큰 경우**
- ⚠️ **옵티마이저가 커버링 인덱스를 선택하지 않을 수도 있음**
- ✅ **실행 계획(EXPLAIN)으로 실제 사용 여부 확인 필수**

### 인덱스 설계 시 고려사항

#### 1. 카디널리티 (Cardinality)

**정의**: 데이터 중복도 (고유한 값의 비율)

**높은 카디널리티** (예: 주문 ID, 사용자 ID)
- ✅ 인덱스 효과 높음
- ✅ 선택도(Selectivity)가 높아 소수 행만 반환

**낮은 카디널리티** (예: 성별, 상태)
- ❌ 단독 인덱스 비효율적
- ✅ 복합 인덱스의 보조 컬럼으로 활용

**예시**:
```sql
-- 카디널리티 확인
SELECT
    COUNT(DISTINCT user_id) / COUNT(*) AS user_id_cardinality,  -- 0.95 (높음)
    COUNT(DISTINCT status) / COUNT(*) AS status_cardinality      -- 0.05 (낮음)
FROM orders;

-- 인덱스 전략
-- ✅ user_id는 단독 인덱스로도 효과적
CREATE INDEX idx_user_id ON orders(user_id);

-- ✅ status는 user_id와 복합 인덱스로 활용
CREATE INDEX idx_user_status ON orders(user_id, status);
```

#### 2. 실제 쿼리 패턴 분석

**로이코치님 조언**:
> "인덱스 설계 시 PK/UNIQUE만 고려하지 말고, 실제 데이터 분포와 접근 패턴 중심으로 판단해야 함."

**쿼리 패턴 분석 방법**:
1. 애플리케이션에서 자주 실행되는 쿼리 로그 수집
2. WHERE 절에 사용되는 컬럼 분석
3. ORDER BY, GROUP BY 절 분석
4. 쿼리 실행 빈도와 응답 시간 측정

**예시**:
```sql
-- 실제 서비스에서 자주 실행되는 쿼리
-- 1. 사용자별 최근 주문 조회 (초당 500건)
SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC LIMIT 10;

-- 2. 주문 상태별 조회 (초당 100건)
SELECT * FROM orders WHERE status = ?;

-- 3. 특정 상품 주문 조회 (초당 50건)
SELECT * FROM order_items WHERE product_id = ?;

-- 인덱스 우선순위
-- 1순위: idx_user_created (user_id, created_at DESC) - 가장 빈번한 쿼리
-- 2순위: idx_status (status) - 두 번째로 빈번
-- 3순위: idx_product (product_id) - 세 번째
```

#### 3. DML 부하 고려

**로이코치님 조언**:
> "택배사 선택은 조회 효율을 위한 장치지만, 인덱스가 많으면 DML 부하가 생길 수 있음."

**DML 부하 발생 시나리오**:
```sql
-- 인덱스가 10개인 테이블
-- INSERT 1건 = 테이블 1번 + 인덱스 10번 = 총 11번 쓰기

INSERT INTO orders (...) VALUES (...);

-- 영향받는 인덱스들
-- idx_user_id
-- idx_status
-- idx_user_status
-- idx_user_created
-- idx_status_created
-- idx_courier_status
-- ...
```

**트레이드오프 판단 기준**:
- ✅ **조회 빈도 > DML 빈도** → 인덱스 추가
- ❌ **조회 빈도 < DML 빈도** → 인덱스 최소화
- ⚖️ **실측 데이터로 판단** (추측 금지)

### 인덱스 설계 실전 예시

#### 주문 테이블 (orders)

**테이블 구조**:
```sql
CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,  -- PENDING, COMPLETED, CANCELLED
    courier_company VARCHAR(50),  -- 택배사
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
```

**쿼리 패턴 분석**:
1. 사용자별 주문 목록 (초당 500건)
2. 주문 상태별 조회 (초당 100건)
3. 택배사별 주문 조회 (초당 10건)

**인덱스 전략**:
```sql
-- 1. 사용자별 주문 목록 (가장 빈번)
CREATE INDEX idx_user_created ON orders(user_id, created_at DESC);

-- 2. 주문 상태별 조회
CREATE INDEX idx_status_created ON orders(status, created_at DESC);

-- 3. 택배사별 조회 (복합 조건)
CREATE INDEX idx_courier_status ON orders(courier_company, status);
-- 이유: 택배사별로 특정 상태 주문을 조회하는 경우가 많음

-- 4. 커버링 인덱스 (선택적)
CREATE INDEX idx_user_cover ON orders(
    user_id,
    order_id,
    status,
    created_at
);
-- 주의: 실행 계획으로 실제 사용 여부 확인 후 결정
```

**인덱스 정당성 검증**:
```sql
-- EXPLAIN으로 실행 계획 확인
EXPLAIN SELECT * FROM orders WHERE user_id = 123 ORDER BY created_at DESC;

-- 결과 확인
-- type: ref (인덱스 사용)
-- key: idx_user_created (올바른 인덱스 선택)
-- rows: 10 (소수 행만 스캔)
-- Extra: Using index (커버링 인덱스 적용 시)
```

---

## 3. 인메모리 vs JPA

### 핵심 메시지

**로이코치님 조언**:
> "인메모리 테스트 통과 ≠ 통합 테스트 성공. JPA로 전환 시 동일하게 동작하지 않을 수 있음."

### 인메모리와 JPA의 차이점

#### 1. 영속성 컨텍스트 부재

**인메모리 (ConcurrentHashMap)**:
```java
@Repository
public class InMemoryProductRepository implements ProductRepository {
    private final Map<Long, Product> storage = new ConcurrentHashMap<>();

    @Override
    public Product save(Product product) {
        storage.put(product.getId(), product);
        return product;  // 항상 새 객체 반환
    }
}
```

**JPA (영속성 컨텍스트)**:
```java
@Repository
public interface JpaProductRepository extends JpaRepository<Product, Long> {
    // Spring Data JPA가 자동 구현
}

// 사용 시
Product product = productRepository.findById(1L).get();
product.decreaseStock(5);  // 변경 감지 (Dirty Checking)
// save() 호출 없이도 UPDATE 실행!
```

**차이점**:
- ✅ **인메모리**: 명시적으로 `save()` 호출해야 변경 반영
- ✅ **JPA**: 영속성 컨텍스트가 변경 감지하여 자동 UPDATE

#### 2. 트랜잭션 경계

**인메모리 (트랜잭션 없음)**:
```java
@Service
public class OrderUseCase {
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 트랜잭션 없음
        Product product = productRepository.findById(productId).get();
        product.decreaseStock(quantity);
        productRepository.save(product);  // 즉시 반영

        Order order = new Order(...);
        orderRepository.save(order);  // 즉시 반영

        // 예외 발생 시 이전 변경사항 롤백 불가!
    }
}
```

**JPA (트랜잭션 필수)**:
```java
@Service
@Transactional
public class OrderUseCase {
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 트랜잭션 시작
        Product product = productRepository.findById(productId).get();
        product.decreaseStock(quantity);
        // save() 없이도 변경 감지

        Order order = new Order(...);
        orderRepository.save(order);

        // 예외 발생 시 모든 변경사항 롤백!
        // 트랜잭션 커밋 시점에 UPDATE 실행
    }
}
```

**차이점**:
- ✅ **인메모리**: 트랜잭션 개념 없음 (롤백 불가)
- ✅ **JPA**: @Transactional로 원자성 보장 (All or Nothing)

#### 3. 지연 로딩 (Lazy Loading)

**인메모리 (즉시 로딩만 가능)**:
```java
public class Order {
    private List<OrderItem> items;  // 항상 즉시 로딩

    public List<OrderItem> getItems() {
        return items;  // 메모리에 이미 로드됨
    }
}
```

**JPA (지연 로딩 가능)**:
```java
@Entity
public class Order {
    @OneToMany(fetch = FetchType.LAZY)
    private List<OrderItem> items;  // 지연 로딩

    public List<OrderItem> getItems() {
        return items;  // 이 시점에 SELECT 쿼리 실행!
    }
}

// 문제: 트랜잭션 밖에서 접근 시 LazyInitializationException 발생
```

**차이점**:
- ✅ **인메모리**: 모든 연관 데이터가 메모리에 존재
- ✅ **JPA**: 지연 로딩으로 인한 예외 발생 가능

### 인메모리 테스트의 한계

**로이코치님 조언**:
> "인메모리와 JPA 각각의 장단점을 인지해야 함. 불필요한 케이스는 삭제 권장."

#### 인메모리 테스트가 통과해도 JPA에서 실패하는 경우

**예시 1: 변경 감지 누락**
```java
// 인메모리 테스트 (통과)
@Test
void 재고_차감_테스트_인메모리() {
    Product product = new Product("노트북", 10);
    productRepository.save(product);

    product.decreaseStock(3);
    productRepository.save(product);  // 명시적 save

    Product result = productRepository.findById(1L).get();
    assertThat(result.getStock()).isEqualTo(7);  // 통과
}

// JPA 테스트 (실패 가능)
@Test
@Transactional
void 재고_차감_테스트_JPA() {
    Product product = new Product("노트북", 10);
    productRepository.save(product);

    product.decreaseStock(3);
    // save() 호출 누락 (JPA는 자동 변경 감지하지만 테스트는 실패할 수 있음)

    entityManager.flush();  // 강제 flush
    entityManager.clear();  // 영속성 컨텍스트 초기화

    Product result = productRepository.findById(1L).get();
    assertThat(result.getStock()).isEqualTo(7);  // 통과 (변경 감지 덕분)
}
```

**예시 2: 지연 로딩 예외**
```java
// 인메모리 테스트 (통과)
@Test
void 주문_상세_조회_인메모리() {
    Order order = orderRepository.findById(1L).get();
    List<OrderItem> items = order.getItems();  // 즉시 로딩
    assertThat(items).hasSize(3);  // 통과
}

// JPA 테스트 (실패)
@Test
void 주문_상세_조회_JPA() {
    Order order = orderRepository.findById(1L).get();
    // 트랜잭션 종료

    List<OrderItem> items = order.getItems();  // LazyInitializationException 발생!
    assertThat(items).hasSize(3);  // 실패
}

// 해결 방법
@Test
@Transactional  // 트랜잭션 유지
void 주문_상세_조회_JPA_수정() {
    Order order = orderRepository.findById(1L).get();
    List<OrderItem> items = order.getItems();  // 트랜잭션 안에서 접근
    assertThat(items).hasSize(3);  // 통과
}
```

### 인메모리와 JPA의 장단점

#### 인메모리 장점
- ✅ **빠른 테스트 실행**: DB 연결 없이 메모리에서 실행
- ✅ **단순한 구현**: 복잡한 ORM 설정 불필요
- ✅ **격리된 테스트**: 외부 의존성 없음

#### 인메모리 단점
- ❌ **실제 환경과 다름**: 트랜잭션, 영속성 컨텍스트 부재
- ❌ **JPA 특성 검증 불가**: 지연 로딩, 변경 감지 테스트 불가
- ❌ **통합 테스트 필요**: 인메모리 테스트 통과 ≠ 실제 동작 보장

#### JPA 장점
- ✅ **실제 환경과 동일**: 트랜잭션, 영속성 컨텍스트 활용
- ✅ **자동 변경 감지**: save() 호출 없이도 UPDATE
- ✅ **다양한 쿼리 최적화**: Fetch Join, Batch Size 등

#### JPA 단점
- ❌ **학습 곡선**: 영속성 컨텍스트, 지연 로딩 이해 필요
- ❌ **성능 이슈**: N+1 문제, 불필요한 쿼리 발생 가능
- ❌ **테스트 느림**: DB 연결 및 초기화 시간

### 실전 전략

**로이코치님 조언**:
> "불필요한 케이스는 삭제 권장."

#### Step 7-8 전환 전략

**Step 1: JPA 통합 테스트 작성**
```java
@SpringBootTest
@Transactional
class OrderIntegrationTest {

    @Autowired
    private OrderUseCase orderUseCase;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void 주문_생성_통합_테스트() {
        // Given: 실제 DB에 데이터 저장
        Product product = new Product("노트북", 10);
        productRepository.save(product);

        CreateOrderRequest request = new CreateOrderRequest(...);

        // When: 실제 UseCase 실행
        OrderResponse response = orderUseCase.createOrder(request);

        // Then: DB에서 조회하여 검증
        Product updatedProduct = productRepository.findById(product.getId()).get();
        assertThat(updatedProduct.getStock()).isEqualTo(7);
    }
}
```

**Step 2: 인메모리 테스트 정리**
- ✅ **유지**: 핵심 비즈니스 로직 단위 테스트 (Domain Layer)
- ❌ **삭제**: Repository 테스트, UseCase 테스트 중 JPA 통합 테스트로 대체 가능한 것

**예시**:
```java
// ✅ 유지: Domain Layer 단위 테스트
class ProductTest {
    @Test
    void 재고_차감_성공() {
        Product product = new Product("노트북", 10);
        product.decreaseStock(3);
        assertThat(product.getStock()).isEqualTo(7);
    }
}

// ❌ 삭제: InMemoryRepository 테스트 (JPA로 대체)
class InMemoryProductRepositoryTest {
    // JPA 통합 테스트로 대체
}

// ✅ 추가: JPA Repository 테스트
@DataJpaTest
class JpaProductRepositoryTest {
    @Autowired
    private JpaProductRepository productRepository;

    @Test
    void 상품_저장_조회() {
        Product product = new Product("노트북", 10);
        productRepository.save(product);

        Product result = productRepository.findById(product.getId()).get();
        assertThat(result.getName()).isEqualTo("노트북");
    }
}
```

**Step 3: 테스트 커버리지 유지**
- ✅ **목표**: 70% 이상 유지
- ✅ **전략**: 인메모리 테스트 삭제한 만큼 JPA 통합 테스트 추가
- ✅ **검증**: `./gradlew test jacocoTestReport`

---

## 4. 동시성 제어 테스트

### 핵심 메시지

**로이코치님 조언**:
> "동시성 제어 검증 시 단일 셀에 순차적 쿼리 수행은 의미 없음. 멀티스레드 + CountDownLatch 등으로 동시에 접근·수정 상황을 만들어야 함."

### 잘못된 동시성 테스트

**의미 없는 순차 테스트**:
```java
@Test
void 잘못된_동시성_테스트() {
    // 단일 스레드에서 순차 실행 (의미 없음!)
    for (int i = 0; i < 100; i++) {
        couponUseCase.issueCoupon("user" + i, "COUPON_10");
    }

    Coupon coupon = couponRepository.findById("COUPON_10").get();
    assertThat(coupon.getIssuedQuantity()).isEqualTo(100);
    // 이건 동시성 테스트가 아님! 단순 반복 실행
}
```

**문제점**:
- ❌ 단일 스레드에서 순차 실행
- ❌ Race Condition 발생 안 함
- ❌ 동시성 제어 검증 불가

### 올바른 동시성 테스트

**멀티스레드 + CountDownLatch**:
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
        String couponId = "COUPON_10";
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
                    latch.countDown();  // 작업 완료 신호
                }
            });
        }

        latch.await();  // 모든 스레드 작업 완료 대기
        executorService.shutdown();

        // Then: 정확히 100개만 발급되어야 함
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(100);

        Coupon result = couponRepository.findById(couponId).orElseThrow();
        assertThat(result.getIssuedQuantity()).isEqualTo(100);
    }
}
```

**핵심 요소**:
1. ✅ **ExecutorService**: 멀티스레드 실행
2. ✅ **CountDownLatch**: 모든 스레드가 동시에 시작하도록 조율
3. ✅ **AtomicInteger**: 성공/실패 카운트 (Thread-safe)
4. ✅ **200명 요청 → 100개 발급**: Race Condition 발생 시나리오

### CountDownLatch 역할

**동시 실행 보장**:
```java
CountDownLatch startLatch = new CountDownLatch(1);  // 시작 신호
CountDownLatch endLatch = new CountDownLatch(threadCount);  // 완료 대기

for (int i = 0; i < threadCount; i++) {
    executorService.submit(() -> {
        try {
            startLatch.await();  // 모든 스레드가 여기서 대기
            // 이 시점에 모든 스레드가 동시에 시작!

            couponUseCase.issueCoupon(userId, couponId);
        } finally {
            endLatch.countDown();
        }
    });
}

startLatch.countDown();  // 모든 스레드에게 시작 신호
endLatch.await();  // 모든 스레드 완료 대기
```

### 동시성 제어 방식별 검증

#### 1. synchronized

```java
@Service
public class CouponService {

    public synchronized UserCoupon issueCoupon(String userId, String couponId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }

        coupon.increaseIssuedQuantity();
        couponRepository.save(coupon);

        return userCouponRepository.save(new UserCoupon(...));
    }
}
```

**테스트 결과**:
- ✅ 200명 요청 → 정확히 100개 발급
- ✅ Race Condition 방지
- ⚠️ 단점: 메서드 전체 잠금 (성능 저하)

#### 2. AtomicInteger + CAS

```java
@Entity
public class Coupon {
    private AtomicInteger issuedQuantity;

    public boolean tryIssue() {
        while (true) {
            int current = issuedQuantity.get();

            if (current >= totalQuantity) {
                return false;
            }

            if (issuedQuantity.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }
}
```

**테스트 결과**:
- ✅ 200명 요청 → 정확히 100개 발급
- ✅ Lock-free (성능 우수)
- ⚠️ 단점: 복잡한 로직에는 부적합

#### 3. Pessimistic Lock (JPA)

```java
public interface CouponRepository extends JpaRepository<Coupon, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdWithLock(@Param("id") String id);
}
```

**테스트 결과**:
- ✅ 200명 요청 → 정확히 100개 발급
- ✅ DB 레벨 잠금 (안정적)
- ⚠️ 단점: DB 커넥션 유지 시간 증가

### 동시성 테스트 작성 가이드

**Step 1: 시나리오 정의**
```
Given: 쿠폰 100개 존재
When: 200명이 동시에 쿠폰 발급 요청
Then: 정확히 100명만 성공, 100명은 실패
```

**Step 2: 멀티스레드 환경 구성**
- ExecutorService로 스레드 풀 생성
- CountDownLatch로 동시 실행 보장
- AtomicInteger로 결과 집계

**Step 3: 검증**
- 성공 수 = 쿠폰 수량
- 실패 수 = 총 요청 - 쿠폰 수량
- DB 데이터 = 쿠폰 수량

**Step 4: 반복 테스트**
```java
@RepeatedTest(10)  // 10번 반복 실행
void 동시성_테스트_반복() {
    // 동시성 이슈는 간헐적으로 발생할 수 있음
    // 반복 테스트로 안정성 검증
}
```

---

## 5. JPA 활용 전략

### JPA의 핵심 가치

**로이코치님 조언**:
> "JPA의 핵심은 쿼리 효율보다 도메인 중심 비즈니스 로직 관리와 재사용성에 있음. DB 접근 비용보다 개발 생산성 향상에 초점."

### JPA vs Native Query

**JPA 장점**:
1. ✅ **도메인 중심 설계**: 비즈니스 로직을 Entity에 캡슐화
2. ✅ **생산성 향상**: CRUD 자동 생성, 보일러플레이트 코드 감소
3. ✅ **재사용성**: 공통 쿼리 메서드를 Repository 인터페이스로 추상화
4. ✅ **유지보수성**: SQL 변경 없이 Entity만 수정

**Native Query가 필요한 경우**:
1. ❌ **복잡한 통계 쿼리**: 집계 함수, 다중 조인
2. ❌ **성능 최적화**: 특정 인덱스 힌트 필요
3. ❌ **벌크 연산**: 대량 UPDATE/DELETE

**예시**:
```java
// JPA 쿼리 메서드 (간단한 조회)
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategory(String category);
    List<Product> findByNameContaining(String keyword);
}

// Native Query (복잡한 통계)
@Query(value = """
    SELECT p.product_id, p.name,
           COUNT(oi.order_item_id) as sales_count,
           SUM(oi.quantity * oi.price) as revenue
    FROM products p
    JOIN order_items oi ON p.product_id = oi.product_id
    JOIN orders o ON oi.order_id = o.order_id
    WHERE o.created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
    GROUP BY p.product_id
    ORDER BY sales_count DESC
    LIMIT 5
    """, nativeQuery = true)
List<PopularProductDto> findTop5PopularProducts();
```

### Projection 활용

**로이코치님 조언**:
> "전체 컬럼 조회 대신 Projection 혹은 Native Query를 활용해 일부 컬럼만 가져올 수 있음."

**전체 컬럼 조회 (비효율적)**:
```java
// 모든 컬럼 조회 (name만 필요한데 100개 컬럼 전부 조회)
List<Product> products = productRepository.findAll();
List<String> names = products.stream()
    .map(Product::getName)
    .collect(Collectors.toList());
```

**Projection 활용 (효율적)**:
```java
// 1. Interface-based Projection
public interface ProductNameOnly {
    String getName();
    Long getPrice();
}

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<ProductNameOnly> findAllProjectedBy();
}

// 사용
List<ProductNameOnly> products = productRepository.findAllProjectedBy();
// SELECT p.name, p.price FROM products p (필요한 컬럼만 조회)

// 2. Class-based Projection (DTO)
public class ProductSummaryDto {
    private String name;
    private Long price;

    public ProductSummaryDto(String name, Long price) {
        this.name = name;
        this.price = price;
    }
}

@Query("SELECT new com.example.dto.ProductSummaryDto(p.name, p.price) FROM Product p")
List<ProductSummaryDto> findAllSummaries();
```

**장점**:
- ✅ 네트워크 트래픽 감소
- ✅ 메모리 사용량 감소
- ✅ 쿼리 성능 향상

### 개발 생산성 vs 쿼리 효율

**트레이드오프**:
```
JPA:
  장점: 개발 속도 빠름, 유지보수 쉬움, 코드 가독성 높음
  단점: 쿼리 비효율적일 수 있음 (N+1 문제)

Native Query:
  장점: 최적화된 쿼리 작성 가능
  단점: 개발 속도 느림, 유지보수 어려움, 코드 가독성 낮음
```

**실전 전략**:
1. ✅ **기본은 JPA**: CRUD, 단순 조회는 JPA 활용
2. ✅ **성능 병목 발생 시 최적화**: Native Query로 전환
3. ✅ **데이터 측정 후 결정**: 추측 금지, 실측 데이터 기반 판단

**예시**:
```java
// 초기 구현 (JPA)
public List<Order> findUserOrders(Long userId) {
    return orderRepository.findByUserId(userId);
    // N+1 문제 발생 가능
}

// 성능 측정 결과: 1000ms (느림!)
// 원인: N+1 문제 (주문 100건 → 100번 쿼리)

// 최적화 (Fetch Join)
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.userId = :userId")
List<Order> findByUserIdWithItems(@Param("userId") Long userId);
// 성능: 50ms (20배 개선!)

// 최적화 (Native Query - 필요한 경우에만)
@Query(value = "SELECT * FROM orders WHERE user_id = :userId", nativeQuery = true)
List<Order> findByUserIdNative(@Param("userId") Long userId);
```

---

## 6. 캐시 전략

### 핵심 포인트

**로이코치님 조언**:
> "캐시의 TTL(만료 전략) 설계가 중요함. 캐시 사용 여부 및 만료 전략에 따라 서비스 완성도가 달라짐."

### 캐시 만료 전략 (TTL)

**TTL 설정의 중요성**:
```java
// 잘못된 예: TTL 없음 (무제한 캐시)
@Cacheable(value = "products", key = "#productId")
public Product getProduct(Long productId) {
    return productRepository.findById(productId).orElseThrow();
}
// 문제: 상품 정보 변경 시 캐시 갱신 안 됨 (데이터 불일치)

// 올바른 예: TTL 설정
@Cacheable(value = "products", key = "#productId")
@CacheEvict(value = "products", key = "#productId", condition = "#result.updatedAt != null")
public Product getProduct(Long productId) {
    return productRepository.findById(productId).orElseThrow();
}

// application.yml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=10m  # 10분 TTL
```

### 캐시 사용 여부 결정

**캐시가 필요한 경우**:
1. ✅ **읽기 빈도 >> 쓰기 빈도**: 상품 정보, 카테고리
2. ✅ **계산 비용 높음**: 인기 상품 집계, 통계 데이터
3. ✅ **동일한 데이터 반복 조회**: 사용자 프로필, 설정

**캐시가 불필요한 경우**:
1. ❌ **실시간 데이터**: 재고, 주문 상태
2. ❌ **쓰기 빈도 높음**: 조회수, 클릭 로그
3. ❌ **데이터 용량 큼**: 대용량 파일, 이미지

### 캐시 전략별 비교

#### 1. Look-Aside (Cache-Aside)

**흐름**:
```
1. 애플리케이션 → 캐시 조회
2. 캐시 Hit → 반환
3. 캐시 Miss → DB 조회 → 캐시 저장 → 반환
```

**구현**:
```java
@Service
public class ProductService {

    @Cacheable(value = "products", key = "#productId")
    public Product getProduct(Long productId) {
        // 캐시 Miss 시 DB 조회
        return productRepository.findById(productId).orElseThrow();
    }

    @CachePut(value = "products", key = "#product.id")
    public Product updateProduct(Product product) {
        // DB 업데이트 후 캐시 갱신
        return productRepository.save(product);
    }

    @CacheEvict(value = "products", key = "#productId")
    public void deleteProduct(Long productId) {
        // DB 삭제 후 캐시 삭제
        productRepository.deleteById(productId);
    }
}
```

**장점**:
- ✅ 구현 간단
- ✅ 캐시 실패 시에도 서비스 정상 동작

**단점**:
- ❌ 초기 조회 느림 (캐시 워밍업 필요)
- ❌ 캐시-DB 불일치 가능성

#### 2. Write-Through

**흐름**:
```
1. 애플리케이션 → 캐시 저장
2. 캐시 → DB 저장
3. 완료 응답
```

**장점**:
- ✅ 캐시-DB 일관성 보장
- ✅ 읽기 성능 우수

**단점**:
- ❌ 쓰기 성능 저하
- ❌ 불필요한 데이터도 캐시 저장

#### 3. Write-Behind (Write-Back)

**흐름**:
```
1. 애플리케이션 → 캐시 저장
2. 즉시 응답
3. 캐시 → 비동기로 DB 저장
```

**장점**:
- ✅ 쓰기 성능 우수
- ✅ DB 부하 감소

**단점**:
- ❌ 캐시 장애 시 데이터 손실
- ❌ 구현 복잡

### 실전 예시: 인기 상품 캐시

**요구사항**:
- 최근 3일 판매량 기준 Top 5
- 5분마다 배치로 집계
- 배치 실패 시 캐시 데이터 반환

**구현**:
```java
@Service
public class PopularProductService {

    private static final String CACHE_KEY = "popular_products";

    @Cacheable(value = "popularProducts", key = "#root.methodName")
    public List<PopularProductDto> getTop5Products() {
        // 캐시 Miss 시 DB 조회
        return calculateTop5Products();
    }

    @Scheduled(cron = "0 */5 * * * *")  // 5분마다 실행
    @CachePut(value = "popularProducts", key = "'getTop5Products'")
    public List<PopularProductDto> refreshTop5Products() {
        try {
            // 배치 집계
            List<PopularProductDto> result = calculateTop5Products();
            log.info("인기 상품 캐시 갱신 완료: {}", result.size());
            return result;
        } catch (Exception e) {
            log.error("인기 상품 집계 실패", e);
            // 캐시 유지 (기존 데이터 반환)
            return getTop5ProductsFromCache();
        }
    }

    private List<PopularProductDto> calculateTop5Products() {
        // 복잡한 집계 쿼리 실행
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        return orderItemRepository.findTop5ByCreatedAtAfter(threeDaysAgo);
    }

    private List<PopularProductDto> getTop5ProductsFromCache() {
        // 캐시에서 직접 조회
        Cache cache = cacheManager.getCache("popularProducts");
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get("getTop5Products");
            if (wrapper != null) {
                return (List<PopularProductDto>) wrapper.get();
            }
        }
        return Collections.emptyList();
    }
}
```

**TTL 설정**:
```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=100,expireAfterWrite=10m  # 10분 TTL
```

**Fallback 전략**:
```
1. 정상: 5분마다 배치 실행 → 캐시 갱신
2. 배치 실패: 캐시 유지 (기존 데이터 반환)
3. 캐시 만료: DB 조회 (느리지만 서비스 유지)
```

---

## 7. 액션 아이템

### 🎯 Week 4 완료 전 체크리스트

#### 1. 데이터베이스 설계
- [ ] DDL 작성 및 검증
  - [ ] CREATE TABLE 스크립트
  - [ ] 컬럼 타입, Nullable, 제약조건 확인
  - [ ] FK 관계 설정
- [ ] Entity 매핑 검증
  - [ ] @Entity, @Table, @Column 정확성
  - [ ] DDL과 Entity 일치 확인
  - [ ] 비즈니스 로직 메서드 유지

#### 2. 인덱스 설계
- [ ] 쿼리 패턴 분석
  - [ ] 자주 실행되는 쿼리 로그 수집
  - [ ] WHERE, ORDER BY, GROUP BY 절 분석
- [ ] 인덱스 전략 수립
  - [ ] 단일 vs 복합 인덱스 결정
  - [ ] 카디널리티 고려
  - [ ] DML 부하 측정
- [ ] 인덱스 생성 및 검증
  - [ ] CREATE INDEX 스크립트
  - [ ] EXPLAIN으로 실행 계획 확인
  - [ ] 성능 측정 (Before/After)

#### 3. JPA 전환
- [ ] Repository 변경
  - [ ] InMemory → JpaRepository
  - [ ] 커스텀 쿼리 메서드 작성
  - [ ] Projection 활용
- [ ] Transaction 적용
  - [ ] UseCase에 @Transactional
  - [ ] 읽기 전용 메서드 readOnly=true
- [ ] Fetch 전략 최적화
  - [ ] Fetch Join, @EntityGraph
  - [ ] Batch Size 설정
  - [ ] N+1 문제 해결

#### 4. 테스트
- [ ] JPA 통합 테스트 작성
  - [ ] @SpringBootTest, @Transactional
  - [ ] Repository 테스트 (@DataJpaTest)
- [ ] 동시성 테스트
  - [ ] ExecutorService + CountDownLatch
  - [ ] 200명 → 100개 발급 검증
  - [ ] @RepeatedTest(10) 반복 검증
- [ ] 테스트 커버리지 70% 유지
  - [ ] `./gradlew test jacocoTestReport`
  - [ ] 불필요한 인메모리 테스트 삭제

#### 5. 문서화
- [ ] 데이터 적재 및 쿼리 실행 계획 개선 문서
  - [ ] 초기 데이터 적재 방법
  - [ ] EXPLAIN 결과 분석
  - [ ] 인덱스 추가 전후 비교
- [ ] 인덱스 설계 근거 문서
  - [ ] 쿼리 패턴별 인덱스 선택 이유
  - [ ] 카디널리티 분석 결과
  - [ ] 성능 측정 데이터

### 📝 다음 주 준비사항

#### Week 5 예정 주제
1. **외부 API 연동**: DataPlatform, TossPay
2. **Async & Fallback**: 비동기 처리, Fallback 전략
3. **인기 상품 배치**: 스케줄러, 캐시 활용
4. **성능 최적화**: 캐시, 인덱스, 쿼리 튜닝

#### 사전 학습 추천
- [ ] Spring Async (@Async, @EnableAsync)
- [ ] Spring Scheduler (@Scheduled)
- [ ] Spring Cache (@Cacheable, @CacheEvict)
- [ ] Resilience4j (Circuit Breaker - Optional)

---

## 💡 핵심 인사이트

### 1. 데이터 중심 사고
> "상황에 따라 적절한 선택을 할 수 있는 능력을 키워야 함."

**액션**:
- 추측 금지, 실측 데이터 기반 판단
- 쿼리 실행 계획(EXPLAIN) 확인 습관화
- 성능 측정 후 최적화 결정

### 2. 실시간 서비스 우선
> "실시간 서비스 쿼리 성능 향상이 가장 중요."

**액션**:
- 조회 성능 vs DML 부하의 트레이드오프 측정
- 인덱스 개수보다 실시간 응답 속도 우선
- 사용자 경험(UX) 중심 성능 최적화

### 3. 테스트 완료 = 품질 보장
> "애플리케이션이 테스트를 통과하느냐 실패하느냐로 품질 판단 가능."

**액션**:
- 통합 테스트 작성 (인메모리 테스트만으로는 부족)
- 동시성 테스트 (멀티스레드 환경)
- 테스트 커버리지 70% 유지

### 4. 문서화의 중요성
> "데이터를 적재하고 쿼리 실행 계획을 개선시키는 과정을 문서로 작성하면 됨. 문서화 자체가 중요함."

**액션**:
- 설계 결정 과정 기록
- 성능 측정 데이터 문서화
- 트레이드오프 분석 결과 공유

---

## 📚 참고 자료

### MySQL 인덱스
- [MySQL 8.0 Reference - Optimization and Indexes](https://dev.mysql.com/doc/refman/8.0/en/optimization-indexes.html)
- [Real MySQL 8.0 (이성욱)](http://www.yes24.com/Product/Goods/103415627)

### JPA 성능 최적화
- [자바 ORM 표준 JPA 프로그래밍 (김영한)](http://www.yes24.com/Product/Goods/19040233)
- [Hibernate Performance Tuning](https://vladmihalcea.com/tutorials/hibernate/)

### 캐시 전략
- [Spring Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Caffeine Cache](https://github.com/ben-manes/caffeine)

### 동시성 제어
- [Java Concurrency in Practice (Brian Goetz)](http://www.yes24.com/Product/Goods/2455506)
- [Pessimistic Locking in JPA](https://vladmihalcea.com/jpa-pessimistic-locking/)

---

**작성일**: 2025.11.11
**작성자**: Claude Code (멘토링 내용 정리)
**버전**: 1.0
