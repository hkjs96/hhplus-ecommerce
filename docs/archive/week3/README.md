# Week 3 Archive - InMemory Implementation

이 디렉토리는 **Week 3 (Step 5-6)** 과제의 InMemory Repository 기반 구현 관련 문서를 보관합니다.

## 📌 아카이브 이유

Week 4부터는 **Spring Data JPA**를 사용하여 실제 데이터베이스와 연동하므로, Week 3의 InMemory Repository 구현은 더 이상 사용되지 않습니다.

하지만 다음과 같은 학습 가치가 있어 문서를 보관합니다:
- Layered Architecture의 기본 개념 이해
- Repository 패턴의 추상화 개념
- 동시성 제어 패턴 (synchronized, ReentrantLock, CAS)
- 인메모리 구현을 통한 테스트 격리 전략

## 📁 디렉토리 구조

```
week3/
├── README.md (이 파일)
├── commands/
│   ├── week3-guide.md          # Step 5-6 전체 구현 가이드
│   └── week3-faq.md            # Step 5-6 자주 묻는 질문
└── learning-points/
    ├── 02-create-order-logic.md
    ├── 03-cart-item-design.md
    ├── 04-order-item-relationship.md
    ├── 05-coupon-validation-layer.md
    ├── 06-payment-validation-flow.md
    ├── 07-price-calculation.md
    ├── 08-repository-pattern.md
    ├── 09-concurrency-control-fix.md
    └── 10-test-isolation-strategy.md
```

## ✅ Week 3 주요 내용

### 1. Layered Architecture 구현
- **Domain Layer**: 순수 Java 클래스 (JPA 어노테이션 없음)
- **Application Layer**: UseCase 패턴
- **Infrastructure Layer**: InMemory Repository 구현

### 2. InMemory Repository 특징
- `ConcurrentHashMap` 기반 데이터 저장
- `AtomicLong`으로 ID 자동 생성
- Thread-safe 동시성 제어

### 3. 동시성 제어 패턴
- **synchronized**: 메서드 레벨 잠금
- **ReentrantLock**: 명시적 잠금 제어
- **AtomicInteger**: CAS(Compare-And-Swap) 기반 원자적 연산
- **BlockingQueue**: 생산자-소비자 패턴

### 4. 테스트 전략
- **단위 테스트**: Mockito를 활용한 레이어별 격리 테스트
- **통합 테스트**: InMemory Repository로 실제 DB 없이 통합 테스트
- **동시성 테스트**: ExecutorService로 멀티스레드 환경 시뮬레이션

## 🔗 현재 구현 (Week 4)

Week 4 이후의 최신 구현은 다음을 참조하세요:
- `/docs/week4/` - JPA 기반 구현 가이드
- `/.claude/commands/architecture.md` - 현재 아키텍처 설명
- `/.claude/commands/testing.md` - 현재 테스트 전략

## 📚 참고 자료

Week 3 과제 요구사항:
- Layered Architecture 구현 (Domain, Application, Infrastructure)
- InMemory Repository 패턴
- 동시성 제어 (재고 감소, 쿠폰 발급)
- 통합 테스트 작성
- 테스트 커버리지 70% 이상

---

**보관 날짜**: 2025-11-18
**현재 Phase**: Week 4 - Database Integration
