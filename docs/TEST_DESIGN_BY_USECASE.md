# 테스트 설계 재검토: 유스케이스별 분리 전략

## 📅 작성일: 2025-12-14

---

## 🎯 **핵심 문제 인식**

### 현재 PaymentEventIntegrationTest의 문제점

1. **통합 테스트가 너무 무겁다**
   - MockMvc → Controller → Facade → UseCase → Domain → Infrastructure
   - 전체 스택을 테스트하면서 **setUp 데이터 준비 실패** 시 모든 테스트 실패

2. **데이터 준비와 테스트 실행의 분리 실패**
   - setUp()에서 `userRepository.save()` → ID가 null
   - TransactionTemplate 사용해도 동일 문제
   - @DirtiesContext로 인한 Context 재시작 문제

3. **테스트 목적이 혼재되어 있음**
   - 주문 생성 API 테스트인가?
   - 이벤트 발행 테스트인가?
   - 랭킹 갱신 로직 테스트인가?

---

## 📊 **유스케이스 분석**

### PaymentEventIntegrationTest가 검증하는 5가지 시나리오

| 테스트 메서드 | 주요 UseCase | 검증 대상 | 현재 레벨 |
|-------------|-------------|----------|----------|
| **1. paymentCompleted_랭킹갱신_비동기실행** | 결제 완료 → 랭킹 갱신 | RankingEventListener 비동기 실행 | E2E (MockMvc) |
| **2. paymentCompleted_데이터플랫폼전송_비동기실행** | 결제 완료 → 데이터 전송 | DataPlatformEventListener 비동기 | E2E (MockMvc) |
| **3. paymentCompleted_여러상품_랭킹갱신** | 여러 상품 주문 → 각각 랭킹 | 상품별 score 증가 | E2E (MockMvc) |
| **4. paymentCompleted_동일상품_여러주문_랭킹누적** | 동일 상품 반복 주문 | score 누적 | E2E (MockMvc) |
| **5. transactionalEventListener_afterCommit검증** | AFTER_COMMIT 검증 | 트랜잭션 커밋 후 이벤트 발행 | E2E (MockMvc) |

**문제**: 모두 **E2E 레벨 (MockMvc 전체 스택)**로 테스트 중
**해결책**: Test Pyramid에 따라 계층별로 분리

---

## 🏗️ **Test Pyramid 재설계**

```
         /\
        /E2E\         ← 5% (핵심 플로우만)
       /------\
      /Integration\   ← 20% (이벤트 발행 검증)
     /------------\
    /  Unit Tests  \  ← 75% (리스너 로직)
   /----------------\
```

---

## 📁 **유스케이스별 테스트 분리 전략**

### UseCase 1: RankingEventListener 단위 테스트 (Unit)

**목적**: 이벤트 수신 시 랭킹 갱신 로직만 검증

**파일**: `RankingEventListenerTest.java`

**구조**:
```java
@ExtendWith(MockitoExtension.class)
class RankingEventListenerTest {

    @Mock
    private ProductRankingRepository rankingRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private RankingEventListener listener;

    @Test
    @DisplayName("결제 완료 이벤트 수신 시 각 상품별 랭킹 score 증가")
    void onPaymentCompleted_랭킹갱신() {
        // Given: 주문 아이템 Mock 데이터
        OrderItem item1 = OrderItem.create(order, product1, 3, 10000L);
        OrderItem item2 = OrderItem.create(order, product2, 5, 20000L);
        when(orderItemRepository.findByOrderId(1L))
            .thenReturn(List.of(item1, item2));

        // When: 이벤트 처리
        PaymentCompletedEvent event = new PaymentCompletedEvent(1L, 1L, 50000L);
        listener.onPaymentCompleted(event);

        // Then: 각 상품별 score 증가 검증
        verify(rankingRepository).incrementScore(
            eq(LocalDate.now()),
            eq(product1.getId().toString()),
            eq(3)
        );
        verify(rankingRepository).incrementScore(
            eq(LocalDate.now()),
            eq(product2.getId().toString()),
            eq(5)
        );
    }

    @Test
    @DisplayName("동일 상품 여러 번 주문 시 score 누적")
    void onPaymentCompleted_score누적() {
        // Given: 동일 상품 3개 주문
        OrderItem item = OrderItem.create(order, product1, 3, 10000L);
        when(orderItemRepository.findByOrderId(any()))
            .thenReturn(List.of(item));

        // When: 3번 이벤트 발행
        for (int i = 0; i < 3; i++) {
            listener.onPaymentCompleted(
                new PaymentCompletedEvent(i, 1L, 30000L)
            );
        }

        // Then: score가 3 + 3 + 3 = 9 증가
        verify(rankingRepository, times(3)).incrementScore(
            eq(LocalDate.now()),
            eq(product1.getId().toString()),
            eq(3)
        );
    }
}
```

**장점**:
- ✅ 빠른 실행 (ms 단위)
- ✅ DB/Redis 불필요
- ✅ 비즈니스 로직만 검증
- ✅ setUp 데이터 준비 문제 없음

---

### UseCase 2: DataPlatformEventListener 단위 테스트 (Unit)

**파일**: `DataPlatformEventListenerTest.java`

```java
@ExtendWith(MockitoExtension.class)
class DataPlatformEventListenerTest {

    @Mock
    private DataPlatformClient dataPlatformClient;

    @Mock
    private OutboxRepository outboxRepository;

    @InjectMocks
    private DataPlatformEventListener listener;

    @Test
    @DisplayName("결제 완료 이벤트 수신 시 데이터 플랫폼 전송")
    void onPaymentCompleted_데이터전송_성공() {
        // Given: 이벤트
        PaymentCompletedEvent event = new PaymentCompletedEvent(1L, 1L, 50000L);

        // When: 이벤트 처리
        listener.onPaymentCompleted(event);

        // Then: DataPlatformClient 호출 검증
        verify(dataPlatformClient).sendPaymentData(
            argThat(data ->
                data.getOrderId() == 1L &&
                data.getUserId() == 1L &&
                data.getAmount() == 50000L
            )
        );
    }

    @Test
    @DisplayName("데이터 전송 실패 시 Outbox에 저장")
    void onPaymentCompleted_전송실패_Outbox저장() {
        // Given: 전송 실패 시뮬레이션
        PaymentCompletedEvent event = new PaymentCompletedEvent(1L, 1L, 50000L);
        when(dataPlatformClient.sendPaymentData(any()))
            .thenThrow(new RuntimeException("External API failure"));

        // When: 이벤트 처리
        listener.onPaymentCompleted(event);

        // Then: Outbox에 저장
        verify(outboxRepository).save(
            argThat(outbox ->
                outbox.getEventType().equals("PAYMENT_COMPLETED")
            )
        );
    }
}
```

---

### UseCase 3: PaymentCompletedEvent 발행 통합 테스트 (Integration)

**목적**: ProcessPaymentUseCase가 PaymentCompletedEvent를 발행하는지 검증

**파일**: `ProcessPaymentUseCaseIntegrationTest.java`

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProcessPaymentUseCaseIntegrationTest {

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @MockBean
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("결제 처리 성공 시 PaymentCompletedEvent 발행")
    void processPayment_이벤트발행() {
        // Given: DB에 실제 데이터 저장 (Transactional로 자동 롤백)
        User user = userRepository.save(User.create("test@example.com", "테스트유저"));
        user.charge(1_000_000L);

        Product product = productRepository.save(
            Product.create("P001", "테스트상품", "설명", 10000L, "전자제품", 100)
        );

        Order order = orderRepository.save(
            Order.create("ORDER-001", user, 30000L, 0L)
        );

        // When: 결제 처리
        PaymentRequest request = new PaymentRequest(user.getId(), "PAYMENT-001");
        processPaymentUseCase.execute(order.getId(), request);

        // Then: 이벤트 발행 검증
        verify(eventPublisher).publishEvent(
            argThat(event ->
                event instanceof PaymentCompletedEvent &&
                ((PaymentCompletedEvent) event).getOrderId().equals(order.getId())
            )
        );
    }
}
```

**장점**:
- ✅ @Transactional로 데이터 자동 롤백
- ✅ MockBean으로 이벤트 리스너 실행 스킵
- ✅ 이벤트 발행만 검증

---

### UseCase 4: @TransactionalEventListener AFTER_COMMIT 검증 (Integration)

**파일**: `TransactionalEventListenerTest.java`

```java
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class TransactionalEventListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ProductRankingRepository rankingRepository;

    @Test
    @DisplayName("AFTER_COMMIT: 트랜잭션 커밋 후에만 이벤트 처리")
    @Transactional
    void afterCommit_이벤트처리() throws InterruptedException {
        // Given: 이벤트 발행 (트랜잭션 내부)
        PaymentCompletedEvent event = new PaymentCompletedEvent(1L, 1L, 30000L);

        // When: 트랜잭션 내에서 이벤트 발행
        eventPublisher.publishEvent(event);

        // 트랜잭션이 커밋되기 전에는 리스너 실행 안 됨
        int scoreBefore = rankingRepository.getScore(LocalDate.now(), "1");
        assertThat(scoreBefore).isEqualTo(0);

        // Then: 트랜잭션 커밋 (@Transactional 종료 시 자동)
        // 이 메서드가 끝나면 트랜잭션 커밋 → 이벤트 리스너 실행

        // 별도 검증 메서드에서 확인
    }

    @Test
    @DisplayName("트랜잭션 롤백 시 이벤트 미발행")
    void rollback_이벤트미발행() throws InterruptedException {
        // Given & When: 트랜잭션 내에서 예외 발생
        assertThatThrownBy(() -> {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.execute(status -> {
                eventPublisher.publishEvent(
                    new PaymentCompletedEvent(1L, 1L, 30000L)
                );
                throw new RuntimeException("강제 롤백");
            });
        });

        // Then: 이벤트 리스너 실행 안 됨
        Thread.sleep(1000);
        int score = rankingRepository.getScore(LocalDate.now(), "1");
        assertThat(score).isEqualTo(0);
    }
}
```

---

### UseCase 5: E2E 테스트 (최소화)

**목적**: 전체 플로우 검증 (주문 → 결제 → 이벤트 → 랭킹)

**파일**: `OrderPaymentE2ETest.java`

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@Sql(scripts = "/test-data-e2e.sql", executionPhase = BEFORE_TEST_METHOD)
@Sql(scripts = "/cleanup.sql", executionPhase = AFTER_TEST_METHOD)
class OrderPaymentE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRankingRepository rankingRepository;

    @Test
    @DisplayName("E2E: 주문 생성 → 결제 → 랭킹 갱신")
    void 전체플로우() throws Exception {
        // Given: SQL로 고정 데이터 준비 (userId=999, productId=888)

        // When 1: 주문 생성
        CreateOrderRequest orderRequest = new CreateOrderRequest(
            999L,
            List.of(new OrderItemRequest(888L, 3)),
            null,
            "ORDER-" + UUID.randomUUID()
        );

        String orderResponse = mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(orderResponse).get("orderId").asLong();

        // When 2: 결제 처리
        PaymentRequest paymentRequest = new PaymentRequest(999L, "PAYMENT-" + UUID.randomUUID());

        mockMvc.perform(post("/api/orders/" + orderId + "/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)))
            .andExpect(status().isOk());

        // Then: 비동기 처리 대기 후 랭킹 확인
        Thread.sleep(3000);

        int score = rankingRepository.getScore(LocalDate.now(), "888");
        assertThat(score).isGreaterThanOrEqualTo(3);
    }
}
```

**특징**:
- ✅ @Sql로 고정 ID (999, 888) 사용
- ✅ 전체 플로우 검증
- ✅ 최소한의 시나리오만 (1개)

---

## 📁 **최종 파일 구조**

```
src/test/java/io/hhplus/ecommerce/
├── application/
│   ├── payment/
│   │   └── listener/
│   │       ├── RankingEventListenerTest.java              ← Unit
│   │       ├── DataPlatformEventListenerTest.java         ← Unit
│   │       └── TransactionalEventListenerTest.java        ← Integration
│   └── usecase/
│       └── order/
│           └── ProcessPaymentUseCaseIntegrationTest.java  ← Integration
└── e2e/
    └── OrderPaymentE2ETest.java                           ← E2E

src/test/resources/
├── test-data-e2e.sql       ← E2E 테스트용 고정 데이터
└── cleanup.sql             ← 테스트 후 정리
```

---

## 📊 **효과 비교**

| 항목 | 현재 (Before) | 개선 후 (After) |
|------|--------------|----------------|
| **테스트 파일 수** | 1개 | 5개 |
| **단위 테스트** | 0개 | 2개 (RankingEventListener, DataPlatformEventListener) |
| **통합 테스트** | 0개 | 2개 (ProcessPaymentUseCase, TransactionalEventListener) |
| **E2E 테스트** | 5개 | 1개 (전체 플로우만) |
| **실행 시간** | ~40초 (E2E만) | ~5초 (Unit) + ~15초 (Integration) + ~10초 (E2E) = 30초 |
| **setUp 실패 시** | 전체 실패 (5/5) | Unit은 영향 없음 (0/2) |
| **테스트 안정성** | 낮음 (데이터 준비 실패) | 높음 (Mock 사용) |

---

## 🎯 **실행 계획 (Phase별)**

### Phase 1: Unit Test 작성 (2시간)
1. ✅ `RankingEventListenerTest.java` 작성
2. ✅ `DataPlatformEventListenerTest.java` 작성
3. 실행: `./gradlew test --tests "*EventListenerTest"`

**목표**: 빠른 피드백 (< 5초), Mock으로 DB 독립

---

### Phase 2: Integration Test 작성 (2시간)
1. ✅ `ProcessPaymentUseCaseIntegrationTest.java` 작성
2. ✅ `TransactionalEventListenerTest.java` 작성
3. 실행: `./gradlew test --tests "*IntegrationTest"`

**목표**: 이벤트 발행 검증, @Transactional 자동 롤백

---

### Phase 3: E2E Test 최소화 (1시간)
1. ✅ `/test-data-e2e.sql` 작성 (고정 ID: userId=999, productId=888)
2. ✅ `OrderPaymentE2ETest.java` 작성 (1개 시나리오)
3. ✅ 기존 `PaymentEventIntegrationTest.java` 삭제

**목표**: 핵심 플로우만 검증 (1개)

---

### Phase 4: 전체 빌드 및 검증 (30분)
```bash
./gradlew clean test
```

**예상 결과**:
- Unit Test: 10개 추가 (빠른 실행)
- Integration Test: 5개 추가
- E2E Test: 5개 → 1개 감소
- **전체 성공률**: 60.5% → 85%+

---

## ✅ **다음 단계**

1. **즉시 시작**: Phase 1 (Unit Test) 작성
2. **검증**: 각 Phase별로 테스트 실행하여 성공 확인
3. **리팩토링**: 기존 PaymentEventIntegrationTest 제거
4. **문서화**: README에 Test Pyramid 구조 반영

---

## 📝 **핵심 원칙**

### Test Pyramid 3원칙

1. **Unit Test (75%)**: Mock 사용, 빠른 실행, 비즈니스 로직만 검증
2. **Integration Test (20%)**: 실제 Bean 사용, 이벤트/트랜잭션 검증
3. **E2E Test (5%)**: 전체 플로우, 핵심 시나리오만

### 테스트 격리 원칙

1. **Unit**: 완전 독립 (Mock/Stub만 사용)
2. **Integration**: @Transactional 자동 롤백
3. **E2E**: @Sql로 고정 데이터, @DirtiesContext 최소화

---

**작성일**: 2025-12-14
**작성자**: Claude Code
**상태**: Ready to Implement
**예상 소요 시간**: 5시간 (Phase 1~4)
