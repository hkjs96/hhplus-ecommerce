# K6 Load Test Scripts

제이 코치 피드백 반영 (Priority 5): K6 부하 테스트 (100 → 500 → 1000 VUs)

---

## 빠른 시작

### 1. K6 설치

```bash
# macOS
brew install k6

# Windows
choco install k6

# Linux
sudo apt-get install k6
```

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 3. K6 테스트 실행

```bash
# 잔액 충전 테스트 (Optimistic Lock)
k6 run scripts/balance-charge.js

# 주문 생성 테스트 (Pessimistic Lock)
k6 run scripts/order-create.js

# 결제 처리 테스트 (Idempotency Key)
k6 run scripts/payment-process.js
```

---

## 테스트 스크립트

### balance-charge.js
- **목적**: Optimistic Lock + 재시도 로직 성능 측정
- **단계적 부하**: 100 → 500 → 1000 VUs
- **예상 결과**: Error Rate < 5%, P95 < 1s
- **메트릭**: `optimistic_lock_conflicts`, `retry_attempts`

### order-create.js
- **목적**: Pessimistic Lock + 타임아웃 성능 측정
- **단계적 부하**: 100 → 500 → 1000 VUs
- **예상 결과**: Error Rate < 20%, P95 < 3.5s
- **메트릭**: `pessimistic_lock_timeouts`, `lock_wait_time`

### payment-process.js
- **목적**: Idempotency Key 중복 결제 방지 검증
- **부하**: 100 → 200 VUs
- **예상 결과**: 동일 키 3번 시도 시 1번만 성공
- **메트릭**: `idempotency_conflicts`, `duplicate_payments_prevented`

---

## 환경 변수

```bash
# BASE_URL 변경
k6 run -e BASE_URL=http://localhost:8080 scripts/balance-charge.js

# USER_ID 변경
k6 run -e USER_ID=1 scripts/balance-charge.js

# PRODUCT_ID 변경
k6 run -e PRODUCT_ID=1 scripts/order-create.js

# 모든 변수 설정
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e USER_ID=1 \
  -e PRODUCT_ID=1 \
  scripts/order-create.js
```

---

## 결과 저장

### JSON 형식으로 저장

```bash
k6 run --out json=results/balance-charge-100.json scripts/balance-charge.js
```

### CSV 형식으로 저장

```bash
k6 run --out csv=results/balance-charge-100.csv scripts/balance-charge.js
```

### Summary 저장

```bash
k6 run --summary-export=results/summary.json scripts/balance-charge.js
```

---

## Before/After 비교

### Before (개선 전)

```bash
# 1. 개선 전 커밋으로 체크아웃
git checkout <before-commit-hash>

# 2. 빌드 및 실행
./gradlew clean build
./gradlew bootRun

# 3. 테스트 실행
k6 run --out json=results/before/balance-charge.json scripts/balance-charge.js
```

### After (개선 후)

```bash
# 1. 개선 후 커밋으로 체크아웃
git checkout <after-commit-hash>

# 2. 빌드 및 실행
./gradlew clean build
./gradlew bootRun

# 3. 테스트 실행
k6 run --out json=results/after/balance-charge.json scripts/balance-charge.js
```

---

## 예상 결과

### balance-charge.js (Optimistic Lock)

```
     ✓ status is 200
     ✓ response has balance
     ✓ balance increased correctly

     checks.........................: 100.00% ✓ 15000     ✗ 0
     errors.........................: 0.00%   ✓ 0         ✗ 15000
   ✓ http_req_duration..............: avg=600ms   p(95)=1s    p(99)=1.2s
     http_reqs......................: 15000   83.333/s
     iterations.....................: 5000    27.777/s
   ✓ optimistic_lock_conflicts......: 150     0.833/s
   ✓ success........................: 100.00% ✓ 5000      ✗ 0
     vus............................: 100→500→1000
```

### order-create.js (Pessimistic Lock)

```
     ✓ status is 200 or 201
     ✓ response has orderId

     checks.........................: 100.00% ✓ 10000     ✗ 0
     errors.........................: 10.00%  ✓ 500       ✗ 4500
   ✓ http_req_duration..............: avg=1.5s    p(95)=3s    p(99)=3.5s
     http_reqs......................: 5000    27.777/s
   ✓ lock_wait_time.................: avg=1.5s    p(95)=3s
   ✓ pessimistic_lock_timeouts......: 40      0.222/s
     vus............................: 100→500→1000
```

### payment-process.js (Idempotency Key)

```
     ✓ status is 200
     ✓ response has orderId

   ✓ duplicate_payments_prevented...: 10000   55.555/s
     errors.........................: 0.00%   ✓ 0         ✗ 5000
   ✓ idempotency_conflicts..........: 10000   55.555/s
   ✓ idempotency_verification_success: 5000   27.777/s
     success........................: 100.00% ✓ 5000      ✗ 0
     vus............................: 100→200
```

---

## Lock Contention 분석

### 임계점 파악

| VUs | TPS | Error Rate | P95 Latency | Lock Timeouts |
|-----|-----|------------|-------------|---------------|
| 100 | 90 | 0% | 600ms | 0 |
| 200 | 160 | 2% | 800ms | 5 |
| 300 | 210 | 5% | 1.2s | 15 |
| 500 | 280 | 10% | 1.8s | 40 |
| 1000 | 350 | 20% | 3s | 150 |

**결론**:
- ✅ **최적 부하**: 200 VUs (Error 2%)
- ⚠️ **경고 구간**: 300-500 VUs (Error 5-10%)
- ❌ **과부하**: 1000 VUs (Error 20%)

---

## MySQL Monitoring

테스트 실행 중 MySQL에서 Lock 상황 모니터링:

```sql
-- Lock Wait 확인
SELECT * FROM performance_schema.data_lock_waits;

-- Lock 대기 시간 확인
SELECT
    ROUND(AVG(TIMER_WAIT) / 1000000000, 2) AS avg_wait_seconds,
    ROUND(MAX(TIMER_WAIT) / 1000000000, 2) AS max_wait_seconds,
    COUNT(*) AS total_waits
FROM performance_schema.events_waits_history_long
WHERE EVENT_NAME LIKE 'wait/lock%';
```

---

## 트러블슈팅

### Connection Refused
- 애플리케이션이 실행 중인지 확인
- `./gradlew bootRun` 실행

### Out of Memory
- K6 VUs 줄이기 (1000 → 500)
- JVM 힙 메모리 증가: `-Xmx2g`

### 테스트 결과가 이상함
- 데이터베이스 초기 상태 확인
- 이전 테스트의 잔여 데이터 정리

---

## 참고 자료

- [K6_LOAD_TEST_GUIDE.md](../K6_LOAD_TEST_GUIDE.md) - 상세 가이드
- [K6 공식 문서](https://k6.io/docs/)
- docs/STEP9-10_COACH_FEEDBACK_IMPROVEMENTS.md
