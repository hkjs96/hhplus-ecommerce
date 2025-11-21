# 멘토링 핵심 요약 및 실전 팁 (Mentor Q&A Summary)

> **목적**: 항해플러스 5주차 멘토링에서 나온 핵심 질문과 답변을 정리하고, 실무에서 바로 적용할 수 있는 팁을 제공한다.

---

## 📌 율무 코치님 멘토링 (2025.11.17)

### Q1: MySQL과 PostgreSQL의 격리 수준 차이가 뭔가요?

**질문**: 같은 REPEATABLE READ 격리 수준인데 MySQL과 PostgreSQL이 다르게 동작한다고 들었는데, 어떻게 다른가요?

**답변 (율무 코치님)**:
> "DBMS마다 격리 수준 보장을 하기 위해서 내부적으로 동작하는 방식이 다릅니다. 격리 수준이 같아도 제약이 다를 수 있어요."

#### 고등학생도 이해하는 설명 🎓

**비유**: 같은 "조용히 하세요" 규칙이라도 도서관마다 다르게 적용되는 것과 같습니다.

**실제 차이점**:

```sql
-- 초기 상태: products 테이블에 id=1, stock=10인 상품 존재

-- MySQL (REPEATABLE READ)
-- Transaction A
BEGIN;
SELECT stock FROM products WHERE id = 1;  -- 10
UPDATE products SET stock = 5 WHERE id = 1;

-- Transaction B (동시 실행)
BEGIN;
UPDATE products SET stock = 8 WHERE id = 1;  -- ⏰ A가 끝날 때까지 대기
COMMIT;

-- Transaction A
COMMIT;  -- 이제 B가 실행됨

-- ✅ MySQL: 정상 동작 (에러 없음)
```

```sql
-- PostgreSQL (REPEATABLE READ)
-- Transaction A
BEGIN;
SELECT stock FROM products WHERE id = 1;  -- 10
UPDATE products SET stock = 5 WHERE id = 1;

-- Transaction B (동시 실행)
BEGIN;
UPDATE products SET stock = 8 WHERE id = 1;  -- ⏰ 대기

-- Transaction A
COMMIT;

-- Transaction B
-- ❌ PostgreSQL: 에러 발생!
-- ERROR: could not serialize access due to concurrent update
```

**왜 다를까요?**

| DBMS | REPEATABLE READ 구현 방식 | 동시 업데이트 동작 |
|------|------------------------|----------------|
| **MySQL** | MVCC (Undo Log) | 대기 후 실행 가능 |
| **PostgreSQL** | MVCC (Tuple Versioning) | 에러 발생, 재시도 필요 |

**실무 권장 사항**:
```java
// PostgreSQL에서는 재시도 로직 필수!
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void updateStockWithRetry(Long productId, int newStock) {
    int maxRetries = 3;
    for (int attempt = 0; attempt < maxRetries; attempt++) {
        try {
            Product product = productRepository.findById(productId).orElseThrow();
            product.setStock(newStock);
            return;  // 성공
        } catch (OptimisticLockException e) {
            if (attempt == maxRetries - 1) throw e;
            Thread.sleep(100 * (attempt + 1));
        }
    }
}
```

---

### Q2: READ COMMITTED로 격리 수준을 낮춰도 되는 경우가 있나요?

**질문**: REPEATABLE READ가 기본인데 READ COMMITTED로 낮추는 게 좋을 때가 있나요?

**답변 (율무 코치님)**:
> "READ COMMITTED로 내렸을 때 영향이 없는 트랜잭션이라면 내리는 게 좋습니다. REPEATABLE READ는 Undo Log를 오래 유지해야 하기 때문에 디스크 공간을 많이 차지합니다."

#### 고등학생도 이해하는 설명 🎓

**비유**:
- **REPEATABLE READ**: 시험 시작할 때 교과서 사진을 찍어두고, 시험 내내 그 사진만 봄 (사진 보관 공간 필요)
- **READ COMMITTED**: 시험 중에 교과서를 계속 볼 수 있지만, 누군가 교과서 내용을 바꿀 수 있음 (공간 절약)

**언제 READ COMMITTED로 낮출까?**

```java
// ✅ READ COMMITTED로 충분한 경우: 단순 조회
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public List<Product> getProducts() {
    // 상품 목록 조회
    // 조회 중에 다른 사람이 상품 가격을 바꿔도 괜찮음
    return productRepository.findAll();
}

// ✅ READ COMMITTED로 충분한 경우: 단일 작업
@Transactional(isolation = Isolation.READ_COMMITTED)
public void addReview(Long productId, String content) {
    // 리뷰 추가
    // 같은 리뷰를 두 번 읽을 일이 없음
    Review review = new Review(productId, content);
    reviewRepository.save(review);
}

// ❌ REPEATABLE READ가 필요한 경우: 통계 계산
@Transactional(isolation = Isolation.REPEATABLE_READ)
public OrderStatistics calculateDailyStatistics() {
    // 주문 통계 계산
    // 계산 중에 데이터가 바뀌면 안 됨!
    int totalOrders = orderRepository.countToday();
    int totalAmount = orderRepository.sumTodayAmount();
    return new OrderStatistics(totalOrders, totalAmount);
}
```

**Undo Log가 쌓이는 문제**:

```
REPEATABLE READ (오래 실행되는 트랜잭션)
↓
Undo Log 계속 쌓임 (스냅샷 유지)
↓
디스크 공간 부족
↓
성능 저하
```

**해결책**:
```yaml
# MySQL 설정 (my.cnf)
innodb_undo_log_truncate = ON
innodb_max_undo_log_size = 1G  # Undo Log 최대 크기

# 격리 수준을 낮춰서 근본적으로 해결
transaction-isolation = READ-COMMITTED
```

---

### Q3: 커버링 인덱스가 뭔가요?

**질문**: 커버링 인덱스를 만들라고 하는데, 정확히 뭔가요?

**답변 (율무 코치님)**:
> "커버링 인덱스는 인덱스만 보고 쿼리 결과를 얻을 수 있으면 커버링 인덱스가 됩니다. SELECT *을 안 하고 일부 컬럼만 조회할 때 고려해볼 수 있습니다."

#### 고등학생도 이해하는 설명 🎓

**비유**:
- **일반 인덱스**: 책의 목차 → 목차에서 페이지 번호 찾음 → 그 페이지로 가서 내용 읽음
- **커버링 인덱스**: 책의 요약본 → 요약본만 봐도 원하는 정보 전부 있음 (책 안 펼쳐봐도 됨)

**예시로 이해하기**:

```sql
-- 테이블 구조
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    product_id BIGINT,
    quantity INT,
    total_amount INT,
    status VARCHAR(20),
    created_at TIMESTAMP
);

-- 자주 실행하는 쿼리
SELECT user_id, total_amount, created_at
FROM orders
WHERE status = 'PAID'
  AND created_at >= '2025-11-01';
```

**❌ 커버링 인덱스 없는 경우**:
```sql
-- 인덱스: (status, created_at)
CREATE INDEX idx_status_created ON orders(status, created_at);

-- 쿼리 실행 과정:
-- 1. 인덱스 탐색: status='PAID' AND created_at >= '2025-11-01' 조건 찾음
-- 2. 인덱스에서 Primary Key (id) 확인
-- 3. 📖 실제 테이블로 가서 user_id, total_amount 읽음 (느림!)
```

**✅ 커버링 인덱스 있는 경우**:
```sql
-- 커버링 인덱스: 쿼리에 필요한 모든 컬럼 포함
CREATE INDEX idx_covering ON orders(status, created_at, user_id, total_amount);

-- 쿼리 실행 과정:
-- 1. 인덱스 탐색: status='PAID' AND created_at >= '2025-11-01' 조건 찾음
-- 2. 인덱스에 user_id, total_amount도 있음!
-- 3. ✅ 테이블 안 가고 인덱스만 읽고 끝! (빠름!)
```

**성능 비교**:

| 방식 | 디스크 I/O | 속도 | 메모리 사용 |
|------|-----------|------|------------|
| 일반 쿼리 | 많음 (테이블 접근) | 느림 | 많음 |
| 커버링 인덱스 | 적음 (인덱스만) | **빠름** | 적음 |

**실무 적용**:
```java
// ❌ 나쁜 예: SELECT * (커버링 인덱스 불가능)
@Query("SELECT o FROM Order o WHERE o.status = :status")
List<Order> findByStatus(@Param("status") String status);

// ✅ 좋은 예: 필요한 컬럼만 (커버링 인덱스 가능)
@Query("SELECT new com.example.dto.OrderSummary(o.userId, o.totalAmount, o.createdAt) " +
       "FROM Order o WHERE o.status = :status")
List<OrderSummary> findSummaryByStatus(@Param("status") String status);

// 인덱스
// CREATE INDEX idx_covering ON orders(status, user_id, total_amount, created_at);
```

---

### Q4: 인덱스 풀 스캔이 뭔가요?

**질문**: 인덱스를 만들었는데 느려요. 인덱스 풀 스캔 때문이라는데...

**답변 (율무 코치님)**:
> "인덱스를 활용하긴 하는데 인덱스 범위 안에 있는 컬럼들을 거의 다 스캔하고 있으면 성능이 더 안 나올 수 있습니다."

#### 고등학생도 이해하는 설명 🎓

**비유**:
- **인덱스 스캔 (좋음)**: 전화번호부에서 "김철수" 찾기 → "김"으로 시작하는 페이지만 봄
- **인덱스 풀 스캔 (나쁨)**: 전화번호부의 모든 페이지를 다 봄 (차라리 이름 순서로 정렬된 전체 명단 보는 게 나음)

**예시**:

```sql
-- 테이블: 100만 건
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    category VARCHAR(50),
    price INT,
    stock INT
);

-- 인덱스 생성
CREATE INDEX idx_category ON products(category);

-- ❌ 인덱스 풀 스캔 발생 (느림)
SELECT * FROM products
WHERE category LIKE '%전자%';  -- 중간 매칭: 인덱스 못 씀
-- → 100만 건 전부 확인

-- ❌ 인덱스 풀 스캔 발생 (느림)
SELECT * FROM products
WHERE category != 'laptop';  -- 부정 조건: 거의 모든 데이터
-- → 100만 건 중 95만 건 확인

-- ✅ 인덱스 범위 스캔 (빠름)
SELECT * FROM products
WHERE category = 'laptop';  -- 정확한 매칭
-- → 5만 건만 확인

-- ✅ 인덱스 범위 스캔 (빠름)
SELECT * FROM products
WHERE category LIKE 'laptop%';  -- 앞부분 매칭
-- → 5만 건만 확인
```

**EXPLAIN으로 확인하기**:

```sql
-- 실행 계획 확인
EXPLAIN SELECT * FROM products WHERE category LIKE '%전자%';

-- 결과
+----+-------------+----------+-------+------+---------+------+--------+-------------+
| id | select_type | table    | type  | key  | key_len | ref  | rows   | Extra       |
+----+-------------+----------+-------+------+---------+------+--------+-------------+
|  1 | SIMPLE      | products | index | idx  | 202     | NULL | 1000000| Using where |
+----+-------------+----------+-------+------+---------+------+--------+-------------+

-- type = 'index' → 인덱스 풀 스캔!
-- rows = 1000000 → 100만 건 전부 확인!
```

**해결 방법**:

```sql
-- 1. Full-Text Search 사용 (중간 매칭이 필요한 경우)
CREATE FULLTEXT INDEX idx_fulltext ON products(category);

SELECT * FROM products
WHERE MATCH(category) AGAINST('전자' IN BOOLEAN MODE);

-- 2. 조건 변경 (부정 → 긍정)
-- ❌
WHERE category != 'laptop'

-- ✅
WHERE category IN ('smartphone', 'tablet', 'desktop', ...)

-- 3. 복합 인덱스 활용
CREATE INDEX idx_category_price ON products(category, price);

SELECT * FROM products
WHERE category = 'laptop'
  AND price BETWEEN 1000000 AND 2000000;
```

---

### Q5: Primary Key가 왜 중요한가요?

**답변 (율무 코치님)**:
> "PK가 변경되면 PK를 바라보고 있는 모든 인덱스들이 전체적으로 업데이트가 일어나야 합니다. PK는 변경이 일어나면 안 될 것들 위주로 구성해야 합니다."

#### 고등학생도 이해하는 설명 🎓

**비유**:
- **Primary Key**: 학생의 학번 (절대 안 바뀜)
- **Secondary Index**: 학생 이름으로 찾는 명단 → 각 이름 옆에 학번이 적혀있음

만약 학번이 바뀌면?
→ 모든 명단의 학번을 다 바꿔야 함! (엄청 느림)

**실제 예시**:

```sql
-- ❌ 나쁜 PK 선택: 이메일 (변경 가능)
CREATE TABLE users (
    email VARCHAR(100) PRIMARY KEY,  -- 이메일은 바뀔 수 있음!
    name VARCHAR(50),
    created_at TIMESTAMP
);

-- 인덱스들 (자동으로 email을 참조함)
CREATE INDEX idx_name ON users(name);  -- (name, email)
CREATE INDEX idx_created ON users(created_at);  -- (created_at, email)

-- 이메일 변경 시
UPDATE users SET email = 'new@example.com'
WHERE email = 'old@example.com';
-- → 모든 인덱스 업데이트 필요! (매우 느림)

-- ✅ 좋은 PK 선택: ID (절대 안 바뀜)
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,  -- 절대 안 바뀜!
    email VARCHAR(100) UNIQUE,  -- 이메일은 유니크 제약만
    name VARCHAR(50),
    created_at TIMESTAMP
);

-- 이메일 변경 시
UPDATE users SET email = 'new@example.com'
WHERE id = 123;
-- → 하나의 컬럼만 변경! (빠름)
```

**왜 인덱스가 PK를 참조할까?**

```
Secondary Index 구조 (MySQL InnoDB):

CREATE INDEX idx_name ON users(name);

인덱스 트리:
       [김철수, PK=5]
      /              \
[강민수, PK=2]    [박영희, PK=7]

1. 인덱스에서 "김철수" 찾음
2. PK=5 확인
3. PK=5로 실제 테이블에서 데이터 읽음

만약 PK가 바뀌면?
→ 모든 인덱스의 PK 값을 업데이트해야 함!
```

**실무 권장 사항**:

```java
// ✅ 좋은 예: Auto-increment ID
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // 절대 안 바뀜

    @Column(unique = true)
    private String email;  // 바뀔 수 있음

    private String name;
}

// ❌ 나쁜 예: 비즈니스 키를 PK로 사용
@Entity
@Table(name = "users")
public class User {
    @Id
    private String email;  // 이메일 변경 시 문제!

    private String name;
}

// ❓ 복합 PK는 언제 쓸까?
@Entity
@IdClass(UserCouponId.class)
public class UserCoupon {
    @Id
    private Long userId;  // 복합 PK

    @Id
    private Long couponId;  // 복합 PK

    // userId, couponId 둘 다 절대 안 바뀜!
}
```

---

## 📌 제이 코치님 멘토링 (2025.11.18)

### Q6: 원자적 업데이트 vs 비관적 락, 언제 뭘 써야 하나요?

**질문**: 재고 차감할 때 원자적 업데이트로도 충분한데, 언제 비관적 락을 써야 하나요?

**답변 (제이 코치님)**:
> "비즈니스 복잡도에 따라 판단합니다. 단순 숫자 증감이면 원자적 업데이트만으로 충분하고, 중간에 복잡한 계산이나 검증이 필요하면 비관적 락을 써야 합니다."

#### 고등학생도 이해하는 설명 🎓

**비유**:
- **원자적 업데이트**: 자동판매기에서 돈 넣고 음료 나옴 (한 번에 처리)
- **비관적 락**: 은행 창구에서 계좌 조회 → 계산 → 송금 (여러 단계, 중간에 끼어들면 안 됨)

**언제 원자적 업데이트?**

```java
// ✅ 단순 증감: 원자적 업데이트
@Modifying
@Query("UPDATE Product p SET p.stock = p.stock - :quantity " +
       "WHERE p.id = :id AND p.stock >= :quantity")
int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);

@Service
public class StockService {
    public void decreaseStock(Long productId, int quantity) {
        int updated = productRepository.decreaseStock(productId, quantity);
        if (updated == 0) {
            throw new InsufficientStockException();
        }
    }
}

// 장점: 빠름, 간단함, Deadlock 없음
// 단점: 복잡한 로직 불가능
```

**언제 비관적 락?**

```java
// ✅ 복잡한 로직: 비관적 락
@Transactional
public void processOrder(OrderRequest request) {
    // 1. 재고 조회 및 락 획득
    Product product = em.createQuery(
        "SELECT p FROM Product p WHERE p.id = :id", Product.class)
        .setParameter("id", request.getProductId())
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getSingleResult();

    // 2. 복잡한 계산
    int baseQuantity = request.getQuantity();
    int bonusQuantity = calculateBonus(request.getUserGrade());  // 등급별 보너스
    int totalQuantity = baseQuantity + bonusQuantity;

    // 3. 재고 검증
    if (product.getStock() < totalQuantity) {
        throw new InsufficientStockException();
    }

    // 4. 할인 쿠폰 적용 여부 확인
    if (request.hasCoupon()) {
        Coupon coupon = couponRepository.findById(request.getCouponId());
        if (!coupon.isValidFor(product)) {
            throw new InvalidCouponException();
        }
    }

    // 5. 재고 차감
    product.decreaseStock(totalQuantity);

    // 6. 포인트 차감
    User user = userRepository.findById(request.getUserId());
    user.deductPoints(calculatePointsUsed(request));
}

// 장점: 복잡한 로직 가능, 데이터 정합성 100%
// 단점: 느림, Deadlock 위험
```

**선택 기준 요약**:

| 상황 | 추천 방식 | 이유 |
|------|----------|------|
| 단순 재고 차감 | 원자적 업데이트 | 빠르고 간단 |
| 쿠폰 적용 + 재고 차감 | 비관적 락 | 중간 검증 필요 |
| 포인트 + 할인 + 재고 | 비관적 락 | 여러 테이블 동시 접근 |
| 조회수 증가 | 원자적 업데이트 | 단순 증가 |

---

### Q7: 외부 API 호출이 포함된 트랜잭션은 어떻게 처리하나요?

**질문**: 결제 처리 중에 PG사 API를 호출해야 하는데, 트랜잭션 안에서 해도 되나요?

**답변 (제이 코치님)**:
> "외부 API 호출은 트랜잭션 밖으로 빼야 합니다. 레이턴시가 길어져서 커넥션 풀도 고갈되고, 메모리 버퍼풀 캐시가 증가하고, Undo Log가 쌓입니다."

#### 고등학생도 이해하는 설명 🎓

**비유**:
- **트랜잭션**: 은행 창구에서 업무 처리 (다른 사람들 대기 중)
- **외부 API**: 다른 은행에 전화해서 확인 (5분 걸림)

창구 앞에서 전화하면? → 뒤에 사람들 다 기다림 (비효율)
창구 밖에서 전화하면? → 다른 사람들 업무 처리 가능 (효율적)

**❌ 나쁜 예: 트랜잭션 안에서 외부 API 호출**

```java
@Transactional  // ❌ 문제!
public PaymentResult processPayment(PaymentRequest request) {
    // 1. 주문 조회 및 락 획득
    Order order = orderRepository.findByIdWithLock(request.getOrderId());

    // 2. 잔액 차감
    User user = userRepository.findByIdWithLock(request.getUserId());
    user.deductBalance(request.getAmount());

    // 3. 외부 PG API 호출 (5초 소요)
    // ⏰ 이 동안 DB 커넥션 점유!
    // ⏰ 이 동안 락 보유!
    // ⏰ 이 동안 다른 트랜잭션 대기!
    PGResponse pgResponse = pgService.charge(request);

    if (pgResponse.isSuccess()) {
        order.markAsPaid();
    } else {
        throw new PaymentFailedException();  // 롤백
    }

    return PaymentResult.success();
}

// 문제점:
// 1. 커넥션 풀 고갈 (초당 20건 주문 → 10개 커넥션이면 절반은 대기)
// 2. 락 보유 시간 증가 (5초 동안 다른 사람 대기)
// 3. 메모리 증가 (Undo Log, Buffer Pool)
```

**✅ 좋은 예: 트랜잭션 분리**

```java
@Service
public class PaymentService {

    // 1. 트랜잭션: 잔액 차감만
    @Transactional
    public Payment reservePayment(PaymentRequest request) {
        User user = userRepository.findByIdWithLock(request.getUserId());
        user.deductBalance(request.getAmount());

        Order order = orderRepository.findById(request.getOrderId());
        order.markAsPending();  // 결제 대기 상태

        Payment payment = Payment.create(request, PaymentStatus.PENDING);
        return paymentRepository.save(payment);
    }

    // 2. 트랜잭션 밖: 외부 API 호출
    public PaymentResult processPayment(PaymentRequest request) {
        // 잔액 차감 (트랜잭션)
        Payment payment = reservePayment(request);

        try {
            // 외부 API 호출 (트랜잭션 밖)
            PGResponse pgResponse = pgService.charge(request);

            if (pgResponse.isSuccess()) {
                // 3. 트랜잭션: 상태 업데이트만
                updatePaymentSuccess(payment.getId(), pgResponse.getTransactionId());
                return PaymentResult.success();
            } else {
                // 4. 보상 트랜잭션: 잔액 복구
                compensatePayment(payment.getId());
                return PaymentResult.failure("PG 승인 실패");
            }
        } catch (Exception e) {
            // 5. 보상 트랜잭션: 잔액 복구
            compensatePayment(payment.getId());
            throw new PaymentProcessingException(e);
        }
    }

    @Transactional
    protected void updatePaymentSuccess(Long paymentId, String txId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.markAsSuccess(txId);

        Order order = orderRepository.findById(payment.getOrderId()).orElseThrow();
        order.markAsPaid();
    }

    @Transactional
    protected void compensatePayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.markAsFailed();

        User user = userRepository.findById(payment.getUserId()).orElseThrow();
        user.restoreBalance(payment.getAmount());  // 잔액 복구

        Order order = orderRepository.findById(payment.getOrderId()).orElseThrow();
        order.markAsFailed();
    }
}
```

**보상 트랜잭션이 필요한 이유**:

```
정상 흐름:
잔액 차감 → PG 승인 → 주문 완료

실패 시나리오 1: PG 승인 실패
잔액 차감 (✅ 완료) → PG 승인 (❌ 실패)
→ 보상: 잔액 복구 필요!

실패 시나리오 2: 네트워크 타임아웃
잔액 차감 (✅ 완료) → PG 승인 (⏰ 타임아웃)
→ 보상: 잔액 복구 필요!
```

**성능 비교**:

| 방식 | 커넥션 보유 시간 | 동시 처리 가능 |
|------|----------------|-------------|
| 트랜잭션 안 | 5초 (API 포함) | 초당 2건 (10개 커넥션) |
| 트랜잭션 밖 | 50ms (DB만) | 초당 200건 (10개 커넥션) |

---

### Q8: 분산 락과 Idempotency Key의 차이가 뭔가요?

**답변 (제이 코치님)**:
> "분산락은 시간 단위가 짧아서 밀리초 단위 동시 요청을 막는 거고, Idempotency는 시간 단위가 길어서 한 번 처리된 요청을 몇 분, 몇 시간 기억해 줍니다."

#### 고등학생도 이해하는 설명 🎓

**비유**:
- **분산 락**: 화장실 자물쇠 (한 번에 한 사람만, 나가면 바로 풀림)
- **Idempotency Key**: 입장 티켓 (한 번 들어갔으면 다시 못 들어옴, 기록 남음)

**분산 락 예시**:

```java
// ✅ 분산 락: 밀리초 단위 동시 요청 방지
public void issueCoupon(Long couponId, Long userId) {
    String lockKey = "lock:coupon:" + couponId;
    RLock lock = redissonClient.getLock(lockKey);

    try {
        // 100ms 대기, 3초 후 자동 해제
        if (lock.tryLock(100, 3000, TimeUnit.MILLISECONDS)) {
            // Critical Section (100ms 소요)
            Coupon coupon = couponRepository.findById(couponId);
            coupon.increaseIssued();
            userCouponRepository.save(new UserCoupon(userId, couponId));
        }
    } finally {
        lock.unlock();  // 락 즉시 해제
    }
}

// 시나리오:
// 10:00:00.000 - User1 락 획득
// 10:00:00.001 - User2 대기 (락 없음)
// 10:00:00.100 - User1 완료, 락 해제
// 10:00:00.101 - User2 락 획득
```

**Idempotency Key 예시**:

```java
// ✅ Idempotency Key: 시간, 분 단위 중복 방지
@Transactional
public PaymentResult processPayment(String idempotencyKey, PaymentRequest request) {
    // 이미 처리된 요청인지 확인 (24시간 보관)
    Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
        log.info("Duplicate request: {}", idempotencyKey);
        return PaymentResult.from(existing.get());
    }

    // 결제 처리
    Payment payment = Payment.create(idempotencyKey, request);
    paymentRepository.save(payment);

    return PaymentResult.success();
}

// 시나리오:
// 10:00:00 - 결제 요청 (idempotencyKey="payment-123")
// 10:00:00 - DB에 저장
// 10:00:05 - 같은 요청 재시도 (네트워크 오류로)
// 10:00:05 - 이미 존재 → 기존 결과 반환
// 11:00:00 - 1시간 후에도 중복 방지
```

**비교표**:

| 특징 | 분산 락 | Idempotency Key |
|------|---------|----------------|
| **목적** | 동시 실행 방지 | 중복 실행 방지 |
| **시간** | 밀리초~초 | 분~시간~일 |
| **저장소** | Redis (메모리) | DB (영구) |
| **자동 해제** | 타임아웃 | 수동 삭제 (또는 TTL) |
| **사용 케이스** | 쿠폰 발급, 재고 차감 | 결제, 주문 생성 |

---

### Q9: 분산 환경에서 스케줄러는 어떻게 처리하나요?

**답변 (제이 코치님)**:
> "여러 서버가 동시에 스케줄러를 실행하면 중복 집계가 발생하니까 ShedLock 같은 라이브러리로 한 서버만 실행되도록 보장해야 합니다."

#### 고등학생도 이해하는 설명 🎓

**비유**: 3개 반에서 동시에 청소 당번을 정하는데, 같은 사람이 3번 뽑히면 안 됨

**❌ 문제 상황**:

```java
// 3대의 서버가 모두 실행
@Scheduled(cron = "0 0 0 * * *")  // 매일 자정
public void aggregateDailySales() {
    // 일일 매출 집계
    List<Order> todayOrders = orderRepository.findToday();
    int totalSales = todayOrders.stream()
        .mapToInt(Order::getAmount)
        .sum();

    // DB에 저장
    salesRepository.save(new DailySales(LocalDate.now(), totalSales));
}

// 결과:
// Server 1: DailySales(2025-11-18, 1000만원) 저장
// Server 2: DailySales(2025-11-18, 1000만원) 저장  // 중복!
// Server 3: DailySales(2025-11-18, 1000만원) 저장  // 중복!
```

**✅ 해결: ShedLock 사용**

```java
// 1. 의존성 추가
dependencies {
    implementation 'net.javacrumbs.shedlock:shedlock-spring:5.9.0'
    implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.9.0'
}

// 2. 설정
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}

// 3. DB 테이블 생성
CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

// 4. 스케줄러에 적용
@Scheduled(cron = "0 0 0 * * *")
@SchedulerLock(
    name = "dailySalesAggregation",
    lockAtMostFor = "9m",  // 최대 9분 동안 락 유지
    lockAtLeastFor = "1m"  // 최소 1분 동안 락 유지
)
public void aggregateDailySales() {
    // 일일 매출 집계
    List<Order> todayOrders = orderRepository.findToday();
    int totalSales = todayOrders.stream()
        .mapToInt(Order::getAmount)
        .sum();

    salesRepository.save(new DailySales(LocalDate.now(), totalSales));
}

// 결과:
// 00:00:00 - Server 1이 락 획득, 집계 시작
// 00:00:00 - Server 2, 3은 락 획득 실패 → 종료
// 00:00:05 - Server 1 집계 완료
// 00:01:00 - 1분 후 락 자동 해제
```

**동작 원리**:

```sql
-- 00:00:00 Server 1 실행
INSERT INTO shedlock (name, lock_until, locked_at, locked_by)
VALUES ('dailySalesAggregation', '2025-11-18 00:09:00', '2025-11-18 00:00:00', 'Server1')
ON DUPLICATE KEY UPDATE ...;  -- 성공!

-- 00:00:00 Server 2 실행
INSERT INTO shedlock ...;  -- 실패! (name이 PRIMARY KEY라 중복)

-- 00:00:00 Server 3 실행
INSERT INTO shedlock ...;  -- 실패!
```

---

## 📚 실무 팁 정리

### Tip 1: 테스트 컨테이너 활용하기

**율무 코치님**: "테스트 컨테이너 구성을 잘 해보시는 게 좋습니다. 초기 구성 비용이 있지만, 현업에서도 바로 적용할 수 있습니다."

```java
// Testcontainers 설정
@SpringBootTest
@Testcontainers
public class ConcurrencyTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("test_db")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Test
    void concurrencyTest() throws InterruptedException {
        // 동시성 테스트 (실제 MySQL 사용)
    }
}
```

### Tip 2: JPA 간접 참조 패턴

**율무 코치님**: "직접 참조 대신 ID만 갖고 있는 간접 참조 방식도 고려해보세요."

```java
// ❌ 직접 참조: Lazy Loading 문제
@Entity
public class Order {
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;  // user.getName() 호출 시 쿼리 발생!
}

// ✅ 간접 참조: 명시적 제어
@Entity
public class Order {
    private Long userId;  // ID만 저장

    // 필요할 때만 조회
    public User getUser() {
        return userRepository.findById(userId).orElseThrow();
    }
}
```

### Tip 3: 모니터링이 핵심

**제이 코치님**: "실무에서는 모니터링을 굉장히 열심히 합니다. 테스트 시나리오는 상황에 따라 확인하면서 정답을 찾습니다."

**권장 모니터링 지표**:
```
1. 애플리케이션
   - TPS (Transactions Per Second)
   - 응답 시간 (P50, P95, P99)
   - 에러율

2. 데이터베이스
   - 커넥션 풀 사용률
   - Slow Query
   - Lock Wait Time
   - Deadlock 발생 횟수

3. 인프라
   - CPU 사용률
   - 메모리 사용률
   - 디스크 I/O
```

---

**작성일**: 2025-11-19
**버전**: 1.0
**출처**: 항해플러스 5주차 멘토링 (율무 코치님, 제이 코치님)
