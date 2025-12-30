# Spring 트랜잭션 동작/전파(Propagation) 핵심 정리 + 흔한 함정

> 목적: “트랜잭션은 그냥 @Transactional 붙이면 끝”이 아니라, 프록시/전파/rollback 기준을 이해하고 설명할 수 있게 한다.

---

## 1) `@Transactional`은 무엇을 보장하나?

`@Transactional`은 “하나의 메서드 실행”을 트랜잭션 경계로 만들고,
- 커밋/롤백
- 트랜잭션 동기화(예: 커넥션 바인딩)
를 스프링이 관리하도록 합니다.

핵심은 보통 **프록시 기반**으로 동작한다는 점입니다.

---

## 2) 프록시 기반 동작과 Self-invocation 함정

아래처럼 같은 클래스 내부에서 `this.someTxMethod()`로 호출하면,
프록시를 우회해서 `@Transactional`이 적용되지 않을 수 있습니다.

즉, “어노테이션이 붙어 있어도 동작하지 않는” 케이스가 생깁니다.

레포에서 같은 종류의 함정(AOP)이 정리된 예:
- `docs/week6/README.md` (AOP Self-invocation 이슈)

---

## 3) 전파(Propagation): 이미 트랜잭션이 있을 때 어떻게 할 것인가?

전파는 “부모 트랜잭션이 있을 때 자식이 어떤 트랜잭션으로 실행되는가”를 정합니다.

자주 쓰는 것만 최소로:

| 옵션 | 의미(요약) | 언제 쓰나 |
|------|------------|----------|
| `REQUIRED` (기본) | 있으면 합류, 없으면 생성 | 대부분의 서비스 로직 |
| `REQUIRES_NEW` | 항상 새로 만들고 기존은 잠시 중단 | 로그/감사/보상처럼 독립 커밋이 필요할 때 |
| `SUPPORTS` | 있으면 합류, 없으면 트랜잭션 없이 실행 | 트랜잭션이 “있으면 좋고 없어도 되는” 조회성 로직 |

---

## 4) rollback 기준(기본 규칙)

기본적으로 스프링은
- `RuntimeException`/`Error` → 롤백
- `Checked Exception` → 커밋
이 기본입니다.

그래서 “체크 예외인데 롤백되어야 하는” 케이스는 `rollbackFor`를 의식적으로 설계해야 합니다.

---

## 5) readOnly / isolation은 “성능/정합성 트레이드오프”

- `readOnly = true`는 “쓰기 금지”가 아니라, **최적화 힌트**(JPA flush 전략 등)로 쓰이는 경우가 많습니다.
- `isolation`은 정합성과 성능의 트레이드오프이며, DB 특성(MySQL MVCC 등)과 함께 봐야 합니다.

관련(격리 수준/MVCC) 정리:
- `docs/week5/TRANSACTION_FUNDAMENTALS.md`

---

## 6) 체크리스트 답변용 요약(3줄)

1. `@Transactional`은 보통 프록시 기반이라 내부 호출(Self-invocation)에서 적용이 빠질 수 있다.  
2. 전파(propagation)는 “이미 트랜잭션이 있을 때 합류/새로 만들기” 같은 규칙이며, 기본은 `REQUIRED`다.  
3. 롤백 기준(런타임 예외 중심), readOnly/isolation은 트레이드오프라 의식적으로 선택해야 한다.

