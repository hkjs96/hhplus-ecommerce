# Redis 기반 선착순 쿠폰 발급 시스템

## 개요

Redis를 활용하여 동시 다발적 요청에서도 선착순 수량을 정확히 보장하는 쿠폰 발급 시스템을 구현합니다.

---

## 핵심 설계 원칙

### 1. 데이터 배치 전략

**DB (MySQL):**
- 쿠폰 메타 정보 (마스터 데이터)
  - 쿠폰 ID, 이름, 할인율, 유효기간
  - 총 발급 가능 수량
  - 생성/수정 일시

**Redis:**
- 선착순 재고 관리 (실시간 차감)
  - `coupon:{id}:remain` → 남은 수량 (Integer)
- 발급자 기록 (중복 방지)
  - `coupon:{id}:issued` → 발급된 userId Set

**왜 분리하는가?**
- DB: 안정성, 백오피스 조회, 통계
- Redis: 고속 처리, 동시성 제어, 원자성 보장

---

## ⚠️ 핵심 정합성 규칙 (트랜잭션)

### 반드시 지켜야 할 원칙

**"잔여 수량 차감"과 "userId 발급 기록"은 하나의 트랜잭션 단위로 처리**

```
✅ 성공 케이스:
  1. 잔여 수량 10 → 9 (DECR)
  2. userId 123 발급 기록 (SADD)
  → 둘 다 성공

❌ 실패 케이스 (둘 중 하나라도 실패):
  1. 잔여 수량 차감 성공
  2. userId 발급 기록 실패 (네트워크 오류)
  → 잔여 수량 원복 필요 (INCR)
```

**❌ 절대 하지 말 것:**
- 수량 차감만 하고 발급 기록은 스케줄러로 나중에 처리
- 발급 기록만 하고 수량 차감은 배치로 처리
- 둘 중 하나 실패 시 그대로 방치

---

## 구현 방식

### 방식 1: Lua 스크립트 (권장)

**장점:**
- Redis 서버에서 원자적으로 실행
- 네트워크 왕복 1회로 처리
- Race Condition 원천 차단

**주의:**
- **짧게 작성** (Redis는 단일 스레드, 긴 스크립트는 전체 지연 유발)
- CPU 연산 최소화

**Lua 스크립트 예시:**
```lua
-- issue_coupon.lua
local remainKey = KEYS[1]      -- coupon:{id}:remain
local issuedKey = KEYS[2]      -- coupon:{id}:issued
local userId = ARGV[1]         -- 123

-- 1. 중복 발급 체크
if redis.call('SISMEMBER', issuedKey, userId) == 1 then
    return -1  -- 이미 발급됨
end

-- 2. 잔여 수량 체크
local remain = tonumber(redis.call('GET', remainKey))
if remain == nil or remain <= 0 then
    return -2  -- 수량 부족
end

-- 3. 수량 차감
redis.call('DECR', remainKey)

-- 4. 발급 기록
redis.call('SADD', issuedKey, userId)

return 1  -- 성공
```

**Java 코드:**
```java
@Service
public class CouponIssueService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> issueCouponScript;

    public CouponIssueResult issueCoupon(Long couponId, Long userId) {
        String remainKey = "coupon:" + couponId + ":remain";
        String issuedKey = "coupon:" + couponId + ":issued";

        List<String> keys = List.of(remainKey, issuedKey);
        Long result = redisTemplate.execute(
            issueCouponScript,
            keys,
            userId.toString()
        );

        return switch (result.intValue()) {
            case 1 -> CouponIssueResult.success();
            case -1 -> CouponIssueResult.alreadyIssued();
            case -2 -> CouponIssueResult.soldOut();
            default -> CouponIssueResult.failed();
        };
    }
}
```

---

### 방식 2: 개별 명령 + 방어적 롤백 (대안)

**장점:**
- Lua 스크립트 학습 불필요
- 디버깅 용이

**단점:**
- 네트워크 왕복 증가
- 롤백 로직 필수

**구현 예시:**
```java
public CouponIssueResult issueCoupon(Long couponId, Long userId) {
    String remainKey = "coupon:" + couponId + ":remain";
    String issuedKey = "coupon:" + couponId + ":issued";

    try {
        // 1. 중복 발급 체크
        Boolean alreadyIssued = redisTemplate.opsForSet()
            .isMember(issuedKey, userId.toString());

        if (Boolean.TRUE.equals(alreadyIssued)) {
            return CouponIssueResult.alreadyIssued();
        }

        // 2. 수량 차감 (원자적)
        Long remain = redisTemplate.opsForValue().decrement(remainKey);

        // 3. 수량 부족 체크
        if (remain == null || remain < 0) {
            // 롤백: 수량 복구
            redisTemplate.opsForValue().increment(remainKey);
            return CouponIssueResult.soldOut();
        }

        // 4. 발급 기록
        Long addResult = redisTemplate.opsForSet()
            .add(issuedKey, userId.toString());

        if (addResult == null || addResult == 0) {
            // 롤백: 수량 복구
            redisTemplate.opsForValue().increment(remainKey);
            throw new CouponIssueException("발급 기록 실패");
        }

        return CouponIssueResult.success();

    } catch (Exception e) {
        // 예외 발생 시 롤백 처리
        log.error("쿠폰 발급 실패: couponId={}, userId={}", couponId, userId, e);
        rollbackCouponIssue(couponId, userId);
        throw new CouponIssueException("쿠폰 발급 실패", e);
    }
}

private void rollbackCouponIssue(Long couponId, Long userId) {
    String remainKey = "coupon:" + couponId + ":remain";
    String issuedKey = "coupon:" + couponId + ":issued";

    // 발급 기록 제거
    redisTemplate.opsForSet().remove(issuedKey, userId.toString());

    // 수량 복구
    redisTemplate.opsForValue().increment(remainKey);
}
```

---

## 에러 케이스 처리

### 1. 수량 부족 (Sold Out)
```java
if (remain <= 0) {
    return CouponIssueResult.soldOut();
}
```

**Response:**
```json
{
  "success": false,
  "errorCode": "COUPON_SOLD_OUT",
  "message": "선착순 마감되었습니다"
}
```

### 2. 중복 발급
```java
if (alreadyIssued) {
    return CouponIssueResult.alreadyIssued();
}
```

**Response:**
```json
{
  "success": false,
  "errorCode": "ALREADY_ISSUED",
  "message": "이미 발급받은 쿠폰입니다"
}
```

### 3. 수량 마이너스 방지
```java
// DECR 후 0 미만이면 즉시 롤백
Long remain = redisTemplate.opsForValue().decrement(remainKey);

if (remain < 0) {
    redisTemplate.opsForValue().increment(remainKey);  // 원복
    return CouponIssueResult.soldOut();
}
```

**주의:** 수량이 -1, -2, -3... 이 되도록 방치하면 안 됨

---

## DB-Redis 싱크 전략

### 초기 데이터 로딩
```java
@Component
public class CouponDataInitializer {

    @EventListener(ApplicationReadyEvent.class)
    public void initializeCouponData() {
        List<Coupon> activeCoupons = couponRepository.findAllActive();

        for (Coupon coupon : activeCoupons) {
            String remainKey = "coupon:" + coupon.getId() + ":remain";

            // DB의 발급 가능 수량을 Redis에 세팅
            redisTemplate.opsForValue().set(
                remainKey,
                String.valueOf(coupon.getTotalQuantity())
            );
        }
    }
}
```

### 발급 후 DB 기록 (비동기)
```java
@EventListener
@Async
public void recordCouponIssue(CouponIssuedEvent event) {
    UserCoupon userCoupon = UserCoupon.builder()
        .userId(event.getUserId())
        .couponId(event.getCouponId())
        .status(CouponStatus.AVAILABLE)
        .issuedAt(LocalDateTime.now())
        .build();

    userCouponRepository.save(userCoupon);
}
```

**주의:**
- 발급 성공은 Redis 기준
- DB 기록은 통계/백오피스용 (eventual consistency)
- DB 기록 실패해도 발급은 유효

---

## 동시성 테스트 예시

```java
@Test
void 선착순_쿠폰_발급_동시성_테스트() throws InterruptedException {
    // Given
    Long couponId = 1L;
    int totalQuantity = 100;
    int threadCount = 1000;

    // Redis 초기화
    String remainKey = "coupon:" + couponId + ":remain";
    redisTemplate.opsForValue().set(remainKey, String.valueOf(totalQuantity));

    // When
    ExecutorService executorService = Executors.newFixedThreadPool(100);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        long userId = i;
        executorService.submit(() -> {
            try {
                CouponIssueResult result = couponIssueService.issueCoupon(couponId, userId);
                if (result.isSuccess()) {
                    successCount.incrementAndGet();
                }
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();
    executorService.shutdown();

    // Then
    assertThat(successCount.get()).isEqualTo(totalQuantity);  // 정확히 100개만 발급

    String remain = redisTemplate.opsForValue().get(remainKey);
    assertThat(remain).isEqualTo("0");  // 남은 수량 0

    Long issuedCount = redisTemplate.opsForSet().size("coupon:" + couponId + ":issued");
    assertThat(issuedCount).isEqualTo(totalQuantity);  // 발급 기록 100개
}
```

---

## 주의사항

### 1. Redis 데이터 만료 정책
- 쿠폰 유효기간과 Redis TTL 일치시키기
```java
Duration ttl = Duration.between(LocalDateTime.now(), coupon.getExpiredAt());
redisTemplate.expire(remainKey, ttl);
redisTemplate.expire(issuedKey, ttl);
```

### 2. Redis 장애 대비
- Redis 다운 시 발급 중단 (DB만으로는 동시성 보장 불가)
- Redis HA 구성 (Sentinel, Cluster) 권장

### 3. 수량 오차 방지
- 발급 수량 차감과 기록은 반드시 트랜잭션 단위
- 스케줄러로 나중에 맞추는 방식 절대 금지

### 4. Lua 스크립트 길이
- 짧게 유지 (Redis 단일 스레드 특성)
- 복잡한 로직은 Application에서 처리

---

## 참고: QnA 핵심 요약

> **김종협 코치님:**
> - 수량 차감과 발급 기록은 **트랜잭션으로 묶어야 함**
> - 트랜잭션 불가능하면 **방어 로직으로 원복**
> - 스케줄러로 나중에 맞추는 방식 절대 금지
> - 손실 발생하면 안 됨, 실시간 처리 필수

---

## 참고: Redis 공식 문서

- [Lua Scripting](https://redis.io/docs/manual/programmability/eval-intro/)
- [DECR](https://redis.io/commands/decr/)
- [SADD](https://redis.io/commands/sadd/)
- [SISMEMBER](https://redis.io/commands/sismember/)
