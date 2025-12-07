# K6 Load Testing Suite

## 개요

이 디렉토리는 Week 7 과제(Redis 기반 랭킹 시스템 및 선착순 쿠폰 발급)의 부하 테스트를 포함합니다.

---

## 테스트 파일

### 1. step13-ranking-improved-test.js
**개선된 랭킹 시스템 부하 테스트**

#### 특징
- 읽기/쓰기 시나리오 명확히 분리
- 현실적인 임계값 설정
- 상세한 정확도 검증
- 점진적인 부하 증가

#### 시나리오
1. **Ranking Read Test** (4분)
   - 읽기 집중 워크로드
   - 0 → 20 → 50 → 0 VUs
   - 랭킹 조회 성능 측정

2. **Ranking Write Test** (4분)
   - 쓰기 집중 워크로드
   - 0 → 5 → 15 → 0 VUs
   - 주문/결제로 인한 랭킹 업데이트
   - 10초 지연 시작

3. **Accuracy Test** (2분)
   - 50 VUs, 50 iterations
   - 주문 후 랭킹 정확도 검증
   - 30초 지연 시작

#### 실행
```bash
# 기본 실행
k6 run docs/week7/loadtest/k6/step13-ranking-improved-test.js

# 결과 저장
k6 run --out json=results/ranking-improved-$(date +%Y%m%d-%H%M%S).json \
  docs/week7/loadtest/k6/step13-ranking-improved-test.js
```

#### 성공 기준
- ✅ ranking_query_duration: p95 < 200ms, p99 < 500ms
- ✅ ranking_update_duration: p95 < 1s, p99 < 2s
- ✅ ranking_accuracy: > 90%
- ✅ order_success_rate: > 85%
- ✅ payment_success_rate: > 85%
- ✅ http_req_failed{scenario:read}: < 5%
- ✅ http_req_failed{scenario:write}: < 15%

---

### 2. step14-coupon-concurrency-test.js
**선착순 쿠폰 발급 동시성 테스트**

#### 특징
- 극한 동시성 검증 (100 VUs 동시 요청)
- 정확한 수량 제어 검증
- 중복 발급 방지 검증
- 다양한 부하 패턴 (극한/순차/램프업)

#### 시나리오
1. **Extreme Concurrency** (30초)
   - 100 VUs가 동시에 100번 시도
   - Race Condition 극한 테스트
   - 정확히 50개만 발급되어야 함

2. **Sequential Issue** (1분)
   - 1 VU가 순차적으로 50번 시도
   - 비교 기준선 확보
   - 35초 지연 시작

3. **Ramp Up Test** (40초)
   - 점진적 부하 증가 (0 → 20 → 50 → 0)
   - 현실적인 패턴
   - 1분 30초 지연 시작

#### 실행
```bash
# 쿠폰 ID 지정하여 실행
k6 run -e COUPON_ID=test-coupon-001 \
  docs/week7/loadtest/k6/step14-coupon-concurrency-test.js

# 결과 저장
k6 run -e COUPON_ID=test-coupon-001 \
  --out json=results/coupon-$(date +%Y%m%d-%H%M%S).json \
  docs/week7/loadtest/k6/step14-coupon-concurrency-test.js
```

#### 성공 기준
- ✅ coupon_issue_success_rate: 30-60% (100명 중 50명)
- ✅ coupon_issue_duration: p95 < 1s, p99 < 2s
- ✅ http_req_failed: < 10%
- ✅ http_req_duration{test:extreme}: p99 < 3s
- ⭐ **actual_issued_count: 정확히 50개**
- ⭐ **duplicate_issue_attempts: 0개**

---

### 3. step13-ranking-load-test.js
**기존 랭킹 테스트 (레거시)**

이전 버전의 테스트입니다. 비교 목적으로 보관되어 있습니다.

---

## 공통 파일

### common/config.js
공통 설정 (BASE_URL, PRODUCT_ID, USER_ID)

### common/metrics.js
커스텀 메트릭 정의

### common/test-data.js
테스트 데이터 생성 함수

---

## 빠른 시작

### 1. 사전 준비
```bash
# 애플리케이션 실행
./gradlew bootRun

# Redis 확인
redis-cli ping  # PONG 응답 확인
```

### 2. 테스트 실행
```bash
# 개선된 랭킹 테스트
k6 run docs/week7/loadtest/k6/step13-ranking-improved-test.js

# 쿠폰 동시성 테스트
k6 run -e COUPON_ID=<your-coupon-id> \
  docs/week7/loadtest/k6/step14-coupon-concurrency-test.js
```

### 3. 결과 확인
```bash
# 테스트 콘솔 출력 확인
# - checks: 통과한 검증 항목
# - 커스텀 메트릭: ranking_accuracy, actual_issued_count 등
# - HTTP 메트릭: http_req_duration, http_req_failed 등

# Redis 확인
redis-cli ZREVRANGE "ranking:product:orders:daily:<date>" 0 4 WITHSCORES
redis-cli GET "coupon:<coupon-id>:remain"
```

---

## 문제 해결

### 높은 실패율
**증상**: http_req_failed > 20%

**원인**:
- 서버 과부하
- 데이터베이스 연결 풀 부족
- 재고/잔액 부족

**해결**:
1. 애플리케이션 로그 확인: `tail -f logs/application.log`
2. 리소스 모니터링: `top -o cpu` or `docker stats`
3. connection pool 증가: `application.yml`에서 `hikari.maximum-pool-size` 조정
4. VU 수 감소 또는 램프업 시간 증가

### 느린 응답 시간
**증상**: p95 > 1s

**원인**:
- Redis 연결 문제
- 네트워크 지연
- GC 문제

**해결**:
1. Redis 레이턴시 확인: `redis-cli --latency`
2. JVM 힙 크기 증가: `-Xmx2g`
3. Redis 연결 풀 증가

### 쿠폰 수량 불일치
**증상**: actual_issued_count ≠ 50

**원인**:
- Race Condition (동시성 제어 실패)
- Lua 스크립트 오류
- 롤백 로직 문제

**해결**:
1. Redis 데이터 직접 확인:
   ```bash
   redis-cli GET "coupon:<id>:remain"
   redis-cli SCARD "coupon:<id>:issued"
   ```
2. 데이터베이스 확인:
   ```sql
   SELECT COUNT(*) FROM user_coupons WHERE coupon_id = '<id>';
   ```
3. 애플리케이션 로그에서 에러 확인

---

## 기존 테스트와의 차이점

### 개선 사항

| 항목 | 기존 | 개선 |
|------|------|------|
| **Thresholds** | p95 < 50ms (너무 엄격) | p95 < 200ms (현실적) |
| **시나리오 분리** | 혼재 | 읽기/쓰기 명확히 분리 |
| **VU 수** | 최대 220 (과부하) | 최대 65 (안정적) |
| **검증 로직** | 단순 status 체크 | 상세한 accuracy 검증 |
| **에러 처리** | 모든 실패 동일 취급 | 비즈니스 실패 구분 |
| **문서화** | 최소한 | 상세한 주석 및 설명 |

### 추가된 기능
- ✅ **setup/teardown**: 테스트 전후 로그
- ✅ **그룹화**: 시나리오별 명확한 구분
- ✅ **커스텀 메트릭**: 비즈니스 로직 검증
- ✅ **명확한 threshold**: 각 시나리오별 다른 기준
- ✅ **쿠폰 테스트**: 새로 추가

---

## 메트릭 설명

### 기본 HTTP 메트릭
- `http_req_duration`: 요청부터 응답까지 전체 시간
- `http_req_waiting`: 서버 처리 시간
- `http_req_failed`: 실패율 (4xx, 5xx)
- `http_reqs`: 총 요청 수
- `data_received/sent`: 네트워크 트래픽

### 랭킹 테스트 커스텀 메트릭
- `ranking_query_duration`: 랭킹 조회 API 응답 시간
- `ranking_update_duration`: 주문+결제 전체 시간
- `ranking_accuracy`: 랭킹 정확도 비율 (0.0-1.0)
- `order_success_rate`: 주문 생성 성공률
- `payment_success_rate`: 결제 성공률
- `zincrby_calls`: ZINCRBY 호출 횟수 (결제 성공 수)

### 쿠폰 테스트 커스텀 메트릭
- `coupon_issue_success_rate`: 발급 성공률
- `coupon_issue_duration`: 발급 요청 응답 시간
- `actual_issued_count`: 실제 발급된 쿠폰 수 ⭐
- `sold_out_responses`: 품절 응답 수
- `duplicate_issue_attempts`: 중복 시도 횟수 ⭐

---

## 참고 자료

- [K6 Documentation](https://k6.io/docs/)
- [K6 Executors](https://k6.io/docs/using-k6/scenarios/executors/)
- [K6 Thresholds](https://k6.io/docs/using-k6/thresholds/)
- [K6 Metrics](https://k6.io/docs/using-k6/metrics/)
- [Redis Sorted Sets](https://redis.io/docs/data-types/sorted-sets/)
- [Redis INCR](https://redis.io/commands/incr/)

---

**마지막 업데이트**: 2025-12-05
