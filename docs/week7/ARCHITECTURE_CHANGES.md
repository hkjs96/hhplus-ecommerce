# Step 13-14 아키텍처 변경사항 요약

> **작성일:** 2025-12-09
> **피드백 기반:** 제이 코치님 Step 13-14 피드백

이 문서는 코치님 피드백을 반영한 아키텍처 변경사항을 한눈에 파악할 수 있도록 요약한 문서입니다.

---

## 📊 변경사항 요약표

| 항목 | Before | After | 효과 |
|------|--------|-------|------|
| **Redis Fallback** | 빈 목록 반환 | DB 백업 조회 | 가용성 ↑ |
| **쿠폰 테이블** | CouponReservation + UserCoupon | UserCoupon만 | DB write 66% ↓ |
| **Profile 분리** | 모든 환경 실행 | local/test만 | 운영 안전성 ↑ |
| **Connection Pool** | 200 | 100 (모니터링 필요) | 리소스 50% ↓ |
| **K6 시나리오** | Extreme만 | Extreme + Ramp-up | 테스트 현실성 ↑ |
| **성능 수치** | 비정상 상태 비교 | 정상 상태 필요 명시 | 문서 정확성 ↑ |

---

## 1️⃣ Redis 장애 시 Fallback 전략 강화

### 변경 전 (2025-12-07)
```java
public RankingResponse getTopProducts(LocalDate date, int limit) {
    try {
        return redisRankingRepository.getTopN(targetDate, limit);
    } catch (Exception e) {
        return RankingResponse.of(date, List.of());  // 빈 목록
    }
}
```

### 변경 후 (2025-12-09)
```java
public RankingResponse getTopProducts(LocalDate date, int limit) {
    try {
        // 1차: Redis 조회
        List<ProductRanking> rankings = redisRankingRepository.getTopN(targetDate, limit);
        if (rankings.isEmpty()) {
            return getTopProductsFromDb(targetDate, limit);  // 2차: DB 백업
        }
        return RankingResponse.of(targetDate, items);
    } catch (Exception e) {
        return getTopProductsFromDb(targetDate, limit);  // 2차: DB 백업
    }
}
```

**신규 컴포넌트:**
- `ProductRankingBackup` - DB 백업 엔티티
- `ProductRankingBackupRepository` - 백업 데이터 조회
- `RankingBackupScheduler` - 10분마다 Redis → DB 백업

**효과:**
- Redis 장애 시에도 최근 10분 이내 랭킹 제공
- 서비스 가용성 향상

**파일 위치:**
- `src/main/java/io/hhplus/ecommerce/domain/product/ProductRankingBackup.java`
- `src/main/java/io/hhplus/ecommerce/infrastructure/batch/RankingBackupScheduler.java`
- `src/main/java/io/hhplus/ecommerce/application/product/usecase/ProductRankingUseCase.java`

---

## 2️⃣ CouponReservation 테이블 제거

### 변경 전 (2025-12-04)
```
선착순 예약 흐름:
1. Redis INCR → sequence 획득
2. DB INSERT → CouponReservation (RESERVED)
3. Event 발행 → CouponReservedEvent
4. Event Listener → UserCoupon INSERT (ISSUED)
5. CouponReservation UPDATE → ISSUED
→ 총 3회 DB write
```

### 변경 후 (2025-12-09)
```
선착순 예약 흐름:
1. Redis INCR → sequence 획득
2. Redis SADD → 예약자 Set에 userId 추가
3. Event 발행 → CouponReservedEvent
4. Event Listener → UserCoupon INSERT
→ 총 1회 DB write (66% 감소)
```

**삭제된 컴포넌트:**
- ❌ `CouponReservation.java` (엔티티)
- ❌ `CouponReservationRepository.java` (인터페이스)
- ❌ `JpaCouponReservationRepository.java` (구현체)
- ❌ `CouponReservationConcurrencyTest.java` (테스트)

**변경된 컴포넌트:**
- `ReserveCouponUseCase.java` - CouponReservation INSERT 제거
- `CouponReservedEvent.java` - sequenceNumber는 응답/로깅용으로만 사용
- `CouponReservedEventListener.java` - 보상 트랜잭션 추가 (Redis 원복)

**효과:**
- DB write 66% 감소
- Redis-DB 일관성 문제 제거
- Redis가 Single Source of Truth

**트레이드오프:**
- sequenceNumber 영구 저장 불가 (허용)
- 감사/추적 제한 (로그로 대체)

**파일 위치:**
- `src/main/java/io/hhplus/ecommerce/application/usecase/coupon/ReserveCouponUseCase.java`
- `src/main/java/io/hhplus/ecommerce/application/coupon/listener/CouponReservedEventListener.java`

---

## 3️⃣ LoadTestDataInitializer Profile 분리

### 변경 전 (2025-12-07)
```java
@Component
@RequiredArgsConstructor
public class LoadTestDataInitializer implements CommandLineRunner {
    // 모든 환경에서 실행
}
```

### 변경 후 (2025-12-09)
```java
@Component
@Profile({"local", "test"})  // local, test에서만 실행
@RequiredArgsConstructor
public class LoadTestDataInitializer implements CommandLineRunner {
    // 운영 환경에서는 실행 안 됨
}
```

**추가 개선:**
- userId 1 잔액: 1억원 → 200억원 (K6 테스트 중 잔액 부족 해결)

**효과:**
- 운영 환경 테스트 데이터 생성 방지
- K6 테스트 중 잔액 부족 문제 해결

**파일 위치:**
- `src/main/java/io/hhplus/ecommerce/infrastructure/init/LoadTestDataInitializer.java`

---

## 4️⃣ Connection Pool 크기 재검토

### 변경 전 (2025-12-07)
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 200  # K6 350 VUs 기준
```

### 변경 후 (2025-12-09)
```yaml
spring:
  datasource:
    hikari:
      # K6 테스트(최대 350 VUs)를 기반으로 200으로 설정했으나,
      # 실제 모니터링 후 튜닝 필요
      # 우선 100으로 조정
      maximum-pool-size: 100
      minimum-idle: 50
      connection-timeout: 10000  # 10초 (빠른 실패)
      leak-detection-threshold: 30000  # 누수 감지
```

**효과:**
- 리소스 50% 감소
- 실제 모니터링 필요성 명시

**다음 단계:**
- HikariCP metrics 모니터링
- Peak active connection 확인
- 120-130% 여유 확보

**파일 위치:**
- `src/main/resources/application.yml`

---

## 5️⃣ K6 테스트 시나리오 개선

### 변경 전 (2025-12-07)
```javascript
scenarios: {
  extremeConcurrency: {
    vus: 100,         // 즉시 100명
    iterations: 100,
  },
  sequentialIssue: {
    vus: 1,
    iterations: 100,
  },
}
```

### 변경 후 (2025-12-09)
```javascript
scenarios: {
  extremeConcurrency: { /* 기존 유지 */ },
  sequentialIssue: { /* 기존 유지 */ },

  // 🆕 Ramp-up 시나리오 추가
  rampUpTest: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '10s', target: 20 },   // Warm-up
      { duration: '20s', target: 50 },   // Load
      { duration: '10s', target: 0 },    // Cool-down
    ],
    exec: 'issueCouponRampUp',
  },
}
```

**효과:**
- 실제 트래픽 패턴 반영
- Cold Start 문제 식별
- Bottleneck 조기 발견
- 시스템 회복력 검증

**파일 위치:**
- `docs/week7/loadtest/k6/step14-coupon-concurrency-test.js`

---

## 6️⃣ 성능 개선 수치 문서화

### 변경 전 (2025-12-07)
```markdown
## 성능 개선 결과
- Before: 30,065ms (p95)
- After: 16ms (p95)
- 개선율: 1,857배 🎉
```

### 변경 후 (2025-12-09)
```markdown
## 성능 개선 결과

### Before v1 (Connection Pool 고갈 - 비정상 상태)
- ranking_query p(95): 30,065ms
- ⚠️ 주의: 이 수치는 Connection Pool이 고갈된 비정상 상태입니다.
- 정상 상태 Before 수치 측정 필요

### After v3 (개선 후 - 정상 상태)
- ranking_query p(95): < 50ms
- 정확한 개선율은 정상 상태 Before 측정 후 산출
```

**효과:**
- 독자에게 올바른 비교 방법 교육
- 실수 투명하게 공개 (신뢰도 향상)

**파일 위치:**
- `docs/week7/loadtest/PERFORMANCE_IMPROVEMENTS.md`

---

## 📁 신규 파일 목록

### 1. 도메인 & 인프라
```
✅ src/main/java/io/hhplus/ecommerce/domain/product/ProductRankingBackup.java
✅ src/main/java/io/hhplus/ecommerce/domain/product/ProductRankingBackupRepository.java
✅ src/main/java/io/hhplus/ecommerce/infrastructure/persistence/product/JpaProductRankingBackupRepository.java
✅ src/main/java/io/hhplus/ecommerce/infrastructure/batch/RankingBackupScheduler.java
```

### 2. 문서
```
✅ docs/week7/FEEDBACK_IMPROVEMENTS.md (신규 - 500줄)
✅ docs/week7/ARCHITECTURE_CHANGES.md (신규 - 본 문서)
```

---

## 🗑️ 삭제 파일 목록

### 1. 도메인 & 인프라
```
❌ src/main/java/io/hhplus/ecommerce/domain/coupon/CouponReservation.java
❌ src/main/java/io/hhplus/ecommerce/domain/coupon/CouponReservationRepository.java
❌ src/main/java/io/hhplus/ecommerce/infrastructure/persistence/coupon/JpaCouponReservationRepository.java
```

### 2. 테스트
```
❌ src/test/java/io/hhplus/ecommerce/application/usecase/coupon/CouponReservationConcurrencyTest.java
❌ src/test/java/io/hhplus/ecommerce/application/usecase/coupon/CouponReservationIntegrationTest.java
```

---

## 📝 수정 파일 목록

### 1. 애플리케이션
```
✏️ ReserveCouponUseCase.java - CouponReservation 제거, Redis Only
✏️ CouponReservedEvent.java - sequenceNumber 역할 변경
✏️ CouponReservedEventListener.java - 보상 트랜잭션 추가
✏️ ProductRankingUseCase.java - DB Fallback 로직 추가
✏️ ReserveCouponResponse.java - sequenceNumber 응답용으로만
```

### 2. 인프라
```
✏️ LoadTestDataInitializer.java - Profile 분리, 잔액 증가
```

### 3. 설정
```
✏️ application.yml - Connection Pool 100으로 조정
✏️ CacheConfig.java - (필요 시 백업 캐시 설정)
```

### 4. 문서
```
✏️ COUPON_RESERVATION_DESIGN.md - CouponReservation 제거 반영
✏️ K6_LOAD_TEST_PLAN.md - Ramp-up 시나리오 추가
✏️ README.md - 피드백 반영 섹션 추가
✏️ PERFORMANCE_IMPROVEMENTS.md - 정상 상태 비교 필요성 명시
```

### 5. K6 테스트
```
✏️ step14-coupon-concurrency-test.js - Ramp-up 시나리오 추가
```

---

## 🎯 다음 단계 (권장)

### 즉시 실행 (이번 주)
- [ ] Connection Pool 모니터링 구현 (HikariCP metrics)
- [ ] 정상 상태 Before 수치 측정
- [ ] Redis Failover 테스트 실행

### 중기 계획 (다음 Sprint)
- [ ] Grafana 대시보드 구성
- [ ] Alerting 설정 (Connection Pool > 80%)
- [ ] K6 테스트 CI/CD 통합

### 장기 비전 (차기 과제)
- [ ] Auto Scaling (Connection Pool 동적 조정)
- [ ] Multi-region Redis (Replication)
- [ ] Circuit Breaker 패턴 적용

---

## 📚 관련 문서

- **상세 문서:** [FEEDBACK_IMPROVEMENTS.md](./FEEDBACK_IMPROVEMENTS.md) - 각 항목별 상세 분석 (500줄)
- **설계 문서:** [COUPON_RESERVATION_DESIGN.md](./COUPON_RESERVATION_DESIGN.md) - Redis Only 구조
- **테스트 문서:** [K6_LOAD_TEST_PLAN.md](./K6_LOAD_TEST_PLAN.md) - Ramp-up 시나리오
- **성능 문서:** [PERFORMANCE_IMPROVEMENTS.md](./loadtest/PERFORMANCE_IMPROVEMENTS.md) - 개선 내역

---

## 🔍 변경사항 Git Diff 요약

```bash
# 신규 파일 (4개)
+ ProductRankingBackup.java
+ ProductRankingBackupRepository.java
+ JpaProductRankingBackupRepository.java
+ RankingBackupScheduler.java

# 삭제 파일 (5개)
- CouponReservation.java
- CouponReservationRepository.java
- JpaCouponReservationRepository.java
- CouponReservationConcurrencyTest.java
- CouponReservationIntegrationTest.java

# 수정 파일 (11개)
M ReserveCouponUseCase.java
M CouponReservedEvent.java
M CouponReservedEventListener.java
M ProductRankingUseCase.java
M ReserveCouponResponse.java
M LoadTestDataInitializer.java
M application.yml
M COUPON_RESERVATION_DESIGN.md
M K6_LOAD_TEST_PLAN.md
M README.md
M PERFORMANCE_IMPROVEMENTS.md
```

---

## 💡 핵심 메시지

### 1. Single Source of Truth
- Redis가 선착순 판정의 유일한 진실 원천
- DB는 최종 발급 결과만 저장

### 2. Graceful Degradation
- Redis 장애 시에도 최소한의 서비스 제공
- DB 백업 + 스케줄러로 가용성 보장

### 3. 측정 기반 의사결정
- 추측으로 설정 금지
- 반드시 모니터링 후 조정

### 4. 현실적 테스트
- 극단적 시나리오 + 점진적 부하 증가
- 두 시나리오 모두 필요

### 5. 투명한 문서화
- 실수도 투명하게 공개
- 올바른 방법 교육

---

**작성자:** 항해플러스 백엔드 수강생
**피드백 제공:** 제이 튜터
**분석 협력:** Gemini AI
**작성일:** 2025-12-09
