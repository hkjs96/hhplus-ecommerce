# 테스트 실패 분석 및 개선 계획 요약

## 📅 작성일: 2025-12-14

---

## 🎯 **분석 요청 사항**

> payment-test.log를 확인하고, 본질적으로 코드 정보를 다시 확인하고,
> 테스트 설계를 고민 또는 코드 역설계 고민 계획을 작성 (유스케이스로 분리해도 좋음)

---

## 🔍 **근본 원인 분석 결과**

### 1차 조사: @DistributedLock 의심
- **가설**: DistributedLockAspect가 Redis lock 획득 실패 → BusinessException → HTTP 400
- **검증**: CreateOrderUseCase에서 @DistributedLock 주석 처리
- **결과**: ❌ **여전히 400 에러** → DistributedLock은 원인이 아님

### 2차 조사: TransactionTemplate 람다 캡처 문제
- **가설**: setUp()에서 testUserId/testProductId가 람다 바깥으로 전달 안 됨
- **검증**: TransactionTemplate.execute() 리턴값 디버깅
- **결과**: ❌ **`result[0]: null`, `result[1]: null`** → ID 자체가 생성 안 됨

### 3차 조사 (최종): **JPA save() 후 ID 미생성**
- **근본 원인**:
  ```java
  User savedUser = userRepository.save(user);
  savedUser.getId() → null !!!

  // @GeneratedValue(strategy = GenerationType.IDENTITY)를 사용하는 경우
  // flush() 없이는 DB INSERT가 지연되어 ID가 할당되지 않음
  ```

- **검증 로그**:
  ```
  ===== TransactionTemplate.execute() 리턴값 =====
  result: [Ljava.lang.Object;@2644facd   ← 배열은 정상
  result.length: 2                       ← 길이도 2
  result[0]: null                        ← ID가 null!!
  result[1]: null                        ← ID가 null!!
  ```

- **실제 에러 메시지**:
  ```json
  Request: {"userId":null,"items":[{"productId":null,"quantity":3}],...}
  Response: {"code":"COMMON002","message":"사용자 ID는 필수입니다",...}
  ```

---

## 📋 **작성한 계획 문서**

### 1. `CODE_REVERSE_ENGINEERING_PLAN.md`
**내용**: 근본 원인 분석 및 3가지 해결 방안

| 해결 방안 | 설명 | 장점 | 단점 |
|----------|------|------|------|
| **Option 1 (권장)** | @Sql 스크립트로 고정 ID 사용 | 명확한 데이터 준비 | SQL 파일 관리 |
| **Option 2** | JdbcTemplate으로 직접 INSERT | Java 코드로 관리 | 여전히 복잡함 |
| **Option 3** | Repository를 JpaRepository로 변경 | saveAndFlush() 사용 가능 | 도메인 코드 수정 |

**실행 계획**:
- Phase 1: @Sql 적용 (30분)
- Phase 2: 다른 테스트 적용 (2시간)
- Phase 3: 전체 빌드 검증 (30분)

---

### 2. `TEST_DESIGN_BY_USECASE.md` ✨ **핵심 문서**
**내용**: 유스케이스별 테스트 분리 전략 (Test Pyramid 재설계)

#### 현재 문제:
```
PaymentEventIntegrationTest (5개 테스트)
└── 모두 E2E 레벨 (MockMvc 전체 스택)
    ├── setUp() 데이터 준비 실패 → 전체 실패
    ├── @DirtiesContext로 Context 재시작
    └── 실행 시간 ~40초
```

#### 개선 후:
```
Unit Test (2개 파일)
├── RankingEventListenerTest          ← Mock 사용, < 1초
└── DataPlatformEventListenerTest     ← Mock 사용, < 1초

Integration Test (2개 파일)
├── ProcessPaymentUseCaseIntegrationTest  ← @Transactional 자동 롤백
└── TransactionalEventListenerTest        ← AFTER_COMMIT 검증

E2E Test (1개 파일)
└── OrderPaymentE2ETest               ← @Sql로 고정 데이터, 1개 시나리오
```

#### Test Pyramid:
```
         /\
        /E2E\         ← 5% (1개 시나리오)
       /------\
      /Integration\   ← 20% (이벤트 발행)
     /------------\
    /  Unit Tests  \  ← 75% (리스너 로직)
   /----------------\
```

---

## 📊 **효과 비교**

| 항목 | Before (현재) | After (개선) |
|------|--------------|-------------|
| **테스트 파일** | 1개 (PaymentEventIntegrationTest) | 5개 (UseCase별 분리) |
| **Unit Test** | 0개 | 2개 (빠른 실행) |
| **Integration Test** | 0개 | 2개 (@Transactional) |
| **E2E Test** | 5개 (모두 MockMvc) | 1개 (핵심만) |
| **실행 시간** | ~40초 | ~30초 (Unit 5초 + Integration 15초 + E2E 10초) |
| **setUp 실패 시** | 5/5 실패 | Unit은 영향 없음 (Mock 사용) |
| **테스트 안정성** | 낮음 (데이터 준비 문제) | 높음 (격리된 테스트) |
| **전체 성공률** | 60.5% (124/205) | **85%+ 예상** |

---

## 🎯 **5개 UseCase 분리**

### UseCase 1: RankingEventListener 로직 검증 (Unit)
**파일**: `RankingEventListenerTest.java`
- **목적**: 이벤트 수신 시 랭킹 갱신 로직만 검증
- **방법**: Mock Repository 사용
- **검증**: `verify(rankingRepository).incrementScore(...)`

### UseCase 2: DataPlatformEventListener 로직 검증 (Unit)
**파일**: `DataPlatformEventListenerTest.java`
- **목적**: 데이터 전송 로직 검증
- **방법**: Mock Client 사용
- **검증**: 전송 성공/실패 시 Outbox 저장

### UseCase 3: PaymentCompletedEvent 발행 검증 (Integration)
**파일**: `ProcessPaymentUseCaseIntegrationTest.java`
- **목적**: UseCase가 이벤트를 발행하는지 검증
- **방법**: @MockBean ApplicationEventPublisher
- **검증**: `verify(eventPublisher).publishEvent(...)`

### UseCase 4: AFTER_COMMIT 동작 검증 (Integration)
**파일**: `TransactionalEventListenerTest.java`
- **목적**: 트랜잭션 커밋 후 이벤트 처리 확인
- **방법**: 실제 Redis 사용
- **검증**: 커밋 전/후 랭킹 score 변화

### UseCase 5: 전체 플로우 검증 (E2E)
**파일**: `OrderPaymentE2ETest.java`
- **목적**: 주문 → 결제 → 이벤트 → 랭킹 전체 검증
- **방법**: @Sql로 고정 데이터 (userId=999, productId=888)
- **검증**: 1개 시나리오만 (핵심 플로우)

---

## 📁 **최종 파일 구조**

```
src/test/java/io/hhplus/ecommerce/
├── application/
│   ├── payment/listener/
│   │   ├── RankingEventListenerTest.java              ✅ Unit
│   │   ├── DataPlatformEventListenerTest.java         ✅ Unit
│   │   └── TransactionalEventListenerTest.java        ✅ Integration
│   └── usecase/order/
│       └── ProcessPaymentUseCaseIntegrationTest.java  ✅ Integration
└── e2e/
    └── OrderPaymentE2ETest.java                       ✅ E2E

src/test/resources/
├── test-data-e2e.sql       ← E2E용 고정 데이터 (userId=999, productId=888)
└── cleanup.sql             ← 테스트 후 정리
```

---

## 🚀 **실행 계획 (5시간)**

### Phase 1: Unit Test 작성 (2시간)
```bash
# 1. RankingEventListenerTest 작성
# 2. DataPlatformEventListenerTest 작성
./gradlew test --tests "*EventListenerTest"
```
**목표**: Mock으로 빠른 실행 (< 5초)

### Phase 2: Integration Test 작성 (2시간)
```bash
# 3. ProcessPaymentUseCaseIntegrationTest 작성
# 4. TransactionalEventListenerTest 작성
./gradlew test --tests "*IntegrationTest"
```
**목표**: @Transactional 자동 롤백

### Phase 3: E2E Test 최소화 (1시간)
```bash
# 5. test-data-e2e.sql 작성
# 6. OrderPaymentE2ETest 작성 (1개 시나리오)
# 7. PaymentEventIntegrationTest 삭제
./gradlew test --tests "OrderPaymentE2ETest"
```
**목표**: @Sql로 데이터 준비 문제 해결

### Phase 4: 전체 검증 (30분)
```bash
./gradlew clean build
```
**예상 결과**: 60.5% → 85%+ 성공률

---

## ✅ **핵심 원칙**

### 1. Test Pyramid 준수
- **Unit (75%)**: Mock 사용, 빠른 피드백
- **Integration (20%)**: 실제 Bean, 이벤트/트랜잭션 검증
- **E2E (5%)**: 핵심 플로우만

### 2. 테스트 격리
- **Unit**: 완전 독립 (DB/Redis 불필요)
- **Integration**: @Transactional 자동 롤백
- **E2E**: @Sql로 고정 데이터, @DirtiesContext 최소화

### 3. 데이터 준비 전략
- **Unit**: Mock 객체
- **Integration**: @Transactional + 실제 Repository
- **E2E**: @Sql 스크립트 (고정 ID)

---

## 📝 **관련 문서**

1. **TEST_FAILURE_ROOT_CAUSE_ANALYSIS.md**
   - @DistributedLock 의심 → 실제 원인은 ID 미생성

2. **CODE_REVERSE_ENGINEERING_PLAN.md**
   - JPA save() ID 생성 문제 분석
   - 3가지 해결 방안 제시

3. **TEST_DESIGN_BY_USECASE.md** ⭐
   - 유스케이스별 테스트 분리 상세 계획
   - 각 테스트 파일의 구체적 코드 예시 포함

4. **TEST_STRATEGY_REDESIGN.md**
   - 전체 테스트 전략 재설계 (81개 실패 분석)

---

## 🎯 **다음 단계**

1. ✅ **Phase 1 시작**: Unit Test 작성 (RankingEventListener, DataPlatformEventListener)
2. ✅ **Phase 2**: Integration Test 작성
3. ✅ **Phase 3**: E2E Test 최소화 + 기존 삭제
4. ✅ **Phase 4**: 전체 빌드 및 성공률 확인

**시작 순서**: `TEST_DESIGN_BY_USECASE.md` → Phase 1부터 순차 진행

---

**작성일**: 2025-12-14
**작성자**: Claude Code
**상태**: ✅ Analysis Complete, Ready to Implement
**예상 소요**: 5시간
**목표**: 테스트 성공률 60.5% → 85%+
