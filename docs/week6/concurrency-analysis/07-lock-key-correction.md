# 분산락 키 vs 멱등성 키 - 개념 정리

## 🔴 제가 혼동했던 부분

### 잘못된 이해
```java
// ❌ 잘못된 구현
@DistributedLock(key = "'charge:idempotency:' + #request.idempotencyKey()")
public ChargeBalanceResponse execute(Long userId, ChargeBalanceRequest request) {
    // ...
}
```

**문제점**:
- 멱등성 키가 매번 다르면 분산락도 매번 다른 키로 걸림
- **충전/차감/조회가 서로 다른 락 키 사용** → Lost Update 발생 가능!
- User Entity는 하나인데 락이 제각각!

---

## 💡 올바른 이해

### 분산락 키 vs 멱등성 키의 차이

| 구분 | 분산락 키 | 멱등성 키 |
|------|----------|----------|
| **목적** | 동시성 제어 | 중복 요청 방지 |
| **기준** | 리소스 (User ID) | 요청 (UUID) |
| **생명주기** | 트랜잭션 동안 (30초) | 24시간 (DB 저장) |
| **저장 위치** | Redis (In-Memory) | MySQL (DB) |
| **사용 방식** | Lock 획득/해제 | DB 조회/저장 |

---

## 🔑 올바른 분산락 키 전략

### 원칙: 리소스 기준으로 락 획득

```java
// ✅ 올바른 구현
@DistributedLock(key = "'balance:user:' + #userId")  // ⭐ userId 기준!
public ChargeBalanceResponse execute(Long userId, ChargeBalanceRequest request) {
    // 1. 분산락 획득 (balance:user:1)
    //    → 동일 사용자의 모든 잔액 작업 직렬화

    // 2. 멱등성 체크 (DB 조회)
    Optional<ChargeBalanceIdempotency> existing =
        idempotencyRepository.findByIdempotencyKey(request.idempotencyKey());

    if (existing.isPresent() && existing.get().isCompleted()) {
        // 캐시된 응답 반환 (중복 충전 방지)
        return deserializeResponse(existing.get().getResponsePayload());
    }

    // 3. 멱등성 키 생성 (DB 저장)
    ChargeBalanceIdempotency idempotency =
        ChargeBalanceIdempotency.create(request.idempotencyKey(), userId, amount);
    idempotencyRepository.save(idempotency);

    // 4. 충전 처리
    // ...
}
```

---

## 🎯 핵심 개념

### 1. 분산락 = 리소스 보호

**목적**: 동일 리소스(User)에 대한 동시 작업 직렬화

```java
// 충전
@DistributedLock(key = "'balance:user:' + #userId")

// 차감
@DistributedLock(key = "'balance:user:' + #request.userId()")

// 조회 (필요 시)
@DistributedLock(key = "'balance:user:' + #userId")
```

**모두 같은 락 키 사용!** → `balance:user:1`

### 2. 멱등성 키 = 요청 식별

**목적**: 동일 요청의 중복 실행 방지

```java
// 첫 번째 충전 요청
idempotencyKey: "abc-123"  // UUID
→ DB 저장: idempotency_key="abc-123", status=COMPLETED

// 두 번째 충전 요청 (같은 키)
idempotencyKey: "abc-123"  // 동일
→ DB 조회: 이미 존재 (COMPLETED) → 캐시 반환 ✅

// 다른 충전 요청
idempotencyKey: "def-456"  // 다른 UUID
→ DB 저장: idempotency_key="def-456", status=COMPLETED
```

**각 요청마다 고유 키!**

---

## 🔄 동작 흐름 (올바른 구현)

### 시나리오: 두 개의 충전 요청 (같은 사용자)

```
요청 1: userId=1, amount=10000, idempotencyKey="abc-123"
요청 2: userId=1, amount=20000, idempotencyKey="def-456"

시간 순서:
  0ms: 요청 1 시작
    ↓
  1ms: 분산락 획득 (balance:user:1) 🔒
    ↓
  2ms: 멱등성 체크 (idempotencyKey="abc-123") → 없음
    ↓
  3ms: 멱등성 키 저장 (PROCESSING)
    ↓
 10ms: 충전 처리 (10,000원)
    ↓
 15ms: 멱등성 키 업데이트 (COMPLETED)
    ↓
 16ms: 분산락 해제 🔓
    ↓
--- (동시에) 요청 2 시작 ---
    ↓
 17ms: 분산락 획득 대기... ⏳
    ↓
 17ms: 분산락 획득 (balance:user:1) 🔒 (요청 1 해제 후)
    ↓
 18ms: 멱등성 체크 (idempotencyKey="def-456") → 없음
    ↓
 19ms: 멱등성 키 저장 (PROCESSING)
    ↓
 25ms: 충전 처리 (20,000원)
    ↓
 30ms: 멱등성 키 업데이트 (COMPLETED)
    ↓
 31ms: 분산락 해제 🔓
```

**핵심**:
- ✅ 분산락 키는 동일 (`balance:user:1`) → 순차 처리
- ✅ 멱등성 키는 다름 → 각각 새로운 충전으로 처리

---

## 🎯 중복 요청 시나리오

### 시나리오: 같은 요청 두 번 (네트워크 타임아웃 후 재시도)

```
요청 1: userId=1, amount=10000, idempotencyKey="abc-123"
요청 2: userId=1, amount=10000, idempotencyKey="abc-123" (재시도!)

시간 순서:
  0ms: 요청 1 시작
    ↓
  1ms: 분산락 획득 (balance:user:1) 🔒
    ↓
  2ms: 멱등성 체크 (idempotencyKey="abc-123") → 없음
    ↓
  3ms: 멱등성 키 저장 (PROCESSING)
    ↓
 10ms: 충전 처리 (10,000원)
    ↓
 15ms: 멱등성 키 업데이트 (COMPLETED, 응답 캐싱)
    ↓
 16ms: 분산락 해제 🔓
    ↓
--- (사용자 재시도) 요청 2 시작 ---
    ↓
 20ms: 분산락 획득 (balance:user:1) 🔒
    ↓
 21ms: 멱등성 체크 (idempotencyKey="abc-123") → 있음! (COMPLETED)
    ↓
 22ms: ✅ 캐시된 응답 반환 (충전 안 함!)
    ↓
 23ms: 분산락 해제 🔓
```

**결과**:
- ✅ 10,000원만 충전 (한 번)
- ✅ 두 번째 요청은 캐시 반환
- ✅ Lost Update 없음

---

## 📊 비교표

### 충전/차감/조회의 락 키 전략

| UseCase | 분산락 키 | 멱등성 키 | 비고 |
|---------|----------|----------|------|
| **ChargeBalance (충전)** | `balance:user:{userId}` | 요청마다 고유 UUID | 중복 충전 방지 |
| **ProcessPayment (차감)** | `balance:user:{userId}` | 요청마다 고유 UUID | 중복 결제 방지 |
| **GetBalance (조회)** | `balance:user:{userId}` | 없음 (조회는 멱등성 불필요) | 읽기 일관성 |

**핵심**:
- ✅ 분산락 키는 모두 동일 (`balance:user:{userId}`)
- ✅ 멱등성 키는 각 요청마다 고유
- ✅ Lost Update 방지

---

## 🛡️ 다층 방어 (Defense in Depth)

### 1차 방어: 분산락
```
목적: 인스턴스 간 동시성 제어
키: balance:user:{userId}
효과: 동일 사용자의 충전/차감 순차 처리
```

### 2차 방어: Optimistic Lock
```
목적: DB 레벨 Lost Update 방지
키: @Version (User Entity)
효과: 동시 UPDATE 감지 및 재시도
```

### 3차 방어: 멱등성 키
```
목적: 중복 요청 방지
키: idempotencyKey (요청별 고유)
효과: 같은 요청 재실행 방지 (캐시 반환)
```

**모두 함께 작동** → 완벽한 동시성 제어!

---

## ✅ 수정 완료

### ChargeBalanceUseCase.java

**Before**:
```java
@DistributedLock(key = "'charge:idempotency:' + #request.idempotencyKey()")  // ❌
```

**After**:
```java
@DistributedLock(key = "'balance:user:' + #userId")  // ✅
```

**변경 이유**:
- 분산락 키는 리소스(User) 기준
- 멱등성 키는 요청 식별용 (DB 저장)
- 충전/차감/조회 모두 동일 락 키 사용 필수

---

## 🎯 최종 정리

### 분산락 키
- **목적**: 동시성 제어 (Race Condition 방지)
- **기준**: 리소스 ID (userId)
- **예시**: `balance:user:1`
- **생명주기**: 트랜잭션 동안 (30초)

### 멱등성 키
- **목적**: 중복 요청 방지
- **기준**: 요청 ID (UUID)
- **예시**: `abc-123-def-456`
- **생명주기**: 24시간 (DB 저장)

### 함께 사용
```java
@DistributedLock(key = "'balance:user:' + #userId")  // 리소스 기준 락
public ChargeBalanceResponse execute(Long userId, ChargeBalanceRequest request) {
    // 멱등성 체크 (요청 기준)
    if (idempotencyRepository.existsByIdempotencyKey(request.idempotencyKey())) {
        // 캐시 반환
    }

    // 충전 처리
}
```

**완벽!** ✅

---

**작성자**: Backend Development Team
**최종 수정**: 2025-11-26
**버전**: 1.0
**상태**: 수정 완료
