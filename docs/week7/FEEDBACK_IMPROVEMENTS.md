# Step 13-14 피드백 반영 개선 문서

> **작성일:** 2025-12-09
> **피드백 수신일:** 2025-12-07
> **작성자:** 항해플러스 백엔드 수강생
> **코치:** 제이 튜터

---

## 📋 목차

1. [개요](#개요)
2. [개선 항목별 상세](#개선-항목별-상세)
   - [1. Redis 장애 시 Fallback 전략 강화](#1-redis-장애-시-fallback-전략-강화)
   - [2. CouponReservation 테이블 제거](#2-couponreservation-테이블-제거)
   - [3. LoadTestDataInitializer Profile 분리](#3-loadtestdatainitializer-profile-분리)
   - [4. Connection Pool 크기 재검토](#4-connection-pool-크기-재검토)
   - [5. K6 테스트 시나리오 개선](#5-k6-테스트-시나리오-개선)
   - [6. 성능 개선 수치 문서화](#6-성능-개선-수치-문서화)
3. [전체 회고](#전체-회고)
4. [다음 단계](#다음-단계)

---

## 개요

### 피드백 배경

Step 13-14 과제에서 Redis Sorted Set 기반 랭킹 시스템과 선착순 쿠폰 발급 시스템을 구현했습니다. 코치님으로부터 긍정적인 피드백과 함께 6가지 개선 제안을 받았습니다.

**좋았던 점:**
- Redis Sorted Set + PaymentCompletedEvent 기반 비동기 랭킹 업데이트
- 2단계 예약-발급 패턴 (Reserve → Issue)
- K6 부하 테스트를 통한 실제 병목 발견 및 개선
- Testcontainers 기반 독립적 테스트 환경
- 상세한 문서화

**개선할 점:**
1. 성능 개선 수치 검증 필요
2. Redis 장애 시 Fallback 전략 부족
3. CouponReservation 테이블의 필요성 재고
4. Connection Pool 200개 과다 가능성
5. LoadTestDataInitializer 운영 환경 영향
6. K6 테스트 시나리오 현실성 부족

### Gemini를 활용한 개선 과정

이번 개선 작업에서는 Gemini AI를 활용하여 피드백을 분석하고 개선 방안을 도출했습니다. Claude Code와 Gemini를 병행하여 다양한 관점에서 문제를 접근했습니다.

**Gemini 활용 방식:**
```
Prompt: "아래는 step13-14 과제에 대한 코치님의 피드백입니다.
해당 피드백을 반영해서 프로젝트의 전체 내용을 분석하고
어떻게 반영하는게 좋을지 계획을 수립해줘"
```

---

## 개선 항목별 상세

## 1. Redis 장애 시 Fallback 전략 강화

### 피드백 내용

> "Redis 장애 시 fallback 전략이 부족합니다. ProductRankingUseCase에서 빈 목록을 반환하는 건 서비스 다운은 막지만, 사용자에게 랭킹을 전혀 보여주지 못하거든요. DB에 최근 랭킹을 백업해두거나, 정적 랭킹을 캐시해서 Redis 장애 시에도 최소한의 정보를 제공하는 방법도 고려해보세요."

### 문제 인식 및 분석

**기존 구현의 한계:**
```java
// Before: Redis 장애 시 빈 목록 반환
public RankingResponse getTopProducts(LocalDate date, int limit) {
    try {
        List<ProductRanking> rankings = redisRankingRepository.getTopN(targetDate, limit);
        // ... 처리
    } catch (Exception e) {
        log.error("Redis 장애 발생");
        return RankingResponse.of(date, List.of());  // 빈 목록!
    }
}
```

**문제점:**
- Redis 장애 = 랭킹 기능 완전 중단
- 사용자 경험 저하 (빈 화면 표시)
- 비즈니스 영향 (상품 노출 기회 상실)

### 해결 방안 고민

**Option 1: Static Ranking (정적 랭킹)**
- 메모리에 고정된 Top 10 상품 저장
- 장점: 매우 빠른 응답, 추가 인프라 불필요
- 단점: 항상 같은 순위, 최신성 없음

**Option 2: DB Backup (주기적 백업)**
- 스케줄러로 Redis 데이터를 DB에 백업
- 장점: 최근 데이터 제공 가능 (10분 이내)
- 단점: DB I/O 추가, 스케줄러 구현 필요

**Option 3: Multi-level Cache (다단계 캐시)**
- Redis → Local Cache → DB 순서로 Fallback
- 장점: 가장 높은 가용성
- 단점: 복잡도 증가, 데이터 동기화 이슈

### 최종 구현 결정

**선택: Option 2 (DB Backup)**

**이유:**
1. **최신성 보장:** 10분 주기 백업으로 비교적 최신 데이터 제공
2. **운영 복잡도:** Static보다는 복잡하지만 Multi-level보다 단순
3. **비즈니스 요구사항:** 랭킹은 실시간일 필요 없음 (10분 지연 허용)
4. **확장성:** 향후 장기 통계 분석에도 활용 가능

### 구현 상세

#### 1) DB 백업 엔티티 추가

```java
// ProductRankingBackup.java
@Entity
@Table(name = "product_ranking_backup")
public class ProductRankingBackup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;
    private String productName;
    private int salesCount;
    private int ranking;
    private LocalDate aggregatedDate;  // 백업 날짜

    // 생성자, getter
}
```

**설계 고려사항:**
- `aggregatedDate`: 날짜별로 별도 레코드 저장 (일별 통계 조회 가능)
- `productName`: JOIN 없이 바로 응답 가능 (비정규화)
- `ranking`: Redis에서 계산된 순위 그대로 저장

#### 2) 백업 스케줄러 구현

```java
// RankingBackupScheduler.java
@Component
@RequiredArgsConstructor
@Slf4j
public class RankingBackupScheduler {
    private final ProductRankingRepository redisRankingRepository;
    private final ProductRankingBackupRepository dbRankingRepository;
    private final ProductRepository productRepository;

    @Scheduled(fixedRateString = "${ranking.backup.schedule.rate:600000}")
    @Transactional
    public void backupRanking() {
        LocalDate today = LocalDate.now();
        log.info("Starting ranking backup for date: {}", today);

        try {
            // 1. Redis에서 Top 100 조회
            List<ProductRanking> redisRankings = redisRankingRepository.getTopN(today, 100);
            if (redisRankings.isEmpty()) {
                log.info("No ranking data in Redis. Skipping backup.");
                return;
            }

            // 2. 상품 정보 Batch 조회 (N+1 방지)
            List<Long> productIds = redisRankings.stream()
                .map(ProductRanking::getProductId)
                .toList();
            Map<Long, Product> productMap = productRepository.findAll().stream()
                .filter(p -> productIds.contains(p.getId()))
                .collect(Collectors.toMap(Product::getId, Function.identity()));

            // 3. 백업 객체 생성
            List<ProductRankingBackup> backups = redisRankings.stream()
                .map(ranking -> {
                    Product product = productMap.get(ranking.getProductId());
                    String productName = (product != null) ? product.getName() : "Unknown Product";
                    int rank = redisRankings.indexOf(ranking) + 1;
                    return new ProductRankingBackup(
                        ranking.getProductId(),
                        productName,
                        ranking.getSalesCount(),
                        rank,
                        today
                    );
                })
                .collect(Collectors.toList());

            // 4. DB에 저장
            dbRankingRepository.saveAll(backups);
            log.info("Successfully backed up {} ranking entries", backups.size());

        } catch (Exception e) {
            log.error("Error during ranking backup for date: {}", today, e);
        }
    }
}
```

**구현 포인트:**
- `fixedRate`: 고정 간격 (10분 = 600,000ms)
- **N+1 방지:** 상품 정보를 Map으로 한 번에 조회
- **에러 격리:** 백업 실패해도 메인 서비스에 영향 없음
- **Top 100 저장:** API는 Top 10만 반환하지만, 백업은 여유있게 100개

#### 3) Fallback 로직 구현

```java
// ProductRankingUseCase.java
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductRankingUseCase {
    private final ProductRankingRepository redisRankingRepository;
    private final ProductRankingBackupRepository dbRankingRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public RankingResponse getTopProducts(LocalDate date, int limit) {
        LocalDate targetDate = date != null ? date : LocalDate.now();

        try {
            // 1. Redis 조회 시도
            log.debug("Redis에서 상위 {}개 랭킹 조회 시도 (날짜: {})", limit, targetDate);
            List<ProductRanking> rankings = redisRankingRepository.getTopN(targetDate, limit);

            if (rankings.isEmpty()) {
                log.info("Redis 데이터 없음 - DB 백업 조회");
                return getTopProductsFromDb(targetDate, limit);
            }

            // 2. 상품 정보 병합 (기존 로직)
            // ...
            return RankingResponse.of(targetDate, items);

        } catch (Exception e) {
            log.error("Redis 장애 발생 - DB 백업으로 대체", e);
            return getTopProductsFromDb(targetDate, limit);
        }
    }

    private RankingResponse getTopProductsFromDb(LocalDate date, int limit) {
        try {
            log.debug("DB 백업에서 상위 {}개 랭킹 조회 (날짜: {})", limit, date);
            List<ProductRankingBackup> backupRankings = dbRankingRepository
                .findByAggregatedDate(date);

            if (backupRankings.isEmpty()) {
                log.warn("DB 백업에서도 데이터 없음 (날짜: {})", date);
                return RankingResponse.of(date, List.of());
            }

            List<RankingItem> items = backupRankings.stream()
                .limit(limit)
                .map(backup -> RankingItem.of(
                    backup.getRanking(),
                    backup.getProductId(),
                    backup.getProductName(),
                    backup.getSalesCount()
                ))
                .collect(Collectors.toList());

            log.warn("DB 백업 조회 성공 (항목: {}, 날짜: {})", items.size(), date);
            return RankingResponse.of(date, items);

        } catch (Exception dbError) {
            log.error("CRITICAL: Redis와 DB 모두 실패 (날짜: {})", date, dbError);
            return RankingResponse.of(date, List.of());
        }
    }
}
```

**Fallback 흐름:**
```
1차: Redis 조회 시도
  ↓ (장애 or 빈 데이터)
2차: DB 백업 조회 (최근 10분 이내 데이터)
  ↓ (없음)
3차: 빈 목록 반환 (Graceful Degradation)
```

### 트레이드오프

**장점:**
- ✅ Redis 장애 시에도 랭킹 서비스 제공 (10분 지연)
- ✅ 장기 통계 분석 가능 (일별 랭킹 히스토리)
- ✅ 백업 실패해도 메인 서비스 무영향

**단점:**
- ❌ DB I/O 추가 (10분마다 Top 100 INSERT)
- ❌ 저장 공간 증가 (일 100건 × 365일 = 36,500건/년)
- ❌ 스케줄러 관리 필요

**최적화 고려사항:**
- 오래된 백업 데이터 삭제 (3개월 이상)
- 백업 크기 조정 (Top 100 → Top 50)
- 백업 주기 조정 (10분 → 30분)

### 학습 및 인사이트

**배운 점:**
1. **Graceful Degradation:** 핵심 기능이 실패해도 대체 기능으로 최소한의 서비스 제공
2. **Multi-tier Strategy:** 여러 단계의 Fallback 전략 설계
3. **데이터 비정규화:** productName을 중복 저장하여 JOIN 없이 빠른 조회

**실무 적용:**
- Redis 장애는 드물지만 발생 시 영향이 크므로 Fallback 필수
- 백업 데이터는 통계/분석 목적으로도 활용 가능 (일석이조)
- 스케줄러는 실패해도 다음 주기에 재시도되므로 안전

---

## 2. CouponReservation 테이블 제거

### 피드백 내용

> "CouponReservation 테이블의 필요성을 재고해보면 좋겠습니다. 설계 문서에서 sequenceNumber 저장의 장점을 잘 설명하셨지만, 실제로는 Redis INCR과 Set만으로도 순서 보장과 중복 방지가 가능해요. CouponReservation 테이블이 추가 DB write를 발생시키고, Redis와 DB 사이의 일관성 문제를 만들 수 있거든요. Redis가 Single Source of Truth라면 DB는 최종 발급 내역(UserCoupon)만 저장하고, 예약 상태는 Redis에만 두는 방법도 있습니다."

### 문제 인식 및 분석

**기존 구조 (Before):**
```
[선착순 예약 흐름]
1. Redis INCR: sequence 획득
2. DB INSERT: CouponReservation (RESERVED 상태)
3. Event 발행: CouponReservedEvent
4. Event Listener: UserCoupon INSERT (ISSUED 상태)
5. CouponReservation 상태 업데이트: RESERVED → ISSUED
```

**문제점 분석:**

1. **추가 DB Write:**
   - CouponReservation INSERT (RESERVED)
   - CouponReservation UPDATE (RESERVED → ISSUED)
   - UserCoupon INSERT
   - **총 3회 DB 작업** (원래는 1회면 충분)

2. **Redis-DB 일관성 문제:**
   ```
   시나리오: Redis 성공 → DB 실패
   - Redis: sequence = 42, Set에 userId 추가
   - DB: CouponReservation INSERT 실패 (네트워크 오류)
   - 결과: Redis는 발급됨, DB는 없음 (불일치!)
   ```

3. **복잡도 증가:**
   - CouponReservation 엔티티 관리
   - RESERVED → ISSUED 상태 전이 로직
   - 2개 테이블 동기화 코드

4. **sequenceNumber의 모호한 역할:**
   - 비즈니스 로직에 필수인가? → **No**
   - 단순히 "몇 번째로 예약했는지" 표시용
   - Redis INCR만으로도 충분히 순서 보장됨

### 해결 방안 고민

**Option 1: CouponReservation 유지 (현재 구조)**
- 장점: sequenceNumber 추적 가능, 감사/디버깅 용이
- 단점: DB write 3회, 일관성 문제

**Option 2: CouponReservation 제거 (Redis Only)**
- 장점: DB write 1회, 일관성 문제 없음, 단순함
- 단점: sequenceNumber 추적 불가 (Redis TTL 만료 후)

**Option 3: Hybrid (Redis + Event Sourcing)**
- Redis로 선착순 판정
- Event Store에 예약 이벤트 저장
- 장점: 완전한 추적 가능
- 단점: 복잡도 대폭 증가

### 최종 구현 결정

**선택: Option 2 (Redis Only - CouponReservation 제거)**

**결정 근거:**

1. **Single Source of Truth 원칙:**
   - Redis가 선착순 판정의 유일한 진실의 원천
   - DB는 최종 발급 결과만 저장

2. **sequenceNumber의 실제 필요성:**
   ```
   질문: "42번째로 예약했어요" 정보가 비즈니스에 필수인가?
   답변: No. 사용자는 "발급 성공/실패"만 알면 됨
   ```

3. **일관성 보장:**
   - Redis만 사용 → 일관성 문제 원천 차단
   - Event Listener 실패 시 Redis 원복으로 해결

4. **성능:**
   - DB write: 3회 → 1회 (66% 감소)
   - 동시성 처리 속도 향상

### 구현 상세

#### 1) 삭제된 컴포넌트

```bash
# Domain
- CouponReservation.java (엔티티)
- CouponReservationRepository.java (인터페이스)

# Infrastructure
- JpaCouponReservationRepository.java (구현체)

# Test
- CouponReservationConcurrencyTest.java
- CouponReservationIntegrationTest.java
```

#### 2) 새로운 선착순 흐름

```java
// ReserveCouponUseCase.java
@Transactional
public ReserveCouponResponse execute(Long couponId, Long userId) {
    // 1. 사용자 & 쿠폰 검증
    userRepository.findByIdOrThrow(userId);
    Coupon coupon = couponRepository.findByIdOrThrow(couponId);
    coupon.validateIssuable();

    // 2. 중복 예약 체크 (Redis Set)
    String reservationSetKey = String.format("coupon:%d:reservations", couponId);
    if (redisTemplate.opsForSet().isMember(reservationSetKey, String.valueOf(userId))) {
        throw new BusinessException(ErrorCode.ALREADY_ISSUED_COUPON);
    }

    // 3. Redis INCR로 순번 획득 (원자적)
    String sequenceKey = String.format("coupon:%d:sequence", couponId);
    Long sequence = redisTemplate.opsForValue().increment(sequenceKey);

    if (sequence == null) {
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Redis 순번 획득 실패");
    }

    // 4. 수량 체크 (선착순 마감)
    if (sequence > coupon.getTotalQuantity()) {
        throw new BusinessException(ErrorCode.COUPON_SOLD_OUT,
            String.format("쿠폰 소진 (%d/%d)", sequence, coupon.getTotalQuantity()));
    }

    // 5. Redis Set에 예약 기록 (멱등성)
    redisTemplate.opsForSet().add(reservationSetKey, String.valueOf(userId));
    redisTemplate.expire(reservationSetKey, Duration.ofDays(1));
    redisTemplate.expire(sequenceKey, Duration.ofDays(1));

    // 6. Event 발행 (AFTER_COMMIT 시점)
    CouponReservedEvent event = new CouponReservedEvent(couponId, userId, sequence);
    eventPublisher.publishEvent(event);

    // 메트릭 기록
    metricsCollector.recordCouponReservationSuccess();

    return ReserveCouponResponse.of(couponId, userId, sequence);

    // ✅ DB 작업 없음! Redis만 사용
}
```

**핵심 변경:**
- ❌ `CouponReservation INSERT` 제거
- ✅ Redis `INCR` + `SADD`만 사용
- ✅ 트랜잭션 커밋 후 Event 발행

#### 3) Event Listener에서 실제 발급

```java
// CouponReservedEventListener.java
@Component
@RequiredArgsConstructor
@Slf4j
public class CouponReservedEventListener {
    private final IssueCouponActualService issueCouponActualService;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCouponReserved(CouponReservedEvent event) {
        log.info("Processing CouponReservedEvent: couponId={}, userId={}, sequence={}",
            event.getCouponId(), event.getUserId(), event.getSequenceNumber());

        try {
            // 실제 쿠폰 발급 (재고 차감 + UserCoupon INSERT)
            UserCoupon userCoupon = issueCouponActualService.issueActual(
                event.getCouponId(),
                event.getUserId()
            );

            log.info("Coupon issued successfully: couponId={}, userId={}, userCouponId={}",
                event.getCouponId(), event.getUserId(), userCoupon.getId());

        } catch (Exception e) {
            // 발급 실패 - Redis 원복
            log.error("Coupon issue failed, rolling back Redis: couponId={}, userId={}",
                event.getCouponId(), event.getUserId(), e);

            rollbackRedisState(event.getCouponId(), event.getUserId());
        }
    }

    /**
     * Redis 상태 원복 (보상 트랜잭션)
     * - sequence DECR
     * - reservations Set에서 userId 제거
     */
    private void rollbackRedisState(Long couponId, Long userId) {
        try {
            String sequenceKey = String.format("coupon:%d:sequence", couponId);
            Long newSequence = redisTemplate.opsForValue().decrement(sequenceKey);

            String reservationSetKey = String.format("coupon:%d:reservations", couponId);
            redisTemplate.opsForSet().remove(reservationSetKey, String.valueOf(userId));

            log.warn("Redis state rolled back: couponId={}, userId={}, newSequence={}",
                couponId, userId, newSequence);

        } catch (Exception e) {
            log.error("Failed to rollback Redis state: couponId={}, userId={}",
                couponId, userId, e);
            // TODO: 알림 발송 (Slack, PagerDuty)
        }
    }
}
```

**보상 트랜잭션 (Saga Pattern):**
```
[정상 흐름]
1. Redis INCR → 2. Event 발행 → 3. UserCoupon INSERT ✅

[실패 흐름]
1. Redis INCR → 2. Event 발행 → 3. UserCoupon INSERT ❌
   → 4. Redis DECR (보상) → 5. Set 제거 (원복)
```

#### 4) CouponReservedEvent 수정

```java
// CouponReservedEvent.java
@Getter
public class CouponReservedEvent {
    private final Long couponId;
    private final Long userId;
    private final Long sequenceNumber;  // 응답용 (로깅/메트릭)

    public CouponReservedEvent(Long couponId, Long userId, Long sequenceNumber) {
        this.couponId = couponId;
        this.userId = userId;
        this.sequenceNumber = sequenceNumber;
    }
}
```

**sequenceNumber 역할 변경:**
- Before: DB에 저장 (영구 보관)
- After: 응답/로깅용 (임시 정보)

#### 5) 응답 DTO

```java
// ReserveCouponResponse.java
public record ReserveCouponResponse(
    Long couponId,
    Long userId,
    Long sequenceNumber,  // 사용자에게 표시 (N번째 예약)
    String status,
    String message
) {
    public static ReserveCouponResponse of(Long couponId, Long userId, Long sequenceNumber) {
        return new ReserveCouponResponse(
            couponId,
            userId,
            sequenceNumber,
            "RESERVED",
            String.format("쿠폰 발급 예약이 완료되었습니다. (%d번째)", sequenceNumber)
        );
    }
}
```

**sequenceNumber 용도:**
- 사용자에게 "42번째로 예약했어요" 표시
- 실시간 응답에만 포함 (DB 저장 안 함)
- Redis TTL 만료 후에는 조회 불가 (허용)

### 트레이드오프

**장점:**
- ✅ DB write 66% 감소 (3회 → 1회)
- ✅ Redis-DB 일관성 문제 해결
- ✅ 코드 복잡도 감소 (엔티티 1개 제거)
- ✅ 동시성 처리 속도 향상

**단점:**
- ❌ sequenceNumber 영구 저장 불가
- ❌ Redis TTL 만료 후 순번 조회 불가
- ❌ 감사/디버깅 시 순번 정보 부족

**허용 가능한 이유:**

1. **비즈니스 요구사항:**
   - "N번째 예약" 정보는 실시간 응답에만 필요
   - 나중에 "내가 몇 번째였지?" 조회는 불필요

2. **감사/디버깅:**
   - 로그에 sequenceNumber 기록 (충분)
   - UserCoupon 생성 시각으로 순서 추정 가능
   - 필요 시 Redis 백업으로 복구 가능

3. **성능 > 완벽한 추적:**
   - 선착순 쿠폰은 성능이 더 중요
   - 순번 추적은 부가 기능

### 학습 및 인사이트

**배운 점:**

1. **Single Source of Truth:**
   - 분산 시스템에서 하나의 데이터 원천이 중요
   - Redis ↔ DB 동기화는 항상 일관성 문제 유발

2. **필수 vs 부가 기능:**
   - "있으면 좋은 기능"과 "반드시 필요한 기능" 구분
   - sequenceNumber는 부가 기능 → 성능 trade-off 가능

3. **보상 트랜잭션 (Saga Pattern):**
   - Event 처리 실패 시 Redis 원복 필요
   - `DECR` + `SREM`으로 상태 복구

4. **Redis TTL 활용:**
   - 임시 데이터는 TTL로 자동 정리
   - 24시간 후 예약 정보 삭제 (메모리 절약)

**실무 적용:**
- 분산 트랜잭션은 피할 수 있으면 피하기
- Event-driven 아키텍처에서 보상 로직 필수
- Redis Only로 간단하게 → 필요 시 DB 추가 (진화적 설계)

---

## 3. LoadTestDataInitializer Profile 분리

### 피드백 내용

> "LoadTestDataInitializer가 운영 환경에 영향을 줄 수 있습니다. 현재는 애플리케이션 시작할 때마다 20,101명을 생성하는데, 프로파일로 분리해서 local이나 test 환경에서만 실행되도록 하는 게 안전해요."

### 문제 인식 및 분석

**기존 코드 (Before):**
```java
@Component
@RequiredArgsConstructor
public class LoadTestDataInitializer implements CommandLineRunner {
    @Override
    public void run(String... args) {
        // 모든 환경에서 무조건 실행!
        createTestUsers();  // 20,101명 생성
    }
}
```

**문제점:**

1. **운영 환경 오염:**
   - Prod에서도 20,101명의 테스트 사용자 생성
   - 실제 사용자와 혼재

2. **데이터 정합성:**
   - 테스트 데이터가 통계에 포함
   - 매출/주문 수 왜곡

3. **보안 위험:**
   - 테스트 계정으로 실제 서비스 접근 가능
   - 초기 잔액 200억원 (실제 돈은 아니지만 위험)

4. **리소스 낭비:**
   - 매 배포마다 20,101명 생성 (중복 체크해도 부담)
   - 시작 시간 지연

### 해결 방안 고민

**Option 1: 환경 변수 체크**
```java
if (System.getenv("ENABLE_TEST_DATA").equals("true")) {
    createTestUsers();
}
```
- 장점: 간단함
- 단점: 환경 변수 설정 누락 위험

**Option 2: Spring Profile**
```java
@Component
@Profile({"local", "test"})
public class LoadTestDataInitializer { ... }
```
- 장점: Spring 표준 방식, 안전
- 단점: 없음

**Option 3: Conditional Bean**
```java
@ConditionalOnProperty(name = "test.data.enabled", havingValue = "true")
```
- 장점: 세밀한 제어
- 단점: 설정 파일 관리 필요

### 최종 구현 결정

**선택: Option 2 (Spring Profile)**

**이유:**
1. Spring Boot 표준 방식
2. `--spring.profiles.active=prod` 설정만으로 제어
3. Profile별 설정 분리 가능 (application-{profile}.yml)
4. 실수 방지 (prod에서 절대 실행 안 됨)

### 구현 상세

```java
// LoadTestDataInitializer.java
@Slf4j
@Component
@Profile({"local", "test"})  // 🔥 핵심: local, test에서만 실행
@RequiredArgsConstructor
public class LoadTestDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("=== K6 Load Test Data Initializer START ===");
        // Profile이 local 또는 test일 때만 여기 실행됨

        long startTime = System.currentTimeMillis();
        int totalCreated = 0;

        // 0. K6 기본 테스트 사용자 (userId: 1)
        totalCreated += createUsersIfNotExist(1, 1, "K6Test-Default");

        // 1. extremeConcurrency 시나리오용 (1000-10999)
        totalCreated += createUsersIfNotExist(1000, 10999, "K6Test-Extreme");

        // 2. sequentialIssue 시나리오용 (200000-200099)
        totalCreated += createUsersIfNotExist(200000, 200099, "K6Test-Seq");

        // 3. rampUpTest 시나리오용 (300000-309999)
        totalCreated += createUsersIfNotExist(300000, 309999, "K6Test-Ramp");

        long duration = System.currentTimeMillis() - startTime;
        log.info("=== K6 Load Test Data Initializer END ===");
        log.info("Created {} new test users in {}ms", totalCreated, duration);
    }

    private int createUsersIfNotExist(long startId, long endId, String namePrefix) {
        int created = 0;
        String insertSql = "INSERT INTO users (id, email, username, balance, version, created_at, updated_at) " +
                           "VALUES (?, ?, ?, ?, ?, NOW(), NOW())";

        for (long id = startId; id <= endId; id++) {
            if (userRepository.findById(id).isPresent()) {
                continue;  // 이미 존재하면 skip
            }

            String email = String.format("k6test%d@loadtest.com", id);
            String username = String.format("%s-%d", namePrefix, id);

            // userId 1은 충분한 잔액 제공 (K6 테스트용)
            long balance = (id == 1) ? 20_000_000_000L : 10_000L;  // 200억원
            long version = 0L;

            jdbcTemplate.update(insertSql, id, email, username, balance, version);
            created++;

            if (created % 1000 == 0) {
                log.info("Progress: {} users created so far...", created);
            }
        }

        log.info("Created {} users for range {} - {}", created, startId, endId);
        return created;
    }
}
```

### 프로필별 동작

**Local 환경 (개발자 PC):**
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```
- ✅ LoadTestDataInitializer 실행
- ✅ 20,101명 생성
- ✅ K6 테스트 가능

**Test 환경 (CI/CD):**
```bash
./gradlew test  # 자동으로 test 프로필
```
- ✅ LoadTestDataInitializer 실행
- ✅ 통합 테스트용 데이터 생성

**Prod 환경 (운영):**
```bash
java -jar app.jar --spring.profiles.active=prod
```
- ❌ LoadTestDataInitializer 실행 안 됨
- ❌ 테스트 사용자 생성 안 됨
- ✅ 운영 환경 깨끗

### 추가 개선: userId 1 잔액 증가

**문제:**
- K6 테스트 중 userId 1 잔액 소진
- payment 실패율 27.87%

**분석:**
```
K6 테스트 3.5분 동안:
- 총 주문: 10,203회
- 평균 주문 금액: 1,350,000원
- 필요 총액: 10,203 × 1,350,000 = 13,774,050,000원 (137.7억원)

기존 잔액: 1억원 (100,000,000) → 부족!
```

**해결:**
```java
// userId 1에게 200억원 부여 (여유 확보)
long balance = (id == 1) ? 20_000_000_000L : 10_000L;
```

**근거:**
- 필요: 137.7억원
- 제공: 200억원
- 여유: 62.3억원 (45%)

### 트레이드오프

**장점:**
- ✅ 운영 환경 안전 보장
- ✅ 환경별 다른 데이터 전략 가능
- ✅ 실수 방지 (자동 필터링)

**단점:**
- ❌ Profile 설정 누락 시 테스트 실패
- ❌ 개발자가 Profile 개념 알아야 함

**완화 방안:**
- README에 Profile 사용법 명시
- CI/CD에서 자동으로 test 프로필 적용
- 로그에 Profile 정보 출력

### 학습 및 인사이트

**배운 점:**

1. **환경 분리의 중요성:**
   - Dev/Test/Prod 환경은 반드시 구분
   - 테스트 데이터는 운영 환경에 절대 유입 금지

2. **Spring Profile 활용:**
   - `@Profile` 어노테이션으로 Bean 조건부 생성
   - Profile별 설정 파일 분리 가능

3. **테스트 데이터 크기 계산:**
   - 부하 테스트 시나리오 → 필요 데이터 양 계산
   - 넉넉한 여유 (50%) 확보

4. **CommandLineRunner:**
   - 애플리케이션 시작 시 초기화 로직 실행
   - Profile로 제어 가능

**실무 적용:**
- 초기 데이터 생성 로직은 항상 Profile 분리
- Prod에서는 Migration Script 사용 (Flyway, Liquibase)
- 테스트 데이터는 Testcontainers에서 독립적으로 관리

---

## 4. Connection Pool 크기 재검토

### 피드백 내용

> "Connection Pool 200개가 과다할 수 있어요. 계산이 '최대 350 VUs → 200-300개 동시 요청'이라고 하셨는데, 실제로는 각 VU가 항상 connection을 점유하는 게 아니라 요청 중에만 잠깐 사용하거든요. 실제 connection 사용량을 모니터링해서 peak active connection 수치를 확인하고, 그보다 20-30% 여유를 두는 게 적절합니다."

### 문제 인식 및 분석

**기존 설정 (Before):**
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 200  # K6 350 VUs 기준
```

**잘못된 가정:**
```
가정: 350 VUs → 350개 connection 필요
실제: VU는 요청/응답 사이클 동안만 connection 사용
      대부분 시간은 sleep 또는 데이터 처리 중
```

**문제점:**

1. **과다한 리소스 할당:**
   - 200개 connection = 200개 DB 세션
   - 각 세션마다 메모리/CPU 사용

2. **DB 부담:**
   - MySQL max_connections 기본값: 151
   - 200 > 151 → DB 설정 변경 필요

3. **실제 사용량 미확인:**
   - Peak active connection이 50개일 수도 있음
   - 모니터링 없이 추측만으로 설정

4. **Connection Leak 위험:**
   - Pool이 크면 Leak 발견이 늦어짐

### 해결 방안 고민

**Option 1: 200 유지**
- 장점: 충분한 여유
- 단점: 리소스 낭비

**Option 2: 50으로 감소**
- 장점: 리소스 절약
- 단점: 부족할 수 있음

**Option 3: 100으로 조정 + 모니터링**
- 장점: 중간값, 실제 사용량 확인 후 재조정
- 단점: 2단계 작업 필요

### 최종 구현 결정

**선택: Option 3 (100 + 모니터링 필요 명시)**

**이유:**
1. 200은 명백히 과다 (실제 사용량 미확인)
2. 50은 너무 보수적 (병목 가능성)
3. 100은 합리적 중간값
4. **실제 모니터링이 가장 중요** (추측 금지)

### 구현 상세

#### 1) Connection Pool 설정 조정

```yaml
# application.yml
spring:
  datasource:
    hikari:
      # K6 테스트(최대 350 VUs)를 기반으로 200으로 설정했으나,
      # 실제 모니터링 후 튜닝 필요
      # 우선 100으로 조정
      maximum-pool-size: 100
      minimum-idle: 50           # 최소 유휴 커넥션
      connection-timeout: 10000  # 10초 (빠른 실패)
      idle-timeout: 600000       # 10분
      max-lifetime: 1800000      # 30분
      leak-detection-threshold: 30000  # 30초 (누수 감지)
```

**설정 의미:**

1. **maximum-pool-size: 100**
   - 최대 100개 connection 생성
   - 실제 사용량 모니터링 후 조정

2. **minimum-idle: 50**
   - 항상 50개는 유휴 상태 유지
   - 갑작스런 트래픽 증가 대응

3. **connection-timeout: 10000**
   - Connection 획득 대기 시간: 10초
   - 초과 시 예외 발생 (빠른 실패)

4. **leak-detection-threshold: 30000**
   - 30초 이상 connection 점유 시 경고
   - Connection Leak 조기 발견

#### 2) HikariCP 모니터링 로그

```java
// HikariCP 상태 로그 (자동 출력)
HikariPool-1 - Pool stats (total=100, active=42, idle=58, waiting=0)
```

**모니터링 지표:**
- `total`: 현재 Pool 크기
- `active`: 사용 중인 connection
- `idle`: 유휴 connection
- `waiting`: 대기 중인 요청

**목표:**
```
정상 상태: active ≤ 70 (70% 이하)
경고: active > 80 (80% 초과)
위험: active = 100, waiting > 0 (고갈)
```

#### 3) K6 테스트 재실행 계획

```bash
# Before: Connection Pool 200
k6 run step13-ranking-load-test.js
# 결과: http_req_failed < 1%

# After: Connection Pool 100
k6 run step13-ranking-load-test.js
# 모니터링: HikariCP active connections
# 목표: active < 70
```

**시나리오별 예상 사용량:**

| 시나리오 | VUs | Duration | 예상 Active Connections |
|---------|-----|----------|-------------------------|
| getRanking | 200 | 3.5분 | 20-30 |
| createOrder | 100 | 3.5분 | 30-40 |
| verifyRanking | 100 | 30초 | 10-15 |
| **Total** | **350** | **동시** | **60-85** |

**결론:**
- 예상 Peak: 85개
- 설정: 100개
- 여유: 15% (충분)

### Connection Pool 크기 계산 공식

**공식:**
```
Pool Size = (Core Count × 2) + Effective Spindle Count
```

**하지만 실무에서는:**
```
Pool Size = (Peak Active Connections) × 1.2 ~ 1.3
```

**예시:**
```
Peak Active: 70개 (모니터링 결과)
여유: 20% (1.2배)
최종 Pool Size: 70 × 1.2 = 84 → 100으로 반올림
```

### 트레이드오프

**장점:**
- ✅ 리소스 절약 (200 → 100)
- ✅ DB 부담 감소
- ✅ Connection Leak 빠른 발견
- ✅ MySQL 기본 설정 (151) 내에서 여유

**단점:**
- ❌ 트래픽 급증 시 부족 가능
- ❌ 모니터링 후 재조정 필요

**완화 방안:**
- Actuator + Prometheus로 실시간 모니터링
- 알림 설정: active > 80일 때
- Auto Scaling 고려 (Connection Pool도 동적 조정)

### 학습 및 인사이트

**배운 점:**

1. **측정 없이 최적화 없다:**
   - 추측으로 설정 금지
   - 반드시 모니터링 후 결정

2. **VU ≠ Connection:**
   - VU는 논리적 사용자
   - Connection은 물리적 자원
   - 1 VU가 여러 요청 → 1 Connection 재사용

3. **Pool 크기 공식:**
   - 이론적 공식은 참고용
   - 실제 워크로드에 맞게 조정

4. **Connection Leak 감지:**
   - `leak-detection-threshold` 필수
   - 30초 이상 점유 시 경고

**실무 적용:**
- 초기값: 보수적 (50-100)
- 모니터링: Grafana + Prometheus
- 조정: Peak의 120-130%
- 주기적 재검토 (분기별)

---

## 5. K6 테스트 시나리오 개선

### 피드백 내용

> "K6 테스트 시나리오가 실제 트래픽 패턴을 반영하는지 검증이 필요해요. 현재는 350 VUs가 동시에 요청하는 극단적 시나리오인데, 실제 운영 환경에서는 트래픽이 점진적으로 증가하거든요. Ramp-up 시간을 두고 점진적으로 부하를 올리는 시나리오도 추가하면 더 현실적인 테스트가 될 겁니다."

### 문제 인식 및 분석

**기존 시나리오 (Before):**
```javascript
// step14-coupon-concurrency-test.js
export const options = {
  scenarios: {
    // 시나리오 1: 극한 동시성
    extremeConcurrency: {
      executor: 'shared-iterations',
      vus: 100,         // 바로 100명
      iterations: 100,  // 동시에 100번
      maxDuration: '30s',
    },

    // 시나리오 2: 순차 발급
    sequentialIssue: {
      executor: 'per-vu-iterations',
      vus: 1,           // 1명씩
      iterations: 100,
    },
  },
};
```

**문제점:**

1. **비현실적인 트래픽 패턴:**
   ```
   실제: 0명 → 10명 → 50명 → 100명 (점진적)
   테스트: 0명 → 100명 (순간)
   ```

2. **Cold Start 미반영:**
   - Connection Pool 준비 시간 없음
   - Cache Warm-up 시간 없음
   - JVM JIT 컴파일 시간 없음

3. **Peak만 테스트:**
   - 평상시 성능 확인 불가
   - 부하 증가 과정의 문제 미발견

4. **복구 시나리오 없음:**
   - 부하 감소 후 회복력 테스트 없음

### 해결 방안 고민

**Option 1: Ramping VUs (점진적 증가)**
```javascript
{
  executor: 'ramping-vus',
  stages: [
    { duration: '1m', target: 50 },   // 1분 동안 50명까지
    { duration: '3m', target: 100 },  // 3분 동안 100명 유지
    { duration: '1m', target: 0 },    // 1분 동안 0명으로
  ],
}
```
- 장점: 현실적, 점진적 부하
- 단점: 테스트 시간 길어짐

**Option 2: Arrival Rate (도착률 기반)**
```javascript
{
  executor: 'ramping-arrival-rate',
  startRate: 10,   // 초당 10 요청
  timeUnit: '1s',
  stages: [
    { duration: '1m', target: 100 },  // 초당 100 요청까지
  ],
}
```
- 장점: RPS 기반, 더 현실적
- 단점: 설정 복잡

**Option 3: Mixed (극한 + Ramp-up)**
- 기존 극한 시나리오 유지
- Ramp-up 시나리오 추가
- 장점: 양쪽 모두 테스트
- 단점: 테스트 시간 증가

### 최종 구현 결정

**선택: Option 3 (Mixed - 극한 + Ramp-up)**

**이유:**
1. 극한 동시성 테스트는 여전히 필요 (최악의 경우)
2. Ramp-up 테스트로 현실성 추가
3. 두 시나리오 비교로 더 많은 인사이트

### 구현 상세

#### 1) Ramp-up 시나리오 추가

```javascript
// step14-coupon-concurrency-test.js
export const options = {
  scenarios: {
    // 시나리오 1: 극한 동시성 (기존 유지)
    extremeConcurrency: {
      executor: 'shared-iterations',
      vus: 100,
      iterations: 100,
      maxDuration: '30s',
      exec: 'issueCouponConcurrent',
      tags: { test: 'extreme' },
    },

    // 시나리오 2: 순차 발급 (기존 유지)
    sequentialIssue: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 100,
      maxDuration: '2m',
      exec: 'issueCouponSequential',
      tags: { test: 'sequential' },
      startTime: '40s',  // 극한 테스트 후 시작
    },

    // 🆕 시나리오 3: Ramp-up - 점진적 부하 증가
    rampUpTest: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 20 },   // 10초 동안 20명까지
        { duration: '20s', target: 50 },   // 20초 동안 50명까지
        { duration: '10s', target: 0 },    // 10초 동안 0명으로 (회복)
      ],
      exec: 'issueCouponRampUp',
      tags: { test: 'rampup' },
      startTime: '1m30s',  // 다른 테스트 후 시작
    },
  },

  thresholds: {
    // 극한 동시성 (strict)
    'http_req_duration{test:extreme}': ['p(99)<3000'],

    // Ramp-up (relaxed)
    'http_req_duration{test:rampup}': ['p(99)<1000'],  // 더 빠를 것으로 예상

    'coupon_issue_success_rate': ['rate>=0.45', 'rate<=0.55'],
  },
};
```

**Ramp-up 단계 설명:**

```
[Stage 1: Warm-up (10초)]
0명 → 20명
- Connection Pool 초기화
- JVM Warm-up
- Cache Warm-up

[Stage 2: Load (20초)]
20명 → 50명
- 실제 부하 증가
- 시스템 반응 관찰
- Bottleneck 탐지

[Stage 3: Cool-down (10초)]
50명 → 0명
- 시스템 회복력 테스트
- Connection Pool 정리
- Memory GC
```

#### 2) Ramp-up Test 함수

```javascript
// issueCouponRampUp 함수
export function issueCouponRampUp() {
  // userId를 300000-309999 범위에서 선택 (Ramp-up 전용)
  const userId = 300000 + (__VU % 10000);

  const payload = {
    userId,
    couponId: config.couponId,
  };

  const startTime = Date.now();
  const res = http.post(
    `${config.baseUrl}/api/coupons/reserve`,
    JSON.stringify(payload),
    {
      headers: jsonHeaders,
      timeout: '5s',
      tags: { test: 'rampup', scenario: 'rampUpTest' },
    }
  );

  const duration = Date.now() - startTime;
  couponIssueDuration.add(duration, { test: 'rampup' });

  // 성공/실패 체크
  if (res.status === 200 || res.status === 201) {
    couponIssueSuccess.add(1, { test: 'rampup' });
    actualIssuedCount.add(1, { test: 'rampup' });
  } else if (res.status === 409) {  // SOLD_OUT
    soldOutResponses.add(1, { test: 'rampup' });
  } else {
    couponIssueSuccess.add(0, { test: 'rampup' });
  }

  // Ramp-up에서는 약간의 think time 추가 (더 현실적)
  sleep(0.1);  // 100ms think time
}
```

**Extreme vs Ramp-up 차이:**

| 항목 | Extreme | Ramp-up |
|------|---------|---------|
| 시작 VU | 100 (즉시) | 0 → 20 → 50 (점진) |
| Think Time | 없음 | 100ms |
| 목적 | 최악 시나리오 | 현실 시나리오 |
| Threshold | p99 < 3000ms | p99 < 1000ms |

#### 3) 시나리오 타임라인

```
0s ────────── 30s ─────── 90s ──────── 150s ─────── 190s
│              │           │            │            │
├─ Extreme ───┤           │            │            │
│  (100 VUs)  │           │            │            │
│              │           │            │            │
│              └─ Sequential ───────────┤            │
│                 (1 VU)                │            │
│                                       │            │
│                                       └─ Ramp-up ──┤
│                                          (0→20→50→0)
```

**총 테스트 시간: 약 3분 10초**

### 기대 효과

**1. 현실적인 성능 지표:**
```
Extreme (극한):
- p50: 200ms
- p95: 1000ms
- p99: 3000ms

Ramp-up (현실):
- p50: 50ms    (4배 빠름)
- p95: 200ms   (5배 빠름)
- p99: 500ms   (6배 빠름)
```

**2. Bottleneck 조기 발견:**
- Ramp-up에서 20명 → 50명 증가 시 급격한 지연
- → Connection Pool 부족 신호

**3. Cold Start 문제 식별:**
- 첫 10초 동안 응답 시간 불안정
- → Warm-up 로직 필요

**4. 회복력 테스트:**
- 50명 → 0명 감소 후에도 응답 시간 정상인지
- → Connection 누수 없는지 확인

### 트레이드오프

**장점:**
- ✅ 현실적인 트래픽 패턴
- ✅ 점진적 부하 증가 테스트
- ✅ 시스템 회복력 검증
- ✅ Cold Start 문제 발견

**단점:**
- ❌ 테스트 시간 증가 (30초 → 3분)
- ❌ 시나리오 복잡도 증가
- ❌ 결과 분석 더 어려움

**완화 방안:**
- 필요 시 개별 시나리오만 실행 가능
- Tags로 결과 필터링 (test:extreme, test:rampup)
- CI/CD에서는 Extreme만 실행 (빠른 피드백)

### 학습 및 인사이트

**배운 점:**

1. **테스트 현실성:**
   - 극단적 시나리오만으로는 부족
   - 실제 사용자는 점진적으로 증가

2. **K6 Executor 종류:**
   - `shared-iterations`: 고정 횟수 (극한 테스트)
   - `ramping-vus`: 점진적 증가 (현실 테스트)
   - `ramping-arrival-rate`: RPS 기반 (더 정교)

3. **Think Time의 중요성:**
   - 실제 사용자는 요청 사이 지연 존재
   - Think time 없으면 비현실적 부하

4. **Warm-up vs Peak:**
   - Cold Start: 느린 응답
   - Warm-up 후: 빠른 응답
   - 두 상황 모두 테스트 필요

**실무 적용:**
- 부하 테스트는 여러 시나리오 조합
- Smoke Test (1 VU) → Load Test (Ramp-up) → Stress Test (Extreme)
- 각 단계별 다른 Threshold 설정

---

## 6. 성능 개선 수치 문서화

### 피드백 내용

> "성능 개선 수치를 검증하면 더 좋겠어요. PR에서 '1,857배 개선(30,065ms → 16ms)'이라고 하셨는데, 30,065ms는 Connection Pool이 고갈된 비정상 상태의 수치거든요. 정상 상태의 Before 수치를 측정해서 비교하면 더 정확한 개선율을 보여줄 수 있어요."

### 문제 인식 및 분석

**기존 문서 (Before):**
```markdown
## 성능 개선 결과
- Before: 30,065ms (p95)
- After: 16ms (p95)
- 개선율: 1,857배 🎉
```

**문제점:**

1. **비정상 상태 비교:**
   ```
   Before (Connection Pool 고갈):
   - total=50, active=50, idle=0, waiting=51
   - 30초 타임아웃 발생
   - → 이건 장애 상황!

   After (Connection Pool 충분):
   - total=200, active=42, idle=158, waiting=0
   - 16ms 응답
   ```

2. **잘못된 개선율:**
   ```
   30,065ms vs 16ms 비교는 부적절

   정확한 비교:
   Before (정상 상태): 50ms (추정)
   After (개선 후): 16ms
   개선율: 3.1배 (312% 개선)
   ```

3. **컨텍스트 부족:**
   - 왜 30초가 걸렸는지 설명 없음
   - Connection Pool 고갈이 원인임을 명시 안 함

### 해결 방안 고민

**Option 1: 비교 수치 삭제**
- 장점: 오해 방지
- 단점: 개선 효과 전달 안 됨

**Option 2: 정상 상태 재측정**
- 장점: 정확한 비교
- 단점: 시간/환경 필요

**Option 3: 컨텍스트 추가**
- 비정상 상태임을 명시
- 정상 상태 비교 필요성 언급
- 장점: 학습 효과
- 단점: 없음

### 최종 구현 결정

**선택: Option 3 (컨텍스트 추가 + 주의사항)**

**이유:**
1. 실수로부터 배우기 (교육적 가치)
2. 독자에게 올바른 비교 방법 전달
3. 정상 상태 측정 필요성 강조

### 구현 상세

#### 1) PERFORMANCE_IMPROVEMENTS.md 수정

```markdown
## 📊 예상 결과

### Before v1 (첫 테스트 - Connection Pool 부족)
```
✗ http_req_failed: 49.74%
✗ ranking_query p(95): 30,065ms (600배 초과!)
  - **주의**: 이 수치는 Connection Pool이 고갈된 비정상 상태의 수치입니다.
    정확한 개선율을 파악하려면 정상 상태의 Before 수치를 측정해서
    비교해야 합니다.

    예) "1,857배 개선(30,065ms → 16ms)" 주장은 이 비정상 수치에
        기반한 것이므로 컨텍스트 이해 필요

  - 정상 상태 Before 수치 (추정): 50-100ms
  - 실제 개선율 (추정): 3-6배 (50-100ms → 16ms)

✗ ranking_update p(95): 60,000ms (120배 초과!)
✗ ranking_accuracy: 3.57%
✗ dropped_iterations: 3,201
- HikariPool exhausted (total=50, active=50, idle=0, waiting=51)
```

**왜 30초가 걸렸나?**
1. Connection Pool 완전 고갈 (50/50 사용 중)
2. 51개 요청이 Connection 대기 중
3. HTTP timeout 30초 설정
4. 대부분의 요청이 30초 대기 후 실패

**교훈:**
- 장애 상황과 정상 상태를 비교하면 안 됨
- 개선율은 같은 조건에서 측정해야 유의미
- Connection Pool 크기가 성능에 미치는 영향 이해
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

**정상 상태 Before 측정 필요:**
```bash
# Connection Pool 충분한 상태에서 재측정
# 1. Pool size 100 설정
# 2. VUs 50으로 제한 (고갈 방지)
# 3. Before 수치 측정

k6 run \
  -e RANKING_MAX_VUS=50 \
  -e ORDER_PEAK_VUS=25 \
  step13-ranking-load-test.js

# 예상 결과 (정상 상태):
# ranking_query p(95): 50-100ms
# ranking_update p(95): 200-300ms
```
```

#### 2) 올바른 성능 측정 가이드 추가

```markdown
## 📏 성능 측정 베스트 프랙티스

### 1. 같은 조건에서 비교
```bash
# ❌ 잘못된 비교
Before: Pool 50, VUs 350 → 30초 (장애)
After:  Pool 200, VUs 350 → 16ms (정상)
→ 1,857배 개선? NO! 장애 vs 정상 비교

# ✅ 올바른 비교
Before: Pool 100, VUs 50 → 80ms (정상)
After:  Pool 100, VUs 50 → 16ms (개선)
→ 5배 개선 (정확)
```

### 2. Baseline 먼저 측정
```
1단계: Baseline 측정 (최적화 전, 정상 상태)
2단계: 최적화 적용
3단계: 개선 후 측정 (같은 조건)
4단계: 비교 및 분석
```

### 3. 여러 지표 종합 판단
```
단일 지표만 보지 말고:
- p50, p95, p99 모두 확인
- Throughput (RPS)
- Error Rate
- CPU/Memory 사용률
```

### 4. 통계적 유의성
```
1번 측정: 우연일 수 있음
3번 측정: 평균 및 편차 확인
→ 신뢰할 수 있는 개선율
```
```

### 트레이드오프

**장점:**
- ✅ 독자에게 올바른 측정 방법 교육
- ✅ 실수를 투명하게 공개 (신뢰도 향상)
- ✅ 향후 같은 실수 방지

**단점:**
- ❌ 기존 개선율 주장 철회 (겸손해야 함)
- ❌ 추가 측정 작업 필요

### 학습 및 인사이트

**배운 점:**

1. **장애 vs 정상:**
   - 장애 상황의 수치는 의미 없음
   - 항상 정상 상태끼리 비교

2. **Baseline의 중요성:**
   - 최적화 전 정상 상태 측정 필수
   - Baseline 없으면 개선 효과 알 수 없음

3. **통계적 사고:**
   - 1번 측정은 우연
   - 여러 번 측정 후 평균

4. **투명성:**
   - 실수를 숨기지 않고 공개
   - 학습 과정을 문서화

**실무 적용:**
- 성능 개선 PR에는 항상 Before/After 비교
- 측정 조건 명시 (HW, 부하, 설정)
- 여러 번 측정 후 평균값 사용
- P50, P95, P99 모두 보고

---

## 전체 회고

### 피드백 수용 과정

**1. 초기 반응:**
- "이미 잘 만들었는데 왜 수정해야 하지?" (방어적)
- "시간이 더 걸리는데..." (부담)

**2. 분석 단계:**
- Gemini에게 피드백 분석 요청
- 각 항목의 타당성 검토
- 트레이드오프 고려

**3. 수용 단계:**
- "코치님 말씀이 맞네" (깨달음)
- "이렇게 개선하면 더 좋겠다" (긍정)

**4. 실행 단계:**
- 6개 항목 모두 반영
- 추가 개선사항까지 적용

### 주요 학습

**1. 기술적 학습:**
- Redis Only 아키텍처 (Single Source of Truth)
- Graceful Degradation (DB Fallback)
- Spring Profile 활용
- K6 Ramp-up 테스트

**2. 프로세스 학습:**
- 피드백 수용 태도
- 트레이드오프 분석
- 문서화의 중요성
- 투명한 실수 공개

**3. 사고방식 변화:**
- "완벽한 코드"는 없다 → 지속적 개선
- "일단 동작"에서 "제대로 동작"으로
- 측정 기반 의사결정
- 실무 관점 사고

### Gemini 활용 효과

**장점:**
1. **전체 맥락 파악:**
   - 6개 피드백을 종합적으로 분석
   - 우선순위 제시

2. **다양한 관점:**
   - Option 1, 2, 3 비교
   - 각 선택의 트레이드오프 제시

3. **구현 계획:**
   - 파일 단위 변경 계획
   - 순서 제시

**한계:**
1. **세부 구현:**
   - 실제 코드 작성은 직접
   - Gemini 제안을 참고만

2. **프로젝트 컨텍스트:**
   - 기존 코드 스타일 모름
   - 일관성 유지는 직접 챙겨야

**효과적 사용법:**
- 계획 수립: Gemini
- 상세 구현: 직접 + Claude Code
- 검증: 직접 + 테스트

### 개선 효과 요약

| 항목 | Before | After | 효과 |
|------|--------|-------|------|
| Redis 장애 대응 | 빈 목록 | DB Fallback | 가용성 ↑ |
| DB Write | 3회 | 1회 | 성능 ↑ 66% |
| 운영 환경 안전 | 위험 | Profile 분리 | 안전성 ↑ |
| Connection Pool | 200 (과다) | 100 (적정) | 리소스 ↓ 50% |
| 테스트 현실성 | 극한만 | Ramp-up 추가 | 품질 ↑ |
| 문서 정확성 | 오해 | 명확한 설명 | 신뢰도 ↑ |

### 아쉬운 점

1. **정상 상태 Before 미측정:**
   - 시간 부족으로 실제 측정 못 함
   - 추정값만 문서화

2. **Connection Pool 모니터링 미구현:**
   - Grafana 대시보드 구성 못 함
   - 실제 사용량 확인 필요

3. **Redis Failover 테스트 미실행:**
   - DB Fallback 코드만 작성
   - 실제 장애 시나리오 테스트 필요

### 앞으로의 개선 방향

**단기 (이번 주):**
- [ ] Connection Pool 모니터링 구현
- [ ] 정상 상태 Before 측정
- [ ] Redis Failover 테스트

**중기 (다음 Sprint):**
- [ ] Grafana 대시보드 구성
- [ ] Alerting 설정 (Connection Pool > 80%)
- [ ] K6 테스트 자동화 (CI/CD)

**장기 (차기 과제):**
- [ ] Auto Scaling 고려
- [ ] Multi-region Redis
- [ ] Circuit Breaker 패턴

---

## 다음 단계

### 즉시 실행 (이번 주)

#### 1. Connection Pool 모니터링

**목표:** 실제 사용량 확인

**Task:**
```bash
# 1. HikariCP Metrics 활성화
spring.datasource.hikari.register-mbeans=true

# 2. Actuator 엔드포인트 확인
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active

# 3. K6 테스트 중 모니터링
watch -n 1 'curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.active | jq'

# 4. Peak 값 기록
```

**기대 결과:**
```
Peak Active Connections: 60-80
→ Pool Size 100 적정 확인
```

#### 2. 정상 상태 Before 측정

**목표:** 정확한 개선율 산출

**Task:**
```bash
# 1. 최적화 전 상태로 롤백 (별도 브랜치)
git checkout before-optimization

# 2. Connection Pool 100으로 설정 (고갈 방지)

# 3. VUs 50으로 제한 테스트
k6 run \
  -e RANKING_MAX_VUS=50 \
  -e ORDER_PEAK_VUS=25 \
  step13-ranking-load-test.js

# 4. 결과 기록
ranking_query p(95): ???ms
ranking_update p(95): ???ms

# 5. 현재 브랜치와 비교
git checkout step15-16-gemini
k6 run (같은 조건)

# 6. 정확한 개선율 계산
```

#### 3. Redis Failover 테스트

**목표:** DB Fallback 동작 확인

**Task:**
```bash
# 1. Redis 정상 동작 확인
curl http://localhost:8080/api/products/ranking/top

# 2. Redis 중지
docker stop redis

# 3. API 호출 (DB Fallback 기대)
curl http://localhost:8080/api/products/ranking/top
# 응답: 최근 10분 내 백업 데이터

# 4. 로그 확인
tail -f logs/application.log | grep "DB 백업"

# 5. Redis 재시작
docker start redis

# 6. 정상 복구 확인
```

### 중기 계획 (다음 Sprint)

#### 1. Grafana 대시보드

**패널 구성:**
- HikariCP Active Connections (Gauge)
- HikariCP Wait Time (Graph)
- Redis Hit Rate (Graph)
- API Response Time (Heatmap)

#### 2. Alerting 설정

**알림 규칙:**
```yaml
- alert: ConnectionPoolNearFull
  expr: hikaricp_connections_active / hikaricp_connections_max > 0.8
  for: 1m
  annotations:
    summary: "Connection Pool 80% 초과"

- alert: RedisDown
  expr: redis_up == 0
  for: 30s
  annotations:
    summary: "Redis 장애 발생"
```

#### 3. K6 CI/CD 통합

```yaml
# .github/workflows/load-test.yml
name: Load Test
on:
  pull_request:
    branches: [main]

jobs:
  k6-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Run K6
        run: |
          k6 run step13-ranking-load-test.js
      - name: Check Thresholds
        run: |
          if [ $? -ne 0 ]; then
            echo "Performance regression detected!"
            exit 1
          fi
```

### 장기 비전 (차기 과제)

#### 1. Auto Scaling

**Connection Pool 동적 조정:**
```java
@Component
public class DynamicPoolSizer {
    @Scheduled(fixedRate = 60000)  // 1분마다
    public void adjustPoolSize() {
        int active = getActiveConnections();
        int max = getMaxPoolSize();

        if (active > max * 0.8) {
            increasePoolSize(max * 1.2);  // 20% 증가
        } else if (active < max * 0.3) {
            decreasePoolSize(max * 0.8);  // 20% 감소
        }
    }
}
```

#### 2. Multi-region Redis

**Replication:**
```yaml
# Redis Sentinel 구성
sentinel monitor mymaster redis-1 6379 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
```

#### 3. Circuit Breaker

**Resilience4j:**
```java
@CircuitBreaker(name = "rankingService", fallbackMethod = "getFromDb")
public RankingResponse getTopProducts() {
    return redisRankingRepository.getTopN(date, limit);
}

public RankingResponse getFromDb(Exception e) {
    return dbRankingRepository.findByAggregatedDate(date);
}
```

---

## 마치며

### 핵심 메시지

**1. 피드백은 성장의 기회**
- 방어적 태도 → 배움의 기회
- "이미 완성"은 없다 → 지속적 개선

**2. 측정 기반 의사결정**
- 추측 금지 → 측정 후 결정
- 정상 상태 비교 → 정확한 개선율

**3. 트레이드오프 이해**
- 모든 결정에는 장단점
- 상황에 맞는 선택

**4. 문서화의 힘**
- 사고 과정 기록 → 학습 가속
- 실수 공개 → 신뢰 구축

### 감사의 말

**코치님께:**
- 상세하고 구체적인 피드백 감사합니다
- 단순히 "좋다/나쁘다"가 아닌 "왜, 어떻게" 설명
- 실무 관점의 조언으로 큰 배움을 얻었습니다

**Gemini에게 (?):**
- 피드백 분석과 개선 계획 수립에 도움
- 다양한 관점 제시로 사고 확장

**나 자신에게:**
- 겸손하게 배우는 자세 유지
- 완벽보다는 지속적 개선
- 측정하고, 분석하고, 개선하는 습관

---

**끝.**

> "The only way to go fast is to go well."
> — Robert C. Martin (Uncle Bob)

빠르게 가는 유일한 방법은 제대로 가는 것이다.
