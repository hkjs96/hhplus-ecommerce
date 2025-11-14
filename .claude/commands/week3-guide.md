---
description: Week 3 (Step 5-6) Layered Architecture 전체 가이드
---

# Week 3: Layered Architecture Implementation

> 이 가이드는 Week 3 (Step 5-6) 과제의 전체 내용을 포함합니다.
> 현재 Week 4를 진행 중이라면, 참고용으로만 사용하세요.

## 📋 Week 3 Assignment: Layered Architecture Implementation

### Assignment Objectives
1. **Domain Layer**: ERD 기반 도메인 모델 구현 (Entity, Value Object)
2. **Application Layer**: API 명세를 유스케이스로 구현
3. **Infrastructure Layer**: In-Memory Repository 구현
4. **Concurrency Control**: 선착순 쿠폰 Race Condition 방지
5. **Unit Testing**: 테스트 커버리지 70% 이상

---

## 🚩 STEP 5: Layered Architecture 기본 구현

### 과제 요구사항

#### 1. 도메인 모델 구현
- Week 2의 ERD를 기반으로 Entity 클래스 작성
- Value Object 구현 (Money, Quantity, CouponDiscount 등)
- 비즈니스 규칙을 도메인 모델에 캡슐화

#### 2. 레이어드 아키텍처 구조
```
src/main/java/io/hhplus/ecommerce/
├── domain/              # 핵심 비즈니스 로직 (Entity, Repository Interface, Domain Service)
├── application/         # 유스케이스 (UseCase, DTO)
├── infrastructure/      # 외부 세계와의 통합 (In-Memory Repository 구현체)
└── presentation/        # API 엔드포인트 (Controller)
```

#### 3. 핵심 비즈니스 로직 구현
- **재고 관리**: 재고 조회, 차감, 복구
- **주문/결제**: 주문 생성, 상태 관리, 결제 처리
- **선착순 쿠폰**: 쿠폰 발급, 사용, 만료 처리

#### 4. 단위 테스트
- 각 계층별 단위 테스트 작성
- 테스트 커버리지 70% 이상 달성
- Mock/Stub을 활용한 격리된 테스트

### Pass 조건 (모두 충족 필요)

#### 1. 아키텍처 분리 ✅
- [ ] **4계층 분리**: Presentation, Application, Domain, Infrastructure가 명확히 분리
- [ ] **의존성 방향**: Domain이 Infrastructure를 의존하지 않음
- [ ] **패키지 구조**: 각 계층이 별도 패키지로 구성됨

#### 2. 도메인 모델 설계 ✅
- [ ] **Entity 구현**: Product, Order, Coupon, User 등 ERD 기반 Entity 작성
- [ ] **비즈니스 로직**: Entity 내부에 비즈니스 규칙 메서드 존재
  - 예: `Product.decreaseStock()`, `User.charge()`, `Coupon.isAvailable()`
- [ ] **Value Object**: Money, Quantity 등 값 객체 활용 (선택)

#### 3. Repository 패턴 ✅
- [ ] **인터페이스 위치**: Repository 인터페이스가 Domain Layer에 위치
- [ ] **구현체 위치**: 구현체가 Infrastructure Layer에 위치
- [ ] **In-Memory 구현**: ConcurrentHashMap으로 데이터 관리
- [ ] **DB 미사용**: JPA, Hibernate 등 DB 라이브러리 사용하지 않음

#### 4. UseCase 구현 ✅
- [ ] **유스케이스 분리**: API 명세가 UseCase 메서드로 구현됨
- [ ] **단일 책임**: 각 UseCase는 하나의 비즈니스 흐름만 담당
- [ ] **DTO 사용**: Request/Response DTO로 데이터 전달

#### 5. 핵심 비즈니스 로직 ✅
- [ ] **재고 관리**: 재고 조회, 차감, 복구 로직 정상 동작
- [ ] **주문/결제**: 주문 생성 및 결제 프로세스 정상 동작
- [ ] **선착순 쿠폰**: 쿠폰 발급, 사용, 만료 로직 정상 동작

#### 6. 테스트 커버리지 ✅
- [ ] **커버리지 70% 이상**: Jacoco 리포트 기준
- [ ] **단위 테스트**: Domain, Application Layer 테스트 작성
- [ ] **Mock 활용**: Mockito로 의존성 격리

---

### Fail 사유 (하나라도 해당 시 불합격)

#### 아키텍처 Fail ❌
- ❌ **계층 미분리**: 단일 파일에 Controller + Service + Repository 로직이 혼재
- ❌ **의존성 역전**: Domain이 Infrastructure를 직접 의존 (import)
- ❌ **책임 혼재**: Controller에 비즈니스 로직 작성

#### 구현 Fail ❌
- ❌ **비즈니스 로직 위치**: Controller나 Repository에 비즈니스 규칙 작성
- ❌ **DB 사용**: JPA, Hibernate, @Entity 어노테이션 사용
- ❌ **Mock 데이터**: Controller에 하드코딩된 Mock 데이터 (Week 2 방식)

#### 테스트 Fail ❌
- ❌ **테스트 부재**: 테스트 코드가 전혀 없음
- ❌ **낮은 커버리지**: 50% 미만의 테스트 커버리지
- ❌ **통합 테스트만**: 단위 테스트 없이 통합 테스트만 존재

---

### 핵심 역량 및 평가 포인트

#### 1. 레이어드 아키텍처 이해도 🏗️
**평가 기준:**
- 각 계층의 책임을 명확히 이해하고 구현했는가?
- 의존성 방향을 올바르게 유지했는가?

**토론 주제:**
- "왜 Repository 인터페이스를 Domain에 두었나요?"
- "UseCase와 DomainService의 차이는 무엇인가요?"
- "Controller에서 직접 Repository를 호출하면 안 되는 이유는?"

#### 2. 비즈니스 로직 배치 📍
**평가 기준:**
- 비즈니스 규칙이 Entity 내부에 캡슐화되었는가?
- Anemic Domain Model을 피했는가?

**토론 주제:**
- "재고 차감 로직을 어디에 구현했나요? 그 이유는?"
- "할인 계산 로직은 어느 계층에 있나요?"

#### 3. Repository 패턴 이해 🗄️
**평가 기준:**
- 인터페이스와 구현체가 분리되었는가?
- In-Memory 구현이 올바르게 작동하는가?

**토론 주제:**
- "Repository와 DAO의 차이는 무엇인가요?"
- "ConcurrentHashMap을 선택한 이유는?"

#### 4. 테스트 가능한 설계 🧪
**평가 기준:**
- Mock을 활용한 격리된 테스트가 가능한가?
- 각 계층별로 테스트가 분리되어 있는가?

**토론 주제:**
- "Domain Layer 테스트에서 Mock이 필요한가요?"
- "통합 테스트와 단위 테스트의 비율은 어떻게 가져갔나요?"

---

## 🚩 STEP 6: 동시성 제어 및 고급 기능

### 과제 요구사항

#### 1. 동시성 제어 구현
- 선착순 쿠폰 발급 시 Race Condition 방지
- 선택 가능한 방식:
  - Mutex/Lock (synchronized, ReentrantLock)
  - Semaphore
  - Atomic Operations (AtomicInteger, AtomicReference)
  - Queue 기반 (BlockingQueue)

#### 2. 통합 테스트 작성
- 동시 요청 시나리오 검증
- 멀티 스레드 환경 테스트 (ExecutorService)
- Race Condition 방지 검증

#### 3. 인기 상품 집계 로직
- 조회수/판매량 기반 순위 계산
- 최근 3일 데이터 집계
- Top 5 상품 반환

#### 4. 동시성 제어 분석 문서 작성
- README.md에 동시성 제어 방식 설명
- 선택한 방식의 장단점 분석
- 대안 방식 비교

### Pass 조건 (모두 충족 필요)

#### 1. 동시성 제어 구현 ✅
- [ ] **Race Condition 방지**: 200명이 동시 요청해도 정확히 100개만 발급
- [ ] **동시성 제어 방식 선택**: synchronized, ReentrantLock, Atomic, Queue 중 택1
- [ ] **일관성 보장**: 쿠폰 발급 수량이 정확히 일치

#### 2. 통합 테스트 작성 ✅
- [ ] **동시성 테스트**: ExecutorService + CountDownLatch 활용
- [ ] **시나리오 검증**: 200명 요청 → 100명 성공, 100명 실패
- [ ] **테스트 통과**: 100% 성공률로 동시성 테스트 통과

#### 3. 인기 상품 집계 ✅
- [ ] **집계 로직**: 최근 3일 판매량 기준 Top 5 계산
- [ ] **효율성**: O(N log N) 이하의 시간 복잡도
- [ ] **API 응답**: period, rank, salesCount, revenue 포함

#### 4. 동시성 제어 문서화 ✅
- [ ] **README.md**: 동시성 제어 방식 설명 포함
- [ ] **선택 이유**: 해당 방식을 선택한 근거 작성
- [ ] **대안 비교**: 최소 2가지 다른 방식과 비교 분석
- [ ] **코드 예시**: 핵심 동시성 제어 코드 포함

---

### Fail 사유 (하나라도 해당 시 불합격)

#### 동시성 제어 Fail ❌
- ❌ **Race Condition 발생**: 200명 요청 시 100개를 초과하여 발급
- ❌ **동시성 제어 부재**: synchronized, Lock, Atomic 등 어떠한 제어도 없음
- ❌ **불안정한 결과**: 테스트 실행마다 발급 수량이 달라짐

#### 테스트 Fail ❌
- ❌ **테스트 부재**: 동시성 검증 테스트가 없음
- ❌ **단순 테스트**: 단일 스레드 테스트만 존재
- ❌ **테스트 실패**: 동시성 테스트가 통과하지 못함

#### 문서화 Fail ❌
- ❌ **문서 없음**: README.md에 동시성 제어 분석이 없음
- ❌ **설명 부족**: 어떤 방식을 사용했는지만 언급 (이유 없음)
- ❌ **대안 비교 없음**: 다른 방식과의 비교 분석 누락

---

### 핵심 역량 및 평가 포인트

#### 1. 동시성 제어 이해도 🔒
**평가 기준:**
- Race Condition이 무엇인지 이해하는가?
- 선택한 동시성 제어 방식을 정확히 설명할 수 있는가?

**토론 주제:**
- "synchronized와 ReentrantLock의 차이는 무엇인가요?"
- "AtomicInteger가 ConcurrentHashMap보다 빠른 이유는?"
- "BlockingQueue 방식의 장단점은 무엇인가요?"

#### 2. 통합 테스트 설계 🧪
**평가 기준:**
- ExecutorService를 올바르게 활용했는가?
- CountDownLatch의 역할을 이해하는가?

**토론 주제:**
- "200명의 동시 요청을 어떻게 시뮬레이션했나요?"
- "테스트 실패 시 어떻게 디버깅했나요?"

#### 3. 인기 상품 집계 효율성 📊
**평가 기준:**
- 최근 3일 데이터만 필터링하는가?
- 정렬 알고리즘의 시간 복잡도를 이해하는가?

**토론 주제:**
- "매번 정렬하는 것이 효율적인가요? 대안은?"
- "실시간 집계와 배치 집계 중 어떤 방식을 선택했나요?"

#### 4. 기술 문서 작성 능력 📝
**평가 기준:**
- 기술적 선택의 근거를 명확히 제시하는가?
- 트레이드오프를 이해하고 설명하는가?

**토론 주제:**
- "README.md에 어떤 내용을 포함했나요?"
- "다른 개발자가 읽고 이해하기 쉽게 작성했나요?"

---

## ✅ Step 5 Implementation Checklist

### Domain Layer
- [ ] Product Entity (재고 차감/복구 메서드)
- [ ] User Entity (잔액 충전/차감 메서드)
- [ ] Coupon Entity (발급 수량 검증)
- [ ] UserCoupon Entity
- [ ] Cart & CartItem Entity
- [ ] Order & OrderItem Entity
- [ ] Repository Interfaces (domain 패키지에 위치)

### Application Layer
- [ ] ProductUseCase (목록/상세 조회)
- [ ] CartUseCase (추가/조회/수정/삭제)
- [ ] OrderUseCase (주문 생성/조회)
- [ ] PaymentUseCase (결제 처리)
- [ ] CouponUseCase (발급/조회)
- [ ] UserUseCase (잔액 조회/충전)
- [ ] DTO 클래스 (Request, Response)

### Infrastructure Layer
- [ ] InMemoryProductRepository
- [ ] InMemoryUserRepository
- [ ] InMemoryCouponRepository
- [ ] InMemoryUserCouponRepository
- [ ] InMemoryCartRepository
- [ ] InMemoryOrderRepository
- [ ] DataInitializer (초기 데이터 로딩)

### Presentation Layer
- [ ] Controller 리팩토링 (ConcurrentHashMap 제거)
- [ ] UseCase 의존성 주입
- [ ] Mock 데이터 제거

### Testing
- [ ] Domain Layer 단위 테스트
- [ ] Application Layer 단위 테스트 (Mock 사용)
- [ ] Repository 단위 테스트
- [ ] 테스트 커버리지 70% 이상 달성

---

## ✅ Step 6 Implementation Checklist

### Concurrency Control
- [ ] 동시성 제어 방식 선택 (synchronized, ReentrantLock, Atomic, Queue 중 택1)
- [ ] 선착순 쿠폰 발급 Race Condition 방지 구현
- [ ] 재고 차감 동시성 제어 (optional)

### Popular Products Aggregation
- [ ] 인기 상품 집계 로직 구현 (최근 3일, Top 5)
- [ ] 판매량 기반 순위 계산
- [ ] PopularProductUseCase 구현

### Integration Testing
- [ ] 동시성 테스트 (ExecutorService, CountDownLatch)
- [ ] 200명 동시 요청 시나리오 테스트
- [ ] 정확히 100개만 발급 검증

### Documentation
- [ ] README.md에 동시성 제어 방식 설명
- [ ] 선택한 방식의 장단점 분석
- [ ] 대안 방식 비교 (최소 2가지)
- [ ] 코드 예시 포함

---

## 🔍 Common Pitfalls to Avoid

### Architecture
- ❌ Controller에 비즈니스 로직 작성
- ❌ Repository 구현체를 Domain에 위치
- ❌ UseCase에서 다른 UseCase 직접 호출 (DomainService 사용)
- ✅ 의존성 방향 준수 (Presentation → Application → Domain ← Infrastructure)

### Concurrency
- ❌ 동시성 제어 없이 쿠폰 발급
- ❌ Thread-unsafe 컬렉션 사용 (HashMap, ArrayList)
- ✅ ConcurrentHashMap, AtomicInteger 사용
- ✅ synchronized 또는 Lock 적용

### Testing
- ❌ 테스트 없이 구현
- ❌ 통합 테스트만 작성 (단위 테스트 누락)
- ✅ 각 계층별 단위 테스트 작성
- ✅ Mock을 활용한 격리된 테스트

### Data Management
- ❌ DB 라이브러리 사용 (JPA, Hibernate)
- ❌ 영속성 어노테이션 사용 (@Entity, @Table)
- ✅ 순수 Java 클래스로 Entity 구현
- ✅ In-Memory 컬렉션으로 저장

---

## 📚 더 자세한 내용은

- `/architecture` - 레이어드 아키텍처 상세 설명
- `/concurrency` - 동시성 제어 패턴 4가지
- `/testing` - 테스트 전략 및 코드 예시
- `/week3-faq` - Week 3 FAQ 17개
- `/implementation` - 구현 가이드 및 코드 예시
