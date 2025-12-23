# 프로젝트 구조 분석 및 개선 계획

> **작성일**: 2025-12-16
> **프로젝트**: 항해플러스 이커머스 백엔드
> **현재 단계**: Week 8 (Event Listener & Outbox Pattern)

---

## 📊 현재 상태 분석

### 기본 정보
- **기술 스택**: Java 21, Spring Boot 3.5.7, MySQL, Redis
- **빌드 도구**: Gradle
- **테스트**: JUnit 5, Testcontainers (MySQL, Redis)
- **아키텍처**: Layered Architecture (4계층)
- **테스트 커버리지 목표**: 70% 이상

### 코드 규모
| 항목 | 수량 |
|------|------|
| 메인 소스 파일 | 159개 (~10,843 LoC) |
| 테스트 파일 | 37개 |
| API 컨트롤러 | 7개 |
| UseCase | 18개 |
| Event Listener | 6개 |
| 문서 (.md) | 154개 |

### 아키텍처 구조

```
src/main/java/io/hhplus/ecommerce/
├── presentation/        # 7 Controllers (Cart, Coupon, Order, Product x2, User)
│   └── api/{domain}/
│
├── application/         # 18 UseCases + 6 Event Listeners
│   ├── usecase/{domain}/
│   └── {domain}/listener/
│
├── domain/             # Entity, Repository Interface
│   ├── cart/           (Cart, CartItem)
│   ├── coupon/         (Coupon, UserCoupon)
│   ├── order/          (Order, OrderItem)
│   ├── payment/
│   ├── product/        (Product, ProductSalesAggregate)
│   └── user/           (User)
│
└── infrastructure/     # Repository Impl, Redis, Batch, External
    ├── persistence/    (JPA Repository 구현)
    ├── redis/          (ProductRankingRepository)
    ├── batch/          (ProductSalesAggregateScheduler)
    └── external/       (외부 API 연동)
```

### 테스트 현황
- ✅ Testcontainers 사용 (MySQL, Redis)
- ✅ 단위 테스트, 통합 테스트, E2E 테스트, 성능 테스트 존재
- ✅ 동시성 테스트 포함 (쿠폰 발급, 잔액 충전)
- ⚠️ 커버리지 70% 달성 여부 미확인

---

## 🎯 개선 목록

> **진행 원칙**:
> - 한 번에 **1개 항목만** 선택하여 진행
> - **Test-First** 워크플로우 준수
> - **1-3 파일, 200 LoC 이하** 제한
> - **Decision Gate**에서 확인 (태스크 선택, 접근 방식, 긴 커맨드, 범위 증가)

---

## 우선순위 A: Critical (즉시 개선 필요)

### A1. 테스트 커버리지 검증 및 개선 ⭐

**현재 상태**
- 커버리지 리포트 존재 (`build/reports/jacoco/test/html/index.html`)
- 70% 이상 달성 여부 미확인

**목표**
1. `./gradlew test jacocoTestReport` 실행하여 현재 커버리지 확인
2. 70% 미만 시 커버리지 낮은 클래스에 테스트 추가
3. Domain, Application 계층 우선 개선

**변경 예상 파일**
- 커버리지 낮은 클래스에 대한 테스트 파일 (2-3개)
- 예: `src/test/java/.../domain/**/*Test.java`

**리스크**: 낮음 (테스트 추가만)
**예상 LoC**: ~100-200 (테스트 코드)

**진행 단계**
1. 커버리지 리포트 확인
2. 커버리지 70% 미만 클래스 리스트업
3. 가장 중요한 클래스 1개 선택
4. 실패 테스트 작성 → 통과 → 전체 테스트 실행
5. 커버리지 재확인

---

### A2. Event Listener 트랜잭션 Phase 검증 ⭐

**현재 상태**
- 6개 Event Listener 존재
- `@TransactionalEventListener(phase = AFTER_COMMIT)` 사용 여부 확인 필요

**목표**
- 모든 Listener가 `AFTER_COMMIT` phase 사용하는지 검증
- 외부 시스템 연동/사이드 이펙트는 반드시 `AFTER_COMMIT` 사용
- 트랜잭션 내부 로직만 `BEFORE_COMMIT` 또는 default 허용

**변경 예상 파일**
```
application/payment/listener/DataPlatformEventListener.java
application/product/listener/RankingEventListener.java
application/product/listener/EventIdempotencyListener.java (등)
application/user/listener/* (등)
```

**리스크**: 중간 (트랜잭션 동작 변경 가능)
**예상 LoC**: ~50-100

**진행 단계**
1. 6개 Listener 파일 읽기
2. `@TransactionalEventListener` 어노테이션 확인
3. phase 설정 없거나 잘못된 경우 리스트업
4. 1개씩 수정 (Test-First)
5. 통합 테스트로 검증

---

### A3. 문서 정합성 검증

**현재 상태**
- 154개 .md 파일 존재
- 규칙 문서: `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`
- 주차별 문서: `docs/week*/README.md`

**목표**
1. 규칙 충돌 확인 (AGENTS.md가 단일 소스)
2. 중복 또는 상충하는 내용 정리
3. 최신 가이드 (Week 8) 우선 적용 확인

**변경 예상 파일**
- 문서 파일들만 (코드 변경 없음)

**리스크**: 낮음 (문서만)
**예상 LoC**: 문서 정리

**진행 단계**
1. AGENTS.md, CLAUDE.md, GEMINI.md 읽기
2. 규칙 충돌 부분 리스트업
3. 충돌 해결 방안 제시
4. 문서 업데이트

---

## 우선순위 B: Important (품질 개선)

### B1. Integration Test 안정성 개선

**현재 상태**
- Testcontainers 사용
- 일부 테스트에 `sleep`/시간 의존 가능성

**목표**
- 모든 통합 테스트에서 `sleep` 제거
- 상태 기반 검증으로 전환 (DB row 확인, 이벤트 확인)
- 테스트 간 데이터 격리 보장

**변경 예상 파일**
```
src/test/java/.../application/*/listener/*Test.java (3-5개)
src/test/java/.../e2e/*.java
```

**리스크**: 중간 (테스트 로직 변경)
**예상 LoC**: ~100-200

---

### B2. Repository 인터페이스 일관성 검증

**현재 상태**
- Domain에 Repository 인터페이스
- Infrastructure에 구현체

**목표**
- 모든 Repository가 패턴 준수 확인
- Domain → Infrastructure 의존성 방향 검증
- Repository 메서드 네이밍 일관성 확인

**변경 예상 파일**
```
domain/*/Repository.java (인터페이스)
infrastructure/persistence/*/*.java (구현체)
```

**리스크**: 낮음 (구조 검증 위주)
**예상 LoC**: 검증 위주 (수정 최소)

---

### B3. UseCase 트랜잭션 범위 검증

**현재 상태**
- 18개 UseCase 존재
- `@Transactional` 사용 여부 및 범위 확인 필요

**목표**
- 각 UseCase의 트랜잭션 범위가 적절한지 검증
- 핵심 DB 쓰기 작업만 트랜잭션에 포함
- 외부 API 호출, 알림 등은 이벤트로 분리

**변경 예상 파일**
```
application/usecase/*/*.java (18개)
```

**리스크**: 높음 (트랜잭션 동작 변경)
**예상 LoC**: ~100-300

---

### B4. 동시성 제어 패턴 문서화

**현재 상태**
- 동시성 테스트 존재 (쿠폰 발급, 잔액 충전)
- 패턴: synchronized, ReentrantLock, AtomicInteger, BlockingQueue

**목표**
- 사용된 동시성 제어 패턴 문서화
- 각 패턴의 사용 이유 및 장단점 설명
- 코드 예시 포함

**변경 예상 파일**
```
docs/CONCURRENCY_PATTERNS.md (신규 또는 업데이트)
```

**리스크**: 낮음 (문서만)
**예상 LoC**: 문서 작성

---

## 우선순위 C: Nice to Have (선택적 개선)

### C1. API 응답 일관성 개선

**목표**: 모든 API가 `ApiResponse<T>` 래퍼 사용
**변경 파일**: `presentation/api/*/*.java` (7개)
**리스크**: 중간 (API 응답 변경)
**예상 LoC**: ~50-150

---

### C2. 에러 코드 체계 정리

**목표**: 에러 코드 정리 (P001, O001, PAY001 등) 및 문서화
**변경 파일**: `common/exception/ErrorCode.java`, 문서 추가
**리스크**: 낮음
**예상 LoC**: ~50-100

---

### C3. Swagger API 문서 완성도 개선

**목표**: 모든 API에 `@Operation`, `@ApiResponse` 어노테이션 추가
**변경 파일**: `presentation/api/*/*.java` (7개)
**리스크**: 낮음
**예상 LoC**: ~100-200

---

### C4. 성능 테스트 결과 분석 및 문서화

**목표**: 성능 테스트 결과 분석 및 개선 방향 문서화
**변경 파일**: `docs/PERFORMANCE_ANALYSIS.md` (신규)
**리스크**: 낮음
**예상 LoC**: 문서 작성

---

### C5. Redis 캐시 전략 문서화

**목표**: Redis 사용 패턴 및 캐시 전략 문서화 (TTL, 무효화 정책 등)
**변경 파일**: `docs/REDIS_STRATEGY.md` (신규 또는 업데이트)
**리스크**: 낮음
**예상 LoC**: 문서 작성

---

## 우선순위 D: Deferred (대규모 변경, 현재 진행 불가)

### D1. Package 구조 재구성 ❌

**상태**: **현재 진행 불가**
**사유**: AGENTS.md Rule - NO Large-Scale Refactoring

---

### D2. 아키텍처 변경 ❌

**상태**: **현재 진행 불가**
**사유**: AGENTS.md Rule - NO Architectural Changes

---

## 📋 진행 방식

### 1. 항목 선택
- 위 목록에서 1개 항목 선택
- 우선순위 A → B → C 순서 권장

### 2. Test-First 워크플로우
1. 실패하는 테스트 작성/수정
2. 해당 테스트만 실행 → 실패 확인 (`./gradlew test --tests "..."`)
3. 최소 코드 변경으로 테스트 통과
4. **(개발 중)** `--tests` 옵션을 사용하여 관련된 테스트만 실행하며 점진적으로 개발
5. **(기능 완료 후)** 전체 테스트 실행 (`./gradlew test`)
6. **(최종 검증)** 전체 테스트 및 커버리지 확인 (`./gradlew clean test jacocoTestReport`)

> **참고**: 전체 테스트는 시간이 많이 소요될 수 있으므로, 개발 중에는 특정 테스트만 실행하여 빠른 피드백을 받는 것이 효율적입니다.

### 3. Decision Gate (반드시 확인)
- **태스크 선택**: 변경 후보 중 어느 것부터 할지
- **접근 방식 선택**: 옵션이 2개 이상이면 비교 후 선택 요청
- **긴 커맨드 실행**: `./gradlew clean test`, `jacocoTestReport` 실행 전 확인
- **범위 증가**: 파일/테스트가 늘어나면 쪼개서 다음 태스크로

### 4. 제약 사항
- 1-3 파일, 200 LoC 이하
- Assertion 삭제/약화 금지
- 대규모 리팩터링/패키지 이동 금지
- Testcontainers 유지 (mock 금지)

---

## ✅ 체크리스트

### 작업 시작 전
- [x] `AGENTS.md` 규칙 확인
- [ ] 최신 `docs/week8/README.md` 확인
- [ ] 4-layer 아키텍처 의존성 방향 준수 확인
- [x] 변경 후보 리스트 작성 (1개씩 처리)

### 작업 완료 전
- [ ] Test-First로 진행 (실패 테스트 → 최소 코드 → 통과)
- [x] `./gradlew test` 통과
- [x] `./gradlew clean test` 통과 (최종 검증)
- [x] `./gradlew test jacocoTestReport` 통과 (커버리지 70% 이상)
- [ ] 변경 범위: 1-3 파일, 200 LoC 이하
- [x] Assertion 삭제/약화 없음
- [ ] Testcontainers 사용 (mock 없음)
- [ ] 트랜잭션 짧게 유지, 외부 연동은 `AFTER_COMMIT`

---

## 🚀 다음 단계

**선택 가이드**:
1. **즉시 시작**: A1 (테스트 커버리지) 또는 A2 (Event Listener Phase)
2. **품질 개선**: B1-B4 중 선택
3. **선택적 개선**: C1-C5 중 선택

**시작 프롬프트 예시**:
```
A1 항목(테스트 커버리지 검증)을 시작하자.
먼저 ./gradlew test jacocoTestReport를 실행해서
현재 커버리지를 확인해줘. (Decision Gate: 긴 커맨드 실행)
```

---

## 📚 참고 문서

### 규칙 & 가이드
- `./AGENTS.md` - 모든 코딩 규칙 (단일 소스)
- `./.claude/CLAUDE.md` - Claude Code 사용 가이드
- `./GEMINI.md` - Gemini용 규칙 (AGENTS.md 보충)

### 주간 가이드
- `docs/week8/README.md` - Event Listener & Outbox Pattern
- `docs/week7/README.md` - Redis 기반 랭킹 시스템
- `docs/week*/README.md` - 주차별 코치 피드백

### 아키텍처 & 테스트
- `docs/PROJECT_STRUCTURE.md` - 4-layer 아키텍처 상세
- `.claude/commands/testing.md` - 테스트 전략 (`/testing`)
- `.claude/commands/concurrency.md` - 동시성 제어 (`/concurrency`)

---

## ✅ 완료 기록 (Completed)

### 2025-12-18
- **Phase 3 개선**: OrderPaymentE2ETest.java의 sleep 제거 → Awaitility 적용
  - 변경 파일: 1개
  - 변경 LoC: 13줄
  - 개선: 상태 기반 대기, 더 빠른 폴링 (500ms → 200ms)
  - 결과: 3개 테스트 통과 ✅

- **@MockBean → @MockitoBean 교체**: Spring Boot 3.4+ Deprecation 대응
  - 변경 파일: 5개 (CompensationEventHandler, PgApiEventHandler, RankingEventRetry, ProcessPaymentUseCase, EventListener 통합 테스트)
  - 변경 LoC: 파일당 2-3줄 (import + 어노테이션)
  - 이유: Spring Boot 3.5.7에서 @MockBean deprecated
  - 결과: 모든 테스트 통과 ✅

- **Phase 4 완료**: 전체 빌드 검증 (`./gradlew test --rerun-tasks`)
  - 테스트 결과: 282개 / 282개 통과 (100%) ✅
  - 커버리지: Instruction 73%, Line 74%, Method 80% (목표 70% 달성) ✅
  - 소요 시간: 1분 13.29초
  - 개선 필요: `application.product.usecase` (1%), `application.facade` (23%)
  - 상세 리포트: `build/test-coverage-summary.md`
  - 결과: **Phase 4 목표 달성** ✅

### 다음 단계
- **우선순위 A1**: 테스트 커버리지 개선 (product.usecase, facade)
- **우선순위 A2**: Event Listener 트랜잭션 Phase 검증

---

**Last Updated**: 2025-12-18
