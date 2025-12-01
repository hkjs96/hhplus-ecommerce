## :pushpin: PR 제목 규칙
[STEP11-12] 정승배 - e-commerce

---
### **핵심 체크리스트** :white_check_mark:

#### :one: 분산락 적용 (3개)
- [x] 적절한 곳에 분산락이 사용되었는가?
  - ✅ 잔액 충전: `ChargeBalanceUseCase` (사용자별 직렬화)
  - ✅ 쿠폰 발급: `IssueCouponUseCase` (쿠폰별 직렬화)
  - ✅ 주문 생성: `CreateOrderUseCase` (멱등성 키별 직렬화)
  - ✅ 결제 처리: `ProcessPaymentUseCase` (주문별 직렬화)
- [x] 트랜젝션 순서와 락순서가 보장되었는가?
  - ✅ 분산락 → 트랜잭션 시작 → 비즈니스 로직 → 트랜잭션 커밋 → 분산락 해제 순서 보장
  - ✅ 여러 상품 주문 시 Product ID 오름차순 정렬로 데드락 방지

#### :two: 통합 테스트 (2개)
- [x] infrastructure 레이어를 포함하는 통합 테스트가 작성되었는가?
  - ✅ `CreateOrderConcurrencyWithDistributedLockTest` - Redis + MySQL + JPA 통합
  - ✅ `PaymentConcurrencyWithDistributedLockTest` - 분산락 + DB 통합
  - ✅ `OrderIdempotencyIntegrationTest` - 멱등성 키 + DB 통합
- [x] 핵심 기능에 대한 흐름이 테스트에서 검증되었는가?
  - ✅ 잔액 충전 동시성 (99.94% 성공률)
  - ✅ 쿠폰 선착순 발급 (정확히 50개 발급, 재고 초과 방지)
  - ✅ 주문 생성 및 재고 차감
- [x] 동시성을 검증할 수 있는 테스트코드로 작성 되었는가?
  - ✅ `CountDownLatch` + `ExecutorService`로 동시성 테스트
  - ✅ K6 부하 테스트로 실제 환경 검증 (100 VUs)
- [x] Test Container 가 적용 되었는가?
  - ✅ `TestContainersConfig` - MySQL, Redis 컨테이너 자동 실행

#### :three: Cache 적용 (3개)
- [x] 적절하게 Key 적용이 되었는가?
  - ✅ 장바구니 캐시: `cart:user:{userId}` (사용자별 분리)
  - ✅ 상품 캐시: `product:{productId}` (상품별 캐싱)
  - ✅ 인기 상품 캐시: `top-products:{period}` (기간별 캐싱)

---
#### STEP11
- [x] Redis 분산락 적용
  - ✅ Redisson 기반 분산락 AOP 구현 (`@DistributedLock`)
  - ✅ 4개 핵심 기능에 적용 (잔액 충전, 쿠폰 발급, 주문 생성, 결제 처리)
- [x] Test Container 구성
  - ✅ MySQL 8.0 + Redis 7.0 컨테이너 자동 구성
  - ✅ `@Import(TestContainersConfig.class)`로 테스트 격리
- [x] 기능별 통합 테스트
  - ✅ Balance Charge: 99.94% 성공률 (18,388 요청)
  - ✅ Coupon Issuance: 100% 정확성 (정확히 50개 발급)
  - ✅ Cart Cache: 88.04% 성공률, 평균 31ms 응답

#### STEP12
- [x] 캐시 필요한 부분 분석
  - ✅ 장바구니 조회 (읽기 빈번, 쓰기 상대적 적음)
  - ✅ 상품 상세 조회 (변경 적음, 조회 많음)
  - ✅ 인기 상품 목록 (배치 집계, 주기적 갱신)
- [x] redis 기반의 캐시 적용
  - ✅ Spring Cache + Redis 연동
  - ✅ Cache-Aside 패턴 적용
  - ✅ TTL 설정 (장바구니: 1일, 상품: 1시간, 인기 상품: 5분)
- [x] 성능 개선 등을 포함한 보고서 제출
  - ✅ `docs/week6/LOAD_TEST_FINAL_SUMMARY.md` - 전체 테스트 결과 요약
  - ✅ `docs/week6/ORDER_IDEMPOTENCY_ISSUE_ANALYSIS.md` - 멱등성 문제 상세 분석
  - ✅ Cart Cache 성능: 평균 31ms (목표 <300ms 달성)

### **간단 회고**
- **잘한 점**: 분산락과 캐시를 실제 K6 부하 테스트로 검증하여 Balance Charge(99.94%), Coupon Issuance(100% 정확성), Cart Cache(평균 31ms)의 우수한 성능을 확인했습니다. 문제 발견 시 명확한 분석과 문서화를 통해 학습 성과를 남겼습니다.

- **어려운 점**: Order Idempotency에서 `REQUIRES_NEW` 트랜잭션과 분산락의 타이밍 갭으로 7.34% 실패율 발생. 멱등성 키가 별도 트랜잭션으로 빠르게 커밋되면서 분산락 해제 전에 다른 요청이 동일 레코드에 접근해 `Lock wait timeout` 발생. 현재 구조는 **분산락(애플리케이션 레벨) + DB Unique Constraint(DB 레벨)의 이중 락**으로 인해 오히려 경합 증가. Pessimistic Lock 추가, Lease Time 증가 등 시도했으나 개선 미미하여 **DB 중심 멱등성 보장 또는 이벤트 기반 비동기 처리**로의 아키텍처 전환이 필요함을 깨달았습니다.

- **다음 시도**: (1) Database Unique Constraint만 활용하는 방식으로 전환 - 분산락 제거하고 `INSERT ... ON DUPLICATE KEY UPDATE`로 DB 레벨에서 멱등성 보장. (2) 또는 Saga 패턴 적용 - 멱등성 키 저장을 이벤트로 발행하고 비동기 핸들러에서 처리하여 트랜잭션 분리 문제 해결. (3) Outbox 패턴으로 실패 요청 별도 관리하여 최종 일관성 보장하는 방향을 시도하겠습니다.