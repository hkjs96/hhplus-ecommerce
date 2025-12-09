# 선착순 쿠폰 예약 테이블 설계 결정 문서

> **📝 2025-12-09 업데이트:** Step 13-14 피드백 반영 - CouponReservation 테이블 제거
>
> **변경 내역:**
> - ❌ CouponReservation 테이블 제거 (Redis Only 구조로 변경)
> - ✅ Redis가 선착순 판정의 Single Source of Truth
> - ✅ DB는 최종 발급 내역(UserCoupon)만 저장
> - ✅ DB write 66% 감소 (3회 → 1회)
>
> **상세 문서:** [FEEDBACK_IMPROVEMENTS.md](./FEEDBACK_IMPROVEMENTS.md#2-couponreservation-테이블-제거)

## 📋 목차
1. [설계 배경](#설계-배경)
2. [핵심 질문: sequenceNumber의 역할](#핵심-질문-sequencenumber의-역할)
3. [NULL 문제와 해결 방법](#null-문제와-해결-방법)
4. [최종 설계 결정 (2025-12-09 변경)](#최종-설계-결정)
5. [구현 상세](#구현-상세)

---

## 설계 배경

### 두 가지 쿠폰 타입

#### 1️⃣ 일반 쿠폰 (수량 제한 없음)

**예시:** "신규 가입 환영 쿠폰", "생일 축하 쿠폰"

**특징:**
- 수량 제한 없음 (또는 매우 큼)
- 1인 1개 제한만 존재
- 선착순 개념 없음

**필요한 것:**
- 중복 발급 방지: `unique(user_id, coupon_id)`
- 쿠폰 메타: `Coupon` 테이블
- 발급 내역: `UserCoupon` 테이블
- **Redis 불필요** - DB만으로 충분

---

#### 2️⃣ 선착순 쿠폰 (수량 제한)

**예시:** "블랙프라이데이 선착순 1000명", "오픈 기념 선착순 100명"

**특징:**
- 정확한 수량 제한 (100개, 1000개 등)
- 동시 다발적 요청 (트래픽 폭증)
- 선착순 순서가 중요할 수 있음

**필요한 것:**
- 정확한 수량 제어: **Redis INCR** (원자적)
- 중복 발급 방지: `unique(user_id, coupon_id)` + Redis SISMEMBER
- 발급 내역: `UserCoupon` 테이블
- **순번 기록**: 감사/UX 목적
- **Redis 필수** - 동시성 제어

---

## 핵심 질문: sequenceNumber의 역할

### sequenceNumber란?

Redis INCR 결과로, 선착순 요청 순서를 나타내는 숫자

```java
Long sequence = redis.increment("coupon:1:sequence");
// 1, 2, 3, ..., 42, ..., 100, 101, ...
```

---

### sequenceNumber는 비즈니스 로직에 필수적인가?

**핵심 로직:**
```java
Long seq = redis.incr("coupon:1:sequence");
if (seq <= 100) {
    // ✅ 선착순 자격 획득!
    // 여기서 seq를 DB에 저장 안 해도 로직은 동작함
}
```

**답변: 필수는 아니지만, 있으면 좋다**

---

### sequenceNumber를 저장하는 이유

#### ✅ 있으면 좋은 이유

1. **감사/추적 목적**
   - "이 사용자는 42번째로 요청했다"
   - 법적 분쟁 시 증빙 자료

2. **디버깅**
   - Redis와 DB 불일치 발견
   - "Redis sequence는 150인데 DB에는 100개만 있네?"

3. **사용자 경험**
   - "축하합니다! 당신은 42번째 고객입니다!"
   - 순번 자체가 가치 (1등, 100등)

4. **복구 참고**
   - Redis 장애 시 마지막 순번 확인
   - 수동 복구 근거 자료

---

#### ❌ 없어도 되는 이유

1. **핵심 로직은 Redis INCR만으로 완성**
   - "100명 안에 들었는지"만 중요
   - 몇 번째인지는 부가 정보

2. **Redis가 Source of Truth**
   - Redis만 믿고 감
   - DB는 발급 내역만 기록

3. **불일치 가능성**
   - Redis sequence = 150
   - DB에 저장된 건 = 100개 (50개 실패)
   - 순번 낭비 발생

---

### 결론: sequenceNumber는 **선택 사항**

**STEP 14 과제 관점:**
- 없어도 선착순 로직은 완벽히 동작
- 하지만 **감사/추적을 위해 저장하는 것을 권장**

---

## NULL 문제와 해결 방법

### 문제 정의

만약 `user_coupons` 테이블에 `sequence_number` 컬럼을 추가하면:

```sql
-- 일반 쿠폰 발급
id | user_id | coupon_id | sequence_number | status
1  | 123     | 5         | NULL            | AVAILABLE  ← NULL!

-- 선착순 쿠폰 발급
2  | 456     | 1         | 42              | AVAILABLE
```

**고민:**
- sequenceNumber는 추적용으로 저장하고 싶다
- 하지만 일반 쿠폰은 **항상 NULL** → 깔끔하지 않음
- 컬럼의 의미가 불명확 (있을 수도, 없을 수도)

---

## 해결 방법 비교

### 방법 1: NULL 그냥 허용 ⭐⭐⭐ (실용적)

```java
@Column(name = "sequence_number", nullable = true)
private Long sequenceNumber;  // 일반 쿠폰은 NULL
```

**장점:**
- 가장 간단
- 테이블 하나로 모든 쿠폰 관리
- 실무에서 흔히 쓰이는 방식

**단점:**
- NULL 체크 필요
  ```java
  if (userCoupon.getSequenceNumber() != null) {
      // 선착순 쿠폰
  }
  ```
- 데이터베이스 관점에서 "깔끔하지 않음"
- 3-Valued Logic (True, False, NULL) 복잡

**평가:**
- 복잡도: ⭐ (매우 간단)
- 정규화: ❌
- 실용성: ✅

---

### 방법 2: 기본값 사용 (0 또는 -1) ⭐

```java
@Column(name = "sequence_number", nullable = false)
private Long sequenceNumber;  // 일반 쿠폰은 0

// 발급 시
일반 쿠폰: sequenceNumber = 0
선착순 쿠폰: sequenceNumber = 42
```

**장점:**
- NOT NULL 제약 가능
- NULL 체크 불필요

**단점:**
- 0이 "값 없음"인지 "진짜 0번째"인지 애매
- 의미 혼동 가능
- 쿼리 시 `WHERE sequence_number > 0` 같은 조건 필요

**평가:**
- 복잡도: ⭐
- 정규화: ❌
- 권장도: ⭐ (비추천)

---

### 방법 3: JSON 컬럼 사용 ⭐

```java
@Column(name = "metadata", columnDefinition = "json")
private String metadata;

// 일반 쿠폰
metadata = {}

// 선착순 쿠폰
metadata = {"sequenceNumber": 42, "reservedAt": "2025-12-04T10:00:00"}
```

**장점:**
- 쿠폰 타입별 다른 메타데이터 저장 가능
- 확장성 좋음

**단점:**
- JSON 파싱 오버헤드
- 인덱스 안 됨 (검색 어려움)
- JPA 매핑 복잡

**평가:**
- 복잡도: ⭐⭐⭐
- 정규화: ❌
- 권장도: ⭐ (오버엔지니어링)

---

### 방법 4: 1:1 분리 테이블 ⭐⭐⭐

```sql
-- user_coupons (모든 쿠폰)
id | user_id | coupon_id | status

-- user_coupon_sequences (선착순 정보만)
user_coupon_id | sequence_number | reserved_at
2              | 42              | 2025-12-04 10:00:00
```

**장점:**
- NULL 없음
- 선착순 정보는 별도 테이블에만
- 일반 쿠폰은 user_coupons만 사용 (깔끔)
- 정규화 원칙 준수

**단점:**
- 조인 필요
- 테이블 하나 더

**평가:**
- 복잡도: ⭐⭐
- 정규화: ✅
- 권장도: ⭐⭐⭐ (정석)

---

### 방법 5: 예약 테이블 (선택) ⭐⭐⭐⭐

```sql
-- coupon_reservations (선착순 자격 기록)
id | user_id | coupon_id | sequence_number | status
1  | 456     | 1         | 42              | ISSUED

-- user_coupons (발급 완료된 쿠폰만)
id | user_id | coupon_id | status
1  | 123     | 5         | AVAILABLE  ← 일반 쿠폰 (sequence_number 없음)
2  | 456     | 1         | AVAILABLE  ← 선착순 쿠폰 (sequence_number 없음)
```

**특징:**
- `user_coupons`에는 `sequence_number` 컬럼 자체가 없음!
- 선착순 정보는 `coupon_reservations`에만 존재
- 두 테이블을 조인하면 전체 정보 확인 가능

**장점:**
- **NULL 문제 완전 해결** ✅
- 개념적 명확성 (예약 ≠ 발급)
- 선착순이 아닌 쿠폰은 user_coupons만 사용
- 정규화 원칙 준수
- 코치님 QnA의 "선착순 판정과 발급 처리 분리" 명확히 표현

**단점:**
- 테이블 하나 더
- 선착순 정보 조회 시 조인 필요
- 복잡도 증가

**평가:**
- 복잡도: ⭐⭐⭐
- 정규화: ✅
- 개념 명확성: ✅
- 권장도: ⭐⭐⭐⭐ (STEP 14 과제에 최적)

---

## 데이터베이스 설계 원칙

### NULL은 언제 허용하는가?

**일반적 가이드:**
1. **선택적 정보** (주소2, 중간이름) → NULL OK
2. **타입별 다른 속성** (선착순 순번) → NULL 피하는 게 좋음

**왜?**
- NULL은 "값이 없다"는 의미가 불명확
  - 아직 입력 안 함? vs 해당 없음?
- 인덱스, 집계 함수에서 특별 처리 필요
- 3-Valued Logic (True, False, NULL) 복잡

---

### 정규화 관점

**sequenceNumber가 일부 쿠폰에만 해당된다면:**

→ **정규화 원칙상 별도 테이블이 맞습니다**

```
제1정규형: 원자값
제2정규형: 부분 함수 종속 제거
제3정규형: 이행 함수 종속 제거
→ "특정 타입에만 있는 속성"은 별도 테이블
```

---

## 최종 설계 결정

### ⚠️ 2025-12-09 변경: Redis Only 구조로 전환

**기존 설계 (2025-12-04):**
- ✅ 채택: 예약 테이블 방식 (방법 5)
- CouponReservation 테이블로 선착순 자격 기록
- user_coupons에 sequence_number 없음 (NULL 문제 해결)

**변경 후 설계 (2025-12-09):**
- ✅ **Redis Only 구조** (CouponReservation 제거)
- Redis가 선착순 판정의 Single Source of Truth
- DB는 최종 발급 내역(UserCoupon)만 저장

**변경 이유 (코치님 피드백):**
1. **DB write 감소** - 3회 → 1회 (66% 감소)
2. **Redis-DB 일관성 문제 제거** - Redis 단일 진실 원천
3. **복잡도 감소** - 예약 테이블 관리 불필요
4. **sequenceNumber의 실제 필요성** - 비즈니스 필수 아님 (응답/로깅용)

**트레이드오프:**
- ✅ 성능 향상 (DB write 감소)
- ✅ 일관성 보장 (Redis Only)
- ❌ sequenceNumber 영구 저장 불가 (Redis TTL 만료 후)
- ❌ 감사/추적 제한 (로그에만 기록)

**허용 가능한 이유:**
- sequenceNumber는 실시간 응답에만 필요
- "N번째 예약" 정보는 나중에 조회 불필요
- 로그에 sequence 기록으로 충분

---

### ~~기존 설계: 예약 테이블 방식 (방법 5)~~ (Deprecated)

<details>
<summary>기존 설계 상세 (참고용)</summary>

**이유:**
1. **NULL 완전 제거** - user_coupons에 sequence_number 컬럼 자체가 없음
2. **개념적 명확성** - "선착순 자격 획득" ≠ "쿠폰 발급 완료"
3. **코치님 QnA 부합** - "선착순 판정과 발급 처리를 분리"
4. **정규화 원칙 준수** - 타입별 다른 속성을 별도 테이블로
5. **실패 추적** - RESERVED → ISSUED / FAILED 상태 관리

</details>

---

### 테이블 구조

#### ~~coupon_reservations (선착순 자격 기록)~~ (Deprecated - 제거됨)

<details>
<summary>기존 테이블 구조 (참고용)</summary>

```sql
CREATE TABLE coupon_reservations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  coupon_id BIGINT NOT NULL,
  sequence_number BIGINT NOT NULL,         -- Redis INCR 결과
  status VARCHAR(20) NOT NULL,             -- RESERVED, ISSUED, FAILED
  reserved_at TIMESTAMP NOT NULL,
  issued_at TIMESTAMP,
  failed_at TIMESTAMP,
  failure_reason VARCHAR(500),

  UNIQUE KEY uk_user_coupon_reservation (user_id, coupon_id),
  INDEX idx_coupon_status (coupon_id, status),
  INDEX idx_coupon_sequence (coupon_id, sequence_number)
);
```

**역할:**
- "100번째 안에 들었다"는 사실을 **영구 기록**
- 뒤집히지 않는 사실 (Immutable Fact)
- 실패 추적 및 복구 근거

**⚠️ 2025-12-09 제거 이유:**
- DB write 추가 발생 (성능 저하)
- Redis-DB 일관성 문제 유발
- sequenceNumber 영구 저장 불필요 (비즈니스 필수 아님)

</details>

**💡 현재 구조 (Redis Only):**
```
Redis:
- coupon:{couponId}:sequence (String) - 순번 INCR
- coupon:{couponId}:reservations (Set) - 예약자 userId 집합
- TTL: 24시간 (자동 정리)

DB:
- user_coupons - 최종 발급 내역만 저장
```

---

#### user_coupons (발급 완료된 쿠폰)

```sql
CREATE TABLE user_coupons (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  coupon_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,             -- AVAILABLE, USED, EXPIRED
  issued_at TIMESTAMP NOT NULL,
  used_at TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,

  UNIQUE KEY uk_user_coupon (user_id, coupon_id),
  INDEX idx_user_status (user_id, status)
);
```

**역할:**
- 발급 완료된 쿠폰만 기록 (일반 쿠폰 + 선착순 쿠폰)
- sequence_number 컬럼 없음 (NULL 문제 완전 해결)
- 쿠폰 사용 상태 관리 (AVAILABLE → USED → EXPIRED)

---

## 구현 상세

### Phase 1: 선착순 판정 (즉시, 동기)

```java
@Transactional
public CouponReservationResponse reserve(Long couponId, Long userId) {
    // 1. 중복 예약 체크 (DB)
    if (reservationRepository.existsByUserIdAndCouponId(userId, couponId)) {
        throw new AlreadyReservedException();
    }

    // 2. Redis INCR로 순번 획득 (원자적, 락 불필요)
    Long sequence = redis.increment("coupon:" + couponId + ":sequence");

    // 3. 수량 체크
    Coupon coupon = couponRepository.findById(couponId).orElseThrow();
    if (sequence > coupon.getTotalQuantity()) {
        throw new SoldOutException();  // Redis 원복 불필요
    }

    // 4. CouponReservation INSERT (뒤집히지 않는 사실 확정)
    CouponReservation reservation = CouponReservation.create(
        userId, couponId, sequence
    );
    reservationRepository.save(reservation);

    // 5. Event 발행 (AFTER_COMMIT)
    eventPublisher.publishEvent(new CouponReservedEvent(...));

    return CouponReservationResponse.of(reservation);
}
```

**API:** `POST /api/coupons/{couponId}/reserve`

**응답:**
```json
{
  "reservationId": 123,
  "couponId": 1,
  "userId": 456,
  "sequenceNumber": 42,
  "status": "RESERVED",
  "message": "쿠폰 발급 예약이 완료되었습니다. (42번째)",
  "reservedAt": "2025-12-04T10:00:00"
}
```

---

### Phase 2: 쿠폰 발급 (Event, 즉시 실행)

```java
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handleCouponReserved(CouponReservedEvent event) {
    try {
        // 실제 쿠폰 발급 (재고 차감 + 발급 기록 ACID)
        UserCoupon userCoupon = issueCouponActualService.issueActual(
            event.getCouponId(),
            event.getUserId()
        );

        // 발급 성공 - 예약 상태 업데이트 (ISSUED)
        updateReservationStatus(event.getReservationId(), true, null);

    } catch (Exception e) {
        // 발급 실패 - Redis 원복 + 예약 상태 업데이트 (FAILED)
        rollbackRedisSequence(event.getCouponId());
        updateReservationStatus(event.getReservationId(), false, e.getMessage());
    }
}

@Transactional
public UserCoupon issueActual(Long couponId, Long userId) {
    // 1. 재고 차감 (Pessimistic Lock)
    Coupon coupon = couponRepository.findByIdWithLock(couponId).orElseThrow();
    coupon.issue();  // issuedQuantity++

    // 2. 발급 기록 저장
    UserCoupon userCoupon = UserCoupon.create(userId, couponId, ...);
    userCouponRepository.save(userCoupon);

    // 3. Redis 발급 기록 (중복 방지)
    redis.sAdd("coupon:" + couponId + ":issued", userId);

    // ✅ 이 세 개가 하나의 @Transactional (ACID)
    return userCoupon;
}
```

---

### 전체 플로우

```
[Client]
   ↓ POST /api/coupons/1/reserve { "userId": 123 }

[Controller] → ReserveCouponUseCase

[TX1: 선착순 판정]
├─ Redis INCR "coupon:1:sequence" → 42
├─ CouponReservation INSERT (userId=123, sequence=42, status=RESERVED)
└─ publishEvent(CouponReservedEvent)

[TX1 COMMIT] ✅
   ↓ AFTER_COMMIT

[EventListener] handleCouponReserved
├─ [TX2: 쿠폰 발급 ACID]
│   ├─ Coupon.issue() (issuedQuantity++)
│   ├─ UserCoupon INSERT (sequence_number 컬럼 없음!)
│   └─ Redis SADD "coupon:1:issued" {123}
│
├─ [성공] CouponReservation.status = ISSUED
└─ [실패] Redis DECR + CouponReservation.status = FAILED

[Response]
{
  "reservationId": 456,
  "sequenceNumber": 42,
  "status": "RESERVED",
  "message": "쿠폰 발급 예약이 완료되었습니다. (42번째)"
}
```

---

## 일반 쿠폰 vs 선착순 쿠폰 비교

### 일반 쿠폰 발급

```
[Client]
   ↓ POST /api/coupons/5/issue { "userId": 123 }

[IssueCouponUseCase]
├─ Coupon.issue() (issuedQuantity++)
├─ UserCoupon INSERT
└─ Response

[DB 상태]
coupon_reservations: (비어있음)
user_coupons:
  id=1, user_id=123, coupon_id=5, status=AVAILABLE
```

**특징:**
- CouponReservation 생성 안 함
- UserCoupon만 생성
- sequence_number 없음 (NULL 아님, 컬럼 자체가 없음)

---

### 선착순 쿠폰 발급

```
[Client]
   ↓ POST /api/coupons/1/reserve { "userId": 456 }

[ReserveCouponUseCase + EventListener]
├─ CouponReservation INSERT (sequence=42)
└─ UserCoupon INSERT

[DB 상태]
coupon_reservations:
  id=1, user_id=456, coupon_id=1, sequence=42, status=ISSUED

user_coupons:
  id=2, user_id=456, coupon_id=1, status=AVAILABLE
```

**특징:**
- CouponReservation + UserCoupon 둘 다 생성
- sequence_number는 CouponReservation에만 존재
- UserCoupon에는 sequence_number 컬럼 자체가 없음 (NULL 문제 없음)

---

## 코치님 요구사항 충족 확인

### Jay 코치 Jisu 답변 (COACH_JAY_QNA.md:192-193)

> "선착순 발급과 발급 처리를 분리하시면 됩니다"
> "100번째 안에 들었다는 부분은 뒤집히지 않는 사실"
> "쿠폰이 발급되는 부분은 나중에 해도 됩니다"

**우리의 구현:**
- ✅ 선착순 판정 (CouponReservation) ≠ 발급 처리 (UserCoupon)
- ✅ "100번째" = CouponReservation.sequenceNumber (불변)
- ✅ 발급은 Event Listener에서 "나중에" (하지만 즉시)

---

### Kim Jonghyeop 코치 (COACH_QNA_SUMMARY.md:96)

> "재고 차감과 발급 기록, 이 두 개가 액시드하게(ACID) 처리되어야 한다"

**우리의 구현:**
- ✅ `IssueCouponActualService.issueActual()` 메서드 전체가 하나의 @Transactional
- ✅ Coupon.issue() + UserCoupon INSERT + Redis SADD = ACID

---

### STEP_CHECKLIST.md:78-79

> "실패 시 즉시 원복"
> "스케줄러 방식 금지"

**우리의 구현:**
- ✅ EventListener catch 블록에서 Redis DECR 즉시 실행
- ✅ TransactionalEventListener(AFTER_COMMIT) 즉시 실행 (NOT @Async)

---

## 트레이드오프

### 예약 테이블의 장점

1. **NULL 완전 제거**
2. **개념적 명확성** (예약 ≠ 발급)
3. **실패 추적 용이**
4. **정규화 원칙 준수**
5. **sequenceNumber 영구 보존**

---

### 예약 테이블의 단점

1. **테이블 하나 더**
   - 스키마 복잡도 증가
   - 마이그레이션 필요

2. **조인 필요**
   ```sql
   -- 선착순 정보 포함 조회
   SELECT uc.*, cr.sequence_number
   FROM user_coupons uc
   LEFT JOIN coupon_reservations cr
     ON uc.user_id = cr.user_id
     AND uc.coupon_id = cr.coupon_id
   ```

3. **복잡도 증가**
   - CouponReservation 엔티티
   - CouponReservationRepository
   - Event 처리 로직

---

### 대안 (NULL 허용 방식)과 비교

| 항목 | NULL 허용 | 예약 테이블 |
|------|----------|------------|
| 테이블 수 | 1개 | 2개 |
| NULL 존재 | ⚠️ 있음 | ✅ 없음 |
| 개념 명확성 | ⚠️ 혼재 | ✅ 분리 |
| 정규화 | ❌ | ✅ |
| 복잡도 | ⭐ | ⭐⭐⭐ |
| 실패 추적 | ⚠️ 어려움 | ✅ 용이 |
| 권장도 | ⭐⭐⭐ (실용) | ⭐⭐⭐⭐ (정석) |

---

## 결론

### 왜 예약 테이블을 선택했는가?

1. **NULL 문제 완전 해결**
   - "sequenceNumber를 저장하고 싶다" + "NULL은 피하고 싶다"
   - 이 두 조건을 동시에 만족하는 유일한 방법

2. **코치님 QnA 명확히 반영**
   - "선착순 판정과 발급 처리 분리"를 테이블 구조로 표현
   - "뒤집히지 않는 사실"을 별도 테이블로 영구 기록

3. **STEP 14 과제 목적 부합**
   - Redis 기반 선착순 구현
   - 동시성 제어 + 정합성 보장
   - 실패 추적 및 복구

4. **확장성**
   - 향후 다른 선착순 이벤트에도 동일 패턴 적용 가능
   - 예약 내역 분석, 통계 추출 용이

---

### 최종 판단

**STEP 14 과제에서는 예약 테이블 방식을 권장합니다.**

- 복잡도가 증가하지만, 그만큼 얻는 가치가 크다
- "선착순"이라는 특수한 비즈니스 로직을 명확히 표현
- 실무에서도 충분히 사용되는 설계 패턴

**하지만 실무에서는:**
- 프로젝트 규모와 요구사항에 따라 NULL 허용 방식도 충분히 선택 가능
- 트레이드오프를 이해하고 팀과 협의하여 결정

---

## 참고 자료

- COACH_JAY_QNA.md - Jay 코치 멘토링 (Jisu, Hyeyoung 질문)
- COACH_QNA_SUMMARY.md - Kim Jonghyeop 코치 핵심 원칙
- STEP_CHECKLIST.md - STEP 14 요구사항
- 데이터베이스 정규화 이론 (3NF)
