# Payment Process 테스트 사전 준비 가이드

**작성일**: 2025-11-24
**대상**: payment-process.js 테스트 실행 전 필수 준비

---

## 🎯 사전 준비가 필요한 이유

Payment Process 테스트는 다음 두 가지 리소스에 의존합니다:

1. **상품 재고**: 주문 생성에 필요
2. **사용자 잔액**: 결제 처리에 필요

**이전 테스트(order-create.js)로 인해 재고가 소진**되었습니다.

---

## 📊 리소스 요구 사항

### 1. 상품 재고

**테스트 규모**:
```
VUs: 200
Duration: 3.5분
예상 요청: ~20,000
상품 수: 10개
상품당 평균 주문: 2,000건
```

**필요 재고**:
```
상품당 재고: 2,000개 (최소)
안전 마진 10배: 20,000개 (권장)
```

---

### 2. 사용자 잔액

**테스트 규모**:
```
사용자 수: 100명
사용자당 평균 주문: 200건
결제 금액: 10,000원/건
사용자당 필요 금액: 2,000,000원
안전 마진 5배: 10,000,000원 (권장)
```

---

## ✅ 사전 준비 절차

### Step 1: MySQL 접속

```bash
mysql -u root -p ecommerce
```

---

### Step 2: 현재 상태 확인

```sql
-- 상품 재고 확인
SELECT id, name, stock
FROM products
WHERE id BETWEEN 1 AND 10
ORDER BY id;

-- 사용자 잔액 확인 (샘플)
SELECT user_id, balance
FROM user_balance
WHERE user_id BETWEEN 1 AND 10
ORDER BY user_id;
```

**예상 결과**:
```
상품 재고: 대부분 0 (order-create.js로 소진)
사용자 잔액: 1,000,000원 미만 (기본값)
```

---

### Step 3: 재고 재충전 ⭐ **필수**

```sql
-- 상품 재고를 20,000개로 재충전
UPDATE products
SET stock = 20000
WHERE id BETWEEN 1 AND 10;

-- 확인
SELECT id, name, stock
FROM products
WHERE id BETWEEN 1 AND 10
ORDER BY id;
```

**기대 출력**:
```
+----+----------------+-------+
| id | name           | stock |
+----+----------------+-------+
|  1 | 노트북         | 20000 |
|  2 | 키보드         | 20000 |
|  3 | 마우스         | 20000 |
|  4 | 모니터         | 20000 |
|  5 | 헤드셋         | 20000 |
|  6 | 웹캠           | 20000 |
|  7 | 스피커         | 20000 |
|  8 | USB 허브       | 20000 |
|  9 | 마우스패드     | 20000 |
| 10 | 노트북 거치대  | 20000 |
+----+----------------+-------+
```

---

### Step 4: 잔액 재충전 ⭐ **필수**

```sql
-- 사용자 잔액을 10,000,000원으로 재충전
UPDATE user_balance
SET balance = 10000000
WHERE user_id BETWEEN 1 AND 100;

-- 확인 (샘플)
SELECT user_id, balance
FROM user_balance
WHERE user_id BETWEEN 1 AND 10
ORDER BY user_id;
```

**기대 출력**:
```
+---------+-----------+
| user_id | balance   |
+---------+-----------+
|       1 | 10000000  |
|       2 | 10000000  |
|       3 | 10000000  |
|       4 | 10000000  |
|       5 | 10000000  |
|       6 | 10000000  |
|       7 | 10000000  |
|       8 | 10000000  |
|       9 | 10000000  |
|      10 | 10000000  |
+---------+-----------+
```

---

### Step 5: 준비 완료 확인

```sql
-- 재고 총합 확인
SELECT
    COUNT(*) as product_count,
    SUM(stock) as total_stock,
    MIN(stock) as min_stock,
    MAX(stock) as max_stock
FROM products
WHERE id BETWEEN 1 AND 10;

-- 잔액 총합 확인
SELECT
    COUNT(*) as user_count,
    SUM(balance) as total_balance,
    MIN(balance) as min_balance,
    MAX(balance) as max_balance
FROM user_balance
WHERE user_id BETWEEN 1 AND 100;
```

**기대 출력**:
```
재고:
- product_count: 10
- total_stock: 200,000
- min_stock: 20,000
- max_stock: 20,000

잔액:
- user_count: 100
- total_balance: 1,000,000,000 (10억)
- min_balance: 10,000,000
- max_balance: 10,000,000
```

---

## 🚀 테스트 실행

### Step 6: 애플리케이션 확인

```bash
# 애플리케이션이 실행 중인지 확인
curl http://localhost:8080/actuator/health

# 예상 출력: {"status":"UP"}
```

---

### Step 7: K6 테스트 실행

```bash
# Payment Process 테스트 실행
k6 run docs/week5/verification/k6/scripts/payment-process.js

# 또는 결과를 JSON으로 저장
k6 run --out json=results/payment-process-$(date +%Y%m%d-%H%M%S).json \
  docs/week5/verification/k6/scripts/payment-process.js
```

---

### Step 8: 실시간 모니터링 (선택)

```bash
# 다른 터미널에서 로그 모니터링
tail -f logs/application.log | grep -E "(재고|잔액|Idempotency|Order)"

# 또는 MySQL 모니터링
watch -n 1 'mysql -u root -p"your_password" -e "
  SELECT
    (SELECT SUM(stock) FROM ecommerce.products WHERE id BETWEEN 1 AND 10) as total_stock,
    (SELECT COUNT(*) FROM ecommerce.orders WHERE created_at > DATE_SUB(NOW(), INTERVAL 5 MINUTE)) as recent_orders,
    (SELECT COUNT(*) FROM ecommerce.payment_idempotency WHERE created_at > DATE_SUB(NOW(), INTERVAL 5 MINUTE)) as recent_payments
"'
```

---

## 🎯 예상 결과

### 성공 시나리오

```
총 Iteration: ~20,000
주문 생성 성공: >80% (16,000+)
멱등성 검증: >5,000건
Idempotency Conflict: >10,000건
중복 결제 방지: >10,000건
```

### Threshold

```
✅ http_req_duration p(95): < 1s
✅ idempotency_verification_success: count > 0
✅ duplicate_payments_prevented: count > 0
```

---

## ⚠️ 문제 해결

### 문제 1: 여전히 재고 소진 (409)

**증상**:
```
Order creation failed: 409
재고가 부족합니다. 상품: XXX, 요청: 1, 재고: 0
```

**해결**:
```sql
-- 재고 재확인
SELECT id, name, stock FROM products WHERE id BETWEEN 1 AND 10;

-- 재고가 0이면 다시 충전
UPDATE products SET stock = 20000 WHERE id BETWEEN 1 AND 10;
```

---

### 문제 2: 여전히 잔액 부족 (409)

**증상**:
```
Order creation failed: 409
잔액이 부족합니다. 사용자: XXX, 잔액: 0
```

**해결**:
```sql
-- 잔액 재확인
SELECT user_id, balance FROM user_balance WHERE user_id BETWEEN 1 AND 10;

-- 잔액이 부족하면 다시 충전
UPDATE user_balance SET balance = 10000000 WHERE user_id BETWEEN 1 AND 100;
```

---

### 문제 3: 애플리케이션 에러

**증상**:
```
Connection refused
Could not connect to server
```

**해결**:
```bash
# 애플리케이션 재시작
./gradlew bootRun

# 또는 JAR 실행
java -jar build/libs/hhplus-ecommerce-0.0.1-SNAPSHOT.jar
```

---

## 📋 최종 체크리스트

테스트 실행 전 확인:

- [ ] MySQL 실행 중
- [ ] 상품 재고 충분 (각 20,000개)
- [ ] 사용자 잔액 충분 (각 10,000,000원)
- [ ] Spring Boot 애플리케이션 실행 중
- [ ] Health check 성공 (http://localhost:8080/actuator/health)
- [ ] K6 설치 확인 (`k6 version`)
- [ ] 이전 테스트 결과 백업 (선택)

테스트 실행 후 확인:

- [ ] 주문 생성 성공률 >80%
- [ ] 멱등성 검증 >1000건
- [ ] 중복 결제 방지 확인
- [ ] Threshold 3개 모두 PASS
- [ ] 데이터베이스 정합성 확인

---

## 💡 팁

### 1. 여러 번 테스트할 경우

매번 재고와 잔액을 재충전해야 합니다:

```bash
# 테스트 전 자동 준비 스크립트
cat > prepare-test.sh <<'EOF'
#!/bin/bash
mysql -u root -p"your_password" ecommerce <<SQL
UPDATE products SET stock = 20000 WHERE id BETWEEN 1 AND 10;
UPDATE user_balance SET balance = 10000000 WHERE user_id BETWEEN 1 AND 100;
SELECT 'Preparation completed' as status;
SQL
EOF

chmod +x prepare-test.sh
./prepare-test.sh
```

---

### 2. 테스트 결과 저장

```bash
# 타임스탬프와 함께 결과 저장
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
k6 run --out json=results/payment-process-${TIMESTAMP}.json \
  docs/week5/verification/k6/scripts/payment-process.js \
  | tee logs/payment-process-${TIMESTAMP}.log
```

---

### 3. 테스트 후 정리

```bash
# 테스트 데이터 정리 (선택)
mysql -u root -p ecommerce <<SQL
-- 테스트 기간의 주문 삭제
DELETE FROM order_items WHERE order_id IN (
  SELECT id FROM orders WHERE created_at > DATE_SUB(NOW(), INTERVAL 10 MINUTE)
);
DELETE FROM orders WHERE created_at > DATE_SUB(NOW(), INTERVAL 10 MINUTE);

-- 멱등성 키 정리
DELETE FROM payment_idempotency WHERE created_at > DATE_SUB(NOW(), INTERVAL 10 MINUTE);
SQL
```

---

## 📚 관련 문서

- **[PAYMENT_PROCESS_FIX.md](./PAYMENT_PROCESS_FIX.md)** - 문제 분석 및 해결 가이드
- **[TROUBLESHOOTING.md](./TROUBLESHOOTING.md)** - 일반적인 문제 해결
- **[README.md](./README.md)** - K6 테스트 전체 가이드

---

**작성자**: Claude Code
**버전**: 1.0
**다음 작업**: 재고/잔액 충전 후 테스트 실행
