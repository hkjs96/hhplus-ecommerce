# STEP13-14 K6 부하 테스트 실행 가이드

## 🚀 Quick Start (현재 상태)

### ✅ 완료된 작업

#### Ranking 부하 테스트 (step13-ranking-improved-test.js)
- **상태**: ✅ 완료 및 검증 완료
- **실행 결과**: 42,836 iterations (기존 대비 63배 증가), 9/10 threshold 통과
- **실행 명령어**:
```bash
cd /Users/jsb/hanghe-plus/ecommerce
k6 run docs/week7/loadtest/k6/step13-ranking-improved-test.js
```

### 🔧 다음 단계: Coupon 동시성 테스트

#### 코드 수정 완료
- ✅ userId 타입 에러 수정 (String → Long)
- ✅ 모든 시나리오 함수 수정 완료

#### 실행 방법

**Step 1: 애플리케이션 실행 (자동 데이터 생성)**
```bash
# 애플리케이션 시작 시 테스트 사용자가 자동으로 생성됩니다
cd /Users/jsb/hanghe-plus/ecommerce
./gradlew bootRun

# 로그에서 확인:
# === K6 Load Test Data Initializer START ===
# Created 20100 new test users in XXXms
# === K6 Load Test Data Initializer END ===
```

**자동 생성되는 데이터**:
- extremeConcurrency: 10,000명 (userId 1000-10999)
- sequentialIssue: 100명 (userId 200000-200099) ✅ **수정됨** (기존 50명에서 확대)
- rampUpTest: 10,000명 (userId 300000-309999) ✅ **수정됨** (기존 5,000명에서 확대)
- **총 20,100명** (이미 존재하면 skip)

**Step 2: 쿠폰 ID 확인**
```bash
# DB에서 실제 쿠폰 ID 확인 (숫자여야 함)
mysql -h localhost -u root -p ecommerce

SELECT id, name, total_quantity, issued_quantity FROM coupons LIMIT 5;
```

**Step 3: 테스트 실행**
```bash
cd /Users/jsb/hanghe-plus/ecommerce

# 예시: COUPON_ID=1
./docs/week7/loadtest/k6/run-test.sh coupon 1

# 또는 k6 직접 실행
k6 run -e COUPON_ID=1 docs/week7/loadtest/k6/step14-coupon-concurrency-test.js
```

**검증 포인트**:
- `actual_issued_count` = 정확히 100개
- `duplicate_issue_attempts` = 0
- `sold_out_responses` ≈ 100개

**⚠️ 중요: 쿠폰 초기 데이터**
- Coupon ID 1 (WELCOME10): 총 100개 생성
- **사전 발급 없음** (K6 테스트를 위해 100개 전체 확보)
- 애플리케이션 재시작 시 자동으로 초기화됨

---

## 📋 개요

Week 7 과제의 두 가지 핵심 기능에 대한 K6 부하 테스트 계획입니다.

- **STEP 13**: Redis Sorted Set 기반 실시간 상품 랭킹 시스템 ✅
- **STEP 14**: Redis INCR 기반 선착순 쿠폰 예약 시스템 🔧

---

## 🎯 테스트 목표

### STEP 13: 실시간 상품 랭킹 시스템

#### 목표
- Redis ZINCRBY 원자성 검증 (동시 주문 시 정확한 score 증가)
- 랭킹 조회 성능 측정 (Top 5 조회)
- 동시 주문 처리 시 랭킹 정확성 검증
- Redis Sorted Set 읽기/쓰기 성능

#### 성능 목표
- 랭킹 조회: p95 < 50ms
- 주문 생성 (랭킹 업데이트 포함): p95 < 500ms
- 동시 100건 주문 시 랭킹 정확성: 100%

### STEP 14: 선착순 쿠폰 예약 시스템

#### 목표
- Redis INCR 원자성 검증 (1000명 동시 요청 → 100개만 성공)
- 중복 예약 방지 (같은 사용자 다중 요청 → 1개만 성공)
- 예약 → 이벤트 → 발급 전체 플로우 성능
- Connection Pool 고갈 방지 확인

#### 성능 목표
- 쿠폰 예약: p95 < 200ms
- 선착순 정확성: 100% (정확히 지정된 수량만 성공)
- 중복 방지: 100% (같은 사용자 1개만)
- 실패율: 예상 실패율 준수 (900/1000 = 90%)

---

## 📂 테스트 스크립트 구조

```
docs/week7/loadtest/k6/
├── step13-ranking-load-test.js          # STEP 13: 랭킹 시스템 부하 테스트
├── step14-reservation-concurrency.js    # STEP 14: 예약 동시성 테스트
├── step13-14-integration-test.js        # 통합 시나리오 테스트
├── common/
│   ├── config.js                        # 공통 설정
│   ├── metrics.js                       # 커스텀 메트릭 정의
│   └── test-data.js                     # 테스트 데이터 생성
└── results/
    ├── step13-ranking-results.json
    ├── step14-reservation-results.json
    └── integration-results.json
```

---

## 🧪 STEP 13: 실시간 상품 랭킹 테스트

### 테스트 시나리오

#### Scenario 1: 랭킹 조회 성능 테스트
```javascript
{
    executor: 'constant-arrival-rate',
    rate: 100,              // 초당 100 요청
    timeUnit: '1s',
    duration: '1m',
    preAllocatedVUs: 50,
    maxVUs: 100,
    exec: 'getRanking',
}
```

**검증 항목:**
- GET `/api/products/ranking` 응답 시간
- Top 5 상품 조회 정확성
- Redis Sorted Set ZREVRANGE 성능

**예상 결과:**
- p50: 10ms
- p95: 50ms
- p99: 100ms

---

#### Scenario 2: 동시 주문 생성 + 랭킹 업데이트
```javascript
{
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
        { duration: '30s', target: 50 },   // 50명까지 증가
        { duration: '1m', target: 50 },    // 50명 유지
        { duration: '30s', target: 100 },  // 100명까지 증가
        { duration: '1m', target: 100 },   // 100명 유지
        { duration: '30s', target: 0 },    // 종료
    ],
    exec: 'createOrderWithRanking',
}
```

**플로우:**
1. POST `/api/orders` - 주문 생성
2. POST `/api/orders/{orderId}/payment` - 결제 처리
3. Event Listener 실행 → Redis ZINCRBY
4. GET `/api/products/ranking` - 랭킹 확인

**검증 항목:**
- 주문 생성 성공률: 95% 이상
- Redis ZINCRBY 원자성 (score 정확성)
- 랭킹 순위 정확성 (판매량 순서)
- Event 처리 지연 시간

**예상 결과:**
- 주문 생성: p95 < 500ms
- 랭킹 업데이트: 비동기 처리 (3초 이내)
- 최종 랭킹 정확성: 100%

---

#### Scenario 3: 랭킹 정확성 검증
```javascript
{
    executor: 'shared-iterations',
    vus: 100,
    iterations: 100,
    maxDuration: '3m',
    exec: 'verifyRankingAccuracy',
}
```

**플로우:**
1. 100명이 특정 상품 100개 주문
2. 모든 주문 완료 대기 (3초)
3. 랭킹 조회
4. 해당 상품 score = 100인지 검증

**검증 항목:**
- Redis ZINCRBY 원자성 (누락/중복 없음)
- score 값 = 실제 주문 수량

---

## 🎫 STEP 14: 선착순 쿠폰 예약 테스트

### 테스트 시나리오

#### Scenario 1: 선착순 1000명 → 100개 성공
```javascript
{
    executor: 'shared-iterations',
    vus: 100,               // Thread Pool 크기 (Connection Pool 고려)
    iterations: 1000,       // 총 1000건 요청
    maxDuration: '2m',
    exec: 'reservationConcurrency',
}
```

**플로우:**
1. POST `/api/coupons/{couponId}/reserve` - 예약 요청
2. 결과 수집 (성공/실패/에러 타입)

**검증 항목:**
- 성공: 정확히 100건 (200 OK)
- 실패: 정확히 900건 (409 SOLD_OUT)
- Redis INCR 원자성 (sequence 1~1000)
- Connection Pool 고갈 없음

**커스텀 메트릭:**
```javascript
const reservationSuccessCount = new Counter('reservation_success_count');
const reservationSoldOutCount = new Counter('reservation_sold_out_count');
const reservationDuplicateCount = new Counter('reservation_duplicate_count');
const sequenceAccuracy = new Rate('sequence_accuracy_rate');
```

**예상 결과:**
- 성공률: 10% (100/1000)
- 실패율: 90% (900/1000)
- 응답 시간: p95 < 200ms
- Redis Sequence: 1000 (정확히 증가)

---

#### Scenario 2: 중복 예약 방지 테스트
```javascript
{
    executor: 'per-vu-iterations',
    vus: 1,                 // 같은 사용자
    iterations: 10,         // 10번 시도
    maxDuration: '30s',
    exec: 'duplicateReservationAttempt',
    startTime: '2m30s',     // Scenario 1 이후 실행
}
```

**플로우:**
1. 같은 userId로 10번 예약 요청
2. 첫 번째: 성공 또는 SOLD_OUT
3. 나머지 9번: ALREADY_ISSUED (409)

**검증 항목:**
- 성공: 1건 이하 (재고 있으면 1건, 없으면 0건)
- 중복 차단: 9건 (409 ALREADY_ISSUED)
- DB Unique Constraint 작동

**예상 결과:**
- 중복 차단율: 90% (9/10)
- 응답 시간: p95 < 100ms (빠른 실패)

---

#### Scenario 3: 예약 → 발급 전체 플로우 검증
```javascript
{
    executor: 'constant-vus',
    vus: 10,
    duration: '1m',
    exec: 'reservationIssuanceFlow',
    startTime: '3m',
}
```

**플로우:**
1. POST `/api/coupons/{couponId}/reserve` - 예약
2. 3초 대기 (Event Listener 처리)
3. GET `/api/users/{userId}/coupons` - 발급 확인
4. 검증: UserCoupon 존재, status=AVAILABLE

**검증 항목:**
- 예약 성공 → 실제 발급 완료율: 100%
- Event Listener 처리 시간: 3초 이내
- CouponReservation.status = ISSUED
- Coupon.issuedQuantity 증가

**예상 결과:**
- 전체 플로우 성공률: 95% 이상
- Event 처리 시간: p95 < 2s
- 데이터 정합성: 100%

---

## 🔗 통합 시나리오 테스트

### 실전 시뮬레이션: 주문 + 랭킹 + 쿠폰

```javascript
export const options = {
    scenarios: {
        realistic_user_flow: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m', target: 50 },   // 워밍업
                { duration: '3m', target: 50 },   // 안정화
                { duration: '1m', target: 100 },  // 피크
                { duration: '2m', target: 100 },  // 피크 유지
                { duration: '1m', target: 0 },    // 종료
            ],
            exec: 'realisticUserFlow',
        },
    },
};
```

**플로우:**
1. GET `/api/products/ranking` - 인기 상품 조회
2. POST `/api/coupons/{couponId}/reserve` - 쿠폰 예약
3. POST `/api/orders` - 주문 생성 (쿠폰 적용)
4. POST `/api/orders/{orderId}/payment` - 결제
5. Event: 랭킹 업데이트 + 쿠폰 발급
6. GET `/api/products/ranking` - 랭킹 재조회

**검증 항목:**
- 전체 플로우 성공률: 90% 이상
- 쿠폰 재고 관리 정확성
- 랭킹 실시간 반영
- Redis 부하 처리 능력

---

## 📊 메트릭 수집 및 분석

### 커스텀 메트릭

#### STEP 13: 랭킹 시스템
```javascript
// docs/week7/loadtest/k6/common/metrics.js
import { Counter, Rate, Trend } from 'k6/metrics';

// 랭킹 조회 메트릭
export const rankingQueryDuration = new Trend('ranking_query_duration');
export const rankingQuerySuccessRate = new Rate('ranking_query_success_rate');

// 랭킹 업데이트 메트릭
export const rankingUpdateDuration = new Trend('ranking_update_duration');
export const rankingAccuracyRate = new Rate('ranking_accuracy_rate');

// Redis ZINCRBY 메트릭
export const zincrbyOperationCount = new Counter('zincrby_operation_count');
```

#### STEP 14: 쿠폰 예약
```javascript
// 예약 결과 메트릭
export const reservationSuccessCount = new Counter('reservation_success_count');
export const reservationSoldOutCount = new Counter('reservation_sold_out_count');
export const reservationDuplicateCount = new Counter('reservation_duplicate_count');
export const reservationErrorCount = new Counter('reservation_error_count');

// 정확성 메트릭
export const sequenceAccuracyRate = new Rate('sequence_accuracy_rate');
export const duplicatePreventionRate = new Rate('duplicate_prevention_rate');

// 성능 메트릭
export const reservationDuration = new Trend('reservation_duration');
export const issuanceDuration = new Trend('issuance_duration');
```

### Thresholds (성공 기준)

```javascript
export const options = {
    thresholds: {
        // STEP 13: 랭킹 시스템
        'ranking_query_duration': ['p(95)<50', 'p(99)<100'],
        'ranking_query_success_rate': ['rate>0.99'],
        'ranking_accuracy_rate': ['rate==1.0'],  // 100% 정확성

        // STEP 14: 쿠폰 예약
        'reservation_duration': ['p(95)<200', 'p(99)<500'],
        'sequence_accuracy_rate': ['rate==1.0'],
        'duplicate_prevention_rate': ['rate>0.95'],

        // 전체 HTTP 메트릭
        'http_req_duration': ['p(95)<1000', 'p(99)<2000'],
        'http_req_failed': ['rate<0.1'],  // 비즈니스 실패 제외
    },
};
```

---

## 🛠️ 테스트 실행 가이드

### 사전 준비

#### 1. 애플리케이션 시작
```bash
# Redis 시작
docker-compose up -d redis

# MySQL 시작
docker-compose up -d mysql

# 애플리케이션 시작 (clean state)
./gradlew bootRun
```

#### 2. 테스트 데이터 준비
```bash
# 선착순 쿠폰 생성 (재고 100개)
curl -X POST http://localhost:8080/api/coupons \
  -H "Content-Type: application/json" \
  -d '{
    "couponCode": "STEP14-TEST-001",
    "name": "K6 부하 테스트 쿠폰",
    "discountRate": 10,
    "totalQuantity": 100,
    "startDate": "2024-01-01T00:00:00",
    "endDate": "2025-12-31T23:59:59"
  }'

# 반환된 couponId를 K6 스크립트에 설정
# TEST_COUPON_ID = <couponId>
```

#### 3. K6 설치
```bash
# macOS
brew install k6

# 또는 Docker
docker pull grafana/k6
```

---

### 테스트 실행

#### STEP 13: 랭킹 시스템 테스트
```bash
# 기본 실행
k6 run docs/week7/loadtest/k6/step13-ranking-load-test.js

# 결과를 JSON으로 저장
k6 run --out json=docs/week7/loadtest/k6/results/step13-ranking-results.json \
    docs/week7/loadtest/k6/step13-ranking-load-test.js

# 환경 변수 설정
k6 run -e BASE_URL=http://localhost:8080 \
       -e TEST_DURATION=2m \
       docs/week7/loadtest/k6/step13-ranking-load-test.js
```

#### STEP 14: 쿠폰 예약 테스트
```bash
# 기본 실행
k6 run docs/week7/loadtest/k6/step14-reservation-concurrency.js

# 쿠폰 ID 지정
k6 run -e COUPON_ID=<couponId> \
       docs/week7/loadtest/k6/step14-reservation-concurrency.js

# 동시 사용자 수 조정
k6 run -e VUS=100 -e ITERATIONS=1000 \
       docs/week7/loadtest/k6/step14-reservation-concurrency.js
```

#### 통합 테스트
```bash
k6 run docs/week7/loadtest/k6/step13-14-integration-test.js
```

---

### 결과 분석

#### 1. 콘솔 출력
```
     ✓ ranking query successful
     ✓ ranking score accurate
     ✓ reservation exactly 100 succeeded
     ✓ reservation exactly 900 failed (SOLD_OUT)

     checks.........................: 100.00% ✓ 1000  ✗ 0
     data_received..................: 1.2 MB  20 kB/s
     data_sent......................: 800 kB  13 kB/s
     http_req_duration..............: avg=150ms min=10ms med=120ms max=500ms p(95)=280ms
     ranking_query_duration.........: avg=25ms  min=8ms  med=22ms  max=80ms  p(95)=45ms
     reservation_duration...........: avg=180ms min=50ms med=160ms max=600ms p(95)=350ms
     reservation_success_count......: 100
     reservation_sold_out_count.....: 900
     sequence_accuracy_rate.........: 100.00% ✓ 1000  ✗ 0
```

#### 2. Redis 모니터링
```bash
# Redis 명령어 모니터링
redis-cli MONITOR

# Key 확인
redis-cli KEYS "ranking:product:*"
redis-cli KEYS "coupon:*:sequence"

# Sorted Set 확인
redis-cli ZREVRANGE "ranking:product:orders:daily:20241204" 0 4 WITHSCORES
```

#### 3. 애플리케이션 로그 확인
```bash
# 랭킹 업데이트 로그
grep "ZINCRBY" logs/application.log

# 쿠폰 예약 로그
grep "REDIS INCR" logs/application.log
grep "Coupon reserved" logs/application.log
```

---

## 🎯 성공 기준 (Pass/Fail)

### STEP 13: 랭킹 시스템

| 항목 | 목표 | Pass 기준 |
|------|------|-----------|
| 랭킹 조회 성능 | p95 < 50ms | ✅ |
| 랭킹 정확성 | 100% | Redis score = 실제 주문 수 |
| ZINCRBY 원자성 | 100% | 누락/중복 없음 |
| 주문 + 랭킹 업데이트 | p95 < 500ms | ✅ |

### STEP 14: 쿠폰 예약

| 항목 | 목표 | Pass 기준 |
|------|------|-----------|
| 선착순 정확성 | 100% | 1000명 → 정확히 100명 성공 |
| Redis INCR 원자성 | 100% | sequence 1~1000 (누락 없음) |
| 중복 방지 | 95% 이상 | 같은 사용자 1개만 |
| 예약 성능 | p95 < 200ms | ✅ |
| Event 처리 | p95 < 2s | 예약 → 발급 완료 |
| Connection Pool | 고갈 없음 | 에러율 < 5% |

### 통합 테스트

| 항목 | 목표 | Pass 기준 |
|------|------|-----------|
| 전체 플로우 성공률 | 90% 이상 | ✅ |
| 데이터 정합성 | 100% | Redis ↔ DB 일치 |
| 동시 처리 능력 | 100 VUs | 안정적 처리 |

---

## 📝 테스트 체크리스트

### 테스트 전
- [ ] Redis 컨테이너 실행 확인 (`redis-cli ping`)
- [ ] MySQL 컨테이너 실행 확인
- [ ] 애플리케이션 정상 시작 확인 (`curl http://localhost:8080/actuator/health`)
- [ ] 테스트 쿠폰 생성 완료 (couponId 기록)
- [ ] 기존 데이터 정리 (clean state)
- [ ] K6 설치 확인 (`k6 version`)

### 테스트 중
- [ ] Redis MONITOR 실행 (별도 터미널)
- [ ] 애플리케이션 로그 tail (`tail -f logs/application.log`)
- [ ] 시스템 리소스 모니터링 (htop, docker stats)

### 테스트 후
- [ ] 결과 JSON 파일 저장
- [ ] Redis 데이터 검증 (sequence, score)
- [ ] DB 데이터 검증 (CouponReservation, UserCoupon)
- [ ] 실패 케이스 분석
- [ ] 성능 병목 지점 식별

---

## 🚀 다음 단계

1. **K6 스크립트 작성**
   - `step13-ranking-load-test.js` 구현
   - `step14-reservation-concurrency.js` 구현
   - `step13-14-integration-test.js` 구현

2. **실행 및 결과 수집**
   - 각 테스트 3회 이상 실행
   - 결과 일관성 확인
   - Edge case 테스트 추가

3. **문서화**
   - 테스트 결과 보고서 작성
   - 성능 개선 제안
   - 알려진 이슈 정리

4. **CI/CD 통합**
   - GitHub Actions에 K6 테스트 추가
   - 성능 회귀 자동 감지
   - 결과 자동 리포팅

---

## 📚 참고 자료

- [K6 공식 문서](https://k6.io/docs/)
- [Redis ZINCRBY 문서](https://redis.io/commands/zincrby/)
- [Redis INCR 문서](https://redis.io/commands/incr/)
- Week 6 K6 테스트: `docs/week6/loadtest/k6/`
- STEP13-14 설계 문서: `docs/week7/COUPON_RESERVATION_DESIGN.md`
