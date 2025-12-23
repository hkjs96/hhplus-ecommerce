# DB 락 → Redis 분산락 전환 분석 보고서

> **분석일**: 2025-11-26
> **현재 상태**: STEP11-12 분산락 & 캐싱 구현 준비
> **목적**: 현재 DB 락 기반 동시성 제어를 Redis 분산락으로 전환이 필요한 포인트 파악

---

## 📊 현재 상태 분석

### 1. Redis 인프라 준비 상태 ✅

**docker-compose.yml**
```yaml
redis:
  image: redis:7-alpine
  container_name: ecommerce-redis
  ports:
    - "6379:6379"
  command: redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru
```

✅ **준비 완료**: Redis 7-alpine, maxmemory 256MB, LRU 정책

---

### 2. 분산락 인프라 준비 상태 ✅

**이미 구현된 컴포넌트:**
- ✅ `@DistributedLock` 어노테이션 (`src/main/java/io/hhplus/ecommerce/infrastructure/redis/DistributedLock.java`)
- ✅ `DistributedLockAspect` AOP (`src/main/java/io/hhplus/ecommerce/infrastructure/redis/DistributedLockAspect.java`)

**이미 분산락이 적용된 UseCase:**
1. ✅ `IssueCouponUseCase` - `@DistributedLock(key = "'coupon:issue:' + #couponId")`
2. ✅ `ChargeBalanceUseCase` - `@DistributedLock(key = "'charge:user:' + #userId")`
3. ✅ `PaymentTransactionService.reservePayment` - `@DistributedLock(key = "'payment:user:' + #request.userId()")`

---

## 🎯 DB 락 vs Redis 분산락 전환 포인트 분석

### 전환 기준 (멘토링 기반)

| 기준 | DB 락 유지 | Redis 분산락 전환 |
|-----|-----------|-----------------|
| **TPS** | < 100 | > 100 |
| **인스턴스** | 단일 서버 | 다중 인스턴스 |
| **충돌 빈도** | < 1% | > 10% |
| **비즈니스 크리티컬** | 일반적 | 재고/결제/선착순 |
| **DB 부하** | 낮음 | 높음 (병목) |

---

## 📋 현재 동시성 제어 현황

### 1. ✅ 이미 분산락 적용된 영역

#### 1-1. 쿠폰 발급 (IssueCouponUseCase) ✅

**현재 상태:**
```java
@DistributedLock(
    key = "'coupon:issue:' + #couponId",
    waitTime = 5,
    leaseTime = 10
)
@Transactional
public IssueCouponResponse execute(Long couponId, IssueCouponRequest request)
```

**동시성 제어 전략:**
- ✅ Redis 분산락 (여러 인스턴스 간 동시성 제어)
- ✅ Pessimistic Lock (`findByIdWithLockOrThrow`)
- ✅ DB Unique Constraint (중복 발급 방지)

**적용 이유:**
- **선착순 이벤트**: 정확성 최우선
- **높은 동시성**: 수백~수천 명이 동시 요청
- **다중 인스턴스**: 여러 서버에서 동시 처리

**결론:** ✅ **적절히 적용됨 - 유지**

---

#### 1-2. 잔액 충전 (ChargeBalanceUseCase) ✅

**현재 상태:**
```java
@DistributedLock(
    key = "'charge:user:' + #userId",
    waitTime = 5,
    leaseTime = 10
)
@Transactional
protected ChargeBalanceResponse chargeBalance(Long userId, ChargeBalanceRequest request)
```

**동시성 제어 전략:**
- ✅ Redis 분산락 (다중 인스턴스 대응)
- ✅ Optimistic Lock (`@Version`)
- ✅ 자동 재시도 (`OptimisticLockRetryService`, 최대 10회)

**적용 이유:**
- **충돌 가능성 낮음**: 사용자별로 본인만 충전
- **재시도 가능**: 금액 손실 없음
- **성능 우선**: waitTime 짧게 (5초)

**멘토링 인사이트 (제이 코치):**
> "일반적으로 한 사용자가 동시에 여러 번 잔액 충전하는 건 드문 편입니다.
> 낙관락 + 재시도로 충분하지만, 정기 결제 등 자동 결제가 있다면 분산락 고려"

**결론:** ✅ **적절히 적용됨 - 유지**

---

#### 1-3. 결제 처리 (PaymentTransactionService.reservePayment) ✅

**현재 상태:**
```java
@DistributedLock(
    key = "'payment:user:' + #request.userId()",
    waitTime = 10,
    leaseTime = 30
)
@Transactional
public Order reservePayment(Long orderId, PaymentRequest request)
```

**동시성 제어 전략:**
- ✅ Redis 분산락 (다중 인스턴스 대응)
- ✅ Pessimistic Lock (잔액, 재고)
  - `userRepository.findByIdWithLockOrThrow(request.userId())`
  - `productRepository.findByIdWithLockOrThrow(item.getProductId())`

**적용 이유:**
- **크리티컬한 비즈니스**: 잔액/재고 차감 (Lost Update 절대 불가)
- **충돌 빈번**: 동일 상품 동시 주문
- **재시도 불가**: 결제 실패 시 재시도 불가능

**보상 트랜잭션 패턴:**
```
정상 흐름: 잔액 차감 → PG 승인 → 주문 완료
실패 시: compensatePayment() → 잔액 복구 + 재고 복구
```

**결론:** ✅ **적절히 적용됨 - 유지**

---

### 2. ⚠️ 분산락 미적용 영역 (전환 검토 필요)

#### 2-1. 주문 생성 (CreateOrderUseCase) ⚠️ **전환 검토 필요**

**현재 상태:**
```java
@Transactional
public CreateOrderResponse execute(CreateOrderRequest request)
```

**현재 동시성 제어:**
- ❌ **분산락 없음**
- ❌ Pessimistic Lock 없음
- ✅ 재고 검증만 있음 (`product.getStock() < itemReq.quantity()`)

**문제점:**
```java
// 1. 재고 확인 (Read)
if (product.getStock() < itemReq.quantity()) {
    throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
}

// 2. 주문 생성 (Write)
Order order = Order.create(...);
orderRepository.save(order);

// 3. 주문 아이템 생성
// ⚠️ 재고는 ProcessPaymentUseCase에서 차감 (결제 시)
```

**TOCTOU (Time-of-Check to Time-of-Use) 문제:**
```
시나리오: 재고 10개, 동시 주문 20건

Thread 1: 재고 확인 (10개) ✅ → 주문 생성 ✅
Thread 2: 재고 확인 (10개) ✅ → 주문 생성 ✅
...
Thread 20: 재고 확인 (10개) ✅ → 주문 생성 ✅

결과: 20개 주문 생성됨 (재고는 아직 10개)
→ 나중에 결제 시 10개만 성공, 10개 실패 (고객 불만)
```

**멘토링 인사이트 (김종협 코치):**
> "주문 생성 시점에 재고 확인만 하고 차감은 결제 시에 한다면,
> 그 사이에 다른 요청이 들어와 재고가 부족해질 수 있습니다.
> 주문 생성 시점에 이미 '이 재고는 예약됨' 상태로 만들어야 합니다."

---

**전환 방안:**

**Option 1: 분산락 추가 (권장)**
```java
@DistributedLock(
    key = "'order:product:' + #request.items()[0].productId()",  // 첫 번째 상품 기준
    waitTime = 10,
    leaseTime = 30
)
@Transactional
public CreateOrderResponse execute(CreateOrderRequest request)
```

**Option 2: 재고 예약 시스템 도입**
```java
// 주문 생성 시 재고 예약
for (OrderItemRequest item : request.items()) {
    product.reserveStock(item.quantity());  // 예약 상태로 변경
}

// 결제 실패 시 예약 해제
product.releaseReservedStock(quantity);
```

**Option 3: Pessimistic Lock 추가**
```java
// 주문 생성 시 재고 차감까지 진행
Product product = productRepository.findByIdWithLockOrThrow(itemReq.productId());
product.decreaseStock(itemReq.quantity());

// 결제 실패 시 보상 트랜잭션
product.increaseStock(quantity);
```

**추천 전환 순서:**
1. ✅ **단계 1**: 분산락 추가 (다중 인스턴스 대응)
2. ✅ **단계 2**: Pessimistic Lock 추가 (DB 레벨 안전장치)
3. ⚠️ **단계 3**: 재고 예약 시스템 (선택적, 복잡도 증가)

---

#### 2-2. 인기 상품 조회 (GetTopProductsUseCase) ⚠️ **캐싱 적용 필요**

**현재 상태:**
```java
@Transactional(readOnly = true)
public List<TopProductResponse> execute()
```

**현재 동시성 제어:**
- ❌ **캐싱 없음**
- ❌ 분산락 없음
- ✅ 읽기 전용 트랜잭션

**문제점:**
- **매 요청마다 DB 조회**
- **복잡한 집계 쿼리** (JOIN, GROUP BY, ORDER BY)
- **응답 시간 느림** (500ms 이상 예상)

**전환 방안: Cache-Aside 패턴 + 분산락**

```java
@Transactional(readOnly = true)
public List<TopProductResponse> execute() {
    String cacheKey = "popular:products:top5";

    // 1. 캐시 조회
    RBucket<List<TopProductResponse>> bucket = redissonClient.getBucket(cacheKey);
    List<TopProductResponse> cached = bucket.get();

    if (cached != null) {
        log.info("캐시 Hit: {}", cacheKey);
        return cached;
    }

    // 2. Cache Miss - 분산락으로 DB 조회 중복 방지
    log.info("캐시 Miss: {}", cacheKey);
    return getTopProductsWithLock(cacheKey);
}

@DistributedLock(key = "'lock:popular:products'", waitTime = 5, leaseTime = 10)
private List<TopProductResponse> getTopProductsWithLock(String cacheKey) {
    // Double-Check
    RBucket<List<TopProductResponse>> bucket = redissonClient.getBucket(cacheKey);
    List<TopProductResponse> cached = bucket.get();

    if (cached != null) {
        return cached;
    }

    // DB 조회
    List<TopProduct> topProducts = productSalesAggregateRepository.findTop5();
    List<TopProductResponse> response = topProducts.stream()
        .map(TopProductResponse::from)
        .toList();

    // TTL 랜덤화 (Cache Stampede 방지)
    Duration baseTTL = Duration.ofMinutes(5);
    Duration randomizedTTL = baseTTL.plus(
        Duration.ofSeconds(ThreadLocalRandom.current().nextInt(60))
    );

    bucket.set(response, randomizedTTL);
    log.info("캐시 저장: {} (TTL: {})", cacheKey, randomizedTTL);

    return response;
}
```

**성능 개선 예상:**
- Before: 500ms (DB 조회)
- After: 1~5ms (캐시 조회)
- **개선율: 95~99%**

---

### 3. ✅ 분산락 불필요한 영역 (DB 락 유지)

#### 3-1. 조회 UseCase (읽기 전용) ✅

**유지 대상:**
- `GetUserUseCase`
- `GetBalanceUseCase`
- `GetProductUseCase`
- `GetProductsUseCase`
- `GetOrdersUseCase`
- `GetCartUseCase`
- `GetUserCouponsUseCase`

**이유:**
- ✅ **읽기 전용** (`@Transactional(readOnly = true)`)
- ✅ **동시성 이슈 없음** (데이터 변경 없음)
- ✅ **성능 우선** (락 불필요)

**개선 방안:**
- **캐싱 적용** (조회 빈도 높은 경우)
- **DB Connection Pool 관리**

---

#### 3-2. 장바구니 (Cart) 관련 UseCase ✅

**유지 대상:**
- `AddToCartUseCase`
- `UpdateCartItemUseCase`
- `RemoveFromCartUseCase`

**현재 동시성 제어:**
- ❌ 분산락 없음
- ❌ Pessimistic Lock 없음

**이유:**
- ✅ **사용자별 독립적** (userId로 분리)
- ✅ **충돌 가능성 낮음** (본인만 접근)
- ✅ **재시도 가능** (장바구니 변경은 크리티컬하지 않음)

**멘토링 인사이트 (김종협 코치):**
> "장바구니는 사용자별로 독립적이라 동시성 이슈가 거의 없습니다.
> 본인이 여러 기기에서 동시에 장바구니를 수정하는 경우만 문제인데,
> 이는 Last-Write-Wins로 처리해도 충분합니다."

**결론:** ✅ **DB 락 유지 (분산락 불필요)**

---

## 🎯 전환 우선순위

### 1순위: 주문 생성 (CreateOrderUseCase) 🔴 **즉시 적용 필요**

**전환 이유:**
- ⚠️ **TOCTOU 갭 존재**: 재고 확인과 주문 생성 사이 경쟁 상태
- ⚠️ **고객 불만 야기**: 주문 성공 후 결제 실패 (재고 부족)
- ⚠️ **비즈니스 크리티컬**: 주문/결제 프로세스의 시작점

**적용 방법:**
```java
@DistributedLock(
    key = "'order:create:user:' + #request.userId()",
    waitTime = 10,
    leaseTime = 30
)
@Transactional
public CreateOrderResponse execute(CreateOrderRequest request)
```

**추가 보완:**
- Pessimistic Lock 추가 (`findByIdWithLockOrThrow`)
- 재고 예약 시스템 도입 (선택)

---

### 2순위: 인기 상품 조회 (GetTopProductsUseCase) 🟡 **캐싱 적용**

**전환 이유:**
- ⚠️ **성능 병목**: 복잡한 집계 쿼리 (500ms 이상)
- ⚠️ **조회 빈도 높음**: 메인 페이지, 검색 등
- ⚠️ **DB 부하**: 매 요청마다 DB 조회

**적용 방법:**
- Cache-Aside 패턴
- 분산락으로 Cache Stampede 방지
- TTL 랜덤화 (5분 ± 10%)

**기대 효과:**
- 응답 시간 95% 감소 (500ms → 1~5ms)
- DB 부하 99% 감소

---

### 3순위: 장바구니 (Cart) ✅ **유지**

**전환하지 않는 이유:**
- ✅ 사용자별 독립적
- ✅ 충돌 가능성 낮음
- ✅ 비즈니스 크리티컬도 낮음

---

## 📊 전환 효과 예측

### Before (현재 상태)

| UseCase | 동시성 제어 | 예상 TPS | 병목 지점 |
|---------|-----------|---------|----------|
| CreateOrder | ❌ 없음 | 50 | TOCTOU 갭 |
| GetTopProducts | ❌ 없음 | 100 | DB 조회 |
| IssueCoupon | ✅ 분산락 | 200 | Redis 락 |
| ChargeBalance | ✅ 분산락 + 낙관락 | 300 | 낙관락 충돌 |
| ProcessPayment | ✅ 분산락 + 비관락 | 100 | 외부 PG API |

### After (분산락 + 캐싱 적용)

| UseCase | 동시성 제어 | 예상 TPS | 개선율 |
|---------|-----------|---------|--------|
| CreateOrder | ✅ 분산락 + 비관락 | 150 | **+200%** |
| GetTopProducts | ✅ 캐싱 + 분산락 | 1,000 | **+900%** |
| IssueCoupon | ✅ 분산락 | 200 | 유지 |
| ChargeBalance | ✅ 분산락 + 낙관락 | 300 | 유지 |
| ProcessPayment | ✅ 분산락 + 비관락 | 100 | 유지 |

---

## 🚀 구현 로드맵

### Phase 1: 인프라 확인 ✅ (완료)
- [x] docker-compose.yml Redis 설정 확인
- [x] @DistributedLock 어노테이션 확인
- [x] DistributedLockAspect AOP 확인
- [x] 기존 분산락 적용 현황 파악

### Phase 2: 주문 생성 분산락 적용 🔴 (우선순위 1)
- [ ] CreateOrderUseCase에 @DistributedLock 추가
- [ ] Pessimistic Lock 추가 (ProductRepository.findByIdWithLockOrThrow)
- [ ] 동시성 테스트 작성 (100명 동시 주문)
- [ ] TOCTOU 갭 해소 검증

### Phase 3: 인기 상품 조회 캐싱 적용 🟡 (우선순위 2)
- [ ] GetTopProductsUseCase에 Cache-Aside 패턴 구현
- [ ] 분산락으로 Cache Stampede 방지
- [ ] TTL 랜덤화 적용 (5분 ± 10%)
- [ ] 캐시 테스트 작성 (Hit/Miss, Stampede)
- [ ] 성능 측정 (Before/After)

### Phase 4: 통합 테스트 및 검증
- [ ] TestContainers 설정 (MySQL + Redis)
- [ ] 전체 동시성 테스트
- [ ] 성능 보고서 작성

---

## 📝 구현 체크리스트

### CreateOrderUseCase 분산락 적용
```java
@DistributedLock(
    key = "'order:create:user:' + #request.userId()",
    waitTime = 10,
    leaseTime = 30
)
@Transactional
public CreateOrderResponse execute(CreateOrderRequest request) {
    // 기존 로직 유지
    // + Pessimistic Lock 추가
    for (OrderItemRequest itemReq : request.items()) {
        // ✅ 변경: findByIdOrThrow → findByIdWithLockOrThrow
        Product product = productRepository.findByIdWithLockOrThrow(itemReq.productId());

        // 재고 확인
        if (product.getStock() < itemReq.quantity()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }

        // 주문 아이템 생성
        OrderItem orderItem = OrderItem.create(...);
        orderItemRepository.save(orderItem);
    }

    // 주문 생성
    Order order = Order.create(...);
    orderRepository.save(order);

    return CreateOrderResponse.of(order, itemResponses);
}
```

### GetTopProductsUseCase 캐싱 적용
```java
private final RedissonClient redissonClient;

@Transactional(readOnly = true)
public List<TopProductResponse> execute() {
    String cacheKey = "popular:products:top5";

    // 1. 캐시 조회
    RBucket<List<TopProductResponse>> bucket = redissonClient.getBucket(cacheKey);
    List<TopProductResponse> cached = bucket.get();

    if (cached != null) {
        return cached;  // Cache Hit
    }

    // 2. Cache Miss - 분산락
    return getTopProductsWithLock(cacheKey);
}

@DistributedLock(key = "'lock:popular:products'", waitTime = 5, leaseTime = 10)
private List<TopProductResponse> getTopProductsWithLock(String cacheKey) {
    // Double-Check
    RBucket<List<TopProductResponse>> bucket = redissonClient.getBucket(cacheKey);
    List<TopProductResponse> cached = bucket.get();
    if (cached != null) return cached;

    // DB 조회
    List<TopProduct> topProducts = productSalesAggregateRepository.findTop5();
    List<TopProductResponse> response = topProducts.stream()
        .map(TopProductResponse::from)
        .toList();

    // TTL 랜덤화
    Duration randomizedTTL = Duration.ofMinutes(5).plus(
        Duration.ofSeconds(ThreadLocalRandom.current().nextInt(60))
    );

    bucket.set(response, randomizedTTL);
    return response;
}
```

---

## 🎯 핵심 요약

### 즉시 적용 필요 🔴
1. **CreateOrderUseCase**: 분산락 + Pessimistic Lock (TOCTOU 갭 해소)

### 성능 개선 필요 🟡
2. **GetTopProductsUseCase**: Cache-Aside 패턴 + 분산락 (95% 성능 향상)

### 현상 유지 ✅
3. **IssueCouponUseCase**: 이미 적절히 적용됨
4. **ChargeBalanceUseCase**: 이미 적절히 적용됨
5. **PaymentTransactionService**: 이미 적절히 적용됨
6. **Cart 관련**: 분산락 불필요 (사용자별 독립적)

---

## 📚 참고 자료

### 멘토링 내용
- `docs/week6/MENTOR_QNA.md` - 김종협/제이 코치님 QnA
- `docs/week6/LEARNING_SUMMARY.md` - 학습 정리

### 구현 가이드
- `STEP11-12_QUICK_START.md` - 3시간 압축 학습
- `STEP11-12_LEARNING_GUIDE.md` - Day 1~4 상세 가이드
- `STEP11-12_CODE_EXAMPLES.md` - 바로 사용 가능한 코드

---

**작성자**: 항해플러스 백엔드 6기
**최종 수정일**: 2025-11-26
