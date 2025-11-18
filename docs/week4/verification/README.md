# Week 4 Verification Documentation

이 디렉토리는 Week 4 JPA 구현의 **정확성과 성능을 검증**하기 위한 문서들을 보관합니다.

## 📋 문서 목록

### N+1 문제 해결

| 문서 | 설명 | 주요 내용 |
|------|------|-----------|
| [`N1_PROBLEM_SOLUTION.md`](./N1_PROBLEM_SOLUTION.md) | N+1 문제 완전 가이드 | 문제 이해, Fetch Join 구현, 검증 방법, 성능 비교 |

### 쿼리 최적화

| 문서 | 설명 | 주요 내용 |
|------|------|-----------|
| [`EXPLAIN_ANALYZE_GUIDE.md`](./EXPLAIN_ANALYZE_GUIDE.md) | EXPLAIN ANALYZE 사용 가이드 | MySQL 쿼리 실행 계획 분석 방법 |
| [`QUERY_OPTIMIZATION_SUMMARY.md`](./QUERY_OPTIMIZATION_SUMMARY.md) | 쿼리 최적화 종합 가이드 | Index, Rollup 전략, 쿼리 개선 기법 |

### 비즈니스 로직 검증

| 문서 | 설명 | 주요 내용 |
|------|------|-----------|
| [`STOCK_DECREASE_VERIFICATION.md`](./STOCK_DECREASE_VERIFICATION.md) | 재고 차감 플로우 검증 | 주문 생성 vs 결제 시점 재고 차감 |
| [`TOP_PRODUCTS_QUERY_VERIFICATION.md`](./TOP_PRODUCTS_QUERY_VERIFICATION.md) | 인기 상품 쿼리 검증 | ProductSalesAggregate 롤업 전략 |

> **코치 피드백 문서**: Yulmu 코치 피드백은 [`docs/feedback/yulmu-coach-improvements.md`](../../feedback/yulmu-coach-improvements.md)에서 확인하세요.

## 🎯 검증 목적

### 1. N+1 문제 검증

**Why?**
- JPA의 Lazy Loading은 N+1 문제를 쉽게 발생시킴
- 프로덕션에서 성능 저하의 주요 원인
- 반드시 모든 API에서 확인 필요

**How?**
1. `show-sql: true` 설정으로 실제 실행 쿼리 확인
2. 각 API 호출 시 쿼리 개수 카운트
3. N+1 발생 시 Fetch Join 또는 @EntityGraph로 해결

**Where?**
- [`N1_PROBLEM_SOLUTION.md`](./N1_PROBLEM_SOLUTION.md) - 완전 가이드 (문제, 해결, 검증)

### 2. 쿼리 성능 최적화

**Why?**
- 느린 쿼리는 전체 시스템 성능에 영향
- Index 미사용 시 Full Table Scan 발생
- 실행 계획 분석으로 병목 지점 파악

**How?**
1. `EXPLAIN ANALYZE` 로 실제 실행 시간 측정
2. Index 사용 여부 확인 (`key` 컬럼)
3. 불필요한 Filesort, Temporary Table 제거

**Where?**
- [`EXPLAIN_ANALYZE_GUIDE.md`](./EXPLAIN_ANALYZE_GUIDE.md) - 실행 계획 읽는 법
- [`QUERY_OPTIMIZATION_SUMMARY.md`](./QUERY_OPTIMIZATION_SUMMARY.md) - 최적화 기법

### 3. 비즈니스 로직 정확성

**Why?**
- 재고 차감 시점이 잘못되면 데이터 불일치 발생
- 주문 생성 vs 결제 시점에 따라 플로우 달라짐
- 롤백 시나리오 고려 필요

**How?**
1. 테스트로 결제 실패 시 재고 롤백 확인
2. 동시성 테스트로 재고 차감 정확성 검증
3. 실제 API 호출로 플로우 확인

**Where?**
- [`STOCK_DECREASE_VERIFICATION.md`](./STOCK_DECREASE_VERIFICATION.md) - 재고 차감 검증
- [`TOP_PRODUCTS_QUERY_VERIFICATION.md`](./TOP_PRODUCTS_QUERY_VERIFICATION.md) - 집계 쿼리 검증

### 4. 코치 피드백 반영

**Why?**
- 코드 품질 개선
- 실무 패턴 학습
- Pass/Fail 기준 충족

**How?**
1. 피드백 항목별 체크리스트 작성
2. 개선 전후 코드 비교
3. 테스트로 개선 내용 검증

**Where?**
- [`docs/feedback/yulmu-coach-improvements.md`](../../feedback/yulmu-coach-improvements.md) - Yulmu 코치 개선 사항
- [`docs/feedback/coach-park-jisu-feedback.md`](../../feedback/coach-park-jisu-feedback.md) - Park Jisu 코치 피드백

## 📊 검증 체크리스트

### N+1 문제
- [ ] 모든 List 조회 API에서 N+1 발생 여부 확인
- [ ] Lazy Loading이 필요한 곳에 Fetch Join 적용
- [ ] MultipleBagFetchException 방지 (Collection은 1개만 Fetch Join)
- [ ] N+1 자동 검증 테스트 작성 (선택)

### 쿼리 성능
- [ ] 느린 쿼리(>100ms) EXPLAIN ANALYZE로 분석
- [ ] Index 사용 여부 확인 (`key` 컬럼)
- [ ] Filesort, Temporary Table 제거
- [ ] Batch Size 설정 (선택)

### 비즈니스 로직
- [ ] 재고 차감 시점 정확성 (주문 생성 vs 결제)
- [ ] 결제 실패 시 재고 롤백 검증
- [ ] 동시성 테스트 통과 (재고, 쿠폰)
- [ ] 인기 상품 쿼리 정확성 (ProductSalesAggregate 사용)

### 코치 피드백
- [ ] 모든 피드백 항목 검토
- [ ] 개선 가능한 항목 우선순위 정리
- [ ] 개선 전후 코드 비교 문서화
- [ ] 테스트로 개선 내용 검증

## 🔍 빠른 검증 방법

### 1. N+1 문제 확인

**Step 1: application.yml 설정**
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
```

**Step 2: API 호출 후 로그 확인**
```bash
# 주문 목록 조회
curl http://localhost:8080/api/v1/orders/user/1

# 로그에서 SELECT 쿼리 개수 카운트
# 1개: OK
# N개: N+1 발생! → Fetch Join 필요
```

**Step 3: Fetch Join 적용**
```java
@Query("""
    SELECT DISTINCT o FROM Order o
    LEFT JOIN FETCH o.orderItems
    WHERE o.userId = :userId
""")
List<Order> findByUserIdWithItems(@Param("userId") Long userId);
```

### 2. 쿼리 성능 확인

**MySQL에서 EXPLAIN ANALYZE 실행**
```sql
EXPLAIN ANALYZE
SELECT * FROM orders o
LEFT JOIN order_items oi ON o.id = oi.order_id
WHERE o.user_id = 1;
```

**확인 사항:**
- `actual time`: 실제 실행 시간 (ms)
- `key`: 사용된 Index
- `rows`: 검사한 행 수

### 3. 재고 차감 확인

**테스트 시나리오:**
1. 초기 재고 50개
2. 주문 생성 (재고 변화 없음)
3. 결제 성공 (재고 -1 → 49개)
4. 결제 실패 (재고 그대로 49개)

**검증 파일:** [`STOCK_DECREASE_VERIFICATION.md`](./STOCK_DECREASE_VERIFICATION.md)

## 💡 실전 팁

### Tip 1: N+1 발생 빠르게 찾기

**로그에서 같은 SELECT가 반복되면 N+1!**
```
SELECT * FROM orders WHERE user_id = 1;  -- 1개
SELECT * FROM order_items WHERE order_id = 1;  -- N개 (N+1 발생!)
SELECT * FROM order_items WHERE order_id = 2;
SELECT * FROM order_items WHERE order_id = 3;
```

### Tip 2: Fetch Join vs @EntityGraph

**Fetch Join (추천):**
```java
@Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.userId = :userId")
List<Order> findByUserIdWithItems(@Param("userId") Long userId);
```

**@EntityGraph:**
```java
@EntityGraph(attributePaths = {"orderItems"})
List<Order> findByUserId(Long userId);
```

**차이점:**
- Fetch Join: JPQL에서 명시적 제어, DISTINCT 사용 가능
- @EntityGraph: 간결하지만 제어 어려움

### Tip 3: EXPLAIN vs EXPLAIN ANALYZE

| 명령어 | 설명 | 사용 시기 |
|--------|------|-----------|
| `EXPLAIN` | 실행 계획만 보여줌 | 빠른 확인용 |
| `EXPLAIN ANALYZE` | 실제 실행 + 계획 | 정확한 성능 측정 |

**추천:** `EXPLAIN ANALYZE` (실제 실행 시간 포함)

## 📚 참고 자료

### 내부 문서
- [`/docs/week4/README.md`](../README.md) - Week 4 전체 가이드
- [`/.claude/commands/architecture.md`](../../.claude/commands/architecture.md) - 아키텍처 설명
- [`/.claude/commands/testing.md`](../../.claude/commands/testing.md) - 테스트 전략

### 외부 자료
- [Hibernate N+1 문제](https://www.baeldung.com/hibernate-n-plus-1-problem)
- [MySQL EXPLAIN](https://dev.mysql.com/doc/refman/8.0/en/explain.html)
- [JPA Fetch Join](https://www.baeldung.com/jpa-join-types)

---

**현재 Phase**: Week 4 - Database Integration 완료
**검증 상태**: N+1 해결, 쿼리 최적화, 재고 플로우 검증 완료
