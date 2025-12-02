# Week 7 코치 QnA 핵심 요약

**일시:** 2025.12.01 월 오후 8:59
**코치:** 김종협 코치님
**주제:** Redis 자료구조, 랭킹, 쿠폰 발급

---

## 🔑 핵심 개념

### 1. Redis 구조와 특징

#### 단일 스레드 이벤트 루프
```
[요청 대기 리스트]
    ↓
[이벤트 루프] → 하나씩 던짐
    ↓
[처리] → CPU (하나만 처리 가능)
    ↓
[소켓 통신으로 리턴]
```

**특징:**
- Redis는 **단일 스레드 이벤트 루프**로 동작
- 이벤트 루프는 처리가 끝날 때까지 대기하지 않고 **계속 던짐**
- CPU는 하나씩만 처리 가능
- 메모리 기반의 빠른 처리로 즉각 응답

**문제 상황:**
```
이벤트 루프: 1 → 2 → 3 → 4 → 5 (계속 던짐)
처리:        ✓   ❌ (10초 걸림, 병목 발생)
                    ↓
                 3, 4, 5는 대기 상태 → 지연 발생
```

#### Lua 스크립트 사용 시 주의사항

**❌ 잘못된 사용:**
- 10초짜리 Lua 스크립트 실행
- CPU 연산이 많은 복잡한 로직
- → 전체 Redis 처리 지연 유발

**✅ 올바른 사용:**
- **짧은 Lua 스크립트만 사용**
- 메모리 연산으로 즉각 처리 가능한 수준
- 복잡한 로직은 Application 레벨에서 처리

**코치 발언:**
> "빠르게 처리할 수 있게끔 내부 메커니즘이 세팅되어 있는데,
> Lua 스크립트를 쓰게 되면 처리 속도가 늦을 수밖에 없다.
> **Lua 스크립트 쓸 때는 굉장히 신중해야 된다.**"

---

### 2. 실시간 랭킹 기능 구현

#### 갱신 시점
**✅ 올바른 시점: 결제 완료**
```java
// 결제 성공 시점에 비동기로 랭킹 갱신
@EventListener
@Async
public void updateRanking(PaymentCompletedEvent event) {
    // ZINCRBY로 score 증가
    redisTemplate.opsForZSet()
        .incrementScore(key, productId, quantity);
}
```

**❌ 잘못된 시점:**
- 주문 생성 시점 (결제 실패 가능)
- 장바구니 담기 시점 (구매 미확정)

#### 자료구조 선택
**✅ Sorted Set 사용**
- score 기반 자동 정렬
- ZINCRBY로 원자적 증가
- 동시성 이슈 없음 (별도 분산락 불필요)

**코치 발언:**
> "랭킹 갱신을 어떻게 할 수 있을까요?
> 자료구조는 뭘 할 건지 고민해 보고,
> **Sorted Set**이 되어야 된다.
> 원자성을 보장해 주는 score 증가 명령어를 찾아보시면 된다."

---

### 3. 선착순 쿠폰 발급 (핵심 ⭐)

#### 트랜잭션 규칙

**반드시 지켜야 할 원칙:**
> "쿠폰 잔여 수량이 마이너스 1이 되는 것과
> 쿠폰이 이 유저한테 발행했다는 것,
> **이 두 개가 액시드하게(ACID) 처리되어야 한다.**"

**구현 방식:**
1. **트랜잭션 묶기** (Redis 트랜잭션 가능 시)
2. **방어 로직으로 원복** (트랜잭션 불가능 시)

```java
// 방어 로직 예시
try {
    // 1. 수량 차감
    Long remain = redisTemplate.opsForValue().decrement(remainKey);

    // 2. 수량 부족 체크
    if (remain < 0) {
        // 원복
        redisTemplate.opsForValue().increment(remainKey);
        return CouponIssueResult.soldOut();
    }

    // 3. 발급 기록
    Long addResult = redisTemplate.opsForSet().add(issuedKey, userId);

    // 4. 발급 기록 실패 시 원복
    if (addResult == 0) {
        redisTemplate.opsForValue().increment(remainKey);
        throw new Exception("발급 기록 실패");
    }

} catch (Exception e) {
    // 예외 발생 시 롤백
    rollbackCouponIssue(couponId, userId);
}
```

#### ❌ 절대 하지 말 것

**코치 발언:**
> "쿠폰 발급 수량은 마이너스 1로 하면서
> List에 어떤 사용자가 쿠폰 발급했는지 가지고 있다가
> **배치 수량을 하면 안 된다.**
> 그 시점에 싱크 에러가 터질 거다.
> **실시간으로 계속 해줘야 되고,
> 최후의 공극적인 정합성에 맞춰줘야 된다.**"

**금지 사항:**
1. ❌ 수량 차감만 하고 발급 기록은 스케줄러로 나중에 처리
2. ❌ 발급 기록만 하고 수량 차감은 배치로 처리
3. ❌ 둘 중 하나 실패 시 그대로 방치

**필수 사항:**
1. ✅ 수량 차감 + 발급 기록은 **하나의 트랜잭션**
2. ✅ 실패 시 **즉시 원복**
3. ✅ **실시간 처리** (스케줄러 ❌)

---

### 4. 데이터 배치 전략

#### 쿠폰 메타 정보 관리

**DB에 저장:**
- 쿠폰 ID, 이름, 할인율
- 유효기간
- 총 발급 가능 수량 (원본)

**Redis에 저장:**
- `coupon:{id}:remain` → 남은 수량 (실시간 차감)
- `coupon:{id}:issued` → 발급된 userId Set

**코치 발언:**
> "메타 데이터는 DB에 두고,
> 메타 데이터 조회는 캐시 관리하는 것.
> 발급 수량만 차감시키고,
> 발급부터 사용까지 모든 후단 작업을 DB로 가져갈 건지,
> 이거는 **선택 사항**이다."

**선택 옵션:**
1. **옵션 A:** Redis에서 발급만, DB에 기록 (eventual consistency)
2. **옵션 B:** Redis에서 발급 + 사용 모두 처리

**주의사항:**
- 변화가 거의 없는 값은 DB에 유지
- 변화가 빈번한 값은 Redis에서만 처리
- 둘 다 기재 시 예측 가능한 시점에만 동기화 (예: 어드민 쿠폰 등록)

---

## 🎯 실무 관점

### 복잡도 증가는 당연한 것

**코치 발언:**
> "애플리케이션 로직 복잡도 증가는
> 어쩔 수 없이 부하 분산시킬 때 따라올 수밖에 없다.
> **복잡도 증가는 당연한 거다.**"

**이유:**
- 부하 분산을 시키지 않으면 부하를 견딜 수 없음
- 관리 포인트가 두 개(DB + Redis) 늘어나는 건 필연적
- 역할과 책임을 명확히 분리하면 괜찮음

**개발자의 역할:**
- 복잡도를 도메인 서비스 단에서 최소화
- 외적인 데이터 소스 변경 시 기존 도메인 로직 보호
- 복잡도를 경험하고 개발할 수 있는 능력 함양

### 손실은 절대 발생하면 안 됨

**코치 발언:**
> "손실 시 파악 복구 처리 복잡도 증가?
> **손실이 발생하면 안 되죠.
> 손실 시가 아니라,
> 어떻게 손실을 막도록 로직에서 처리하는지가 고민이다.**"

**방법:**
- 로그 처리, 트레이싱 처리
- 모니터링 툴 활용 (어느 지점에서 유실됐는지 추적)
- 데이터 유실 방지 로직 구현

---

## 🚫 자주 하는 실수

### 1. 트랜잭션과 락의 순서 문제

**❌ 잘못된 순서:**
```java
@Transactional
public void charge(Long userId, BigDecimal point) {
    RLock lock = redissonClient.getLock("userChargeLock");

    try {
        if(lock.tryLock()) {
            User user = userRepository.findById(userId);
            user.charge(point);
        }
    } finally {
        lock.unlock();
    }
}
```

**문제점:**
1. 트랜잭션과 락의 순서 보장 실패
2. 충전과 결제가 동시에 수행 가능
3. 충전/결제 기능 자체에 락이 걸림 (모든 사용자 대기)

**✅ 올바른 방법:**
```java
public void charge(Long userId, BigDecimal point) {
    RLock lock = redissonClient.getLock("user:" + userId);  // 사용자별 락

    try {
        if (lock.tryLock()) {
            executeInTransaction(() -> {  // 락 획득 후 트랜잭션 시작
                User user = userRepository.findById(userId);
                user.charge(point);
            });
        }
    } finally {
        lock.unlock();
    }
}
```

### 2. 동시성 테스트 부하 발생

**코치 발언:**
> "Test Code의 병렬 부하만으로
> 부하 발생 환경에서 동시성 테스트가 가능한가?
> → **jmeter, nGrinder를 사용하여 별도의 부하 테스트 필요**"

**권장 방법:**
- Testcontainers로 기본 동시성 검증
- jMeter, nGrinder로 실제 부하 테스트
- ExecutorService로 다중 스레드 테스트

---

## 📊 Redis 명령어 선택 가이드

### 랭킹 시스템
| 작업 | 명령어 | 이유 |
|------|--------|------|
| score 증가 | `ZINCRBY` | 원자적, 분산락 불필요 |
| Top N 조회 | `ZREVRANGE` | score 높은 순 정렬 |
| 순위 조회 | `ZREVRANK` | 특정 member의 순위 |
| score 조회 | `ZSCORE` | 현재 판매량 확인 |

### 쿠폰 발급
| 작업 | 명령어 | 이유 |
|------|--------|------|
| 수량 차감 | `DECR` | 원자적 감소 |
| 발급 기록 | `SADD` | 중복 방지 (Set) |
| 중복 체크 | `SISMEMBER` | O(1) 조회 |
| 원복 | `INCR` | 롤백 시 수량 복구 |

---

## 💡 핵심 교훈

### 1. Redis 특성 이해
- 단일 스레드 이벤트 루프
- Lua 스크립트는 짧게
- 원자적 연산 활용

### 2. 트랜잭션 설계
- 수량 차감 + 발급 기록은 하나의 단위
- 실패 시 즉시 원복
- 스케줄러로 나중에 맞추는 방식 절대 금지

### 3. 데이터 배치
- 메타 정보: DB (안정성)
- 실시간 데이터: Redis (성능)
- 역할 명확히 분리

### 4. 복잡도 관리
- 복잡도 증가는 당연함
- 역할과 책임 명확히 분리
- 손실은 절대 발생하면 안 됨

---

## 🔗 참고 링크

- [원본 QnA 전문](./QNA_FULL_TRANSCRIPT.md)
- [Redis 공식 문서](https://redis.io/docs/)
- [카카오페이 Redis on Kubernetes](https://tech.kakaopay.com/post/kakaopaysec-redis-on-kubernetes/)

---

## ✅ 적용 체크리스트

- [ ] Redis 단일 스레드 특성 이해
- [ ] Lua 스크립트 짧게 작성
- [ ] 랭킹 갱신은 결제 완료 시점
- [ ] ZINCRBY로 원자적 score 증가
- [ ] 쿠폰 수량 차감 + 발급 기록 트랜잭션
- [ ] 실패 시 즉시 원복 로직
- [ ] 스케줄러 방식 사용 금지
- [ ] 데이터 배치 전략 수립 (DB vs Redis)
- [ ] Testcontainers 통합 테스트 작성
- [ ] 동시성 검증 테스트 작성
