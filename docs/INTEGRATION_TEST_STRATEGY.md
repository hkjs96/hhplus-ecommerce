# 통합 테스트 전략: UseCase vs Controller vs Domain

## 📅 작성일: 2025-12-14

---

## 🎯 **핵심 질문**

> "통합 테스트는 UseCase 클래스 부분을 해야되는건가?"

**답변**: 상황에 따라 다릅니다! 각 계층의 **책임**에 따라 통합 테스트 위치를 결정해야 합니다.

---

## 📊 **통합 테스트 배치 전략**

### 1. Controller Integration Test
**목적**: HTTP API 계층 검증 (Request → Response)

**검증 대상**:
- ✅ HTTP 요청/응답 포맷
- ✅ 상태 코드 (200, 201, 400, 404 등)
- ✅ Request Validation (@Valid)
- ✅ GlobalExceptionHandler
- ✅ Controller → Facade/UseCase 연동

**예시**:
```java
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean  // ← UseCase는 Mock 처리
    private CreateOrderUseCase createOrderUseCase;

    @Test
    void POST_주문생성_201_Created() throws Exception {
        // Given: UseCase Mock 설정
        when(createOrderUseCase.execute(any()))
            .thenReturn(new CreateOrderResponse(...));

        // When: HTTP POST 요청
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":1,\"items\":[...]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").exists());

        // Then: UseCase 호출 검증
        verify(createOrderUseCase).execute(any());
    }

    @Test
    void POST_주문생성_400_InvalidRequest() throws Exception {
        // Given: 잘못된 요청 (userId null)

        // When & Then: Validation 실패 → 400
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[...]}"))  // userId 없음
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON002"));
    }
}
```

**장점**:
- ✅ HTTP 레이어만 검증 (빠름)
- ✅ UseCase Mock으로 DB 독립
- ✅ API 명세 준수 검증

---

### 2. UseCase Integration Test
**목적**: 비즈니스 로직 + Infrastructure 연동 검증

**검증 대상**:
- ✅ UseCase 비즈니스 로직
- ✅ Repository 실제 DB 연동
- ✅ 이벤트 발행
- ✅ 트랜잭션 경계

**예시**:
```java
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@Transactional  // ← 각 테스트 후 자동 롤백
class CreateOrderUseCaseIntegrationTest {

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @MockBean  // ← 이벤트는 Mock 처리
    private ApplicationEventPublisher eventPublisher;

    @Test
    void 주문생성_성공_이벤트발행() {
        // Given: 실제 DB에 데이터 저장
        User user = userRepository.save(User.create("test@example.com", "테스트"));
        user.charge(100_000L);

        Product product = productRepository.save(
            Product.create("P001", "상품", "설명", 10_000L, "카테고리", 100)
        );

        CreateOrderRequest request = new CreateOrderRequest(
            user.getId(),
            List.of(new OrderItemRequest(product.getId(), 3)),
            null,
            "ORDER-" + UUID.randomUUID()
        );

        // When: UseCase 실행
        CreateOrderResponse response = createOrderUseCase.execute(request);

        // Then: 비즈니스 로직 검증
        assertThat(response.orderId()).isNotNull();
        assertThat(response.totalAmount()).isEqualTo(30_000L);

        // Then: 이벤트 발행 검증
        verify(eventPublisher).publishEvent(
            argThat(event -> event instanceof OrderCreatedEvent)
        );
    }

    @Test
    void 주문생성_재고부족_예외발생() {
        // Given: 재고 부족 상품
        User user = userRepository.save(User.create("test@example.com", "테스트"));
        Product product = productRepository.save(
            Product.create("P001", "상품", "설명", 10_000L, "카테고리", 5)  // 재고 5
        );

        CreateOrderRequest request = new CreateOrderRequest(
            user.getId(),
            List.of(new OrderItemRequest(product.getId(), 10)),  // 10개 주문
            null,
            "ORDER-" + UUID.randomUUID()
        );

        // When & Then: 재고 부족 예외
        assertThatThrownBy(() -> createOrderUseCase.execute(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("재고가 부족합니다");
    }
}
```

**장점**:
- ✅ 비즈니스 로직 + DB 연동 검증
- ✅ @Transactional 자동 롤백
- ✅ 이벤트는 Mock으로 처리

---

### 3. Domain Service Integration Test
**목적**: 도메인 서비스 + Repository 연동 검증

**검증 대상**:
- ✅ 도메인 로직
- ✅ Pessimistic Lock
- ✅ Optimistic Lock
- ✅ 동시성 제어

**예시**:
```java
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class ProductStockServiceIntegrationTest {

    @Autowired
    private ProductStockService productStockService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @Transactional
    void 재고차감_Pessimistic_Lock_성공() {
        // Given: 재고 100개 상품
        Product product = productRepository.save(
            Product.create("P001", "상품", "설명", 10_000L, "카테고리", 100)
        );

        // When: 재고 30개 차감
        productStockService.decreaseStock(product.getId(), 30);

        // Then: 재고 70개 남음
        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getStock()).isEqualTo(70);
    }

    @Test
    void 동시_재고차감_Pessimistic_Lock_정합성() throws InterruptedException {
        // Given: 재고 100개 상품
        Product product = productRepository.save(
            Product.create("P001", "상품", "설명", 10_000L, "카테고리", 100)
        );

        // When: 10개 스레드가 동시에 10개씩 차감
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    productStockService.decreaseStock(product.getId(), 10);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // Then: 재고 0개 (정확히 100개 차감)
        Product result = productRepository.findById(product.getId()).orElseThrow();
        assertThat(result.getStock()).isEqualTo(0);
    }
}
```

**장점**:
- ✅ 도메인 로직 + 동시성 검증
- ✅ 실제 DB Lock 동작 확인

---

### 4. EventListener Integration Test
**목적**: 이벤트 리스너 + Infrastructure 연동 검증

**검증 대상**:
- ✅ @TransactionalEventListener AFTER_COMMIT
- ✅ 비동기 처리 (@Async)
- ✅ 외부 시스템 연동 (Redis, 외부 API)

**예시**:
```java
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class RankingEventListenerIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ProductRankingRepository rankingRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void AFTER_COMMIT_이벤트처리_랭킹갱신() throws InterruptedException {
        // Given: 트랜잭션 내에서 주문 아이템 생성 및 이벤트 발행
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        Long orderId = template.execute(status -> {
            Order order = Order.create("ORDER-001", user, 30000L, 0L);
            OrderItem item = OrderItem.create(order, product, 3, 10000L);
            orderItemRepository.save(item);

            // 이벤트 발행
            eventPublisher.publishEvent(
                new PaymentCompletedEvent(order.getId(), user.getId(), 30000L)
            );

            return order.getId();
        });
        // ← 여기서 트랜잭션 커밋 → AFTER_COMMIT 리스너 실행

        // When: 비동기 처리 대기
        Thread.sleep(1000);

        // Then: 랭킹 score 증가 확인
        int score = rankingRepository.getScore(LocalDate.now(), product.getId().toString());
        assertThat(score).isEqualTo(3);
    }

    @Test
    void 트랜잭션_롤백_시_이벤트미발행() throws InterruptedException {
        // Given & When: 트랜잭션 내에서 예외 발생
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> {
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

**장점**:
- ✅ AFTER_COMMIT 동작 검증
- ✅ 실제 Redis 연동 확인

---

## 📊 **통합 테스트 배치 매트릭스**

| 테스트 대상 | 위치 | 실제 Bean | Mock Bean | DB | Redis | 목적 |
|-----------|------|----------|-----------|----|----|------|
| **Controller** | `presentation.api` | Controller | UseCase | ❌ | ❌ | HTTP API 검증 |
| **UseCase** | `application.usecase` | UseCase, Repository | EventPublisher | ✅ | ❌ | 비즈니스 로직 + DB |
| **Domain Service** | `domain` | Service, Repository | - | ✅ | ❌ | 도메인 로직 + Lock |
| **EventListener** | `application.listener` | Listener, Repository | - | ✅ | ✅ | 이벤트 + Infrastructure |

---

## 🎯 **PaymentEventIntegrationTest 재배치**

### 기존 (Before)
```
PaymentEventIntegrationTest (E2E)
└── MockMvc → Controller → Facade → UseCase → Domain → Infrastructure
    ├── 전체 스택 테스트
    └── setUp() 데이터 준비 실패 → 전체 실패
```

### 개선 (After)

#### 1. Controller 레벨 통합 테스트
**파일**: `OrderControllerIntegrationTest.java`
```java
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private CreateOrderUseCase createOrderUseCase;  // ← Mock

    @Test
    void POST_주문생성_201() { /* HTTP API 검증 */ }
}
```

#### 2. UseCase 레벨 통합 테스트 ✅ **권장**
**파일**: `ProcessPaymentUseCaseIntegrationTest.java`
```java
@SpringBootTest
@Transactional
class ProcessPaymentUseCaseIntegrationTest {
    @Autowired private ProcessPaymentUseCase useCase;
    @Autowired private OrderRepository orderRepository;  // ← 실제 DB
    @MockBean private ApplicationEventPublisher eventPublisher;  // ← Mock

    @Test
    void 결제처리_성공_이벤트발행() { /* UseCase + DB 검증 */ }
}
```

#### 3. EventListener 레벨 통합 테스트 ✅ **권장**
**파일**: `RankingEventListenerIntegrationTest.java`
```java
@SpringBootTest
class RankingEventListenerIntegrationTest {
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private ProductRankingRepository rankingRepository;  // ← 실제 Redis

    @Test
    void AFTER_COMMIT_랭킹갱신() { /* 이벤트 + Redis 검증 */ }
}
```

---

## ✅ **최종 권장 사항**

### PaymentEventIntegrationTest의 5개 시나리오 재배치

| 기존 테스트 메서드 | 새로운 위치 | 레벨 | 이유 |
|-----------------|-----------|------|------|
| paymentCompleted_랭킹갱신_비동기실행 | `RankingEventListenerIntegrationTest` | Integration | 이벤트 리스너 검증 |
| paymentCompleted_데이터플랫폼전송_비동기실행 | `DataPlatformEventListenerIntegrationTest` | Integration | 이벤트 리스너 검증 |
| paymentCompleted_여러상품_랭킹갱신 | `RankingEventListenerTest` | Unit | 비즈니스 로직만 |
| paymentCompleted_동일상품_여러주문_랭킹누적 | `RankingEventListenerTest` | Unit | 비즈니스 로직만 |
| transactionalEventListener_afterCommit검증 | `RankingEventListenerIntegrationTest` | Integration | AFTER_COMMIT 동작 |

### 통합 테스트 위치 결정 기준

1. **HTTP API 검증**: `*ControllerIntegrationTest`
   - MockMvc 사용
   - UseCase는 Mock

2. **비즈니스 로직 + DB**: `*UseCaseIntegrationTest` ✅
   - UseCase 실행
   - Repository는 실제 DB
   - EventPublisher는 Mock

3. **도메인 로직 + Lock**: `*ServiceIntegrationTest`
   - Domain Service 실행
   - 동시성 제어 검증

4. **이벤트 + Infrastructure**: `*EventListenerIntegrationTest` ✅
   - EventListener 실행
   - Redis/외부 API 실제 연동

---

## 🎯 **답변 요약**

> "통합 테스트는 UseCase 클래스 부분을 해야되는건가?"

**답변**:
- ✅ **UseCase 통합 테스트**: 비즈니스 로직 + DB 연동 검증
- ✅ **EventListener 통합 테스트**: 이벤트 + Redis/외부 API 검증
- ✅ **Controller 통합 테스트**: HTTP API만 검증 (UseCase Mock)

**현재 PaymentEventIntegrationTest의 경우**:
- 대부분은 **EventListener 통합 테스트**로 이동
- 일부는 **Unit Test**로 변경 (Mock 사용)
- E2E는 **1개 시나리오만** 남김

---

**작성일**: 2025-12-14
**작성자**: Claude Code
**상태**: ✅ Strategy Complete
