# Week 4 Documentation - Database Integration

이 디렉토리는 **Week 4 (Step 7-8)** 과제의 JPA 기반 데이터베이스 통합 구현 관련 문서를 보관합니다.

## 📌 Week 4 핵심 목표

**JPA 기반 데이터베이스 통합 및 트랜잭션 관리**

- ✅ JPA Entity 구현 (Week 3 도메인 모델 전환)
- ✅ Spring Data JPA Repository 활용
- ✅ @Transactional 적용
- ✅ MySQL 연동
- ✅ N+1 문제 해결 (Fetch Join, EntityGraph)
- ✅ 쿼리 최적화

## 📁 디렉토리 구조

```
week4/
├── README.md (이 파일)
├── verification/
│   ├── README.md
│   ├── N1_VERIFICATION_RESULT.md
│   ├── N1_TEST_GUIDE.md
│   ├── N1_FETCH_JOIN_GUIDE.md
│   ├── EXPLAIN_ANALYZE_GUIDE.md
│   ├── QUERY_OPTIMIZATION_SUMMARY.md
│   ├── STOCK_DECREASE_VERIFICATION.md
│   ├── TOP_PRODUCTS_QUERY_VERIFICATION.md
│   ├── YULMU_FEEDBACK_STATUS.md
│   └── YULMU_FEEDBACK_IMPROVEMENTS.md
└── (향후 추가 문서)
```

## 🎯 주요 구현 내용

### 1. JPA Entity 변환

**Week 3의 비즈니스 로직을 유지하면서 JPA Entity로 전환**

```java
// Week 3: 순수 Java 클래스
public class Product {
    private String id;
    private String name;
    private Integer stock;

    public void decreaseStock(int quantity) { /* 비즈니스 로직 */ }
}

// Week 4: JPA Entity (비즈니스 로직 유지!)
@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer stock;

    public void decreaseStock(int quantity) { /* 비즈니스 로직 유지 */ }
}
```

**핵심 원칙:**
- ❌ Anemic Domain Model 방지: Entity를 단순 데이터 객체로 만들지 않음
- ✅ Rich Domain Model 유지: 비즈니스 로직 메서드 그대로 유지

### 2. Spring Data JPA Repository

**InMemory Repository → JpaRepository 전환**

```java
// Week 3: InMemory Repository
@Repository
public class InMemoryProductRepository implements ProductRepository {
    private final Map<String, Product> storage = new ConcurrentHashMap<>();
    // 직접 구현...
}

// Week 4: JpaRepository 상속
@Repository
public interface JpaProductRepository extends JpaRepository<Product, Long>, ProductRepository {
    List<Product> findByCategory(String category);  // 메서드 네이밍 쿼리

    @Query("SELECT p FROM Product p WHERE p.stock > :minStock")
    List<Product> findAvailableProducts(@Param("minStock") int minStock);
}
```

### 3. Transaction Management

**UseCase(Application Layer)에 @Transactional 적용**

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 기본 readOnly
public class OrderUseCase {

    @Transactional  // 쓰기 작업은 readOnly=false
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 트랜잭션 내에서 Entity 변경 시 자동 UPDATE (Dirty Checking)
    }
}
```

### 4. N+1 문제 해결

**Fetch Join, @EntityGraph, Batch Size 활용**

```java
// Fetch Join 예시
@Query("""
    SELECT DISTINCT o FROM Order o
    LEFT JOIN FETCH o.orderItems oi
    LEFT JOIN FETCH oi.product p
    WHERE o.userId = :userId
    ORDER BY o.createdAt DESC
""")
List<Order> findByUserIdWithItems(@Param("userId") Long userId);
```

**자세한 내용:** [`verification/N1_FETCH_JOIN_GUIDE.md`](./verification/N1_FETCH_JOIN_GUIDE.md)

### 5. 쿼리 최적화

**EXPLAIN ANALYZE를 통한 성능 분석**

- Execution Time 측정
- Index 사용 여부 확인
- 불필요한 Full Table Scan 제거

**자세한 내용:** [`verification/EXPLAIN_ANALYZE_GUIDE.md`](./verification/EXPLAIN_ANALYZE_GUIDE.md)

## 📊 Week 3 → Week 4 변경 사항

| 항목 | Week 3 | Week 4 |
|------|--------|--------|
| **도메인 모델** | 순수 Java 클래스 | JPA Entity |
| **Repository** | InMemory (ConcurrentHashMap) | Spring Data JPA |
| **ID 타입** | String (UUID) | Long (Auto Increment) |
| **관계 매핑** | Long ID 참조 | @OneToMany, @ManyToOne |
| **트랜잭션** | 수동 관리 | @Transactional |
| **데이터 저장** | 메모리 (휘발성) | MySQL (영구 저장) |
| **동시성 제어** | synchronized, CAS | 낙관적 락(@Version), 비관적 락 |

## 🔍 검증 문서 (verification/)

Week 4 구현의 정확성을 검증하기 위한 문서들:

### N+1 문제 검증
- [`N1_VERIFICATION_RESULT.md`](./verification/N1_VERIFICATION_RESULT.md) - N+1 문제 발생 여부 최종 검증 결과
- [`N1_TEST_GUIDE.md`](./verification/N1_TEST_GUIDE.md) - N+1 문제 테스트 작성 가이드
- [`N1_FETCH_JOIN_GUIDE.md`](./verification/N1_FETCH_JOIN_GUIDE.md) - Fetch Join 해결 방법

### 쿼리 최적화
- [`EXPLAIN_ANALYZE_GUIDE.md`](./verification/EXPLAIN_ANALYZE_GUIDE.md) - MySQL EXPLAIN ANALYZE 사용법
- [`QUERY_OPTIMIZATION_SUMMARY.md`](./verification/QUERY_OPTIMIZATION_SUMMARY.md) - 쿼리 최적화 종합 가이드

### 비즈니스 로직 검증
- [`STOCK_DECREASE_VERIFICATION.md`](./verification/STOCK_DECREASE_VERIFICATION.md) - 재고 차감 플로우 검증
- [`TOP_PRODUCTS_QUERY_VERIFICATION.md`](./verification/TOP_PRODUCTS_QUERY_VERIFICATION.md) - 인기 상품 쿼리 검증

### 코치 피드백 반영
- [`YULMU_FEEDBACK_STATUS.md`](./verification/YULMU_FEEDBACK_STATUS.md) - Yulmu 코치 피드백 진행 상황
- [`YULMU_FEEDBACK_IMPROVEMENTS.md`](./verification/YULMU_FEEDBACK_IMPROVEMENTS.md) - 피드백 기반 개선 사항

## ✅ Week 4 Pass 조건

- [x] JPA Entity 변환 (비즈니스 로직 유지)
- [x] Spring Data JPA Repository 활용
- [x] @Transactional 적절히 적용
- [x] InMemory Repository 제거
- [x] 테스트 커버리지 70% 이상 유지
- [x] N+1 문제 해결
- [x] 쿼리 최적화

## ❌ Week 4 Fail 사유

- ❌ InMemory 유지 (JPA 미사용)
- ❌ Entity에서 비즈니스 로직 제거 (Anemic Domain Model)
- ❌ @Transactional 부재 또는 잘못된 위치 적용 (Controller, Entity에 적용)
- ❌ N+1 문제 미해결

## 🔗 관련 문서

### 현재 구현 가이드
- `/.claude/commands/architecture.md` - Layered Architecture 설명
- `/.claude/commands/testing.md` - 테스트 전략

### 이전 구현 (Week 3)
- `/docs/archive/week3/` - InMemory 구현 아카이브

### Week 2 설계 문서
- `/docs/week2/` - ERD, Sequence Diagrams, API Specification

## 📚 학습 체크리스트

### JPA 기본
- [ ] Entity, @Table, @Column 어노테이션 이해
- [ ] @Id, @GeneratedValue 전략 이해
- [ ] @OneToMany, @ManyToOne 관계 매핑
- [ ] FetchType.LAZY vs EAGER

### Spring Data JPA
- [ ] JpaRepository 메서드 네이밍 쿼리
- [ ] @Query 어노테이션 (JPQL)
- [ ] @Param 사용법
- [ ] findById vs findByIdOrThrow 패턴

### 트랜잭션 관리
- [ ] @Transactional 위치 (Application Layer)
- [ ] readOnly=true 최적화
- [ ] Dirty Checking (변경 감지)
- [ ] 트랜잭션 전파(Propagation)

### 성능 최적화
- [ ] N+1 문제 원인 및 해결
- [ ] Fetch Join vs @EntityGraph
- [ ] EXPLAIN ANALYZE 읽는 법
- [ ] Index 설계

## 💡 실전 팁

### 1. Entity 설계 시 주의사항

**✅ 좋은 예:**
```java
@Entity
public class Product {
    @Id @GeneratedValue
    private Long id;

    private Integer stock;

    // 비즈니스 로직 유지!
    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
        this.stock -= quantity;
    }
}
```

**❌ 나쁜 예:**
```java
@Entity
public class Product {
    @Id @GeneratedValue
    private Long id;

    private Integer stock;

    // Getter/Setter만 있고 비즈니스 로직 없음 (Anemic Model)
}
```

### 2. N+1 문제 확인 방법

**application.yml 설정:**
```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

**로그 확인:**
```sql
-- 1개의 주문 조회
SELECT * FROM orders WHERE id = 1;

-- N개의 주문 아이템 조회 (N+1 발생!)
SELECT * FROM order_items WHERE order_id = 1;
SELECT * FROM order_items WHERE order_id = 2;
SELECT * FROM order_items WHERE order_id = 3;
...
```

### 3. Fetch Join 주의사항

**MultipleBagFetchException 방지:**
```java
// ❌ 나쁜 예: 2개 이상의 Collection Fetch Join
@Query("""
    SELECT o FROM Order o
    LEFT JOIN FETCH o.orderItems
    LEFT JOIN FETCH o.coupons
""")
List<Order> findAllWithItemsAndCoupons();  // 에러 발생!

// ✅ 좋은 예: 하나씩 Fetch Join 또는 @EntityGraph 사용
@Query("""
    SELECT DISTINCT o FROM Order o
    LEFT JOIN FETCH o.orderItems
""")
List<Order> findAllWithItems();
```

---

**보관 날짜**: 2025-11-18
**현재 Phase**: Week 4 - Database Integration 완료
**다음 Phase**: Week 5 - 외부 API 연동, Async/Fallback
