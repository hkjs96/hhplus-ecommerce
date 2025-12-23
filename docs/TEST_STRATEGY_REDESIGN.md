# 테스트 전략 재설계 문서

## 📊 현재 상황 분석 (2025-12-14)

### 테스트 실행 결과
- **총 테스트**: 205개
- **성공**: 124개 (60.5%)
- **실패**: 81개 (39.5%)
- **빌드 시간**: 1분 41초

### 실패 테스트 분류

#### Category A: Controller Integration Tests (50개 실패)
| 테스트 클래스 | 실패 수 | 원인 |
|-------------|---------|------|
| CartControllerIntegrationTest | 16 | MockMvc 400 에러 |
| CouponControllerIntegrationTest | 9 | MockMvc 400 에러 |
| OrderControllerIntegrationTest | 7 | MockMvc 400 에러 |
| UserControllerIntegrationTest | 6 | MockMvc 400 에러 |
| ProductControllerIntegrationTest | 5 | MockMvc 400 에러 |

**공통 원인**:
- MockMvc → Controller → Facade → UseCase (전체 스택 테스트)
- @DistributedLock AOP 실패
- 데이터 준비 부족 (setUp 트랜잭션 문제)

#### Category B: Concurrency Tests (14개 실패)
| 테스트 클래스 | 실패 수 | 원인 |
|-------------|---------|------|
| UserBalanceOptimisticLockConcurrencyTest | 5 | Context 공유 |
| CouponIssuanceConcurrencyWithDistributedLockTest | 3 | Redis Lock 실패 |
| ChargeBalanceIdempotencyTest | 3 | Redis 문제 |
| OrderConcurrencyTest | 2 | Optimistic Lock 충돌 |
| IssueCouponConcurrencyTest | 2 | DB 연결 문제 |
| CartItemConcurrencyTest | 2 | Context 공유 |

**공통 원인**:
- ApplicationContext 공유로 인한 데이터 간섭
- HikariPool Connection 고갈
- Redis 연결 불안정

#### Category C: Event & Repository Tests (17개 실패)
| 테스트 클래스 | 실패 수 | 원인 |
|-------------|---------|------|
| DatabasePerformanceAnalysisTest | 9 | DB Connection 문제 |
| JpaOrderRepository | 7 | Testcontainers 초기화 |
| PaymentEventIntegrationTest | 5 | 400 Bad Request |

---

## 🔍 근본 원인 분석

### 1. Heavy Integration Test 과다
```
18개의 @SpringBootTest/@DataJpaTest
→ ApplicationContext 로드 (MySQL + Redis Testcontainers)
→ 느린 테스트 실행 (1분 41초)
→ Infrastructure 의존성으로 인한 불안정
```

### 2. Infrastructure 강결합
```java
CreateOrderUseCase
├── @DistributedLock (Redis AOP)
├── @Transactional (MySQL)
├── Pessimistic Lock (MySQL)
├── EventPublisher (Spring)
└── Metrics (Micrometer)
```

**모든 Infrastructure가 정상 작동해야만 테스트 통과**

### 3. Context 공유 문제
```
Test Execution:
1. TestContainersConfig.static {} 블록 실행 (MySQL, Redis 시작)
2. 단일 ApplicationContext 생성
3. 모든 테스트 클래스가 같은 Context 공유
4. 데이터 간섭 발생
5. HikariPool 연결 고갈
```

### 4. 데이터 준비 문제
```java
@BeforeEach
void setUp() {
    // @Transactional 없음
    User user = userRepository.save(User.create(...));
    // 커밋 보장되지 않음 → API 호출 시 데이터 없음
}
```

---

## 🎯 개선 전략

### Phase 1: 즉시 적용 (1-2일)
**목표**: 테스트 성공률 60% → 85% 향상

#### 1.1 @DirtiesContext 강화
```java
// 클래스 레벨 → 메서드 레벨 변경
@DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
```

**효과**: Context 격리로 데이터 간섭 제거

#### 1.2 setUp 데이터 커밋 보장
```java
@Autowired
private PlatformTransactionManager transactionManager;

@BeforeEach
void setUp() {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.execute(status -> {
        User user = userRepository.save(User.create(...));
        testUserId = user.getId();
        return null;
    });
}
```

**효과**: API 호출 시 데이터 존재 보장

#### 1.3 Redis 연결 안정화
```java
@TestConfiguration
static class RedisTestConfig {
    @Bean
    @Primary
    public RedissonClient testRedissonClient() {
        // Connection Pool 증가
        // Retry 설정 강화
    }
}
```

---

### Phase 2: 테스트 계층 분리 (3-5일)
**목표**: Test Pyramid 구조 확립

```
         /\
        /E2E\         ← 5% (Critical Path만)
       /------\
      /Integration\   ← 20% (API → UseCase)
     /------------\
    /  Unit Tests  \  ← 75% (UseCase, Domain)
   /----------------\
```

#### 2.1 Unit Test 강화
```java
// Before: Integration Test
@SpringBootTest
class CreateOrderUseCaseTest {
    @Autowired CreateOrderUseCase useCase;
}

// After: Unit Test
class CreateOrderUseCaseTest {
    @Mock ProductRepository productRepository;
    @Mock OrderRepository orderRepository;
    @InjectMocks CreateOrderUseCase useCase;

    @Test
    void 주문생성_성공() {
        // Given: Mock 데이터
        when(productRepository.findById(...)).thenReturn(...);

        // When: UseCase 직접 호출
        CreateOrderResponse response = useCase.execute(request);

        // Then: 비즈니스 로직만 검증
        assertThat(response.getTotalAmount()).isEqualTo(90000);
    }
}
```

**장점**:
- 빠른 실행 (ms 단위)
- Infrastructure 독립
- 비즈니스 로직만 검증

#### 2.2 Integration Test 슬림화
```java
// API → UseCase 통합만 검증
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {
    @Test
    void POST_주문생성_201_Created() {
        // Given: 실제 DB 데이터
        // When: MockMvc API 호출
        // Then: HTTP 상태 코드만 검증
    }
}
```

**검증 범위**: HTTP 레이어 + UseCase 연동만

#### 2.3 E2E Test 최소화
```java
// 핵심 플로우만 검증
@SpringBootTest
class OrderPaymentE2ETest {
    @Test
    void 주문생성_결제_완료_전체플로우() {
        // 1. 주문 생성
        // 2. 결제 처리
        // 3. 이벤트 발행 검증
    }
}
```

**실행**: 별도 Profile로 분리 (`@ActiveProfiles("e2e")`)

---

### Phase 3: Infrastructure 추상화 (1주)
**목표**: 테스트에서 Infrastructure 의존성 제거

#### 3.1 분산락 추상화
```java
// Before
@DistributedLock(key = "order:create:user:" + #userId)
public CreateOrderResponse execute(...) {}

// After
public interface LockManager {
    <T> T executeWithLock(String key, Supplier<T> action);
}

// Test
class MockLockManager implements LockManager {
    public <T> T executeWithLock(String key, Supplier<T> action) {
        return action.get(); // 락 없이 바로 실행
    }
}
```

#### 3.2 Event Publisher 추상화
```java
// Test
@MockBean
ApplicationEventPublisher eventPublisher;

@Test
void 주문생성_이벤트발행() {
    useCase.execute(request);
    verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
}
```

---

## 📋 실행 계획

### Week 1: Quick Wins (Phase 1)
- [ ] Day 1: @DirtiesContext 메서드 레벨 적용 (6개 클래스)
- [ ] Day 2: setUp TransactionTemplate 적용
- [ ] Day 3: Redis 연결 안정화
- [ ] Day 4: 테스트 실행 및 검증 (목표: 85% 성공률)

### Week 2: Refactoring (Phase 2)
- [ ] Day 1-2: UseCase Unit Test 작성 (Mock 기반)
- [ ] Day 3-4: Integration Test 슬림화
- [ ] Day 5: E2E Test 분리

### Week 3: Architecture (Phase 3)
- [ ] Day 1-2: LockManager 인터페이스 도입
- [ ] Day 3-4: Event 추상화
- [ ] Day 5: 전체 테스트 실행 및 최종 검증

---

## 🎯 성공 지표

| 지표 | 현재 | Week 1 목표 | Week 2 목표 | Week 3 목표 |
|------|------|------------|------------|------------|
| **테스트 성공률** | 60.5% | 85% | 95% | 98% |
| **빌드 시간** | 1m 41s | 1m 30s | 1m | 40s |
| **Unit Test 비율** | 62% | 65% | 75% | 80% |
| **Integration Test 안정성** | 불안정 | 안정 | 안정 | 안정 |

---

## 🚨 위험 요소

1. **@DistributedLock 제거 영향**
   - 실제 동시성 제어 로직 변경 필요
   - 프로덕션 코드 수정 가능성

2. **테스트 리팩토링 시간**
   - 26개 테스트 클래스 수정
   - 예상 시간: 3주

3. **기존 기능 회귀**
   - 테스트 변경 중 버그 유입 가능
   - Feature Branch로 안전하게 진행 필요

---

## ✅ 다음 단계

1. **즉시 실행**: Phase 1 적용 (1-2일)
2. **검증**: 테스트 성공률 85% 달성 확인
3. **의사결정**: Phase 2/3 진행 여부 결정

---

**작성일**: 2025-12-14
**작성자**: Claude Code
**상태**: Draft
