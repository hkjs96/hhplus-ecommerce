## :pushpin: PR 제목
[STEP05-06] 장승범

---
## ⚠️ **중요: 이번 과제는 DB를 사용하지 않습니다**
> 모든 데이터는 **인메모리(Map, Array, Set 등)**로 관리해야 합니다.
> 실제 DB 연동은 다음 챕터(데이터베이스 설계)에서 진행합니다.

✅ **확인**: 모든 데이터를 ConcurrentHashMap 기반 인메모리 Repository로 구현했습니다.

---
## 📋 **과제 체크리스트**

### ✅ **STEP 5: 레이어드 아키텍처 기본 구현** (필수)
- [x] **도메인 모델 구현**: Entity, Value Object가 정의되었는가?
  - Product, Order, Cart, Coupon, User 등 모든 Entity 구현
  - OrderStatus, CouponStatus Enum 정의
- [x] **유스케이스 구현**: API 명세가 유스케이스로 구현되었는가?
  - ProductService, OrderService, CartService, CouponService, UserService 구현
  - 16개 User Story 모두 구현 완료 (US-011 Order History 추가)
- [x] **레이어드 아키텍처**: 4계층(Presentation, Application, Domain, Infrastructure)으로 분리되었는가?
  - Presentation: Controllers (5개)
  - Application: Services + DTOs (5개 Service)
  - Domain: Entities + Repository Interfaces
  - Infrastructure: InMemory Repository Implementations (8개)
- [x] **재고 관리**: 재고 조회/차감/복구 로직이 구현되었는가?
  - Product.decreaseStock(), restoreStock() 메서드
  - 동시성 제어: AtomicInteger 사용
- [x] **주문/결제**: 주문 생성 및 결제 프로세스가 구현되었는가?
  - OrderService.createOrder() - 장바구니 기반 주문 생성
  - OrderService.processPayment() - 포인트 결제 처리
- [x] **선착순 쿠폰**: 쿠폰 발급/사용/만료 로직이 구현되었는가?
  - CouponService.issueCoupon() - 선착순 발급 (동시성 제어)
  - Coupon.isAvailable() - 만료/사용 검증
- [x] **단위 테스트**: 테스트 커버리지 70% 이상 달성했는가?
  - ✅ **94% instruction coverage, 89% branch coverage**

### 🔥 **STEP 6: 동시성 제어 및 고급 기능** (심화)
- [x] **동시성 제어**: 선착순 쿠폰 발급의 Race Condition이 방지되었는가?
  - AtomicInteger + CAS (Compare-And-Swap) 방식 사용
  - 200명 동시 요청 시 정확히 100개만 발급 검증
- [x] **통합 테스트**: 동시성 시나리오를 검증하는 테스트가 작성되었는가?
  - CouponServiceConcurrencyTest (ExecutorService, CountDownLatch 활용)
  - 모든 Controller Integration Tests (5개)
- [x] **인기 상품 집계**: 조회수/판매량 기반 순위 계산이 구현되었는가?
  - 최근 3일 판매량 기준 Top 5 상품
  - 실시간 집계 방식 구현
- [x] **문서화**: README.md에 동시성 제어 분석이 작성되었는가?
  - Optimistic Lock vs Pessimistic Lock 설명
  - AtomicInteger 선택 이유 및 트레이드오프

### 🏗️ **아키텍처 설계**
- [x] **의존성 방향**: Domain ← Application ← Infrastructure 방향이 지켜졌는가?
  - Repository Interface는 Domain Layer에 위치
  - Implementation은 Infrastructure Layer에 위치
- [x] **책임 분리**: 각 계층의 책임이 명확히 분리되었는가?
  - Presentation: API 요청/응답 처리
  - Application: 비즈니스 플로우 조정
  - Domain: 핵심 비즈니스 로직 캡슐화
  - Infrastructure: 데이터 저장소 구현
- [x] **테스트 가능성**: Mock/Stub을 활용한 테스트가 가능한 구조인가?
  - 모든 Service는 Repository Interface에 의존
  - 단위 테스트에서 InMemoryRepository 직접 사용
- [x] **인메모리 저장소**: DB 없이 모든 데이터가 인메모리로 관리되는가?
  - ConcurrentHashMap 기반 8개 Repository 구현
  - Thread-safe 보장
- [x] **Repository 패턴**: 인터페이스와 인메모리 구현체가 분리되었는가?
  - 8개 Repository Interface (Domain)
  - 8개 InMemory Implementation (Infrastructure)

---
## 🔗 **주요 구현 커밋**

- 도메인 모델 구현: [6265ed3](https://github.com/hkjs96/hhplus-ecommerce/commit/6265ed3)
- 재고 관리 로직 구현: [fdad6b0](https://github.com/hkjs96/hhplus-ecommerce/commit/fdad6b0)
- 주문/결제 프로세스 구현: [7fb5090](https://github.com/hkjs96/hhplus-ecommerce/commit/7fb5090)
- 선착순 쿠폰 로직 구현: [45df911](https://github.com/hkjs96/hhplus-ecommerce/commit/45df911)
- 동시성 제어 구현 (STEP 6): [11d05c3](https://github.com/hkjs96/hhplus-ecommerce/commit/11d05c3)
- 테스트 코드 작성: [c5c681c](https://github.com/hkjs96/hhplus-ecommerce/commit/c5c681c)
- Order History API 추가 (US-011): [a7db667](https://github.com/hkjs96/hhplus-ecommerce/commit/a7db667)

---
## 💬 **리뷰 요청 사항**

### 질문/고민 포인트
1. **동시성 제어 방식 선택**
   - 쿠폰 발급: AtomicInteger + CAS 방식 선택
   - 재고 차감: AtomicInteger 사용
   - 포인트 충전/차감: AtomicLong 사용
   - Q: 이 선택이 적절한가요? ReentrantLock이 더 나은 경우가 있을까요?

2. **테스트 격리 전략**
   - InMemoryRepository에 clear() 메서드 추가
   - @BeforeEach에서 Repository 초기화
   - Q: 더 나은 테스트 격리 방법이 있을까요?

### 특별히 리뷰받고 싶은 부분
- **동시성 제어 로직** (Coupon.tryIssue() 메서드)
  - CAS 방식의 올바른 구현 여부
  - 무한 루프 가능성에 대한 대응 (현재 최대 시도 횟수 없음)
- **Repository 패턴 구현**
  - Interface와 Implementation 분리가 적절한지
  - ConcurrentHashMap 사용이 적절한지

---
## 📊 **테스트 및 품질**

| 항목 | 결과 |
|------|------|
| 테스트 커버리지 | 94% (instruction), 89% (branch) |
| 단위 테스트 | 13개 파일 (Domain, Application Layer) |
| 통합 테스트 | 5개 파일 (모든 Controller) |
| 총 테스트 수 | **230개** (100% 통과) |
| 동시성 테스트 | **통과** (200명 동시 요청 → 100개만 발급) |

---
## 🔒 **동시성 제어 방식** (STEP 6 필수)

**선택한 방식:**
- [x] Atomic Operations (AtomicInteger, AtomicLong)
- [ ] Mutex/Lock
- [ ] Semaphore
- [ ] Queue 기반

**구현 이유:**
1. **Lock-free 방식의 성능 우위**
   - synchronized 블록이 없어 대기 시간 최소화
   - CAS (Compare-And-Swap) 연산은 CPU 레벨에서 원자적으로 처리
   - 멀티 스레드 환경에서 높은 동시성 처리 가능

2. **단순한 수량 관리에 최적화**
   - 쿠폰 발급, 재고 차감, 포인트 증감은 단순 증감 연산
   - AtomicInteger의 compareAndSet()이 정확히 이런 용도로 설계됨

3. **데드락 위험 없음**
   - Lock을 사용하지 않으므로 데드락 발생 불가능
   - 스핀락(Spin-lock) 방식으로 재시도 로직 단순

**구현 코드 예시:**
```java
// Coupon.java
public class Coupon {
    private AtomicInteger issuedQuantity = new AtomicInteger(0);

    public boolean tryIssue() {
        while (true) {
            int current = issuedQuantity.get();
            if (current >= totalQuantity) {
                return false; // 수량 초과
            }
            if (issuedQuantity.compareAndSet(current, current + 1)) {
                return true; // 발급 성공
            }
            // CAS 실패 시 재시도
        }
    }
}
```

**트레이드오프:**
- ✅ 장점: 빠른 성능, Lock-free, 데드락 없음
- ⚠️ 단점: 복잡한 비즈니스 로직에는 부적합, 무한 루프 가능성 (매우 낮음)

**대안 비교:**

| 방식 | 성능 | 구현 난이도 | 적합한 경우 |
|------|------|------------|----------|
| **AtomicInteger** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | 단순 증감 연산 |
| synchronized | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 간단한 동기화 |
| ReentrantLock | ⭐⭐⭐⭐ | ⭐⭐⭐ | timeout, 공정성 필요 시 |
| Queue 기반 | ⭐⭐ | ⭐⭐ | 순차 처리 필요 시 |

**참고 문서:**
- README.md의 "동시성 제어" 섹션 참조
- Coupon Entity의 tryIssue() 메서드 구현
- CouponServiceConcurrencyTest 참조

---
## 🎯 **아키텍처 설계**

### 디렉토리 구조
```
src/main/java/io/hhplus/ecommerce/
├── presentation/              # Presentation Layer
│   ├── api/
│   │   ├── product/ProductController.java
│   │   ├── cart/CartController.java
│   │   ├── order/OrderController.java
│   │   ├── coupon/CouponController.java
│   │   └── user/UserController.java
│   └── common/
│       ├── ApiResponse.java
│       └── GlobalExceptionHandler.java
│
├── application/               # Application Layer
│   ├── product/ProductService.java
│   ├── cart/CartService.java
│   ├── order/OrderService.java
│   ├── coupon/CouponService.java
│   └── user/UserService.java
│
├── domain/                    # Domain Layer
│   ├── product/Product.java
│   ├── cart/Cart.java, CartItem.java
│   ├── order/Order.java, OrderItem.java
│   ├── coupon/Coupon.java, UserCoupon.java
│   └── user/User.java
│
└── infrastructure/            # Infrastructure Layer
    └── persistence/
        ├── product/InMemoryProductRepository.java
        ├── cart/InMemoryCart(Item)Repository.java
        ├── order/InMemoryOrder(Item)Repository.java
        ├── coupon/InMemoryCoupon(UserCoupon)Repository.java
        └── user/InMemoryUserRepository.java
```

### 주요 설계 결정
- **선택한 아키텍처**: 레이어드 아키텍처 (4-Layer)
- **데이터 저장 방식**: 인메모리 (ConcurrentHashMap)
- **선택 이유**:
  1. **명확한 책임 분리**: 각 계층이 단일 책임을 가짐
  2. **테스트 용이성**: 계층별 독립 테스트 가능
  3. **의존성 역전**: Domain이 Infrastructure를 모름
  4. **확장 가능성**: DB 도입 시 Infrastructure만 변경

- **트레이드오프**:
  - ✅ 장점: 명확한 구조, 유지보수 용이, 팀 협업에 유리
  - ⚠️ 단점: 초기 구조 설계 비용, 작은 변경에도 여러 계층 수정 필요

---
## 📝 **회고**

### ✨ 잘한 점
- **체계적인 구현 순서**: Domain → Infrastructure → Application → Presentation 순서로 Bottom-Up 구현
- **높은 테스트 커버리지**: 94% 달성, 모든 핵심 비즈니스 로직 검증
- **동시성 제어 성공**: 200명 동시 요청 시나리오에서 100% 정확도 달성
- **User Story 완벽 구현**: 16개 User Story 모두 구현 완료 (US-011 추가)

### 😓 어려웠던 점
- **동시성 테스트 작성**: ExecutorService, CountDownLatch 사용법 학습 필요
- **테스트 격리**: InMemoryRepository의 상태 관리 (clear() 메서드 추가로 해결)
- **CAS 방식 이해**: AtomicInteger의 compareAndSet() 메커니즘 이해에 시간 소요
- **OrderItem 조인 로직**: Order 조회 시 OrderItem, Product를 함께 반환하는 로직 복잡

### 🚀 다음에 시도할 것
- **성능 측정**: JMH 벤치마크로 동시성 제어 방식별 성능 비교
- **무한 루프 대응**: Coupon.tryIssue()에 최대 재시도 횟수 추가 검토
- **캐시 도입 고려**: 인기 상품 조회 시 캐싱 전략 (Week 5)
- **배치 집계**: 실시간 집계 대신 스케줄러 기반 배치 집계 (Week 5)

---
## 📚 **참고 자료**

### 아키텍처
- [Martin Fowler - Layered Architecture](https://martinfowler.com/bliki/PresentationDomainDataLayering.html)
- [Clean Architecture - Uncle Bob](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

### 동시성 제어
- [Java Concurrency in Practice - Brian Goetz](https://jcip.net/)
- [AtomicInteger API Documentation](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/AtomicInteger.html)
- [Compare-And-Swap 메커니즘](https://en.wikipedia.org/wiki/Compare-and-swap)

### 테스팅
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Jacoco Code Coverage](https://www.jacoco.org/)

---
## ✋ **체크리스트 (제출 전 확인)**

- [x] DB 관련 라이브러리를 사용하지 않았는가? (JPA, Hibernate 등)
- [x] 모든 Repository가 인메모리로 구현되었는가?
- [x] build.gradle에 DB 드라이버가 없는가? (H2, MySQL, PostgreSQL 등)
- [x] 환경변수에 DB 연결 정보가 없는가?
- [x] 테스트 커버리지 70% 이상 달성했는가? ✅ **94%**
- [x] 동시성 테스트가 통과하는가? ✅ **통과**
- [x] README.md에 동시성 제어 분석이 작성되었는가? ✅ **작성 완료**
- [x] 모든 User Story가 구현되었는가? ✅ **16개 모두 구현**
