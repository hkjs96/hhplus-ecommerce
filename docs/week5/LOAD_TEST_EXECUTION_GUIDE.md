# K6 부하 테스트 실행 가이드 (개선 버전)

## 📋 변경 사항 요약

### 1. DataInitializer 개선 (자동 초기화 ✅)
- **사용자 잔액**: 100만원 → **1억원** (User 1-13, 각 100배 증가)
- **쿠폰 수량**: 1,000개 → **10,000개** (Coupon 1-3, 10배 증가)
- 애플리케이션 시작 시 **자동으로 테스트 데이터 생성** (별도 SQL 실행 불필요)

### 2. K6 스크립트 개선
- **사용자 분산**: userId=1 고정 → **랜덤 1~10** (부하 분산)
- **동시성 검증**: 여러 사용자가 동시에 쿠폰 발급 시도

---

## 🚀 실행 단계

### Step 1: 애플리케이션 실행

```bash
# MySQL 시작
docker-compose up -d

# 애플리케이션 실행 (DataInitializer가 자동으로 테스트 데이터 생성)
./gradlew bootRun

# 헬스 체크
curl http://localhost:8080/actuator/health
```

**애플리케이션 로그 확인 (DataInitializer):**
```
🚀 Starting initial data loading...
📝 Creating test users...
   ✓ Created 13 test users (기본 3명 + K6 테스트 10명)
   💰 K6 test users (1-13): 각 100,000,000원 (지속적인 부하 테스트 가능)
🎟️ Creating test coupons...
   ✓ Created 5 test coupons
   🎫 K6 test coupons (1-3): 각 10,000개 (지속적인 부하 테스트 가능)
✅ Initial data loading completed!
```

### Step 2: 테스트 데이터 검증 (선택)

**DataInitializer가 자동으로 생성한 데이터 확인:**
```sql
-- 사용자 잔액 확인 (User 1-13: 각 1억원)
SELECT id, email, name, FORMAT(balance, 0) as balance
FROM users WHERE id BETWEEN 1 AND 13;

-- 쿠폰 수량 확인 (Coupon 1-3: 각 10,000개)
SELECT id, coupon_code, name, quantity
FROM coupons WHERE id IN (1, 2, 3);
```

**예상 결과:**
```
Users:
+----+---------------------------+----------------+---------------+
| id | email                     | name           | balance       |
+----+---------------------------+----------------+---------------+
| 1  | hanghae@example.com       | 김항해         | 100,000,000원 |
| 2  | plus@example.com          | 이플러스       | 500,000원     |
| 3  | backend@example.com       | 박백엔드       | 100,000원     |
| 4  | testuser4@example.com     | 테스트사용자4  | 100,000,000원 |
| ...| ...                       | ...            | ...           |
+----+---------------------------+----------------+---------------+

Coupons:
+----+--------------+------------------------+----------+
| id | coupon_code  | name                   | quantity |
+----+--------------+------------------------+----------+
| 1  | WELCOME10    | 신규 가입 10% 할인     | 9999     |
| 2  | VIP20        | VIP 회원 20% 할인      | 9999     |
| 3  | EARLYBIRD15  | 얼리버드 15% 할인      | 9999     |
+----+--------------+------------------------+----------+
```

### Step 3: K6 부하 테스트 실행

**기본 실행:**
```bash
k6 run load-test.js
```

**결과를 JSON으로 저장:**
```bash
mkdir -p results
k6 run --out json=results/load-test-$(date +%Y%m%d-%H%M%S).json load-test.js
```

**요약만 저장:**
```bash
k6 run --summary-export=results/summary-$(date +%Y%m%d-%H%M%S).json load-test.js
```

**빠른 테스트 (10 VUs, 30초):**
```bash
k6 run --vus 10 --duration 30s load-test.js
```

---

## 📊 예상 결과

### Before (데이터 증가 전)

```
✗ http_req_failed................: 25.27%
  - Order 실패: 99% (잔액 부족)
  - Coupon 실패: 99% (수량 부족)

✓ TPS............................: 61.28 req/s
✓ P95............................: 33.82ms
✓ System Error Rate..............: 0.00% ✅
```

**문제점:**
- 사용자 1의 잔액 100만원 → 노트북 1개 구매 후 잔액 부족
- 쿠폰 수량 ~10개 → 12번 발급 후 소진

### After (데이터 증가 후) - 예상

```
✓ http_req_failed................: ~5% (정상 비즈니스 에러만)
✓ TPS............................: ~150-200 req/s (+200% 🔥)
✓ P95............................: ~50-100ms (부하 증가로 소폭 상승)
✓ System Error Rate..............: 0.00% ✅

✅ Order + Payment Success.......: ~1,500건 (이전: 1건)
✅ Coupon Issuance Success.......: ~1,000건 (이전: 12건)
✅ HTTP 200 Success Rate.........: ~95%

🎯 동시성 제어 검증:
  - 중복 쿠폰 발급: 0건 ✅
  - Race Condition: 0건 ✅
  - Deadlock: 0건 ✅
```

**개선점:**
- 사용자 1~10 분산 → 각 사용자 1억원 (지속적인 주문 가능)
- 쿠폰 10,000개 → 부하 테스트 전체 기간 동안 발급 가능
- 동시성 제어 검증 가능 (여러 사용자가 동시에 동일 쿠폰 발급)

---

## 🔍 결과 분석 방법

### 1. K6 콘솔 출력 확인

**핵심 메트릭:**
```
http_reqs..................: 12000  200/s
http_req_duration..........: avg=80ms p(95)=150ms
http_req_failed............: 5% ✓ 600 ✗ 11400

✓ http_req_duration........: p(95)<500ms  PASS
✓ http_req_failed..........: rate<0.05    PASS
✓ errors...................: rate<0.05    PASS
```

**시나리오별 성공률:**
```
Product List:
  ✓ 100% success (항상 성공 예상)

Order + Payment:
  ✓ ~90% success (재고 부족 시 일부 실패 가능)

Coupon Issuance:
  ✓ ~90% success (중복 발급 시도는 409 Conflict로 정상 차단)
```

### 2. 데이터베이스 검증

**테스트 후 즉시 실행:**

```sql
USE ecommerce;

-- 1. 쿠폰 발급 현황
SELECT
    c.id,
    c.name,
    c.quantity as remaining,
    COUNT(uc.id) as issued_count,
    (10000 - c.quantity - COUNT(uc.id)) as discrepancy
FROM coupons c
LEFT JOIN user_coupons uc ON c.coupon_id = uc.coupon_id
WHERE c.id = 1
GROUP BY c.id;

-- 예상:
-- remaining: ~9,000개
-- issued_count: ~1,000개
-- discrepancy: 0 (수량 정합성 일치 ✅)

-- 2. 중복 발급 검증 (MUST BE ZERO!)
SELECT
    user_id,
    coupon_id,
    COUNT(*) as duplicate_count
FROM user_coupons
WHERE coupon_id = 1
GROUP BY user_id, coupon_id
HAVING COUNT(*) > 1;

-- 예상: 0 rows (중복 없음 ✅)

-- 3. 사용자별 잔액 확인
SELECT
    id,
    name,
    FORMAT(balance, 0) as remaining_balance,
    (SELECT COUNT(*) FROM orders WHERE user_id = u.id) as order_count
FROM users u
WHERE id BETWEEN 1 AND 10;

-- 예상:
-- remaining_balance: 각 사용자마다 다름 (주문 횟수에 따라)
-- order_count: 1~200건 (랜덤 분산)
```

### 3. 애플리케이션 로그 확인

```bash
# 주문 성공 건수
grep "Order created successfully" logs/application.log | wc -l

# 결제 성공 건수
grep "Payment processed successfully" logs/application.log | wc -l

# 쿠폰 발급 성공 건수
grep "Coupon issued successfully" logs/application.log | wc -l

# 중복 발급 시도 차단 (DB Constraint)
grep "Duplicate coupon issuance blocked" logs/application.log | wc -l
# 예상: 0건 (Pessimistic Lock으로 사전 차단)

# 에러 로그 확인
grep "ERROR" logs/application.log | tail -20
```

### 4. Prometheus 메트릭 확인

```bash
# 전체 메트릭 조회
curl http://localhost:8080/actuator/prometheus

# 주문 메트릭
curl http://localhost:8080/actuator/metrics/orders_total

# 결제 메트릭
curl http://localhost:8080/actuator/metrics/payments_total

# 쿠폰 메트릭
curl http://localhost:8080/actuator/metrics/coupons_issued_total
```

---

## 🎯 Pass 기준

### 성능 목표

| 메트릭 | 목표 | 설명 |
|-------|------|-----|
| TPS | > 100 req/s | 초당 처리량 |
| P95 Latency | < 500ms | 95% 요청 응답 시간 |
| HTTP Success Rate | > 95% | 5xx 에러율 5% 미만 |
| System Error Rate | 0% | 동시성 제어 실패, Deadlock 없음 |

### 동시성 제어 검증

| 항목 | 기대값 | 검증 방법 |
|-----|-------|---------|
| 중복 쿠폰 발급 | 0건 | SQL: `SELECT ... HAVING COUNT(*) > 1` |
| 수량 정합성 | 일치 | `quantity + issued_count = 10000` |
| Race Condition | 0건 | 로그에 예외 없음 |
| Deadlock | 0건 | MySQL: `SHOW ENGINE INNODB STATUS` |

---

## 🐛 문제 해결

### 1. 여전히 높은 실패율 (>20%)

**원인 분석:**
```bash
# K6 에러 로그 확인
grep "ERRO" k6-output.log | head -20

# 애플리케이션 로그 확인
grep "ERROR\|WARN" logs/application.log | tail -50
```

**가능한 원인:**
- 재고 부족 (Product stock 소진)
- 데이터베이스 커넥션 풀 부족
- 쿼리 성능 문제

**해결책:**
```sql
-- 상품 재고 증가
UPDATE products SET stock = 100000 WHERE id = 1;

-- HikariCP 설정 확인
# application.yml: maximum-pool-size: 50 (충분함)
```

### 2. 데이터베이스 연결 실패

**증상:**
```
ERRO[0120] Could not create order: 500 Internal Server Error
```

**확인:**
```bash
# MySQL 프로세스 확인
ps aux | grep mysql

# Docker 컨테이너 확인
docker ps | grep mysql

# 연결 테스트
mysql -u root -p -e "SELECT 1"
```

**해결:**
```bash
# MySQL 재시작
docker-compose restart mysql

# 애플리케이션 재시작
./gradlew bootRun
```

### 3. Pessimistic Lock 타임아웃

**증상:**
```
[ERROR] org.hibernate.exception.LockTimeoutException: could not execute statement
```

**원인:**
- 다른 트랜잭션이 행 잠금을 오래 보유
- 트랜잭션 커밋 지연

**해결:**
```yaml
# application.yml
spring:
  jpa:
    properties:
      javax.persistence.lock.timeout: 5000  # 5초로 증가
```

---

## 📈 성능 비교 문서 업데이트

테스트 완료 후 결과를 다음 파일에 반영하세요:

1. **`docs/week5/OPTIMIZATION_BEFORE_AFTER.md`**
   - "After (최적화 후)" 섹션에 실제 측정값 기록
   - TPS, P95, 성공률 업데이트

2. **`docs/week5/PERFORMANCE_MEASUREMENT.md`**
   - K6 테스트 결과 추가
   - 메트릭 스크린샷 첨부 (선택)

---

## ✅ 체크리스트

- [ ] MySQL 실행 중
- [ ] 애플리케이션 실행 중 (포트 8080)
- [ ] DataInitializer 로그 확인 (테스트 데이터 자동 생성 완료)
- [ ] 헬스 체크 성공 (`curl http://localhost:8080/actuator/health`)
- [ ] 테스트 데이터 검증 (User 잔액 1억원, Coupon 수량 10,000개)
- [ ] K6 부하 테스트 실행 (`k6 run load-test.js`)
- [ ] 결과 분석 (TPS, P95, 성공률)
- [ ] 데이터베이스 검증 (중복 발급 0건, 수량 정합성 일치)
- [ ] 로그 확인 (에러 없음)
- [ ] 성능 문서 업데이트

---

## 📚 참고 문서

- 쿠폰 동시성 검증: `docs/week5/COUPON_CONCURRENCY_VERIFICATION.md`
- K6 가이드: `LOAD_TEST_README.md`
- 성능 측정: `docs/week5/PERFORMANCE_MEASUREMENT.md`
- 최적화 전후: `docs/week5/OPTIMIZATION_BEFORE_AFTER.md`
