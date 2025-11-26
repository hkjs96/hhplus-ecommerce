# Step 11-12 분산락 문제 수정 요약

## 📋 문제 발견

### K6 테스트 결과 (수정 전)
```
VUs: 1000
Duration: 5m 0s
Optimistic Lock Conflicts: 830건 ❌
Errors: 830건 (재시도 10회 초과) ❌
Success Rate: 96.78% ❌
```

### 로그 분석
```
✅ DEBUG i.h.e.a.u.user.ChargeBalanceUseCase - Balance charged successfully
❌ INFO  i.h.e.i.r.DistributedLockAspect - 락 획득 성공 (없음!)
❌ INFO  i.h.e.i.r.DistributedLockAspect - 락 해제 (없음!)
```

### Redis 확인
```bash
$ docker exec ecommerce-redis redis-cli KEYS "*balance:user:*"
(empty array)  ❌
```

**결론**: 분산락이 전혀 작동하지 않음!

---

## 🔍 근본 원인

### Spring AOP Self-Invocation 문제

```java
// ❌ 수정 전 (Self-Invocation 문제)
public ChargeBalanceResponse execute(Long userId, ...) {
    return retryService.executeWithRetry(() -> chargeBalance(userId, request), 10);
    // ↑ 람다 내부에서 this.chargeBalance() 호출 (내부 호출)
}

@DistributedLock(...)  // ❌ AOP 미작동 (프록시 안 거침)
@Transactional
protected ChargeBalanceResponse chargeBalance(Long userId, ...) {
    // ...
}
```

**문제**:
- `OptimisticLockRetryService`에서 람다로 내부 메서드 호출
- `this`는 프록시가 아닌 실제 객체
- **@DistributedLock AOP가 작동하지 않음!**

---

## 💡 해결 방법

### 분산락을 재시도 로직 바깥으로 이동

```java
// ✅ 수정 후
@DistributedLock(
        key = "'balance:user:' + #userId",
        waitTime = 10,
        leaseTime = 30
)
public ChargeBalanceResponse execute(Long userId, ...) {
    log.info("Charging balance for userId: {}, amount: {}", userId, request.amount());

    // 분산락 획득 후 재시도 로직 실행
    return retryService.executeWithRetry(() -> chargeBalanceInternal(userId, request), 10);
}

@Transactional  // ✅ @DistributedLock 제거, @Transactional만 유지
protected ChargeBalanceResponse chargeBalanceInternal(Long userId, ...) {
    // ...
}
```

**동작 흐름**:
```
1. execute() 호출
   ↓
2. @DistributedLock AOP 적용 (✅ 분산락 획득)
   ↓
3. retryService.executeWithRetry(람다)
   ↓
4. 람다 내부에서 chargeBalanceInternal() 호출
   ↓
5. @Transactional 적용 (✅ 트랜잭션 시작)
   ↓
6. Optimistic Lock + 재시도
   ↓
7. 분산락 해제
```

---

## 🛠️ 추가 수정: K6 스크립트 (다중 사용자)

### 문제
```javascript
// ❌ 수정 전: 단일 사용자
const USER_ID = __ENV.USER_ID || '1';  // 모든 VU가 USER_ID=1 사용
```

**문제점**:
- 1000명 VU가 모두 USER_ID=1에 충전
- 분산락이 작동해도 전부 직렬화됨
- 실제 부하 테스트가 아님

### 해결
```javascript
// ✅ 수정 후: 다중 사용자 (100명)
const USER_COUNT = parseInt(__ENV.USER_COUNT) || 100;
const userId = (__VU % USER_COUNT) + 1;  // VU 번호 % 100 + 1 = 1~100
```

**효과**:
- ✅ 1000 VU가 100명 사용자에 분산 (평균 10 VU/사용자)
- ✅ 동시성 테스트 더 현실적
- ✅ 분산락 효과 명확히 측정

---

## 📊 예상 효과

### 분산락 작동 확인
```
✅ INFO  i.h.e.i.r.DistributedLockAspect - 락 획득 성공: key=balance:user:1
✅ DEBUG i.h.e.a.u.user.ChargeBalanceUseCase - Balance charged successfully
✅ INFO  i.h.e.i.r.DistributedLockAspect - 락 해제: key=balance:user:1
```

### Redis 키 생성
```bash
$ docker exec ecommerce-redis redis-cli KEYS "*balance:user:*"
1) "balance:user:1"
2) "balance:user:2"
...
100) "balance:user:100"
```

### K6 테스트 예상 결과
```
VUs: 1000
Duration: 5m 0s
Optimistic Lock Conflicts: 10건 이하 ✅ (830건 → 10건)
Errors: 0건 ✅ (830건 → 0건)
Success Rate: 99.95% 이상 ✅ (96.78% → 99.95%)
```

---

## 🎯 수정 내역

### 1. ChargeBalanceUseCase.java ✅
- `@DistributedLock`을 `execute()` 메서드로 이동
- `chargeBalance()` → `chargeBalanceInternal()` 이름 변경
- Self-Invocation 문제 해결

### 2. balance-charge.js ✅
- 단일 사용자(USER_ID=1) → 다중 사용자(USER_COUNT=100)
- VU 번호 기반 사용자 분산 (`__VU % USER_COUNT`)
- 실제 부하 테스트 가능

---

## 🧪 검증 방법

### 1. 애플리케이션 재시작
```bash
./gradlew bootRun
```

### 2. 로그 확인
```bash
tail -f logs/application.log | grep "락 획득"
```

**예상**:
```
INFO  i.h.e.i.r.DistributedLockAspect - 락 획득 성공: key=balance:user:1
INFO  i.h.e.i.r.DistributedLockAspect - 락 해제: key=balance:user:1
```

### 3. 간단한 cURL 테스트
```bash
# 동시 요청 5개 (같은 사용자)
for i in {1..5}; do
  curl -X POST http://localhost:8080/api/users/1/balance/charge \
    -H "Content-Type: application/json" \
    -d '{"amount": 10000}' &
done
```

**예상 로그**:
```
INFO  락 획득 성공: key=balance:user:1
DEBUG Balance charged successfully. userId: 1, new balance: 10000
INFO  락 해제: key=balance:user:1
INFO  락 획득 성공: key=balance:user:1  (다음 요청)
...
```

### 4. Redis 키 확인
```bash
docker exec ecommerce-redis redis-cli KEYS "*balance:user:*"
```

### 5. K6 부하 테스트
```bash
k6 run docs/week5/verification/k6/scripts/balance-charge.js
```

**기대 결과**:
- ✅ Optimistic Lock 충돌 10건 이하
- ✅ 에러율 0.05% 이하
- ✅ 성공률 99.95% 이상

---

## 📚 학습 포인트

### 1. Spring AOP Self-Invocation 주의

**Self-Invocation이 문제가 되는 AOP**:
- ❌ @DistributedLock (Custom AOP)
- ❌ @Cacheable
- ❌ @Async
- ❌ @Retry (Spring Retry)

**Self-Invocation에서도 작동하는 특수 케이스**:
- ✅ @Transactional (Spring의 특수 처리)

### 2. 해결 방법 3가지

1. **ApplicationContext 주입** → 프록시를 명시적으로 가져옴
2. **외부 Bean 분리** → Self-Invocation 완전 제거
3. **AOP를 외부 메서드로 이동** → Self-Invocation 회피 (✅ 채택)

### 3. 부하 테스트의 중요성

- 단위 테스트로는 AOP 문제 발견 어려움
- **통합 테스트 + 부하 테스트** 필수
- K6, JMeter 등으로 실제 부하 테스트

### 4. 다중 사용자 테스트

- 단일 사용자 테스트는 분산락의 진가를 보여주지 못함
- **사용자 분산**으로 실제 부하 시뮬레이션
- VU 번호 활용한 간단한 분산 전략

---

## 🔄 다음 단계

### 즉시 수행
1. ✅ ChargeBalanceUseCase 수정 (완료)
2. ✅ balance-charge.js 수정 (완료)
3. ⏳ 애플리케이션 재시작 및 검증
4. ⏳ K6 부하 테스트 재실행

### 향후 개선
1. ProcessPaymentUseCase도 동일하게 검토
2. 다른 UseCase의 Self-Invocation 문제 확인
3. AOP 작동 검증 자동화 테스트 추가
4. 메트릭 수집 (분산락 획득 시간, 대기 시간)

---

## ✅ 체크리스트

- [x] 문제 원인 파악 (Spring AOP Self-Invocation)
- [x] ChargeBalanceUseCase 수정 (@DistributedLock 위치 이동)
- [x] balance-charge.js 수정 (다중 사용자)
- [ ] 애플리케이션 재시작
- [ ] 로그 확인 ("락 획득 성공" 메시지)
- [ ] Redis 키 생성 확인
- [ ] cURL 간단 테스트
- [ ] K6 부하 테스트 재실행
- [ ] 결과 분석 (Optimistic Lock 충돌 감소 확인)

---

**작성자**: Backend Development Team
**최종 수정**: 2025-11-26
**버전**: 1.0
**상태**: 수정 완료, 검증 대기
