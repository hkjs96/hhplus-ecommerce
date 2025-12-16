# CLAUDE.md – hhplus-ecommerce

> **Claude Code를 효과적으로 사용하기 위한 프로젝트 가이드**

---

## 🚀 빠른 시작 (Quick Start)

처음 Claude Code를 사용하시나요? 다음 3단계를 따라하세요:

### 1단계: 컨텍스트 제공
```
현재 상황: [버그/기능 한 줄 설명]
기대 동작: [원하는 결과]
실제 동작: [현재 상태 또는 에러 메시지]
재현 방법: [테스트 명령어 또는 실패하는 테스트]
관련 문서: AGENTS.md, docs/week*/README.md
```

### 2단계: 변경 후보 리스트 요청
```
"변경 후보 리스트를 먼저 작성해줘 (5-10개):
- 각 항목: (1) 변경 파일 (2) 추가/수정 테스트 (3) 리스크
- 아직 코드는 수정하지 말고 리스트만"
```

### 3단계: 1개씩 진행
```
"리스트 중 가장 작은 태스크 1개를 골라서 Test-First로 진행하자.
Decision gate (태스크 선택/접근 방식/긴 커맨드/범위 증가)에서 확인 받아."
```

### 첫 요청 예시 (복사해서 사용하세요)

<details>
<summary>📋 버그 수정 요청 템플릿</summary>

```
페어 코딩 모드로 진행해줘. Decision gate마다 내 확인을 받아.
저장소 규칙은 ./AGENTS.md를 단일 소스로 따르세요.

목표: OrderService에서 쿠폰 적용 시 NPE 발생
기대 동작: 쿠폰이 없어도 주문 생성 성공
실제 동작: NullPointerException at OrderService:45
재현 방법: ./gradlew test --tests "OrderServiceTest.shouldCreateOrderWithoutCoupon"
관련 문서: docs/week8/README.md (트랜잭션/이벤트 원칙)

요구:
1) 먼저 변경 후보 리스트를 작성해줘 (코드 수정 X)
2) 내가 1개를 고르면 Test-First로 진행:
   - 실패 테스트 추가/수정 → 실패 확인
   - 최소 코드 수정으로 통과
   - ./gradlew test 통과
   - 최종 clean test + jacoco는 내가 OK 하면 실행
제약: 패키지 이동/대규모 리팩터 금지, Testcontainers 유지
```
</details>

<details>
<summary>✨ 새 기능 추가 요청 템플릿</summary>

```
페어 코딩 모드로 진행해줘. Decision gate마다 내 확인을 받아.
저장소 규칙은 ./AGENTS.md를 단일 소스로 따르세요.

목표: User 엔티티에 nickname 필드 추가
기대 동작: 사용자가 닉네임을 설정/변경할 수 있음
현재 상태: nickname 필드 없음
관련 파일: domain/user/User.java, UserService.java
관련 문서: docs/PROJECT_STRUCTURE.md (4-layer 아키텍처)

요구:
1) 먼저 변경 후보 리스트를 작성해줘:
   - 각 항목: (변경 파일, 추가 테스트, 리스크)
   - 아직 코드 수정 X
2) 내가 1개를 고르면 Test-First로 진행
3) 최종 clean test + jacoco는 내가 OK 하면 실행
제약: 1-3 파일, 200 LoC 이하, 아키텍처 유지
```
</details>

---

## 📌 핵심 원칙 (Most Important)

### 1. 단일 소스 원칙 (Single Source of Truth)

**모든 코딩 규칙의 단일 소스는 `./AGENTS.md`입니다.**

- 규칙 충돌 시 **AGENTS.md가 항상 우선**합니다
- GEMINI.md, CODEX_GUIDE.md는 AGENTS.md를 보충하는 문서입니다
- 코드 변경 전에 AGENTS.md의 규칙을 확인하세요

### 2. 작업 방식 (How to Work)

**"리스트업 후 1개씩"**
- 변경할 게 많아도 먼저 **변경 후보 리스트**만 작성
- 실제 코드 변경은 **한 번에 1개 태스크만** 끝까지 처리
- 작은 단위: **1-3 파일, 200 LoC 이하**

**Decision Gate (여기선 반드시 물어보기)**
1. **태스크 선택**: 변경 후보 중 어느 것부터 할지
2. **접근 방식 선택**: 옵션이 2개 이상이면 비교 후 선택 요청
3. **긴 커맨드 실행**: `./gradlew clean test`, `jacocoTestReport` 실행 전 확인
4. **범위 증가**: 파일/테스트가 늘어나면 쪼개서 다음 태스크로

**Test-First 워크플로우**
1. 실패하는 테스트 먼저 작성/수정
2. 해당 테스트만 실행해서 실패 확인
3. 최소 코드 변경으로 테스트 통과
4. 전체 테스트 실행 (회귀 방지)
5. 최종: `./gradlew clean test` + `jacocoTestReport`

### 3. 금지 사항 (Strictly Enforced)

- ❌ 대규모 리팩터링/패키지 이동/리네임
- ❌ 임의 스타일 변경 (기존 파일 스타일 따르기)
- ❌ 아키텍처 변경 (4-layer 구조 유지)
- ❌ Assertion 삭제/약화
- ❌ Repository/DB mock (Testcontainers 사용)

---

## 💬 Claude Code에게 효과적으로 요청하는 방법

### 좋은 요청 vs 나쁜 요청

| 항목 | ❌ 나쁜 요청 | ✅ 좋은 요청 |
|------|------------|------------|
| **목표** | "코드 고쳐줘" | "OrderService:45에서 NPE 발생. 쿠폰 없어도 주문 생성되게 수정" |
| **컨텍스트** | (없음) | "재현: `./gradlew test --tests OrderServiceTest.shouldCreateOrderWithoutCoupon`" |
| **범위** | "Order 관련 전부 수정" | "OrderService.createOrder 메서드만 수정 (1개 파일)" |
| **테스트** | "테스트도 같이 해줘" | "먼저 실패 테스트 추가 → 코드 수정 → 통과 확인" |
| **확인** | "다 알아서 해줘" | "Decision gate에서 확인 받고 진행" |

### 컨텍스트 제공 체크리스트

Claude Code에게 요청할 때 다음을 제공하세요:

- [ ] **목표**: 무엇을 하려는가? (1-2문장)
- [ ] **기대 동작**: 원하는 결과
- [ ] **실제 동작**: 현재 상태 또는 에러 메시지
- [ ] **재현 방법**: 테스트 명령어 또는 실패하는 테스트 이름
- [ ] **관련 파일**: 예상되는 변경 파일 (있으면)
- [ ] **관련 문서**: AGENTS.md, docs/week*/README.md 링크

### 실전 프롬프트 패턴

#### 패턴 1: "변경 후보 리스트 먼저"
```
"아래 요구사항을 보고 변경 후보를 5-10개로 나열해줘.
각 항목에 (1) 변경 파일 (2) 추가/수정 테스트 (3) 리스크를 포함.
아직 코드는 수정하지 마."

[요구사항 설명]
```

#### 패턴 2: "가장 작은 태스크 1개만"
```
"위 리스트 중 가장 작은 태스크 1개만 골라서 Test-First로 진행하자.
Decision gate에서 확인 받아."
```

#### 패턴 3: "범위 제어"
```
"지금 변경 범위가 커졌어. 파일 1-3개, 200 LoC 이하로 쪼개서,
이번 태스크에 필요한 최소 수정만 하자."
```

#### 패턴 4: "테스트 실패 디버깅"
```
"이 테스트가 실패했어:
- 테스트: [테스트 클래스명]
- 에러: [에러 메시지]
- 스택트레이스: [첫 5줄]

변경 후보 리스트 작성 후 1개씩 수정하자."
```

---

## 🎮 Claude Code 제어 문구

Claude가 범위를 넓히려 할 때 사용하세요:

### 범위 제어
```
"지금 변경 범위가 커졌어. 파일 1-3개/200 LoC 이하로 쪼개자."
"이번 태스크에 필요한 최소 수정만 하자."
```

### 금지 행동 제어
```
"리네임/패키지 이동/대규모 리팩터는 금지."
"행동(테스트) 단위로만 수정해."
```

### 테스트 우선 강제
```
"우선 failing test 하나를 고정하고, 그 테스트만 통과시키는 데 집중해."
"실패 테스트 추가 없이 코드 수정 금지."
```

### Decision Gate 강제
```
"지금 Decision gate야. 먼저 물어보고 진행해."
"접근 방식이 2개 이상이면 장단점 비교 후 내가 선택할게."
```

---

## 🧠 Claude Code 특화 기능 활용

### Task Tool 사용 시점

Claude Code는 복잡한 작업을 위해 **Task Tool**을 사용할 수 있습니다.

**언제 Task Tool을 사용하도록 요청하나요?**

1. **코드베이스 탐색** (Explore Agent)
   ```
   "프로젝트 전체에서 'CouponService' 관련 파일을 찾아줘."
   "에러 처리 패턴이 어떻게 구현되어 있는지 조사해줘."
   ```

2. **구현 계획 수립** (Plan Agent)
   ```
   "새 기능 추가 전에 구현 계획을 세워줘."
   "Step 5-6 구현 전략을 설계해줘."
   ```

3. **대규모 검색** (여러 파일/패턴)
   ```
   "모든 Controller에서 에러 핸들링 방식을 조사해줘."
   "Redis 관련 설정이 어디에 있는지 찾아줘."
   ```

**Task Tool을 사용하지 말아야 할 때:**
- 특정 파일 읽기 (그냥 "User.java 읽어줘")
- 특정 클래스 찾기 (그냥 "class Order 찾아줘")
- 2-3개 파일 검색 (직접 검색이 더 빠름)

### 병렬 실행

**여러 독립적인 작업은 병렬로 요청하세요:**
```
"다음 3가지를 병렬로 실행해줘:
1. OrderService 테스트 실행
2. PaymentService 테스트 실행
3. git status 확인"
```

### 슬래시 커맨드 실전 사용

프로젝트에서 제공하는 커스텀 슬래시 커맨드:

- `/architecture`: "4-layer 구조 다시 확인" 대신
- `/concurrency`: "동시성 제어 방법 알려줘" 대신
- `/testing`: "테스트 전략 설명해줘" 대신
- `/implementation`: "Step 5-6 구현 가이드 보여줘" 대신

---

## 🔄 컨텍스트 관리 (Compact)

### 왜 Compact가 필요한가?

Claude Code는 대화가 길어지면 **컨텍스트가 누적**됩니다:
- ✅ 장점: 이전 대화 내용을 기억
- ⚠️ 단점: 불필요한 정보가 쌓여 응답 품질 저하 가능
- ⚠️ 단점: 토큰 사용량 증가

**Compact는 컨텍스트를 요약/압축하여 핵심만 유지합니다.**

### 언제 Compact를 해야 하나?

다음 상황에서 Compact를 고려하세요:

1. **대화가 20-30회 이상 길어졌을 때**
2. **완전히 새로운 작업을 시작할 때**
   - 예: 버그 수정 완료 → 새 기능 추가
3. **Claude의 응답이 이상할 때**
   - 이전 작업 내용을 계속 언급
   - 이미 수정한 코드를 다시 수정하려 함
4. **작업 단계가 명확히 구분될 때**
   - Step 5 완료 → Step 6 시작
   - Week 7 완료 → Week 8 시작

### Compact 방법

⚠️ **중요**: Claude Code는 자동으로 컨텍스트를 정리하지 **않습니다**. 명시적으로 관리해야 합니다.

#### 방법 1: 새 대화 시작 (가장 확실) ⭐ 추천
- IDE에서 새 대화 세션 시작
- 이전 컨텍스트 완전히 제거
- 깨끗한 상태에서 새 작업 시작
- **언제**: Step/Week 단위 작업 완료 후

#### 방법 2: 명시적 요약 요청
```
"지금까지 대화를 요약하고 핵심만 남겨서 컨텍스트를 정리해줘.

유지할 정보:
- 현재 작업: [작업명]
- 완료된 것: [완료 항목]
- 다음 할 것: [다음 항목]
- 주요 결정사항: [결정 내용]

나머지는 버려."
```

#### 방법 3: 컨텍스트 리셋 요청
```
"이전 작업은 완료했어. 컨텍스트를 정리하고 새 작업을 시작하자:
- 목표: [새 작업]
- 규칙: AGENTS.md, docs/week*/README.md
- 진행 방식: 리스트업 후 1개씩"
```

#### 주기적 Compact 권장 타이밍

다음 시점에 **반드시** Compact 하세요:

1. **매 5-10개 태스크마다** (대화 20-30회)
2. **Week/Step 단위 완료 후**
   - Step 5 완료 → Step 6 시작 전
   - Week 7 완료 → Week 8 시작 전
3. **큰 버그 수정 완료 후** (10개 이상 파일 변경)
4. **Claude가 이상하게 행동할 때**
   - 이미 수정한 코드를 다시 언급
   - 불필요한 이전 컨텍스트 계속 참조
   - 응답이 느려지거나 품질 저하

### Compact 후 체크리스트

Compact 직후 다음을 확인하세요:

- [ ] 작업 목표를 다시 명확히 전달
- [ ] 관련 문서 링크 제공 (AGENTS.md, docs/week*/README.md)
- [ ] 현재 단계 설명 (Week X, Step Y)
- [ ] 필요한 컨텍스트 요약 제공

**Compact 후 첫 요청 예시:**
```
"컨텍스트를 정리했어. 다시 시작하자.

현재 작업: Week 8 - Event Listener 구현
목표: PaymentCompletedEvent 발행 후 OrderCompletedEvent 처리
규칙: AGENTS.md, docs/week8/README.md 참조
진행 방식: 리스트업 후 1개씩, Decision gate 확인"
```

---

## 📍 상황별 가이드 매핑

상황에 맞는 섹션을 빠르게 찾으세요:

### 🐛 버그 수정할 때
1. [빠른 시작 - 버그 수정 템플릿](#🚀-빠른-시작-quick-start)
2. [테스트 수정 우선순위](#테스트-수정-우선순위)
3. [문제 해결 - 테스트가 계속 실패해요](#🆘-문제-해결)

### ✨ 새 기능 추가할 때
1. [빠른 시작 - 새 기능 추가 템플릿](#🚀-빠른-시작-quick-start)
2. [아키텍처: Layered Architecture](#아키텍처-layered-architecture-4계층)
3. [트랜잭션 & 이벤트 원칙](#트랜잭션--이벤트-원칙-critical)

### 🧪 테스트 작성할 때
1. [테스트 전략](#🧪-테스트-전략)
2. [Testcontainers 기반 통합 테스트](#testcontainers-기반-통합-테스트)
3. [슬래시 커맨드 - /testing](#슬래시-커맨드)

### 🔍 코드 탐색할 때
1. [Claude Code 특화 기능 - Task Tool](#🧠-claude-code-특화-기능-활용)
2. [문서 참조 가이드](#📚-문서-참조-가이드)

### 🔄 작업 전환할 때
1. [컨텍스트 관리 (Compact)](#🔄-컨텍스트-관리-compact)
2. [작업 시작 전 체크리스트](#📋-작업-시작-전-체크리스트)

### 🚨 Claude가 말을 안 들을 때
1. [Claude Code 제어 문구](#🎮-claude-code-제어-문구)
2. [Decision Gate 강제](#decision-gate-여기선-반드시-물어보기)

### 📦 커밋/PR 준비할 때
1. [작업 완료 전 체크리스트](#📋-작업-완료-전-체크리스트)
2. [커밋 메시지 템플릿](#📝-커밋-메시지-템플릿)

---

## 🏗️ 프로젝트 개요

### 기본 정보
- **프로젝트**: 항해플러스 이커머스 백엔드
- **현재 단계**: Week 8 (Event Listener & Outbox Pattern)
- **목표**: 애플리케이션 레벨에서 가용성을 보장하는 이커머스 시스템

### 기술 스택
- **언어/프레임워크**: Java 21, Spring Boot 3.5.7
- **빌드 도구**: Gradle
- **데이터베이스**: MySQL 8 (JPA, Spring Data JPA)
- **캐시/동시성**: Redis (Sorted Set, Distributed Lock)
- **테스트**: JUnit 5, Testcontainers (MySQL, Redis)

### 아키텍처: Layered Architecture (4계층)

```
src/main/java/io/hhplus/ecommerce/
├── presentation/        # API Controller, Request/Response DTO
├── application/         # UseCase, Application Service, Event Publisher
├── domain/             # Entity, Value Object, Domain Service, Repository Interface
└── infrastructure/     # Repository Impl, External API Client, Redis, Config
    ├── persistence/    # JPA Repository 구현
    ├── redis/         # Redis 접근 어댑터
    └── event/         # Event Publisher 구현
```

**의존성 방향 (엄격히 준수)**
```
Presentation → Application → Domain ← Infrastructure
```

- Domain은 다른 계층에 의존하지 않음 (가장 안정적)
- Infrastructure는 Domain의 인터페이스를 구현
- Application은 Domain과 Infrastructure를 조합하여 유스케이스 구현

---

## 🧪 테스트 전략

### 테스트 커버리지 요구사항
- **최소 70% 이상** (엄격히 준수)
- 검증 명령: `./gradlew test jacocoTestReport`

### 테스트 실행 래더 (Fast Feedback → Final)

```bash
# 1. 집중 테스트 (개발 중, 빠른 피드백)
./gradlew test --tests "io.hhplus.ecommerce.domain.order.OrderServiceTest"

# 2. 전체 테스트 (태스크 완료 후)
./gradlew test

# 3. 최종 검증 (PR/제출 전)
./gradlew clean test
./gradlew test jacocoTestReport

# 4. 디버깅 (실패 로그 캡처)
./gradlew test --console=plain --info --stacktrace | tee build/test-full.log
grep -nE "FAILED|FAILURE|Exception" build/test-full.log
```

### Testcontainers 기반 통합 테스트

**필수 규칙:**
- Repository/DB 레이어를 mock으로 우회하지 않음
- 실제 MySQL, Redis 컨테이너로 테스트
- 테스트 간 데이터 격리 보장 (독립성)
- 시간 의존 제거 (sleep 대신 상태 기반 검증)

**통합 테스트 체크리스트:**
- [ ] 시간 의존 제거: `sleep`/타임아웃 대신 상태(DB row, 이벤트) 기반 검증
- [ ] 데이터 격리: 테스트 간 공유 데이터/순서 의존 제거
- [ ] 정리 보장: 트랜잭션 롤백/DB 초기화 일관성
- [ ] 동시성 테스트: Barrier/Latch로 재현 가능한 방식 구성

### 테스트 수정 우선순위

테스트 실패 시 다음 순서로 수정:

1. **비즈니스 로직 수정** (최우선): `src/main`의 프로덕션 코드
2. **테스트 로직 수정**: `src/test`의 테스트 코드 (setup/assertion 오류)
3. **테스트 인프라 수정** (최후): Testcontainers 설정 (`src/test/.../config/`)

**절대 금지:**
- ❌ Assertion 삭제/주석 처리
- ❌ 강한 assertion을 약한 것으로 변경 (예: `assertEquals(10, x)` → `assertTrue(x > 5)`)

---

## 🎯 핵심 비즈니스 규칙

### 도메인 모델
- `cart`: 장바구니 (CartItem 포함)
- `coupon`: 쿠폰 (선착순 발급, UserCoupon)
- `order`: 주문 (OrderItem 포함)
- `payment`: 결제 (잔액 기반)
- `product`: 상품 (재고 관리, ProductSalesAggregate)
- `user`: 사용자 (잔액 충전)

### 동시성 제어 패턴 (4가지)

프로젝트에서 사용하는 동시성 제어 방식:

1. **synchronized** (간단한 메모리 기반 동기화)
2. **ReentrantLock** (명시적 락 제어)
3. **AtomicInteger** (lock-free 원자 연산)
4. **BlockingQueue** (생산자-소비자 패턴)

**상세**: `.claude/commands/concurrency.md` 또는 `/concurrency` 슬래시 커맨드 참조

### 트랜잭션 & 이벤트 원칙 (Critical)

**출처**: `docs/week8/README.md`, `GEMINI.md`, `AGENTS.md`

#### Rule 1: 도메인 디커플링에 `@TransactionalEventListener` 사용
- 도메인 간 결합을 줄이기 위해 Application Event 발행
- **이유**: 긴 트랜잭션으로 인한 DB 락 방지

#### Rule 2: 항상 `phase = TransactionPhase.AFTER_COMMIT` 사용
- 외부 시스템 연동/사이드 이펙트는 **트랜잭션 커밋 후**에만 실행
- **예시**:
  ```java
  // ✅ DO THIS
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleOrderCompleted(OrderCompletedEvent event) {
      // 외부 API 호출, 알림 전송 등
  }

  // ❌ DON'T DO THIS
  @EventListener
  public void handleOrderCompleted(OrderCompletedEvent event) {
      // 트랜잭션 롤백 시 불일치 발생 가능
  }
  ```

#### Rule 3: 트랜잭션은 짧게 유지
- 트랜잭션은 **핵심 DB 쓰기 작업만** 포함
- 외부 API 호출, 알림, 복잡한 계산은 비동기 이벤트 리스너로 분리
- **이유**: 긴 트랜잭션은 처리량 감소 및 데드락 유발

---

## 📚 문서 참조 가이드

### 규칙 & 가이드
- **`./AGENTS.md`** ⭐ 단일 소스: 모든 코딩 규칙, 워크플로우, 금지 사항
- **`./GEMINI.md`**: Gemini용 규칙 (AGENTS.md 기반, 트랜잭션/이벤트 원칙)
- **`docs/CODEX_GUIDE.md`**: IDE에서 Codex 사용 시 참고 (Decision gate, Pair-coding)

### 주간 가이드 (Progressive)
- **항상 최신 `docs/week*/README.md` 우선 확인**
- 이전 주차 Q&A/피드백도 참고하되 최신 지침 우선 적용
- 코드 변경 시 가이드/피드백 준수 여부를 5인 시니어(7-20년차) 관점에서 검증

### 아키텍처 & 구조
- **`docs/PROJECT_STRUCTURE.md`**: 4-layer 아키텍처 상세 설명
- **`.claude/commands/architecture.md`**: 아키텍처 슬래시 커맨드 (`/architecture`)

### 동시성 & 테스트
- **`.claude/commands/concurrency.md`**: 동시성 제어 패턴 (`/concurrency`)
- **`.claude/commands/testing.md`**: 테스트 전략 & Jacoco (`/testing`)
- **`docs/week5/COUPON_CONCURRENCY_VERIFICATION.md`**: 쿠폰 동시성 검증

### Step별 구현 가이드
- **`.claude/commands/implementation.md`**: Step 5-6 구현 가이드 (`/implementation`)

### Redis 관련 (Week 7)
- **`agent_docs/redis_ranking.md`**: Sorted Set 랭킹 시스템 상세
- **`agent_docs/redis_coupon_issue.md`**: 선착순 쿠폰 발급 Lua 스크립트
- **`agent_docs/testing_redis_features.md`**: Redis 통합 테스트 시나리오

---

## 💡 Import 규칙

**원칙**: 타입은 import 후 짧은 이름 사용

```java
// ✅ Good (normal import, short name)
import io.hhplus.ecommerce.domain.order.Order;

Order order = new Order(...);
```

**이름 충돌 시**:
1. 먼저 네이밍을 재고 (더 명확한 이름으로)
2. 정말 불가피한 경우에만 fully-qualified 이름 사용

```java
// ⚠️ Last resort (unavoidable conflict)
io.hhplus.ecommerce.domain.order.Order domainOrder = ...;
com.external.lib.Order externalOrder = ...;
```

---

## 🚀 빠른 참조

### 자주 쓰는 명령어

```bash
# 빌드
./gradlew clean build

# 실행
./gradlew bootRun

# 테스트 (집중)
./gradlew test --tests "패키지.클래스명"

# 테스트 (전체)
./gradlew test

# 테스트 (최종 검증)
./gradlew clean test
./gradlew test jacocoTestReport

# Docker (MySQL, Redis)
# docker-compose.yml 또는 scripts/ 폴더 참조
```

### 슬래시 커맨드

프로젝트에서 사용 가능한 커스텀 슬래시 커맨드:

- `/architecture`: 레이어드 아키텍처 상세 설명 및 프로젝트 구조
- `/concurrency`: 동시성 제어 패턴 4가지 (synchronized, ReentrantLock, AtomicInteger, BlockingQueue)
- `/testing`: 테스트 전략 및 Jacoco 커버리지 가이드
- `/implementation`: Step 5-6 구현 가이드 및 코드 예시

**사용법**: Claude Code 대화창에 `/architecture` 입력

---

## 📋 작업 시작 전 체크리스트

코드 변경 전에 확인하세요:

- [ ] `AGENTS.md`의 규칙 확인 (단일 소스)
- [ ] 최신 `docs/week*/README.md` 확인 (코치 피드백)
- [ ] 4-layer 아키텍처 의존성 방향 준수 확인
- [ ] 변경 후보 리스트 작성 (1개씩 처리)
- [ ] Decision gate 체크 (태스크 선택, 접근 방식, 긴 커맨드, 범위 증가)

## 📋 작업 완료 전 체크리스트

제출/커밋 전에 확인하세요:

- [ ] Test-First로 진행했는가? (실패 테스트 → 최소 코드 → 통과)
- [ ] `./gradlew test` 통과 (전체 테스트)
- [ ] `./gradlew clean test` 통과 (최종 검증)
- [ ] `./gradlew test jacocoTestReport` 통과 (커버리지 70% 이상)
- [ ] 변경 범위: 1-3 파일, 200 LoC 이하
- [ ] Assertion 삭제/약화 없음
- [ ] Testcontainers 사용 (Repository/DB mock 없음)
- [ ] 트랜잭션 짧게 유지, 외부 연동은 `AFTER_COMMIT`
- [ ] Import 규칙 준수 (짧은 이름, 충돌 시 네이밍 재고)

---

## 🎯 페르소나 검증 (실무 적합성)

코드 변경 후 다음 관점에서 점검:

**5인 시니어 페르소나 (7-20년차 경험)**
1. **아키텍트 (20년)**: 의존성 방향, 계층 분리가 적절한가?
2. **백엔드 리드 (15년)**: 트랜잭션 범위, 동시성 제어가 안전한가?
3. **시니어 개발자 (10년)**: 테스트 커버리지, 엣지 케이스 처리가 충분한가?
4. **미들 개발자 (7년)**: 코드 가독성, 유지보수성이 좋은가?
5. **데브옵스 (12년)**: 성능, 리소스 사용, 장애 대응이 고려되었는가?

**질문**:
- 이 변경이 프로덕션에서 문제를 일으킬 가능성은?
- 동시성 환경에서 안전한가?
- 트랜잭션 경계가 적절한가?
- 테스트로 충분히 검증되었는가?

---

## 📝 커밋 메시지 템플릿

**형식**: `AGENTS.md` 섹션 5 참조

```
feat(domain): 제목 (예: Add 'nickname' to User)

### 1. Purpose
- 왜 이 변경이 필요한가? (예: 사용자 닉네임 표시 요구사항)

### 2. Changed Files
- src/main/java/io/hhplus/ecommerce/domain/user/User.java
- src/test/java/io/hhplus/ecommerce/domain/user/UserTest.java

### 3. Test Results
- Command: ./gradlew test --tests "io.hhplus.ecommerce.domain.user.UserTest"
- Output:
  ```
  > Task :test
  io.hhplus.ecommerce.domain.user.UserTest > canUpdateNickname() PASSED

  BUILD SUCCESSFUL in 5s
  ```

### 4. Risks & Rollback
- Risks: (예: None. Additive change.)
- Rollback: (예: Revert this commit.)
```

---

## 🆘 문제 해결

### "테스트가 계속 실패해요"

1. 실패 로그 확인: `./gradlew test --console=plain --info --stacktrace | tee build/test-full.log`
2. 필터링: `grep -nE "FAILED|FAILURE|Exception" build/test-full.log`
3. 수정 우선순위: 비즈니스 로직 → 테스트 로직 → 테스트 인프라
4. 여전히 안 되면: AGENTS.md의 예제 참조 또는 `docs/week*/README.md` 확인

### "커버리지가 70% 미만이에요"

1. 현재 커버리지 확인: `./gradlew test jacocoTestReport`
2. 리포트 확인: `build/reports/jacoco/test/html/index.html`
3. 누락된 테스트 추가 (특히 Domain, Application 계층)
4. Testcontainers 기반 통합 테스트 추가

### "Decision gate에서 뭘 물어봐야 하나요?"

1. **태스크 선택**: "변경 후보 A, B, C 중 어떤 걸 먼저 할까요?"
2. **접근 방식**: "방식 1 (장점/단점) vs 방식 2 (장점/단점), 어떤 게 좋을까요?"
3. **긴 커맨드**: "`./gradlew clean test` 실행해도 될까요?"
4. **범위 증가**: "파일이 5개로 늘었어요. 쪼갤까요?"

### "Claude가 범위를 계속 넓혀요"

1. [Claude Code 제어 문구](#🎮-claude-code-제어-문구) 사용
2. "이번 태스크에 필요한 최소 수정만 하자" 명시
3. "파일 1-3개, 200 LoC 이하로 쪼개자" 요청

### "Claude가 이전 작업을 계속 언급해요"

1. [컨텍스트 관리 (Compact)](#🔄-컨텍스트-관리-compact) 참조
2. 명시적 요청: "지금까지 대화를 요약하고 핵심만 남겨줘"
3. 새 대화 시작 (IDE에서 새 세션)
4. Compact 후 작업 목표 다시 전달

---

## 🎓 학습 자료

### 아키텍처 패턴
- Layered Architecture (이 프로젝트)
- Hexagonal Architecture (Port & Adapter)
- Clean Architecture (의존성 역전)
- DDD (Domain-Driven Design)

### 동시성 제어
- `/concurrency` 슬래시 커맨드
- `docs/week5/COUPON_CONCURRENCY_VERIFICATION.md`

### 이벤트 기반 설계
- `docs/week8/README.md` (Event Listener & Outbox Pattern)
- `GEMINI.md` Rule 1-3

---

## 🔗 외부 참고 자료 (Optional)

- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [Testcontainers 공식 문서](https://www.testcontainers.org/)
- [Redis 공식 문서](https://redis.io/docs/)
- [Martin Fowler - Patterns of Enterprise Application Architecture](https://martinfowler.com/eaaCatalog/)

---

## 마무리

**가장 중요한 것:**

1. **`AGENTS.md`가 모든 규칙의 단일 소스입니다.**
2. **리스트업 후 1개씩, Decision gate에서 확인 받으며 작업하세요.**
3. **Test-First, 작은 단위 (1-3 파일, 200 LoC), 테스트 커버리지 70% 이상.**
4. **트랜잭션 짧게, 외부 연동은 `AFTER_COMMIT`.**
5. **대화가 길어지면 Compact로 컨텍스트 정리.**

Claude Code가 이 가이드를 따르면 안전하고 품질 높은 코드를 생산할 수 있습니다.

**Happy Coding with Claude! 🚀**
