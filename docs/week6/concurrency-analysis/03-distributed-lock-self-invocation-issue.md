# 분산락 Self-Invocation 문제 및 해결

## 📋 문제 요약

**발견**: K6 부하 테스트에서 Optimistic Lock 충돌 830건 발생, 재시도 10회 초과 실패
**원인**: Spring AOP Self-Invocation 문제로 분산락(@DistributedLock) 미작동
**영향**: 1000명 VU가 동일 사용자(ID=1) 충전 시 분산락 없이 Optimistic Lock만 작동
**해결**: 분산락 적용 방식 개선 필요

---

## 🔍 문제 상황

### K6 테스트 결과

```
VUs: 1000
Duration: 5m 0s
Optimistic Lock Conflicts: 830건
Errors: 830건 (재시도 10회 초과)
Success Rate: 96.78%
```

### 로그 분석

**예상 로그**:
```
INFO  i.h.e.i.r.DistributedLockAspect - 락 획득 성공: key=balance:user:1
DEBUG i.h.e.a.u.user.ChargeBalanceUseCase - Balance charged successfully
INFO  i.h.e.i.r.DistributedLockAspect - 락 해제: key=balance:user:1
```

**실제 로그**:
```
DEBUG org.hibernate.SQL - select ... from users ...
DEBUG i.h.e.a.u.user.ChargeBalanceUseCase - Balance charged successfully
ERROR i.h.e.a.u.u.OptimisticLockRetryService - Optimistic Lock 최대 재시도 횟수 초과: 10/10
```

**분석**:
- ❌ "락 획득 성공" 로그 없음
- ❌ "락 해제" 로그 없음
- ✅ Optimistic Lock만 작동
- ✅ 재시도는 작동하지만 분산락 없이 충돌 빈번

### Redis 확인

```bash
$ docker exec ecommerce-redis redis-cli KEYS "*balance:user:*"
(empty array)
```

**결론**: 분산락 키가 Redis에 전혀 생성되지 않음

---

## 🔎 원인 분석

### 현재 구현 (ChargeBalanceUseCase)

```java
@UseCase
@RequiredArgsConstructor
public class ChargeBalanceUseCase {

    private final OptimisticLockRetryService retryService;

    // 1단계: 외부 호출 (AOP 프록시 거침)
    public ChargeBalanceResponse execute(Long userId, ChargeBalanceRequest request) {
        // OptimisticLockRetryService에서 람다로 chargeBalance() 호출
        return retryService.executeWithRetry(() -> chargeBalance(userId, request), 10);
    }

    // 2단계: 내부 메서드 (protected)
    @DistributedLock(
            key = "'balance:user:' + #userId",
            waitTime = 10,
            leaseTime = 30
    )
    @Transactional
    protected ChargeBalanceResponse chargeBalance(Long userId, ChargeBalanceRequest request) {
        User user = userRepository.findByIdOrThrow(userId);
        user.charge(request.amount());
        userRepository.save(user);
        return ChargeBalanceResponse.of(...);
    }
}
```

### OptimisticLockRetryService

```java
@Service
@RequiredArgsConstructor
public class OptimisticLockRetryService {

    @Transactional(propagation = Propagation.NEVER)
    public <T> T executeWithRetry(Supplier<T> operation, int maxRetries) {
        while (retryCount < maxRetries) {
            try {
                return operation.get();  // ⚠️ 람다 내부에서 chargeBalance() 호출
            } catch (OptimisticLockingFailureException e) {
                // 재시도...
            }
        }
    }
}
```

### Spring AOP Self-Invocation 문제

**호출 순서**:
```
1. execute() 호출
   ↓
2. retryService.executeWithRetry(람다)
   ↓
3. 람다 내부에서 chargeBalance() 호출
   ↓
4. ⚠️ SELF-INVOCATION 발생!
```

**Spring AOP 동작 원리**:
```
외부 호출 → Proxy → AOP (DistributedLock) → 실제 메서드
내부 호출 → ❌ Proxy 안 거침 → AOP 미작동 → 실제 메서드
```

**핵심 문제**:
- `OptimisticLockRetryService`는 **별도 Spring Bean**
- 람다 내부에서 `this.chargeBalance()`를 호출
- `this`는 프록시가 아닌 **실제 객체**
- **@DistributedLock AOP가 작동하지 않음!**

---

## 💡 해결 방안

### 방안 1: Self-Invocation 제거 (ApplicationContext 주입)

```java
@UseCase
@RequiredArgsConstructor
public class ChargeBalanceUseCase {

    private final OptimisticLockRetryService retryService;
    private final ApplicationContext applicationContext;  // ✅ 추가

    public ChargeBalanceResponse execute(Long userId, ChargeBalanceRequest request) {
        return retryService.executeWithRetry(() -> {
            // ✅ 프록시를 통해 호출 (AOP 작동)
            ChargeBalanceUseCase proxy = applicationContext.getBean(ChargeBalanceUseCase.class);
            return proxy.chargeBalance(userId, request);
        }, 10);
    }

    @DistributedLock(...)
    @Transactional
    public ChargeBalanceResponse chargeBalance(Long userId, ChargeBalanceRequest request) {
        // ...
    }
}
```

**장점**:
- ✅ Self-Invocation 문제 해결
- ✅ @DistributedLock AOP 정상 작동

**단점**:
- ❌ ApplicationContext 의존성 추가 (약간의 결합도 증가)
- ❌ 코드 복잡도 증가

---

### 방안 2: 분산락을 재시도 로직 바깥으로 이동 (추천)

```java
@UseCase
@RequiredArgsConstructor
public class ChargeBalanceUseCase {

    private final OptimisticLockRetryService retryService;

    // ✅ 분산락을 외부 메서드에 적용
    @DistributedLock(
            key = "'balance:user:' + #userId",
            waitTime = 10,
            leaseTime = 30
    )
    public ChargeBalanceResponse execute(Long userId, ChargeBalanceRequest request) {
        // 분산락 획득 후 재시도 로직 실행
        return retryService.executeWithRetry(() -> chargeBalanceInternal(userId, request), 10);
    }

    // ✅ 내부 메서드: Optimistic Lock + 재시도만 담당
    @Transactional
    protected ChargeBalanceResponse chargeBalanceInternal(Long userId, ChargeBalanceRequest request) {
        User user = userRepository.findByIdOrThrow(userId);
        user.charge(request.amount());
        userRepository.save(user);
        return ChargeBalanceResponse.of(...);
    }
}
```

**동작 흐름**:
```
1. execute() 호출
   ↓
2. @DistributedLock AOP 적용 (분산락 획득)
   ↓
3. retryService.executeWithRetry(람다)
   ↓
4. 람다 내부에서 chargeBalanceInternal() 호출 (내부 호출이지만 @Transactional은 작동)
   ↓
5. Optimistic Lock + 재시도
   ↓
6. 분산락 해제
```

**장점**:
- ✅ Self-Invocation 문제 해결
- ✅ 코드 간결성 유지
- ✅ 관심사 분리 명확:
  - `execute()`: 분산락 담당
  - `chargeBalanceInternal()`: Optimistic Lock + 재시도 담당

**단점**:
- ⚠️ @Transactional은 Self-Invocation에도 작동 (Spring의 특수 처리)
- ⚠️ @DistributedLock은 Self-Invocation 시 미작동 (일반 AOP)

---

### 방안 3: ChargeBalanceTransactionService 분리 (가장 안전)

```java
// 1. UseCase (분산락 담당)
@UseCase
@RequiredArgsConstructor
public class ChargeBalanceUseCase {

    private final OptimisticLockRetryService retryService;
    private final ChargeBalanceTransactionService transactionService;  // ✅ 별도 서비스

    @DistributedLock(
            key = "'balance:user:' + #userId",
            waitTime = 10,
            leaseTime = 30
    )
    public ChargeBalanceResponse execute(Long userId, ChargeBalanceRequest request) {
        // ✅ 외부 Bean 호출 (확실한 프록시 적용)
        return retryService.executeWithRetry(() ->
            transactionService.chargeBalance(userId, request), 10
        );
    }
}

// 2. TransactionService (트랜잭션 + Optimistic Lock 담당)
@Service
@RequiredArgsConstructor
public class ChargeBalanceTransactionService {

    private final UserRepository userRepository;

    @Transactional
    public ChargeBalanceResponse chargeBalance(Long userId, ChargeBalanceRequest request) {
        User user = userRepository.findByIdOrThrow(userId);
        user.charge(request.amount());
        userRepository.save(user);
        return ChargeBalanceResponse.of(...);
    }
}
```

**장점**:
- ✅ Self-Invocation 완전 제거 (외부 Bean 호출)
- ✅ 관심사 분리 명확
- ✅ ProcessPaymentUseCase와 일관성 (PaymentTransactionService 사용)

**단점**:
- ❌ 클래스 추가 (복잡도 약간 증가)

---

## 🎯 권장 해결 방안

### 최종 선택: **방안 2 (분산락을 재시도 로직 바깥으로 이동)**

**이유**:
1. ✅ 코드 간결성 유지 (별도 서비스 불필요)
2. ✅ ProcessPaymentUseCase도 동일하게 수정 가능
3. ✅ 관심사 분리 명확
4. ✅ Self-Invocation 문제 해결

**단, @Transactional의 특수성 이해 필요**:
- @Transactional은 Self-Invocation에서도 작동 (Spring의 특수 처리)
- @DistributedLock은 일반 AOP라서 Self-Invocation 시 미작동

---

## 📝 수정 코드

### ChargeBalanceUseCase.java (수정)

```java
@Slf4j
@UseCase
@RequiredArgsConstructor
public class ChargeBalanceUseCase {

    private final UserRepository userRepository;
    private final OptimisticLockRetryService retryService;

    /**
     * 잔액 충전
     * <p>
     * 동시성 제어: 분산락 + Optimistic Lock + 자동 재시도
     * - 1차 방어: 분산락 (인스턴스 간 동시성 제어)
     * - 2차 방어: Optimistic Lock (@Version, DB 레벨)
     * - 3차 방어: 자동 재시도 (일시적 충돌 해결)
     */
    @DistributedLock(
            key = "'balance:user:' + #userId",
            waitTime = 10,
            leaseTime = 30
    )
    public ChargeBalanceResponse execute(Long userId, ChargeBalanceRequest request) {
        log.info("Charging balance for userId: {}, amount: {}", userId, request.amount());

        // 분산락 획득 후 재시도 로직 실행
        return retryService.executeWithRetry(() -> chargeBalanceInternal(userId, request), 10);
    }

    /**
     * 잔액 충전 실행 (트랜잭션 단위)
     * <p>
     * 동시성 제어: Optimistic Lock (@Version) + 자동 재시도
     * - Optimistic Lock: 충돌 가능성 낮음 (사용자별 데이터)
     * - 자동 재시도: 충돌 시 재시도로 해결
     */
    @Transactional
    protected ChargeBalanceResponse chargeBalanceInternal(Long userId, ChargeBalanceRequest request) {
        // 1. 사용자 조회 (Optimistic Lock)
        User user = userRepository.findByIdOrThrow(userId);

        // 2. 잔액 충전
        user.charge(request.amount());
        userRepository.save(user);

        log.debug("Balance charged successfully. userId: {}, new balance: {}", userId, user.getBalance());

        // 3. 충전 결과 반환
        return ChargeBalanceResponse.of(
            user.getId(),
            user.getBalance(),
            request.amount(),
            LocalDateTime.now()
        );
    }
}
```

---

## 🧪 검증

### 1. 수정 후 로그 확인

**예상 로그**:
```
INFO  i.h.e.i.r.DistributedLockAspect - 락 획득 성공: key=balance:user:1, leaseTime=30SECONDS
DEBUG i.h.e.a.u.user.ChargeBalanceUseCase - Balance charged successfully. userId: 1, new balance: 10000
INFO  i.h.e.i.r.DistributedLockAspect - 락 해제: key=balance:user:1
```

### 2. Redis 키 확인

```bash
$ docker exec ecommerce-redis redis-cli KEYS "*balance:user:*"
1) "balance:user:1"
2) "balance:user:2"
...
```

### 3. K6 테스트 재실행

```bash
$ k6 run docs/week5/verification/k6/scripts/balance-charge.js
```

**기대 결과**:
- ✅ Optimistic Lock 충돌 감소 (830건 → 10건 이하)
- ✅ 에러율 감소 (3.21% → 0.05% 이하)
- ✅ 성공률 증가 (96.78% → 99.95% 이상)

---

## 📊 K6 스크립트 개선 (다중 사용자)

### 현재 문제

```javascript
const USER_ID = __ENV.USER_ID || '1';  // ❌ 단일 사용자
```

**문제점**:
- 1000명 VU가 모두 USER_ID=1에 충전
- 분산락이 작동해도 전부 직렬화됨
- 실제 부하 테스트가 아님

### 개선 방안

```javascript
// ✅ 여러 사용자 사용
const USER_COUNT = __ENV.USER_COUNT || 100;  // 사용자 100명
const USER_ID = (__VU % USER_COUNT) + 1;  // VU 번호 % 100 + 1 = 1~100

export default function() {
  const url = `${BASE_URL}/api/users/${USER_ID}/balance/charge`;  // ✅ 동적 USER_ID
  // ...
}
```

**효과**:
- ✅ 1000 VU가 100명 사용자에 분산 (평균 10 VU/사용자)
- ✅ 동시성 테스트 더 현실적
- ✅ 분산락 효과 명확히 측정

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
3. **AOP를 외부 메서드로 이동** → Self-Invocation 회피

### 3. 테스트 중요성

- 단위 테스트로는 AOP 문제 발견 어려움
- **통합 테스트 + 부하 테스트** 필수
- K6, JMeter 등으로 실제 부하 테스트

---

## 🎯 결론

**문제**: Spring AOP Self-Invocation으로 분산락 미작동
**원인**: OptimisticLockRetryService에서 람다로 내부 메서드 호출
**해결**: 분산락을 재시도 로직 바깥으로 이동 (execute 메서드에 적용)
**효과**: 분산락 정상 작동, Optimistic Lock 충돌 감소, 에러율 감소

**다음 단계**:
1. ChargeBalanceUseCase 수정
2. K6 스크립트 개선 (다중 사용자)
3. 부하 테스트 재실행
4. ProcessPaymentUseCase도 동일하게 검토

---

**작성자**: Backend Development Team
**최종 수정**: 2025-11-26
**버전**: 1.0
