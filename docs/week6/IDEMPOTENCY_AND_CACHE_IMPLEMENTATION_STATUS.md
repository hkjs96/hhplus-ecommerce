# 멱등성 및 캐시 구현 상태 보고서

**작성일**: 2025-11-26
**대상**: Week 6 - 분산락, 멱등성, 캐시 구현

---

## 📋 구현 완료 항목

### 1. ✅ OrderIdempotency Entity 및 Repository 구현 (100%)

#### 생성된 파일
1. **`OrderIdempotency.java`** (domain/order)
   - 주문 생성 멱등성 보장을 위한 Entity
   - 상태: PROCESSING, COMPLETED, FAILED
   - 24시간 TTL (expiresAt 필드)
   - 응답 캐싱 (responsePayload 필드, JSON 직렬화)
   - 유니크 제약조건: `uk_order_idempotency_key` on `idempotency_key`

2. **`OrderIdempotencyRepository.java`** (domain/order)
   - 인터페이스: `findByIdempotencyKey()`, `save()`, `deleteExpired()`

3. **`JpaOrderIdempotencyRepository.java`** (infrastructure/persistence/order)
   - JpaRepository 상속
   - `@Query` 기반 만료 데이터 삭제

4. **`OrderIdempotencyRepositoryImpl.java`** (infrastructure/persistence/order)
   - Repository 인터페이스 구현체

#### 설계 특징
- **목적**: 중복 주문 방지, 네트워크 타임아웃 재시도 안전성, 재고 이중 차감 방지
- **상태 관리**:
  - PROCESSING: 처리 중 (동시 요청 방지)
  - COMPLETED: 완료 (캐시된 응답 반환)
  - FAILED: 실패 (재시도 가능)

---

### 2. ✅ CreateOrderUseCase 멱등성 로직 추가 (100%)

#### 수정된 파일
1. **`CreateOrderRequest.java`**
   - `idempotencyKey` 필드 추가 (`@NotBlank` 검증)
   - 4개 필드: `userId`, `items`, `couponId`, `idempotencyKey`

2. **`CreateOrderUseCase.java`**
   - **전체 재작성**: 멱등성 로직 추가
   - 주요 변경사항:
     - `OrderIdempotencyRepository` 의존성 추가
     - `execute()`: 멱등성 체크 + 처리
     - `createOrderInternal()`: @Transactional 분리 (실제 주문 생성)
     - JSON 직렬화/역직렬화 메서드 추가

#### 멱등성 처리 흐름
```java
1. 멱등성 키 조회
   - COMPLETED → 캐시된 응답 반환 (중복 요청 방지)
   - PROCESSING → 409 에러 (동시 처리 중)
   - FAILED → 재처리 가능

2. 멱등성 키 생성 (PROCESSING 상태)

3. 주문 생성 (createOrderInternal)
   - 분산락: `order:create:user:{userId}` (동일 사용자 직렬화)
   - Pessimistic Lock: 재고 조회 시 정확성 보장
   - 데드락 방지: 상품 ID 오름차순 정렬

4. 완료 처리 (COMPLETED + 응답 캐싱)

5. 예외 발생 시 FAILED 처리
```

#### 기존 기능 유지
- ✅ 분산락 (`@DistributedLock`)
- ✅ Pessimistic Lock (재고 조회)
- ✅ 데드락 방지 (상품 ID 정렬)
- ✅ 메트릭 수집

3. **`OrderFacade.java`**
   - `createAndPayOrder()` 메서드 수정
   - 주문 생성 시 idempotencyKey 자동 생성 (UUID 기반)

---

### 3. ✅ Spring Cache 설정 및 조회 API 캐시 적용 (100%)

#### 생성된 파일
1. **`CacheConfig.java`** (config)
   - Redis 기반 Spring Cache 설정
   - Jackson ObjectMapper 설정 (JavaTimeModule, ISO-8601)
   - 캐시별 TTL 전략:
     - `products`: 1시간 (상품 목록)
     - `product`: 1시간 (상품 상세)
     - `topProducts`: 5분 (인기 상품, 배치 주기와 동일)
     - `carts`: 1일 (장바구니, 사용자별 격리)
   - `transactionAware=true`: 트랜잭션 커밋 후 캐시 갱신

#### 캐시 적용된 UseCase
1. **`GetProductsUseCase.java`**
   - `@Cacheable(value = "products", key = "category:sort", sync = true)`
   - Thundering Herd 방지 (sync=true)
   - null 값 처리: "all", "default"로 치환

2. **`GetProductUseCase.java`**
   - `@Cacheable(value = "product", key = "#productId", sync = true)`
   - 상품 ID별 개별 캐시

3. **`GetTopProductsUseCase.java`**
   - `@Cacheable(value = "topProducts", key = "'recent3days'", sync = true)`
   - 고정 키 (항상 최근 3일)
   - 5분 TTL (배치 주기와 동일)

4. **`GetCartUseCase.java`**
   - `@Cacheable(value = "carts", key = "#userId", sync = true)`
   - 사용자별 장바구니 개별 캐시
   - 1일 TTL (자주 조회, 드문 변경)

#### 캐시 무효화 (@CacheEvict) 적용
1. **`AddToCartUseCase.java`**
   - `@CacheEvict(value = "carts", key = "#request.userId()")`
   - 장바구니 아이템 추가 시 캐시 삭제

2. **`UpdateCartItemUseCase.java`**
   - `@CacheEvict(value = "carts", key = "#request.userId()")`
   - 아이템 수량 변경 시 캐시 삭제

3. **`RemoveFromCartUseCase.java`**
   - `@CacheEvict(value = "carts", key = "#request.userId()")`
   - 아이템 삭제 시 캐시 삭제

#### 캐시 전략
- **Cache-Aside 패턴**:
  - 조회 시: 캐시 확인 → 없으면 DB 조회 → 캐시 저장
  - 갱신 시: DB 갱신 → 캐시 무효화 (@CacheEvict)
- **Thundering Herd 방지**: `sync=true` (동일 키 동시 요청 시 첫 요청만 DB 조회)
- **트랜잭션 인지**: 커밋 후 캐시 갱신, 롤백 시 무효화 안 함

---

### 4. ✅ 통합 테스트 작성 (90% - 컴파일 에러 수정 중)

#### 생성된 파일
1. **`OrderIdempotencyIntegrationTest.java`**
   - 6개 테스트 케이스 작성:
     1. ✅ 동일 idempotencyKey로 중복 요청 시 캐시된 응답 반환
     2. ✅ 동시 요청 시 첫 요청만 처리, 나머지는 PROCESSING 에러
     3. ✅ 실패 후 재시도 가능 - FAILED 상태에서 재처리
     4. ✅ 서로 다른 idempotencyKey는 독립적으로 처리
     5. ✅ 중복 재고 차감 방지 - 동일 키로 재요청 시 재고 변경 없음
     6. ✅ 주문 1개만 생성 검증

#### 수정 중인 파일 (컴파일 에러 수정)
1. **`CreateOrderConcurrencyWithDistributedLockTest.java`**
   - ✅ UUID import 추가
   - ✅ 3개 테스트 모두 idempotencyKey 추가 완료

2. **`PaymentConcurrencyWithDistributedLockTest.java`** ⚠️
   - ❌ CreateOrderRequest 2곳 수정 필요
   - ❌ ChargeBalanceRequest 1곳 수정 필요

3. **`UserControllerIntegrationTest.java`** ⚠️
   - ❌ ChargeBalanceRequest 5곳 수정 필요

#### 잔여 작업
- [ ] PaymentConcurrencyWithDistributedLockTest 수정 (3곳)
- [ ] UserControllerIntegrationTest 수정 (5곳)
- [ ] 전체 테스트 실행 및 검증

---

### 5. ⏸️ K6 부하 테스트 스크립트 작성 (대기 중)

#### 작성 예정 스크립트
1. **`order-creation-idempotency-test.js`**
   - 동일 idempotencyKey로 중복 요청 테스트
   - 캐시된 응답 반환 검증
   - TPS, 응답 시간 측정

2. **`product-query-cache-test.js`**
   - 캐시 적용 전/후 성능 비교
   - 상품 목록, 상세, 인기 상품 조회
   - 캐시 히트율 측정

3. **`cart-cache-test.js`**
   - 장바구니 조회 캐시 성능
   - 캐시 무효화 검증

#### 예상 성능 향상 (CACHE_STRATEGY_ANALYSIS.md 기반)
- **TPS**: 19 → 1000+ (53배 증가)
- **응답 시간**: 200ms → 10ms (20배 개선)
- **DB 부하**: 95% 감소

---

## 🎯 완료율 요약

| 항목 | 상태 | 완료율 |
|------|------|--------|
| 1. OrderIdempotency Entity/Repository | ✅ | 100% |
| 2. CreateOrderUseCase 멱등성 로직 | ✅ | 100% |
| 3. Spring Cache 설정 및 적용 | ✅ | 100% |
| 4. 통합 테스트 작성 | ⚠️ | 90% |
| 5. K6 부하 테스트 | ⏸️ | 0% |
| **전체** | | **78%** |

---

## 🔧 잔여 작업 (우선순위)

### 1순위: 테스트 컴파일 에러 수정
```bash
# 수정 필요 파일
- PaymentConcurrencyWithDistributedLockTest.java (3곳)
- UserControllerIntegrationTest.java (5곳)
```

**예시 수정**:
```java
// Before
CreateOrderRequest request = new CreateOrderRequest(
    userId, items, couponId
);

// After
String idempotencyKey = "ORDER_" + userId + "_" + UUID.randomUUID().toString();
CreateOrderRequest request = new CreateOrderRequest(
    userId, items, couponId, idempotencyKey
);
```

```java
// Before
ChargeBalanceRequest request = new ChargeBalanceRequest(amount);

// After
String idempotencyKey = "CHARGE_" + userId + "_" + UUID.randomUUID().toString();
ChargeBalanceRequest request = new ChargeBalanceRequest(amount, idempotencyKey);
```

### 2순위: 통합 테스트 실행 및 검증
```bash
./gradlew test --tests OrderIdempotencyIntegrationTest
./gradlew test
```

### 3순위: K6 부하 테스트 스크립트 작성
```bash
# docs/week6/verification/k6/
- order-creation-idempotency-test.js
- product-query-cache-test.js
- cart-cache-test.js
```

---

## 📊 기술적 의사결정 및 근거

### 1. 멱등성 키 관리
- **설계**: OrderIdempotency Entity (독립적인 테이블)
- **근거**:
  - PaymentIdempotency와 별도 관리 (단일 책임 원칙)
  - 주문 생성과 결제는 독립적인 비즈니스 로직
  - 각각 다른 TTL 및 정책 적용 가능

### 2. 트랜잭션 경계 분리
- **설계**: `execute()` (멱등성 체크) + `createOrderInternal()` (@Transactional)
- **근거**:
  - 멱등성 체크는 트랜잭션 밖에서 수행 (락 경합 최소화)
  - 실제 주문 생성만 트랜잭션으로 보호
  - 실패 시 멱등성 키 상태 FAILED로 변경 (재시도 가능)

### 3. 캐시 전략 선택
- **설계**: Redis 분산 캐시 (Spring Cache + RedisCacheManager)
- **근거**:
  - 멀티 인스턴스 환경 대비 (로컬 캐시 불가)
  - 캐시 일관성 보장 (Write-Through는 오버헤드)
  - Cache-Aside 패턴으로 충분 (갱신 빈도 낮음)

### 4. Thundering Herd 방지
- **설계**: `sync=true` (Spring Cache 동기화)
- **근거**:
  - 캐시 만료 시 동시 요청 중 첫 요청만 DB 조회
  - 나머지 요청은 첫 요청 결과 대기
  - DB 부하 최소화 (특히 인기 상품 조회)

### 5. 캐시 무효화 시점
- **설계**: `transactionAware=true` (트랜잭션 커밋 후)
- **근거**:
  - 롤백 시 캐시 무효화 방지 (정합성 보장)
  - 일관성 있는 데이터 제공

---

## 🔍 검증 방법

### 1. 멱등성 검증
```bash
# 동일 키로 중복 요청
curl -X POST /api/orders \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: <same-key>" \
  -d '{"userId": 1, "items": [...]}'

# 예상 결과: 동일한 orderId 반환
```

### 2. 캐시 검증
```bash
# Redis에서 캐시 확인
redis-cli KEYS "products:*"
redis-cli GET "products::all:default"

# 캐시 히트 확인 (로그)
# "Getting products - category: null, sort: null" (캐시 미스)
# (로그 없음) → 캐시 히트
```

### 3. 성능 검증
```bash
# K6 부하 테스트
k6 run order-creation-idempotency-test.js
k6 run product-query-cache-test.js
```

---

## 📚 참고 문서

1. **설계 문서**:
   - `CACHE_STRATEGY_ANALYSIS.md` - 캐시 전략 상세 분석
   - `DISTRIBUTED_LOCK_STATUS.md` - 분산락 및 멱등성 현황

2. **구현 가이드**:
   - `@.claude/commands/concurrency.md` - 동시성 제어 패턴
   - `@.claude/commands/architecture.md` - 레이어드 아키텍처

3. **테스트 가이드**:
   - `@.claude/commands/testing.md` - 테스트 전략

---

## 🚀 다음 단계

1. ✅ **즉시**: 테스트 컴파일 에러 수정 (8곳)
2. ⚠️ **단기**: OrderIdempotencyIntegrationTest 실행 및 검증
3. ⏸️ **중기**: K6 부하 테스트 스크립트 작성 및 실행
4. 📊 **장기**: 프로덕션 환경 배포 및 모니터링

---

**작성자**: Claude Code
**검토**: 필요시 Yulmu 코치 피드백 반영 예정
