# Performance Improvements - 2025-12-07

## 🚨 발견된 문제점

### 1. Connection Pool 완전 고갈
```
HikariPool-1 - Connection is not available
request timed out after 30004ms
total=50, active=50, idle=0, waiting=51
```

### 2. K6 테스트 실패
- http_req_failed: 49.74% (threshold: <10%)
- order created: 6% 성공률
- payment: 17% 성공률
- dropped_iterations: 3,201건

### 3. 응답 시간 폭발
- ranking_query: p(95)=30초 (threshold: 50ms) → **600배 초과**
- ranking_update: p(95)=60초 (threshold: 500ms) → **120배 초과**
- http_req_duration: avg=33.93초, max=60초

### 4. 랭킹 정확성 실패
- ranking_accuracy_rate: 3.57% (threshold: >95%)

---

## ✅ 적용된 개선 사항

### 1. HikariCP Connection Pool 증가
```yaml
# Before
maximum-pool-size: 50
minimum-idle: 10
connection-timeout: 30000  # 30초

# After
maximum-pool-size: 200     # 4배 증가
minimum-idle: 50           # 5배 증가
connection-timeout: 10000  # 10초 (빠른 실패)
leak-detection-threshold: 30000  # 누수 감지
```

**근거:**
- K6 테스트: 최대 350 VUs
- 동시 요청: ~200-300개
- 여유 확보: 200개 connection

### 2. K6 HTTP Timeout 감소
```javascript
// Before
기본 timeout: 30초 (k6 default)

// After
timeout: '5s'  // 모든 HTTP 요청
```

**근거:**
- 30초는 너무 길어서 실패 감지가 늦음
- Connection Pool 고갈 악화
- 5초면 충분 (정상 응답: <100ms)

### 3. K6 테스트 부하 조정 권장사항

#### 현재 설정 (과부하)
```javascript
getRanking: {
  rate: 60,           // 초당 60 요청
  maxVUs: 200,
}
createOrderWithRanking: {
  peak: 100 VUs,      // 최대 100명 동시
  duration: 3.5분
}
verifyRankingAccuracy: {
  vus: 100,
  iterations: 100
}
// Total: 최대 350 VUs 동시 실행
```

#### 권장 설정 (단계별 증가)
```javascript
// Phase 1: 기본 동작 검증 (Connection Pool 200 기준)
getRanking: {
  rate: 30,           // 50% 감소
  maxVUs: 100,
}
createOrderWithRanking: {
  peak: 50 VUs,       // 50% 감소
}
verifyRankingAccuracy: {
  vus: 50,            // 50% 감소
  iterations: 50
}
// Total: 최대 175 VUs

// Phase 2: 통과하면 점진적 증가
// Phase 3: 최종 목표 (350 VUs)
```

---

## 🔧 추가 수정 사항 (2025-12-07 v2)

### 4. LoadTestDataInitializer - userId 1 생성 추가
```java
// Before
// userId 1이 생성되지 않음 → K6 setup 실패

// After
// 0. K6 기본 테스트 사용자 (userId: 1) - config.js의 기본값
totalCreated += createUsersIfNotExist(1, 1, "K6Test-Default");
// userId 1에게 100,000,000원 잔액 부여
```

**근거:**
- K6 config.js는 `userId: 1`을 기본값으로 사용
- LoadTestDataInitializer가 userId 1000+만 생성하여 setup 실패
- DataInitializer가 User 1 생성하지만, 충돌 방지 로직 포함

---

## 🔧 추가 수정 사항 (2025-12-07 v3)

### 5. LoadTestDataInitializer - userId 1 잔액 대폭 증가
```java
// Before (v2)
long balance = (id == 1) ? 100_000_000L : 10_000L;  // 1억원
// 문제: 3.5분 테스트 중 초반에 소진, payment 실패율 27.87%

// After (v3)
long balance = (id == 1) ? 20_000_000_000L : 10_000L;  // 200억원
// K6 테스트: ~10,000회 주문 × 평균 1,350,000원 = 13,500,000,000원 필요
```

**근거:**
- K6 테스트 3.5분 동안 10,203회 iteration 실행
- 평균 주문 금액: 1,350,000원 (상품 가격 기준)
- 필요 총액: ~13,500,000,000원 (135억원)
- 여유 확보: 200억원 설정

---

## 🎯 다음 단계

### 1. 애플리케이션 재시작 (필수!)
```bash
# 중요: ddl-auto: create가 테이블을 재생성하고
# LoadTestDataInitializer가 userId 1을 생성하도록 재시작 필요
./gradlew bootRun
```

**또는 Redis 초기화 포함 재시작:**
```bash
./gradlew bootRunRedisReset
```

### 2. 부하 감소된 테스트 실행
```bash
k6 run \
  -e RANKING_RATE=30 \
  -e RANKING_MAX_VUS=100 \
  -e ORDER_PEAK_VUS=50 \
  docs/week7/loadtest/k6/step13-ranking-load-test.js
```

### 3. 모니터링 포인트
- HikariCP 상태 (`active`, `idle`, `waiting`)
- HTTP 응답 시간 (p95 < 100ms)
- 실패율 (< 10%)
- Connection timeout 발생 여부

### 4. 성공 기준
- ✅ http_req_failed < 10%
- ✅ ranking_query p(95) < 50ms
- ✅ ranking_update p(95) < 500ms
- ✅ ranking_accuracy > 95%
- ✅ dropped_iterations < 100

---

## 📊 예상 결과

### Before v1 (첫 테스트 - Connection Pool 부족)
```
✗ http_req_failed: 49.74%
✗ ranking_query p(95): 30,065ms (600배 초과!)
✗ ranking_update p(95): 60,000ms (120배 초과!)
✗ ranking_accuracy: 3.57%
✗ dropped_iterations: 3,201
- HikariPool exhausted (total=50, active=50, idle=0, waiting=51)
```

### After v1 (Connection Pool 개선 후 - User 누락)
```
✗ http_req_failed: 95.76% (악화!)
✅ ranking_query p(95): 46.5ms (✓ 개선!)
✅ ranking_update p(95): 267ms (✓ 개선!)
✗ ranking_accuracy: 0.00%
✗ order created status: 0%
- 원인: "사용자를 찾을 수 없습니다. userId: 1"
- setup phase 실패: initial balance charged: 0%, coupon issued: 0%
```

### After v2 (userId 1 생성 추가 - 잔액 부족)
```
✅ ranking_query p(95): 18.12ms (✓ 개선!)
✅ ranking_update p(95): 81.14ms (✓ 개선!)
✅ ranking_accuracy: 100.00% (완벽!)
✅ order created status: 정상
✗ http_req_failed: 27.87% (여전히 높음)
✗ payment status 200: 1% (77 / 6526 실패)
- 원인: userId 1 잔액 1억원 부족 (135억원 필요)
```

### After v3 (잔액 200억원 증가 - 예상)
```
✅ http_req_failed: < 1% (잔액 충분)
✅ payment status 200: > 99%
✅ ranking_query p(95): < 50ms
✅ ranking_update p(95): < 500ms
✅ ranking_accuracy: > 95%
✅ dropped_iterations: < 10
✅ order created status: > 99%
```

---

## 🔧 추가 개선 가능 항목 (필요 시)

### 1. MySQL Connection Limit 증가
```sql
-- MySQL 설정 확인
SHOW VARIABLES LIKE 'max_connections';

-- 기본 151 → 300 증가
SET GLOBAL max_connections = 300;
```

### 2. JPA Batch Size 최적화
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50  # INSERT batch
        order_inserts: true
        order_updates: true
```

### 3. Redis Connection Pool
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 50  # 10 → 50
          max-idle: 30    # 10 → 30
          min-idle: 10    # 2 → 10
```

### 4. 비동기 처리 Thread Pool
```yaml
# 별도 설정 필요 시
spring:
  task:
    execution:
      pool:
        core-size: 20
        max-size: 50
        queue-capacity: 100
```
