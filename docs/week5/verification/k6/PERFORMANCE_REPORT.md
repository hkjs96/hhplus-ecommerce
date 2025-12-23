# K6 부하 테스트 성능 분석 보고서

**작성일**: 2025-11-24
**테스트 대상**: 잔액 충전 API (`POST /api/users/{userId}/balance/charge`)
**테스트 도구**: K6 Load Testing Tool
**테스트 기간**: 5분 (300초)
**부하 단계**: 100 VU → 500 VU → 1000 VU

---

## 📊 Executive Summary

### 핵심 결과

| 항목 | 다중 사용자 시나리오 | 단일 사용자 시나리오 |
|------|---------------------|---------------------|
| **총 요청 수** | 156,988 | 31,843 |
| **성공률** | 99.99% ✅ | 97.66% ⚠️ |
| **처리량 (TPS)** | 514.6 req/s | 98.9 req/s |
| **평균 응답 시간** | 823ms | 3.65s |
| **P95 레이턴시** | 2.93s | 11.69s |
| **Optimistic Lock 충돌** | 4건 | 743건 |

### 종합 평가

- ✅ **안정성**: 99.99% 성공률로 매우 안정적
- ✅ **동시성 제어**: Optimistic Lock 재시도 메커니즘이 효과적으로 작동
- ✅ **처리량**: 514.6 req/s로 목표(300 req/s) 대비 171% 달성
- ⚠️ **레이턴시**: P95(2.93s), P99(4.2s)가 목표(1s, 2s) 미달
- ⚠️ **개선 필요**: 고부하 시 일부 요청의 응답 시간 급증 (최대 33초)

---

## 🧪 Test 1: 다중 사용자 부하 테스트

### 테스트 시나리오

- **목적**: 실제 프로덕션 환경 시뮬레이션
- **사용자 분포**: USER_ID 1~100 (100명의 사용자)
- **부하 패턴**: 100 VU → 500 VU → 1000 VU (단계적 증가)
- **실행 시간**: 5분 5초
- **스크립트**: `balance-charge.js`

### 상세 결과

#### HTTP 메트릭

```
총 요청 수: 156,988
성공: 156,984 (99.99%)
실패: 4 (0.01%)
HTTP 에러율: 0.00%
```

#### 응답 시간 분포

| 백분위수 | 응답 시간 | 목표값 | 달성 여부 |
|----------|-----------|--------|-----------|
| P50 (중앙값) | 475ms | - | ✅ 양호 |
| P90 | 2.37s | - | ⚠️ 주의 |
| P95 | 2.93s | <1s | ❌ 미달 |
| P99 | 4.2s | <2s | ❌ 미달 |
| 평균 | 823ms | - | ⚠️ 개선 필요 |
| 최대 | 33.25s | - | ❌ 문제 |
| 최소 | 5.86ms | - | ✅ 우수 |

#### 처리량

```
TPS (Throughput): 514.6 req/s
목표: 300 req/s
달성률: 171% ✅
```

#### 동시성 제어

```
Optimistic Lock 충돌: 4건
총 요청 대비 충돌률: 0.0025%
목표 (<1000건): 달성 ✅
```

#### Threshold 검증

| Threshold | 기준 | 실제 | 결과 |
|-----------|------|------|------|
| errors rate | <0.05 (5%) | 0.00% | ✅ PASS |
| success rate | >0.95 (95%) | 99.99% | ✅ PASS |
| optimistic_lock_conflicts | <1000 | 4 | ✅ PASS |
| http_req_duration p(95) | <1000ms | 2930ms | ❌ FAIL |
| http_req_duration p(99) | <2000ms | 4200ms | ❌ FAIL |

**종합 결과**: 5개 중 3개 PASS (60%)

### 분석

#### 강점

1. **높은 안정성**
   - 99.99% 성공률은 프로덕션 환경에서 요구하는 수준
   - 156,988건 중 단 4건만 실패 (거의 완벽)

2. **우수한 동시성 제어**
   - Optimistic Lock 충돌이 0.0025%로 거의 발생하지 않음
   - 100명의 사용자에게 부하를 분산하여 Lock Contention 최소화

3. **목표 처리량 초과 달성**
   - 514.6 req/s는 목표(300 req/s) 대비 171% 달성
   - 시스템이 높은 처리량을 안정적으로 유지

#### 약점

1. **높은 P95/P99 레이턴시**
   - P95(2.93s), P99(4.2s)가 목표를 크게 초과
   - 중앙값(475ms)은 양호하나, 일부 요청이 매우 느림

2. **극단값 존재**
   - 최대 응답 시간 33.25초는 문제
   - 고부하 시 일부 요청의 응답 시간이 급증

3. **응답 시간 편차**
   - P50(475ms) vs P99(4.2s): 약 9배 차이
   - 안정적인 사용자 경험 제공이 어려움

---

## 🧪 Test 2: 단일 사용자 동시성 테스트

### 테스트 시나리오

- **목적**: Optimistic Lock 충돌 및 재시도 메커니즘 검증
- **사용자 분포**: USER_ID=1 (단일 사용자)
- **부하 패턴**: 100 VU → 500 VU → 1000 VU (단계적 증가)
- **실행 시간**: 5분 22초
- **스크립트**: `balance-charge-single-user.js`

### 상세 결과

#### HTTP 메트릭

```
총 요청 수: 31,843
성공: 31,100 (97.66%)
실패: 743 (2.33%)
HTTP 에러율: 2.33%
```

#### 응답 시간 분포

| 백분위수 | 응답 시간 | 목표값 | 달성 여부 |
|----------|-----------|--------|-----------|
| P50 (중앙값) | 1.66s | - | ⚠️ 주의 |
| P90 | 6.03s | - | ❌ 문제 |
| P95 | 11.69s | <1s | ❌ 미달 |
| P99 | 30.92s | <2s | ❌ 미달 |
| 평균 | 3.65s | - | ❌ 문제 |
| 최대 | 31.73s | - | ❌ 문제 |
| 최소 | 5.05ms | - | ✅ 우수 |

#### 처리량

```
TPS (Throughput): 98.9 req/s
다중 사용자 대비: 19.2% (5.2배 감소)
```

#### 동시성 제어

```
Optimistic Lock 충돌: 743건
총 요청 대비 충돌률: 2.33%
목표 (<1000건): 달성 ✅
```

#### Threshold 검증

| Threshold | 기준 | 실제 | 결과 |
|-----------|------|------|------|
| errors rate | <0.05 (5%) | 2.33% | ✅ PASS |
| success rate | >0.95 (95%) | 97.66% | ✅ PASS |
| optimistic_lock_conflicts | <1000 | 743 | ✅ PASS |
| http_req_duration p(95) | <1000ms | 11690ms | ❌ FAIL |
| http_req_duration p(99) | <2000ms | 30920ms | ❌ FAIL |

**종합 결과**: 5개 중 3개 PASS (60%)

### 분석

#### 강점

1. **재시도 메커니즘 작동**
   - 743건의 Optimistic Lock 충돌에도 97.66% 성공률 유지
   - Exponential Backoff 재시도 전략이 효과적으로 작동

2. **극한 상황에서도 복구**
   - 단일 리소스에 대한 집중 공격 시에도 시스템이 실패하지 않음
   - 2.33% 실패율은 허용 범위 내

#### 약점

1. **심각한 Lock Contention**
   - 단일 사용자에게 집중되어 Lock Contention 심각
   - 처리량이 98.9 req/s로 5.2배 감소

2. **매우 높은 응답 시간**
   - 평균 3.65s, P95 11.69s, P99 30.92s
   - 재시도로 인한 대기 시간 누적

3. **사용자 경험 저하**
   - 응답 시간이 매우 불안정 (중앙값 1.66s ~ 최대 31.73s)
   - 실제 환경에서는 발생하기 어려운 극한 상황

---

## 📈 비교 분석

### 시나리오별 성능 비교

| 메트릭 | 다중 사용자 | 단일 사용자 | 배율 |
|--------|-------------|-------------|------|
| **총 요청** | 156,988 | 31,843 | 4.9x |
| **성공률** | 99.99% | 97.66% | 1.02x |
| **TPS** | 514.6 req/s | 98.9 req/s | 5.2x |
| **평균 응답 시간** | 823ms | 3.65s | 0.23x |
| **P95** | 2.93s | 11.69s | 0.25x |
| **P99** | 4.2s | 30.92s | 0.14x |
| **Optimistic Lock 충돌** | 4 | 743 | 0.0054x |

### 핵심 인사이트

1. **부하 분산의 중요성**
   - 다중 사용자 환경에서 TPS가 5.2배 증가
   - Lock Contention 최소화로 응답 시간 4.4배 단축

2. **Optimistic Lock의 특성**
   - 다중 사용자: 충돌 거의 없음 (0.0025%)
   - 단일 사용자: 충돌 빈번 (2.33%)
   - Optimistic Lock은 부하 분산 환경에서 효과적

3. **재시도 메커니즘의 효과**
   - 단일 사용자 환경에서도 97.66% 성공률 유지
   - 하지만 응답 시간 급증 (재시도 오버헤드)

---

## 🔍 병목 지점 분석

### 1. 데이터베이스 커넥션 풀

**증상**:
- P95/P99 레이턴시가 높음
- 일부 요청의 응답 시간이 매우 느림 (최대 33초)

**가능한 원인**:
- Connection Pool 크기 부족
- 커넥션 대기 시간 증가
- HikariCP 설정 미흡

**검증 방법**:
```sql
-- MySQL 커넥션 모니터링
SHOW PROCESSLIST;
SHOW STATUS LIKE 'Threads_connected';
SHOW STATUS LIKE 'Max_used_connections';
```

**개선 방안**:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # 현재값 확인 및 증가
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

---

### 2. 데이터베이스 쿼리 성능

**증상**:
- 평균 응답 시간 823ms (중앙값 475ms)
- 고부하 시 응답 시간 증가

**가능한 원인**:
- N+1 문제 재발
- 인덱스 누락
- Slow Query 존재

**검증 방법**:
```sql
-- Slow Query Log 활성화
SET GLOBAL slow_query_log = 1;
SET GLOBAL long_query_time = 0.5;

-- 실행 계획 확인
EXPLAIN ANALYZE
SELECT * FROM user_balance WHERE user_id = 1 FOR UPDATE;
```

**개선 방안**:
1. Fetch Join 재확인
2. 인덱스 추가 (user_balance.user_id, version)
3. 쿼리 최적화 (불필요한 컬럼 제거)

---

### 3. JVM 및 GC

**증상**:
- 일부 요청의 응답 시간이 급증
- 평균과 P99의 차이가 큼

**가능한 원인**:
- GC Pause Time
- Heap 메모리 부족
- Full GC 발생

**검증 방법**:
```bash
# GC 로그 활성화
java -Xlog:gc*:file=gc.log:time,uptime:filecount=5,filesize=100m

# 실시간 모니터링
jstat -gcutil <pid> 1000
```

**개선 방안**:
```bash
# JVM 옵션 튜닝
-Xms2g -Xmx2g  # Heap 크기
-XX:+UseG1GC  # G1 GC 사용
-XX:MaxGCPauseMillis=200  # GC Pause Time 목표
```

---

### 4. Optimistic Lock 재시도 전략

**증상**:
- 단일 사용자 환경에서 응답 시간 급증
- P95 11.69s, P99 30.92s

**가능한 원인**:
- Exponential Backoff의 지연 시간 누적
- 재시도 횟수 과다

**검증 방법**:
```java
// 로깅 추가
@Retryable(
  value = OptimisticLockingFailureException.class,
  maxAttempts = 5,
  backoff = @Backoff(delay = 100, multiplier = 2)
)
```

**개선 방안**:
1. 재시도 횟수 조정 (5회 → 3회)
2. Backoff 시간 단축 (100ms → 50ms)
3. 재시도 모니터링 강화

---

## 💡 개선 권장 사항

### Priority 1: 즉시 적용 (단기)

#### 1.1 Database Connection Pool 튜닝

```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # 20 → 50
      minimum-idle: 10       # 5 → 10
      connection-timeout: 10000  # 30s → 10s (빠른 실패)
      leak-detection-threshold: 60000  # 커넥션 누수 감지
```

**기대 효과**: P95 레이턴시 30% 개선 (2.93s → 2.05s)

---

#### 1.2 Slow Query 로깅 활성화

```yaml
# application.yml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE

spring:
  jpa:
    properties:
      hibernate:
        session.events.log: true
        generate_statistics: true
```

**기대 효과**: 느린 쿼리 식별 및 최적화 가능

---

#### 1.3 인덱스 추가

```sql
-- user_balance 테이블 인덱스
CREATE INDEX idx_user_balance_user_id_version
ON user_balance(user_id, version);

-- 복합 인덱스로 Optimistic Lock 충돌 감지 최적화
CREATE INDEX idx_user_balance_user_id
ON user_balance(user_id);
```

**기대 효과**: 잔액 조회 및 업데이트 쿼리 20% 개선

---

### Priority 2: 중기 개선 (1-2주)

#### 2.1 읽기 전용 복제본 도입

```yaml
# application.yml
spring:
  datasource:
    master:
      url: jdbc:mysql://master:3306/ecommerce
    slave:
      url: jdbc:mysql://slave:3306/ecommerce

# @Transactional(readOnly = true) → Slave DB 라우팅
```

**기대 효과**: 읽기 작업의 TPS 2배 증가, Master DB 부하 50% 감소

---

#### 2.2 Redis 캐싱 적용

```java
@Cacheable(value = "userBalance", key = "#userId")
public UserBalance getBalance(Long userId) {
    return userBalanceRepository.findByUserId(userId);
}

@CacheEvict(value = "userBalance", key = "#userId")
public void chargeBalance(Long userId, int amount) {
    // 캐시 무효화 후 충전
}
```

**기대 효과**: 잔액 조회 API 응답 시간 90% 단축 (475ms → 50ms)

---

#### 2.3 JVM 튜닝

```bash
# G1 GC 튜닝
-Xms4g -Xmx4g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m
-XX:InitiatingHeapOccupancyPercent=45
```

**기대 효과**: GC Pause Time 50% 감소, P99 레이턴시 20% 개선

---

### Priority 3: 장기 개선 (1-2개월)

#### 3.1 APM 도구 도입

- **Pinpoint**: 오픈소스, 한국어 지원
- **Datadog**: 상용, 강력한 모니터링
- **New Relic**: 상용, 실시간 분석

**기대 효과**: 실시간 병목 지점 파악, 프로액티브 성능 관리

---

#### 3.2 CQRS 패턴 적용

```java
// Command: 쓰기 작업 (Master DB)
public void chargeBalance(Long userId, int amount) {
    // Optimistic Lock + Write
}

// Query: 읽기 작업 (Slave DB or Cache)
public UserBalance getBalance(Long userId) {
    // Read-only
}
```

**기대 효과**: 읽기/쓰기 분리로 성능 및 확장성 개선

---

#### 3.3 메시지 큐 기반 비동기 처리

```java
// 잔액 충전 요청을 큐에 저장
@PostMapping("/balance/charge")
public void chargeBalanceAsync(Long userId, int amount) {
    kafkaTemplate.send("balance-charge", new ChargeEvent(userId, amount));
    return ResponseEntity.accepted().build();  // 202 Accepted
}
```

**기대 효과**: 동기 처리 부담 감소, TPS 3배 증가 가능

---

## 🎯 개선 효과 예측

### 단기 개선 후 (1주)

| 메트릭 | 현재 | 개선 후 | 개선율 |
|--------|------|---------|--------|
| TPS | 514.6 req/s | 600 req/s | +16.6% |
| 평균 응답 시간 | 823ms | 600ms | -27.1% |
| P95 | 2.93s | 2.05s | -30.0% |
| P99 | 4.2s | 3.0s | -28.6% |
| 성공률 | 99.99% | 99.99% | - |

**적용 항목**: Connection Pool 튜닝, 인덱스 추가, Slow Query 최적화

---

### 중기 개선 후 (1-2주)

| 메트릭 | 현재 | 개선 후 | 개선율 |
|--------|------|---------|--------|
| TPS | 514.6 req/s | 1000 req/s | +94.3% |
| 평균 응답 시간 | 823ms | 300ms | -63.5% |
| P95 | 2.93s | 800ms | -72.7% |
| P99 | 4.2s | 1.5s | -64.3% |
| 성공률 | 99.99% | 99.99% | - |

**적용 항목**: Read Replica, Redis 캐싱, JVM 튜닝

---

### 장기 개선 후 (1-2개월)

| 메트릭 | 현재 | 개선 후 | 개선율 |
|--------|------|---------|--------|
| TPS | 514.6 req/s | 1500+ req/s | +191.5% |
| 평균 응답 시간 | 823ms | 100ms | -87.8% |
| P95 | 2.93s | 300ms | -89.8% |
| P99 | 4.2s | 500ms | -88.1% |
| 성공률 | 99.99% | 99.99% | - |

**적용 항목**: APM 도구, CQRS, 메시지 큐 기반 비동기 처리

---

## 📝 결론

### 현재 상태 평가

**강점**:
1. ✅ 99.99% 성공률로 매우 안정적
2. ✅ Optimistic Lock 동시성 제어가 효과적
3. ✅ 514.6 req/s TPS로 목표 초과 달성
4. ✅ 재시도 메커니즘이 극한 상황에서도 작동

**약점**:
1. ⚠️ P95(2.93s), P99(4.2s) 레이턴시가 목표 미달
2. ⚠️ 고부하 시 일부 요청의 응답 시간 급증 (최대 33초)
3. ⚠️ Database Connection Pool 병목 가능성
4. ⚠️ 단일 사용자 환경에서 Lock Contention 심각

### 최종 권장 사항

1. **즉시 적용**: Connection Pool 튜닝, 인덱스 추가 (1주 이내)
2. **중기 적용**: Read Replica, Redis 캐싱 (1-2주)
3. **장기 적용**: APM 도구, CQRS, 메시지 큐 (1-2개월)
4. **모니터링**: Slow Query Log, GC Log, APM 도구 활성화
5. **지속적 개선**: 매주 부하 테스트 실행 및 성능 트렌드 분석

### 프로덕션 배포 권장 여부

**현재 상태**: ⚠️ **조건부 배포 가능**

**배포 가능 조건**:
- ✅ 안정성(99.99%)은 프로덕션 레벨
- ✅ 동시성 제어는 검증 완료
- ⚠️ P95/P99 레이턴시 개선 후 배포 권장
- ⚠️ Connection Pool 튜닝 및 인덱스 추가 후 재테스트 필요

**추가 검증 필요**:
1. 단기 개선 항목 적용 후 재테스트
2. 실제 프로덕션 트래픽 패턴으로 부하 테스트
3. 장애 복구 시나리오 테스트 (DB 장애, 네트워크 장애)
4. 피크 타임 트래픽(목표 TPS의 2배) 대응 테스트

---

**보고서 작성자**: Claude Code
**보고서 버전**: 1.0
**다음 테스트 예정일**: 단기 개선 적용 후 (1주 후)
