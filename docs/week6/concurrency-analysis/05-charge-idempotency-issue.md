# 충전 중복 방지 이슈 - Idempotency Key 필요성

## 🔴 심각한 문제 발견!

**K6 테스트 결과는 완벽하지만, 중복 충전 방지가 안 됨!**

### 현재 상황
```
✅ 분산락 작동: 동시 요청 직렬화
✅ Optimistic Lock: Lost Update 방지
✅ 자동 재시도: 일시적 충돌 해결
❌ 중복 충전 방지: 없음!
```

---

## 🔍 문제 시나리오

### 시나리오 1: 사용자가 실수로 버튼 두 번 클릭

```
사용자: "10,000원 충전" 버튼 클릭 (실수로 두 번)
  ↓
요청 1: POST /api/users/1/balance/charge {"amount": 10000}
  ↓ (0.5초 후)
요청 2: POST /api/users/1/balance/charge {"amount": 10000}
  ↓
분산락 작동: 요청 1 완료 → 요청 2 시작
  ↓
결과: 20,000원 충전됨! 😱
```

**문제점**:
- ✅ 동시 요청은 분산락으로 직렬화됨
- ❌ **순차적으로 처리되어 두 번 다 성공함!**
- ❌ **사용자는 10,000원만 충전하려 했지만 20,000원 충전됨**

### 시나리오 2: 네트워크 타임아웃 후 재시도

```
사용자: "10,000원 충전" 버튼 클릭
  ↓
요청 1: POST /api/users/1/balance/charge {"amount": 10000}
  ↓ (서버 처리 중...)
클라이언트: 타임아웃 (3초) → 에러 표시
  ↓ (실제로는 서버에서 성공)
사용자: "재시도" 버튼 클릭
  ↓
요청 2: POST /api/users/1/balance/charge {"amount": 10000}
  ↓
결과: 20,000원 충전됨! 😱
```

### 시나리오 3: Optimistic Lock 재시도 중복

```
요청 1: 10,000원 충전 시작
  ↓
Optimistic Lock 충돌 → 재시도 1회
  ↓ (재시도 중...)
요청 2: 동일한 10,000원 충전 요청
  ↓
분산락: 요청 1 완료 후 요청 2 처리
  ↓
결과: 20,000원 충전됨! 😱
```

---

## 💡 해결 방안: Idempotency Key

### 결제(ProcessPayment)는 이미 구현됨 ✅

#### PaymentIdempotency Entity
```java
@Entity
@Table(
    name = "payment_idempotency",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotency_key")
    }
)
public class PaymentIdempotency {

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;  // 클라이언트 제공 UUID

    @Enumerated(EnumType.STRING)
    private IdempotencyStatus status;  // PROCESSING, COMPLETED, FAILED

    @Column(columnDefinition = "TEXT")
    private String responsePayload;  // 완료된 응답 (캐싱)

    // ...
}
```

#### ProcessPaymentUseCase (예상 구현)
```java
@DistributedLock(key = "'payment:idempotency:' + #request.idempotencyKey()")
@Transactional
public PaymentResponse execute(Long orderId, PaymentRequest request) {
    // 1. 중복 결제 체크
    PaymentIdempotency idempotency = idempotencyRepository
        .findByIdempotencyKey(request.idempotencyKey());

    if (idempotency != null) {
        if (idempotency.isCompleted()) {
            // ✅ 이미 완료된 요청 → 캐시된 응답 반환
            return deserializeResponse(idempotency.getResponsePayload());
        }
        if (idempotency.isProcessing() && !idempotency.isExpired()) {
            // ⏳ 처리 중 → 대기 또는 에러
            throw new BusinessException("결제 처리 중입니다");
        }
    }

    // 2. 멱등성 키 생성 (PROCESSING 상태)
    idempotency = PaymentIdempotency.create(request.idempotencyKey(), orderId);
    idempotencyRepository.save(idempotency);

    // 3. 결제 처리
    PaymentResponse response = processPaymentInternal(orderId, request);

    // 4. 완료 처리 (응답 캐싱)
    idempotency.complete(orderId, serializeResponse(response));
    idempotencyRepository.save(idempotency);

    return response;
}
```

**핵심 메커니즘**:
1. **Unique Constraint**: DB 레벨에서 동일 키 중복 삽입 방지
2. **상태 관리**: PROCESSING → COMPLETED → 완료된 응답 반환
3. **응답 캐싱**: COMPLETED 상태의 응답을 저장하여 반환

---

## 🛠️ 충전(ChargeBalance)에도 동일하게 적용 필요

### 1. ChargeBalanceIdempotency Entity 생성

```java
@Entity
@Table(
    name = "charge_balance_idempotency",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_charge_idempotency_key", columnNames = "idempotency_key")
    },
    indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_created_at", columnList = "created_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChargeBalanceIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 클라이언트 제공 멱등성 키 (UUID 권장)
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    /**
     * 요청한 사용자 ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 충전 금액
     */
    @Column(nullable = false)
    private Long amount;

    /**
     * 처리 상태 (PROCESSING, COMPLETED, FAILED)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyStatus status;

    /**
     * 완료된 응답 (JSON 저장)
     */
    @Column(columnDefinition = "TEXT")
    private String responsePayload;

    /**
     * 실패 시 에러 메시지
     */
    @Column(length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 만료 시간 (기본 24시간)
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public static ChargeBalanceIdempotency create(String idempotencyKey, Long userId, Long amount) {
        ChargeBalanceIdempotency entity = new ChargeBalanceIdempotency();
        entity.idempotencyKey = idempotencyKey;
        entity.userId = userId;
        entity.amount = amount;
        entity.status = IdempotencyStatus.PROCESSING;
        entity.createdAt = LocalDateTime.now();
        entity.expiresAt = LocalDateTime.now().plusHours(24);
        return entity;
    }

    public void complete(String responsePayload) {
        this.responsePayload = responsePayload;
        this.status = IdempotencyStatus.COMPLETED;
    }

    public void fail(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = IdempotencyStatus.FAILED;
    }

    public boolean isCompleted() {
        return this.status == IdempotencyStatus.COMPLETED;
    }

    public boolean isProcessing() {
        return this.status == IdempotencyStatus.PROCESSING && !isExpired();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
```

### 2. ChargeBalanceRequest DTO 수정

```java
public record ChargeBalanceRequest(
    Long amount,
    String idempotencyKey  // ✅ 추가
) {
    // Validation
    public ChargeBalanceRequest {
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "충전 금액은 0보다 커야 합니다");
        }
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "멱등성 키는 필수입니다");
        }
    }
}
```

### 3. ChargeBalanceUseCase 수정

```java
@Slf4j
@UseCase
@RequiredArgsConstructor
public class ChargeBalanceUseCase {

    private final UserRepository userRepository;
    private final OptimisticLockRetryService retryService;
    private final ChargeBalanceIdempotencyRepository idempotencyRepository;  // ✅ 추가

    /**
     * 잔액 충전 (멱등성 보장)
     * <p>
     * 동시성 제어: 분산락 + Optimistic Lock + 자동 재시도
     * 멱등성 보장: Idempotency Key + DB Unique Constraint
     */
    @DistributedLock(
            key = "'charge:idempotency:' + #request.idempotencyKey()",  // ✅ 키 변경
            waitTime = 10,
            leaseTime = 30
    )
    public ChargeBalanceResponse execute(Long userId, ChargeBalanceRequest request) {
        log.info("Charging balance for userId: {}, amount: {}, idempotencyKey: {}",
                userId, request.amount(), request.idempotencyKey());

        // 1. 멱등성 키 조회
        Optional<ChargeBalanceIdempotency> existingIdempotency =
                idempotencyRepository.findByIdempotencyKey(request.idempotencyKey());

        if (existingIdempotency.isPresent()) {
            ChargeBalanceIdempotency idempotency = existingIdempotency.get();

            // 1-1. 이미 완료된 요청 → 캐시된 응답 반환
            if (idempotency.isCompleted()) {
                log.info("Returning cached response for idempotencyKey: {}", request.idempotencyKey());
                return deserializeResponse(idempotency.getResponsePayload());
            }

            // 1-2. 처리 중인 요청 → 에러 (다른 요청이 처리 중)
            if (idempotency.isProcessing()) {
                throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "이미 처리 중인 요청입니다. idempotencyKey: " + request.idempotencyKey()
                );
            }

            // 1-3. 실패했거나 만료된 요청 → 재처리 가능
            log.info("Retrying expired/failed request. idempotencyKey: {}", request.idempotencyKey());
        }

        // 2. 멱등성 키 생성 (PROCESSING 상태)
        ChargeBalanceIdempotency idempotency =
                ChargeBalanceIdempotency.create(request.idempotencyKey(), userId, request.amount());
        idempotencyRepository.save(idempotency);

        try {
            // 3. 충전 처리 (재시도 로직 포함)
            ChargeBalanceResponse response =
                    retryService.executeWithRetry(() -> chargeBalanceInternal(userId, request), 10);

            // 4. 완료 처리 (응답 캐싱)
            idempotency.complete(serializeResponse(response));
            idempotencyRepository.save(idempotency);

            log.info("Charge completed successfully. idempotencyKey: {}", request.idempotencyKey());
            return response;

        } catch (Exception e) {
            // 5. 실패 처리
            idempotency.fail(e.getMessage());
            idempotencyRepository.save(idempotency);
            throw e;
        }
    }

    @Transactional
    protected ChargeBalanceResponse chargeBalanceInternal(Long userId, ChargeBalanceRequest request) {
        User user = userRepository.findByIdOrThrow(userId);
        user.charge(request.amount());
        userRepository.save(user);

        log.debug("Balance charged successfully. userId: {}, new balance: {}", userId, user.getBalance());

        return ChargeBalanceResponse.of(
            user.getId(),
            user.getBalance(),
            request.amount(),
            LocalDateTime.now()
        );
    }

    /**
     * JSON 직렬화/역직렬화
     */
    private String serializeResponse(ChargeBalanceResponse response) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("응답 직렬화 실패", e);
        }
    }

    private ChargeBalanceResponse deserializeResponse(String json) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        try {
            return objectMapper.readValue(json, ChargeBalanceResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("응답 역직렬화 실패", e);
        }
    }
}
```

---

## 🔄 동작 흐름 (멱등성 보장)

### 정상 케이스
```
1. 요청 1: idempotencyKey="abc-123", amount=10000
   ↓
2. 멱등성 키 조회 → 없음
   ↓
3. ChargeBalanceIdempotency 생성 (PROCESSING)
   ↓
4. 충전 처리 (10,000원)
   ↓
5. COMPLETED 상태로 변경, 응답 캐싱
   ↓
6. 응답 반환
```

### 중복 요청 케이스
```
1. 요청 1: idempotencyKey="abc-123", amount=10000
   ↓
2. 멱등성 키 조회 → 없음
   ↓
3. ChargeBalanceIdempotency 생성 (PROCESSING)
   ↓
4. 충전 처리 중...
   ↓ (동시에)
5. 요청 2: idempotencyKey="abc-123", amount=10000
   ↓
6. 분산락 대기...
   ↓
7. 요청 1 완료 → 분산락 해제
   ↓
8. 요청 2 시작
   ↓
9. 멱등성 키 조회 → 있음! (COMPLETED)
   ↓
10. ✅ 캐시된 응답 반환 (중복 충전 방지!)
```

### 네트워크 타임아웃 후 재시도 케이스
```
1. 요청 1: idempotencyKey="abc-123", amount=10000
   ↓
2. 충전 처리 성공 (COMPLETED)
   ↓ (클라이언트는 타임아웃으로 에러 표시)
3. 사용자: "재시도" 클릭
   ↓
4. 요청 2: idempotencyKey="abc-123", amount=10000 (같은 키!)
   ↓
5. 멱등성 키 조회 → 있음! (COMPLETED)
   ↓
6. ✅ 캐시된 응답 반환 (중복 충전 방지!)
```

---

## 📊 비교: 수정 전 vs 수정 후

| 시나리오 | 수정 전 | 수정 후 (Idempotency Key) |
|---------|---------|--------------------------|
| **버튼 두 번 클릭** | 20,000원 충전 ❌ | 10,000원 충전 ✅ (2번째 요청은 캐시 반환) |
| **네트워크 타임아웃 재시도** | 20,000원 충전 ❌ | 10,000원 충전 ✅ (같은 키로 재시도) |
| **Optimistic Lock 재시도** | 정상 (재시도는 같은 요청) ✅ | 정상 ✅ |
| **동시 요청 (다른 키)** | 순차 처리, 각각 성공 ✅ | 순차 처리, 각각 성공 ✅ |

---

## 🎯 클라이언트 구현 가이드

### 프론트엔드에서 idempotencyKey 생성

```javascript
// React 예시
function ChargeBalancePage() {
  const [idempotencyKey, setIdempotencyKey] = useState(null);

  const handleChargeClick = async () => {
    // 1. 버튼 클릭 시 idempotencyKey 생성 (UUID)
    const key = idempotencyKey || uuidv4();
    setIdempotencyKey(key);

    try {
      // 2. 충전 요청 (같은 키로 재시도)
      const response = await fetch('/api/users/1/balance/charge', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          amount: 10000,
          idempotencyKey: key  // ✅ 멱등성 키 포함
        })
      });

      if (response.ok) {
        // 3. 성공 시 키 초기화 (다음 충전은 새 키)
        setIdempotencyKey(null);
        alert('충전 성공!');
      }
    } catch (error) {
      // 4. 실패 시 키 유지 (재시도에 같은 키 사용)
      alert('충전 실패. 재시도하세요.');
    }
  };

  return (
    <button onClick={handleChargeClick}>10,000원 충전</button>
  );
}
```

**핵심**:
- ✅ 버튼 클릭 시 UUID 생성
- ✅ 성공 시 키 초기화 (다음 충전은 새 키)
- ✅ 실패 시 키 유지 (재시도에 같은 키 사용)

---

## ✅ 구현 체크리스트

### 필수 구현
- [ ] ChargeBalanceIdempotency Entity 생성
- [ ] ChargeBalanceIdempotencyRepository 생성
- [ ] ChargeBalanceRequest에 idempotencyKey 추가
- [ ] ChargeBalanceUseCase에 멱등성 로직 추가
- [ ] 분산락 키 변경 (`balance:user:id` → `charge:idempotency:key`)
- [ ] JSON 직렬화/역직렬화 유틸 추가

### 테스트
- [ ] 중복 충전 방지 테스트 (같은 idempotencyKey)
- [ ] 동시 요청 처리 테스트 (다른 idempotencyKey)
- [ ] 네트워크 타임아웃 시나리오 테스트
- [ ] 만료된 키 재사용 테스트 (24시간 후)

### 프론트엔드 가이드
- [ ] idempotencyKey 생성 가이드 문서 작성
- [ ] 재시도 정책 가이드 (같은 키 사용)
- [ ] API 명세 업데이트

---

## 📚 참고 자료

### 멱등성 개념
- [Idempotency (Wikipedia)](https://en.wikipedia.org/wiki/Idempotence)
- [Stripe API Idempotent Requests](https://stripe.com/docs/api/idempotent_requests)
- [AWS API Gateway Idempotency](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-idempotency.html)

### 결제 멱등성 (이미 구현됨)
- `PaymentIdempotency.java` - 결제 멱등성 Entity
- `IdempotencyStatus.java` - 상태 관리 (PROCESSING, COMPLETED, FAILED)
- `ProcessPaymentUseCase.java` - 결제 멱등성 구현 예시

---

## 🎯 결론

**현재 상황**:
- ✅ 분산락, Optimistic Lock, 자동 재시도는 완벽
- ❌ **멱등성 보장 안 됨** → 중복 충전 가능

**해결 방법**:
- ✅ Idempotency Key 도입 (결제와 동일한 메커니즘)
- ✅ DB Unique Constraint로 동시성 제어
- ✅ 상태 관리 (PROCESSING → COMPLETED)
- ✅ 응답 캐싱 (중복 요청 시 캐시 반환)

**우선순위**:
- 🔴 **매우 높음** - 금전 관련 기능이므로 중복 방지 필수
- 🔴 **프로덕션 배포 전 필수 구현**

**다음 단계**:
1. ChargeBalanceIdempotency Entity 생성
2. ChargeBalanceUseCase 수정
3. 테스트 작성 (중복 충전 방지)
4. 프론트엔드 가이드 작성

---

**작성자**: Backend Development Team
**최종 수정**: 2025-11-26
**버전**: 1.0
**상태**: 설계 완료, 구현 대기
