# Week 3 학습 포인트

3주차 과제를 통해 학습해야 할 핵심 개념과 실전 적용 방법을 정리한 문서입니다.

---

## 📚 학습 자료 목차

### 1️⃣ [레이어드 아키텍처](./01-layered-architecture.md)
- 4계층 아키텍처 구조 (Presentation, Application, Domain, Infrastructure)
- 의존성 방향 규칙 (Dependency Rule)
- 각 계층의 책임과 역할
- Pass/Fail 기준

### 2️⃣ [유스케이스 패턴](./02-usecase-pattern.md)
- UseCase의 정의와 개념
- API 명세를 유스케이스로 구현하는 방법
- DomainService vs UseCase 비교
- 실전 예시 코드

### 3️⃣ [도메인 모델링](./03-domain-modeling.md)
- Rich Domain Model vs Anemic Domain Model
- Entity에 비즈니스 로직을 두는 이유
- 도메인 규칙 캡슐화
- 객체의 능동성

### 4️⃣ [Repository 패턴](./04-repository-pattern.md)
- Repository 인터페이스와 구현체 분리
- In-Memory Repository 구현
- ConcurrentHashMap 활용
- 테스트 용이성

### 5️⃣ [동시성 제어](./05-concurrency-control.md)
- Race Condition이란?
- 4가지 동시성 제어 방식 (synchronized, ReentrantLock, AtomicInteger, Queue)
- Step 5 vs Step 6 차이점
- 통합 테스트 작성 방법

### 6️⃣ [테스트 전략](./06-testing-strategy.md)
- 테스트 커버리지의 실용적 접근
- 핵심 비즈니스 로직 vs 일반 서비스 코드
- 단위 테스트 vs 통합 테스트
- Mock/Stub 활용법

### 7️⃣ [DTO 설계 전략](./07-dto-design.md)
- 레이어별 DTO 분리 원칙
- DTO 재사용 전략 (Composition)
- API별 전용 DTO vs 공통 DTO
- 입력값 검증 레이어

### 8️⃣ [토론 주제](./08-discussion-topics.md) 🔥
- 14개 핵심 토론 주제 Q&A
- 레이어드 아키텍처 (Q1-Q3)
- UseCase 패턴 (Q4-Q5)
- Domain Modeling (Q6-Q7)
- Repository 패턴 (Q8-Q9)
- 동시성 제어 (Q10-Q12)
- Testing (Q13-Q14)
- 면접/코드리뷰 준비 자료

---

## 🎯 학습 목표

### Step 5: 레이어드 아키텍처 기본 구현
- [ ] 4계층이 명확히 분리되어 있는가?
- [ ] 도메인 모델이 비즈니스 규칙을 포함하는가?
- [ ] Repository 패턴이 올바르게 적용되었는가?
- [ ] 핵심 비즈니스 로직이 정상 동작하는가?
- [ ] 테스트 커버리지 70% 이상 달성했는가?

### Step 6: 동시성 제어 및 고급 기능
- [ ] Race Condition이 방지되는가?
- [ ] 동시성 테스트가 작성되고 통과하는가?
- [ ] 인기 상품 집계 로직이 구현되었는가?
- [ ] README.md에 동시성 제어 분석이 포함되어 있는가?

---

## 🔥 핵심 평가 포인트

### 1. 레이어드 아키텍처 이해도
**토론 주제:**
- "왜 Repository 인터페이스를 Domain에 두었나요?"
- "UseCase와 DomainService의 차이는 무엇인가요?"
- "Controller에서 직접 Repository를 호출하면 안 되는 이유는?"

### 2. 비즈니스 로직 배치
**토론 주제:**
- "재고 차감 로직을 어디에 구현했나요? 그 이유는?"
- "할인 계산 로직은 어느 계층에 있나요?"

### 3. Repository 패턴 이해
**토론 주제:**
- "Repository와 DAO의 차이는 무엇인가요?"
- "ConcurrentHashMap을 선택한 이유는?"

### 4. 동시성 제어 이해도
**토론 주제:**
- "synchronized와 ReentrantLock의 차이는 무엇인가요?"
- "AtomicInteger가 ConcurrentHashMap보다 빠른 이유는?"
- "BlockingQueue 방식의 장단점은 무엇인가요?"

---

## 📖 학습 순서 추천

### 초급 (Architecture 이해)
1. 레이어드 아키텍처 개념 학습
2. Repository 패턴 이해
3. UseCase 패턴 학습

### 중급 (Domain 설계)
4. 도메인 모델링 (Rich vs Anemic)
5. DTO 설계 전략
6. 테스트 전략 수립

### 고급 (Concurrency)
7. 동시성 제어 방식 비교
8. 통합 테스트 작성

---

## 🚨 Common Pitfalls (자주 하는 실수)

### Architecture
- ❌ Controller에 비즈니스 로직 작성
- ❌ Repository 구현체를 Domain에 위치
- ❌ Domain이 Infrastructure를 직접 의존

### Domain
- ❌ Anemic Domain Model (getter/setter만 존재)
- ❌ Service에 모든 비즈니스 로직 집중
- ❌ Entity 검증 로직 누락

### Concurrency
- ❌ 동시성 제어 없이 쿠폰 발급
- ❌ Thread-unsafe 컬렉션 사용 (HashMap, ArrayList)
- ❌ 동시성 테스트 부재

### Testing
- ❌ 테스트 없이 구현
- ❌ 통합 테스트만 작성 (단위 테스트 누락)
- ❌ 의미 없는 테스트 작성 (커버리지 맞추기용)

---

## 🎓 참고 자료

### 과제 자료
- [PR Template](../../.github/pull_request_template.md)
- [CLAUDE.md](../../CLAUDE.md) - 종합 가이드

### Q&A 세션
- 20251103 항해 플러스 3주차 Q&A (박지수 코치)
- 로이코치님 QnA 세션

### 외부 자료
- [Martin Fowler - Layered Architecture](https://martinfowler.com/bliki/PresentationDomainDataLayering.html)
- [DDD - Eric Evans](https://www.domainlanguage.com/ddd/)
- [Java Concurrency in Practice](https://jcip.net/)

---

## ✅ 학습 완료 체크리스트

### 이론 학습
- [ ] 레이어드 아키텍처 4계층의 역할을 설명할 수 있다
- [ ] UseCase와 DomainService의 차이를 설명할 수 있다
- [ ] Rich Domain Model의 장점을 설명할 수 있다
- [ ] Repository 패턴의 목적을 설명할 수 있다
- [ ] 4가지 동시성 제어 방식의 차이를 설명할 수 있다

### 실전 적용
- [ ] API 명세를 유스케이스로 구현할 수 있다
- [ ] Entity에 비즈니스 로직을 배치할 수 있다
- [ ] Repository 인터페이스와 구현체를 분리할 수 있다
- [ ] 핵심 비즈니스 로직을 테스트할 수 있다
- [ ] Race Condition을 방지할 수 있다

### 코드 리뷰
- [ ] 다른 사람의 코드를 보고 아키텍처를 평가할 수 있다
- [ ] 비즈니스 로직이 잘못 배치된 것을 찾아낼 수 있다
- [ ] 동시성 문제를 발견할 수 있다
- [ ] 테스트 커버리지의 적절성을 판단할 수 있다

---

## 💡 학습 팁

### 효과적인 학습 방법
1. **이론 먼저**: 각 학습 포인트 문서를 순서대로 읽기
2. **코드 작성**: 예시 코드를 직접 작성해보기
3. **비교 분석**: 좋은 예시와 나쁜 예시를 비교하며 이해하기
4. **토론 참여**: 동료와 토론 주제를 가지고 의견 나누기
5. **코드 리뷰**: 다른 사람의 코드를 보고 피드백 주고받기

### 실전 적용 순서
1. Domain Layer부터 시작 (Entity, Repository Interface)
2. Infrastructure Layer (In-Memory Repository)
3. Application Layer (UseCase)
4. Presentation Layer (Controller 리팩토링)
5. Testing (단위 테스트 → 통합 테스트)

---

## 📞 도움 받기

### 질문하기 전에
1. 해당 학습 포인트 문서를 읽어보았나요?
2. CLAUDE.md의 FAQ를 확인해보았나요?
3. 에러 메시지를 구글에서 검색해보았나요?

### 좋은 질문 예시
- ❌ "안 되는데요?"
- ✅ "재고 차감 로직을 Entity에 두었는데, Service가 너무 간단해진 것 같아요. 이게 맞나요?"

### 질문 템플릿
```
[문제 상황]
- 구현하려는 기능:
- 현재 코드 구조:
- 발생한 문제:

[시도한 것]
1. XXX 방식으로 시도했으나 YYY 문제 발생
2. ZZZ 문서를 읽어봤으나 여전히 이해 안 됨

[질문]
- 구체적인 질문 내용
```

---

**이 학습 자료로 3주차 과제를 성공적으로 완수하세요!** 🚀
