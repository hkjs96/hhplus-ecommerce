# Step 9-10 코치 피드백 개선사항 검증 가이드

제이 코치 피드백 반영 완료 후, 개선 효과를 직접 확인하는 실행 가이드

---

## 목차
1. [환경 준비](#1-환경-준비)
2. [Priority 1: 동시성 테스트 실행](#2-priority-1-동시성-테스트-실행)
3. [Priority 2: Lock Timeout 검증](#3-priority-2-lock-timeout-검증)
4. [Priority 3: Retry Logic 검증](#4-priority-3-retry-logic-검증)
5. [Priority 4: JMeter 성능 테스트](#5-priority-4-jmeter-성능-테스트)
6. [Priority 5: K6 부하 테스트](#6-priority-5-k6-부하-테스트)
7. [Before/After 비교](#7-beforeafter-비교)
8. [결과 확인 체크리스트](#8-결과-확인-체크리스트)

---

## 1. 환경 준비

### 1.1 소스 코드 확인

```bash
# 현재 브랜치 확인
git branch --show-current
# 출력: claude/step11-12-learning-guide-01WPmRS9bGAAUmFSkDGW1qvQ

# 최신 커밋 확인
git log --oneline -7
# 출력:
# db343e0 docs: Add K6 load test guide and scripts (100→500→1000 VUs)
# 3ebc5e9 docs: Add JMeter performance test guide and test plans
# 769db59 feat: Implement optimistic lock retry logic with Exponential Backoff
# 4e4eacb feat: Add lock timeout configuration for pessimistic locks
# 0c5e50f test: Add UserBalance optimistic lock concurrency tests
# 7c6adac test: Add PaymentIdempotency concurrency tests
# be392bc test: Add missing concurrency tests
```

### 1.2 MySQL 실행 확인

```bash
# MySQL 실행 상태 확인
mysql -u root -p -e "SELECT VERSION();"

# 데이터베이스 확인
mysql -u root -p -e "SHOW DATABASES LIKE 'ecommerce';"

# Performance Schema 활성화 확인
mysql -u root -p -e "SHOW VARIABLES LIKE 'performance_schema';"
# 출력: performance_schema | ON
```

### 1.3 애플리케이션 빌드

```bash
# 빌드 (테스트 제외)
./gradlew clean build -x test

# 빌드 성공 확인
ls -lh build/libs/
# 출력: hhplus-ecommerce-0.0.1-SNAPSHOT.jar
```

---

## 2. Priority 1: 동시성 테스트 실행

### 2.1 PaymentIdempotency 테스트

```bash
# 단일 테스트 실행
./gradlew test --tests "io.hhplus.ecommerce.application.usecase.order.PaymentIdempotencyConcurrencyTest"
```

**예상 출력**:
```
PaymentIdempotencyConcurrencyTest > 멱등성키_동시성_테스트_중복차단 PASSED
PaymentIdempotencyConcurrencyTest > 서로_다른_멱등성키_동시성_테스트 PASSED
PaymentIdempotencyConcurrencyTest > 네트워크_재시도_시나리오 PASSED
PaymentIdempotencyConcurrencyTest > 대규모_동시_결제_테스트 PASSED

BUILD SUCCESSFUL in 15s
```

**확인 사항**:
- ✅ 동일 멱등성 키 10번 시도 → 1번만 성공
- ✅ 서로 다른 키 10개 → 10번 모두 성공
- ✅ 네트워크 재시도 3번 → 캐시된 결과 반환
- ✅ 100명 동시 결제 → 중복 없이 100번 성공

### 2.2 UserBalance Optimistic Lock 테스트

```bash
# 단일 테스트 실행
./gradlew test --tests "io.hhplus.ecommerce.domain.user.UserBalanceOptimisticLockConcurrencyTest"
```

**예상 출력**:
```
UserBalanceOptimisticLockConcurrencyTest > 낙관적락_잔액차감_동시성_테스트 PASSED
UserBalanceOptimisticLockConcurrencyTest > 낙관적락_잔액부족_동시성_테스트 PASSED
UserBalanceOptimisticLockConcurrencyTest > 낙관적락_충전과차감_동시_테스트 PASSED
UserBalanceOptimisticLockConcurrencyTest > 대규모_동시_차감_테스트 PASSED
UserBalanceOptimisticLockConcurrencyTest > 버전_증가_확인_테스트 PASSED
UserBalanceOptimisticLockConcurrencyTest > 동시성_없는_단순_차감_테스트 PASSED

BUILD SUCCESSFUL in 20s
```

**확인 사항**:
- ✅ 10명 동시 차감 → 최종 잔액 0원 (Lost Update 방지)
- ✅ 잔액 부족 시 일부만 성공 (50,000원에서 10,000원씩 5번만 성공)
- ✅ 충전과 차감 동시 발생 → 정확한 계산
- ✅ @Version 증가 확인 (10번 업데이트 → version 10)

### 2.3 전체 테스트 커버리지 확인

```bash
# 전체 테스트 실행 + 커버리지 리포트
./gradlew test jacocoTestReport

# 리포트 확인
open build/reports/jacoco/test/html/index.html
```

**예상 결과**:
- **Line Coverage**: 94% (유지)
- **Branch Coverage**: 87% (유지)
- 새로운 테스트 2개 추가로 커버리지 유지 또는 증가

---

## 3. Priority 2: Lock Timeout 검증

### 3.1 애플리케이션 실행

```bash
# 터미널 1: 애플리케이션 실행
./gradlew bootRun
```

### 3.2 MySQL 모니터링 준비

```bash
# 터미널 2: MySQL 모니터링
mysql -u root -p ecommerce

# Performance Schema 쿼리 준비
SELECT * FROM performance_schema.data_locks;
SELECT * FROM performance_schema.data_lock_waits;
```

### 3.3 Lock Timeout 시뮬레이션

#### 방법 1: JPA Repository 메서드 확인

```bash
# 코드 확인
cat src/main/java/io/hhplus/ecommerce/infrastructure/persistence/product/JpaProductRepository.java | grep -A 10 "QueryHints"
```

**예상 출력**:
```java
@QueryHints({
    @QueryHint(name = "javax.persistence.lock.timeout", value = "3000")
})
```

#### 방법 2: 실제 동작 테스트

**터미널 3: 첫 번째 요청 (락 획득)**
```bash
# 주문 생성 (재고 차감, 락 획득)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": [{"productId": 1, "quantity": 1}]
  }'
```

**터미널 4: 두 번째 요청 (락 대기 → 3초 후 타임아웃)**
```bash
# 동일 상품 주문 (락 대기)
time curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 2,
    "items": [{"productId": 1, "quantity": 1}]
  }'
```

**예상 결과**:
- 첫 번째 요청: 정상 처리 (200 OK)
- 두 번째 요청: 3초 대기 후 PessimisticLockException 또는 타임아웃
- `time` 출력: `real 0m3.xxx s` (약 3초)

### 3.4 MySQL Lock Wait 확인

```sql
-- 터미널 2에서 실행 (두 번째 요청 실행 중)
SELECT
    waiting.OBJECT_NAME AS table_name,
    waiting.LOCK_TYPE,
    waiting.LOCK_MODE,
    blocking.THREAD_ID AS blocking_thread,
    waiting.THREAD_ID AS waiting_thread
FROM performance_schema.data_lock_waits dlw
JOIN performance_schema.data_locks waiting
    ON dlw.REQUESTING_ENGINE_LOCK_ID = waiting.ENGINE_LOCK_ID
JOIN performance_schema.data_locks blocking
    ON dlw.BLOCKING_ENGINE_LOCK_ID = blocking.ENGINE_LOCK_ID;
```

**예상 출력**:
```
+------------+-----------+-----------+------------------+------------------+
| table_name | LOCK_TYPE | LOCK_MODE | blocking_thread  | waiting_thread   |
+------------+-----------+-----------+------------------+------------------+
| products   | RECORD    | X         | 123              | 124              |
+------------+-----------+-----------+------------------+------------------+
```

**확인 사항**:
- ✅ 락 타임아웃 3초 동작 확인
- ✅ MySQL에서 Lock Wait 확인
- ✅ 무한 대기 방지

---

## 4. Priority 3: Retry Logic 검증

### 4.1 로그 레벨 설정

**application.yml 수정** (임시):
```yaml
logging:
  level:
    io.hhplus.ecommerce.application.usecase.user.OptimisticLockRetryService: DEBUG
```

### 4.2 잔액 충전 동시 요청 테스트

**터미널 1: 애플리케이션 실행**
```bash
./gradlew bootRun
```

**터미널 2-11: 동시에 10개 요청**
```bash
# 각 터미널에서 동시에 실행 (또는 스크립트 사용)
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/users/1/balance/charge \
    -H "Content-Type: application/json" \
    -d '{"amount": 10000}' &
done
wait
```

### 4.3 로그 확인

**예상 로그 출력**:
```
2025-01-23 12:34:56.123 INFO  [...] Charging balance for userId: 1, amount: 10000
2025-01-23 12:34:56.124 WARN  [...] Optimistic Lock 충돌 발생. 재시도 1/10 (50ms 대기)
2025-01-23 12:34:56.174 WARN  [...] Optimistic Lock 충돌 발생. 재시도 2/10 (100ms 대기)
2025-01-23 12:34:56.274 DEBUG [...] Balance charged successfully. userId: 1, new balance: 110000
```

**확인 사항**:
- ✅ Optimistic Lock 충돌 발생 시 재시도 로그 출력
- ✅ Exponential Backoff 적용 (50ms → 100ms → 200ms)
- ✅ 최종적으로 모든 요청 성공
- ✅ 잔액 정확히 계산 (초기 100,000 + 10,000 × 10 = 200,000)

### 4.4 데이터베이스 확인

```sql
-- 최종 잔액 확인
SELECT id, balance, version FROM users WHERE id = 1;
```

**예상 출력**:
```
+----+----------+---------+
| id | balance  | version |
+----+----------+---------+
| 1  | 200000   | 10      |
+----+----------+---------+
```

**확인**:
- ✅ 잔액: 200,000원 (10번 충전 성공)
- ✅ version: 10 (Optimistic Lock으로 10번 업데이트)

---

## 5. Priority 4: JMeter 성능 테스트

### 5.1 JMeter 설치 확인

```bash
# macOS
brew install jmeter

# 버전 확인
jmeter -v
# 출력: Version 5.6.3 (또는 최신 버전)
```

### 5.2 테스트 플랜 확인

```bash
# 테스트 플랜 파일 확인
ls -lh docs/week4/verification/jmeter/testplans/
# 출력: balance-charge.jmx
```

### 5.3 GUI 모드로 테스트 플랜 열기

```bash
# JMeter GUI 실행
jmeter -t docs/week4/verification/jmeter/testplans/balance-charge.jmx
```

**GUI에서 확인**:
1. Thread Group: 100 users, 10s ramp-up
2. HTTP Request: POST /api/users/1/balance/charge
3. Summary Report, Aggregate Report 활성화

### 5.4 CLI 모드로 테스트 실행 (권장)

```bash
# 1. 애플리케이션 실행 확인
curl http://localhost:8080/actuator/health
# 출력: {"status":"UP"}

# 2. 데이터베이스 초기화 (선택)
mysql -u root -p ecommerce < src/main/resources/data.sql

# 3. JMeter 테스트 실행
jmeter -n \
  -t docs/week4/verification/jmeter/testplans/balance-charge.jmx \
  -l docs/week4/verification/jmeter/results/after/balance-charge-results.jtl \
  -e -o docs/week4/verification/jmeter/reports/after/balance-charge/
```

### 5.5 결과 확인

```bash
# HTML 리포트 열기
open docs/week4/verification/jmeter/reports/after/balance-charge/index.html
```

**대시보드 확인 항목**:
- **Statistics**:
  - Samples: 100
  - Average: ~600ms
  - Error %: 0%
  - Throughput: ~90/sec

- **Response Times Over Time**:
  - 안정적인 응답 시간 (큰 변동 없음)

- **Transactions Per Second**:
  - TPS: 약 90 req/sec

**예상 Summary**:
```
Label                | Samples | Average | Min | Max  | Std.Dev | Error % | Throughput
---------------------|---------|---------|-----|------|---------|---------|------------
POST Charge Balance  | 100     | 600     | 50  | 1500 | 200     | 0.0%    | 90.0/sec
```

**확인 사항**:
- ✅ 평균 응답 시간: 600ms 이하
- ✅ 에러율: 0%
- ✅ TPS: 90 req/sec 이상
- ✅ 재시도 로직으로 안정적 처리

---

## 6. Priority 5: K6 부하 테스트

### 6.1 K6 설치 확인

```bash
# macOS
brew install k6

# 버전 확인
k6 version
# 출력: k6 v0.48.0 (또는 최신 버전)
```

### 6.2 테스트 스크립트 확인

```bash
# 스크립트 파일 확인
ls -lh docs/week4/verification/k6/scripts/
# 출력:
# balance-charge.js
# order-create.js
# payment-process.js
```

### 6.3 잔액 충전 부하 테스트 (100 → 500 → 1000 VUs)

```bash
# 1. 애플리케이션 실행 확인
curl http://localhost:8080/actuator/health

# 2. K6 테스트 실행
k6 run docs/week4/verification/k6/scripts/balance-charge.js
```

**예상 출력 (요약)**:
```
     ✓ status is 200
     ✓ response has balance
     ✓ balance increased correctly

     checks.........................: 100.00% ✓ 15000     ✗ 0
     data_received..................: 4.5 MB  75 kB/s
     data_sent......................: 2.2 MB  37 kB/s
   ✓ errors.........................: 0.00%   ✓ 0         ✗ 15000
   ✓ http_req_duration..............: avg=600ms   min=50ms  med=500ms  max=1.5s   p(95)=1s
     http_req_failed................: 0.00%   ✓ 0         ✗ 15000
     http_reqs......................: 15000   83.333/s
     iterations.....................: 5000    27.777/s
   ✓ optimistic_lock_conflicts......: 150     0.833/s
   ✓ success........................: 100.00% ✓ 5000      ✗ 0
     vus............................: 0       min=0       max=1000
     vus_max........................: 1000    min=1000    max=1000

running (06m00.0s), 0000/1000 VUs, 5000 complete and 0 interrupted iterations
default ✓ [======================================] 0000/1000 VUs  6m0s
```

**확인 사항**:
- ✅ **100 VUs**: Error 0%, P95 < 600ms
- ✅ **500 VUs**: Error < 5%, P95 < 1s
- ✅ **1000 VUs**: Error < 10%, P95 < 1.5s
- ✅ Optimistic Lock Conflicts: 약 150회 (3%, 정상)

### 6.4 주문 생성 부하 테스트 (Pessimistic Lock)

```bash
k6 run docs/week4/verification/k6/scripts/order-create.js
```

**예상 출력**:
```
     ✓ status is 200 or 201
     ✓ response has orderId

     checks.........................: 90.00%  ✓ 4500      ✗ 500
   ✓ errors.........................: 10.00%  ✓ 500       ✗ 4500
   ✓ http_req_duration..............: avg=1.5s    p(95)=3s    p(99)=3.5s
   ✓ lock_wait_time.................: avg=1.5s    p(95)=3s
   ✓ pessimistic_lock_timeouts......: 40      0.222/s
     vus............................: 0       min=0       max=1000
```

**확인 사항**:
- ✅ **100 VUs**: Error 0%, Lock Timeout 0
- ✅ **500 VUs**: Error ~5%, Lock Timeout ~15
- ✅ **1000 VUs**: Error ~10%, Lock Timeout ~40
- ✅ 락 타임아웃 3초 동작 확인

### 6.5 결제 처리 테스트 (Idempotency Key)

```bash
k6 run docs/week4/verification/k6/scripts/payment-process.js
```

**예상 출력**:
```
     ✓ status is 200

   ✓ duplicate_payments_prevented...: 10000   55.555/s
     errors.........................: 0.00%   ✓ 0         ✗ 5000
   ✓ idempotency_conflicts..........: 10000   55.555/s
   ✓ idempotency_verification_success: 5000  27.777/s
   ✓ idempotency_verification_failure: 0     0/s
     success........................: 100.00% ✓ 5000      ✗ 0
```

**확인 사항**:
- ✅ 각 VU마다 3번 결제 시도 → 1번만 성공
- ✅ duplicate_payments_prevented: 2번/VU (총 10,000회)
- ✅ idempotency_verification_success: 100%
- ✅ 중복 결제 완벽 차단

### 6.6 결과를 파일로 저장

```bash
# JSON 형식으로 저장
k6 run --out json=docs/week4/verification/k6/results/after/balance-charge.json \
  docs/week4/verification/k6/scripts/balance-charge.js

# Summary 저장
k6 run --summary-export=docs/week4/verification/k6/results/after/summary.json \
  docs/week4/verification/k6/scripts/balance-charge.js
```

---

## 7. Before/After 비교

### 7.1 Before 테스트 (개선 전 코드로 전환)

```bash
# 1. 개선 전 커밋으로 체크아웃 (예시: be392bc 이전)
git checkout be392bc~1

# 2. 빌드
./gradlew clean build -x test

# 3. 애플리케이션 실행
./gradlew bootRun

# 4. K6 테스트 실행
k6 run --out json=docs/week4/verification/k6/results/before/balance-charge.json \
  docs/week4/verification/k6/scripts/balance-charge.js
```

**예상 Before 결과**:
```
     errors.........................: 15.00%  ✓ 750       ✗ 4250
     http_req_duration..............: avg=1.5s    p(95)=5s    p(99)=30s
     optimistic_lock_conflicts......: 750     4.166/s
     success........................: 85.00%  ✓ 4250      ✗ 750
```

### 7.2 After 테스트 (개선 후 코드로 전환)

```bash
# 1. 최신 커밋으로 체크아웃
git checkout claude/step11-12-learning-guide-01WPmRS9bGAAUmFSkDGW1qvQ

# 2. 빌드
./gradlew clean build -x test

# 3. 애플리케이션 실행
./gradlew bootRun

# 4. K6 테스트 실행
k6 run --out json=docs/week4/verification/k6/results/after/balance-charge.json \
  docs/week4/verification/k6/scripts/balance-charge.js
```

**예상 After 결과**:
```
     errors.........................: 0.00%   ✓ 0         ✗ 5000
     http_req_duration..............: avg=600ms   p(95)=1s    p(99)=1.5s
     optimistic_lock_conflicts......: 150     0.833/s
     success........................: 100.00% ✓ 5000      ✗ 0
```

### 7.3 비교표 작성

| 메트릭 | Before | After | 개선율 |
|--------|--------|-------|--------|
| **TPS** | 50 req/sec | 90 req/sec | ↑ 80% |
| **Error Rate** | 15% | 0% | ↑ 100% |
| **P95 Latency** | 5s | 1s | ↑ 80% |
| **P99 Latency** | 30s | 1.5s | ↑ 95% |
| **Success Rate** | 85% | 100% | ↑ 17% |

---

## 8. 결과 확인 체크리스트

### Priority 1: 동시성 테스트 ✅

- [ ] PaymentIdempotencyConcurrencyTest 4개 테스트 통과
- [ ] UserBalanceOptimisticLockConcurrencyTest 6개 테스트 통과
- [ ] 테스트 커버리지 94% 유지 또는 증가
- [ ] 동일 멱등성 키 10번 → 1번만 성공 확인
- [ ] 10명 동시 차감 → 잔액 0원 확인

### Priority 2: Lock Timeout ✅

- [ ] JpaProductRepository에 @QueryHints 적용 확인
- [ ] JpaCouponRepository에 @QueryHints 적용 확인
- [ ] 실제 동작 테스트: 3초 타임아웃 확인
- [ ] MySQL Lock Wait 확인
- [ ] 무한 대기 방지 확인

### Priority 3: Retry Logic ✅

- [ ] OptimisticLockRetryService 생성 확인
- [ ] ChargeBalanceUseCase에 재시도 로직 적용 확인
- [ ] 동시 요청 시 재시도 로그 출력 확인
- [ ] Exponential Backoff 동작 확인 (50ms → 100ms → 200ms)
- [ ] 최종 잔액 정확히 계산 확인

### Priority 4: JMeter 성능 테스트 ✅

- [ ] JMeter 설치 완료
- [ ] balance-charge.jmx 테스트 플랜 실행
- [ ] HTML 리포트 생성 확인
- [ ] Summary Report: TPS 90, Error 0% 확인
- [ ] Before/After 비교표 작성

### Priority 5: K6 부하 테스트 ✅

- [ ] K6 설치 완료
- [ ] balance-charge.js 실행 (100 → 500 → 1000 VUs)
- [ ] order-create.js 실행 (Lock Timeout 확인)
- [ ] payment-process.js 실행 (Idempotency 확인)
- [ ] Lock Contention 임계점 파악 (500 VUs)
- [ ] Before/After 비교 완료

### 전체 검증 ✅

- [ ] 5가지 Priority 모두 완료
- [ ] 정량적 개선 효과 증명 (TPS 80% 증가, Error 100% 감소)
- [ ] 테스트 커버리지 유지 (94%)
- [ ] 문서화 완료 (가이드 3개, 테스트 스크립트 5개)
- [ ] Git 커밋 7개 모두 푸시 완료

---

## 9. 트러블슈팅

### 문제 1: 테스트 실패

**증상**: PaymentIdempotencyConcurrencyTest 실패
```
expected: 1
but was: 0
```

**원인**: 데이터베이스 초기화 미실행

**해결**:
```bash
mysql -u root -p ecommerce < src/main/resources/data.sql
./gradlew clean test
```

### 문제 2: JMeter Connection Refused

**증상**:
```
java.net.ConnectException: Connection refused
```

**원인**: 애플리케이션이 실행되지 않음

**해결**:
```bash
# 다른 터미널에서 애플리케이션 실행
./gradlew bootRun

# 헬스체크 확인
curl http://localhost:8080/actuator/health
```

### 문제 3: K6 설치 오류

**증상**: `command not found: k6`

**해결**:
```bash
# macOS
brew install k6

# Linux
sudo apt-get install k6

# 버전 확인
k6 version
```

### 문제 4: MySQL Performance Schema 비활성화

**증상**:
```
SELECT * FROM performance_schema.data_locks;
ERROR 1046 (3D000): No database selected
```

**해결**:
```bash
# my.cnf 수정
[mysqld]
performance_schema = ON

# MySQL 재시작
sudo service mysql restart
```

---

## 10. 결과 리포트 예시

### 검증 완료 리포트

```
========================================
제이 코치 피드백 개선사항 검증 완료
========================================

실행 일시: 2025-01-23 14:30:00
실행자: [Your Name]
브랜치: claude/step11-12-learning-guide-01WPmRS9bGAAUmFSkDGW1qvQ

----------------------------------------
Priority 1: 동시성 테스트 ✅
----------------------------------------
✓ PaymentIdempotencyConcurrencyTest: 4/4 통과
✓ UserBalanceOptimisticLockConcurrencyTest: 6/6 통과
✓ 테스트 커버리지: 94%

----------------------------------------
Priority 2: Lock Timeout ✅
----------------------------------------
✓ ProductRepository: @QueryHints 적용 (3000ms)
✓ CouponRepository: @QueryHints 적용 (3000ms)
✓ 실제 동작 확인: 3초 타임아웃 동작

----------------------------------------
Priority 3: Retry Logic ✅
----------------------------------------
✓ OptimisticLockRetryService: 생성 완료
✓ ChargeBalanceUseCase: 재시도 로직 적용
✓ Exponential Backoff: 50ms → 100ms → 200ms
✓ 10명 동시 충전: 200,000원 정확히 계산

----------------------------------------
Priority 4: JMeter 성능 테스트 ✅
----------------------------------------
✓ TPS: 90 req/sec
✓ Error Rate: 0%
✓ P95 Latency: 600ms
✓ Before/After 비교 완료

----------------------------------------
Priority 5: K6 부하 테스트 ✅
----------------------------------------
✓ 100 VUs: Error 0%, P95 600ms
✓ 500 VUs: Error 5%, P95 1.5s
✓ 1000 VUs: Error 10%, P95 3s
✓ Lock Contention 임계점: 500 VUs

----------------------------------------
종합 개선 효과
----------------------------------------
TPS:         50 → 90 req/sec (↑ 80%)
Error Rate:  15% → 0% (↑ 100%)
P95 Latency: 5s → 1s (↑ 80%)
Success:     85% → 100% (↑ 17%)

========================================
검증 결과: 모든 항목 통과 ✅
========================================
```

---

## 참고 자료

- [STEP9-10_COACH_FEEDBACK_IMPROVEMENTS.md](./STEP9-10_COACH_FEEDBACK_IMPROVEMENTS.md)
- [JMETER_PERFORMANCE_TEST_GUIDE.md](./JMETER_PERFORMANCE_TEST_GUIDE.md)
- [K6_LOAD_TEST_GUIDE.md](./K6_LOAD_TEST_GUIDE.md)
- [jmeter/README.md](./jmeter/README.md)
- [k6/README.md](./k6/README.md)
