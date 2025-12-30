# ThreadLocal / MDC / 자바 동시성 도구 빠른 정리(면접/실무용)

> 목적: 3주차 체크리스트의 “ThreadLocal/MDC/자바 동시성 처리”를 한 페이지로 요약한다.

---

## 1) ThreadLocal: “스레드별 저장소”

### 개념
`ThreadLocal`은 같은 코드라도 **스레드마다 다른 값**을 보관할 수 있는 저장소입니다.

### 어디서 유용한가?
- 요청 단위 컨텍스트(예: traceId, auth info) 보관
- 트랜잭션 컨텍스트(스프링이 내부적으로 활용)

### 주의(실무에서 중요)
- 스레드 풀 환경에서는 스레드가 재사용됩니다.  
  값을 정리하지 않으면 **다른 요청에 값이 섞이는 사고**가 납니다.

---

## 2) MDC: 로그 컨텍스트(ThreadLocal 기반)

### 개념
`MDC(Mapped Diagnostic Context)`는 로그에 “요청 식별자(traceId 등)”를 붙이기 위해 쓰는 컨텍스트입니다.

### 왜 문제가 생기나?
- MDC도 보통 ThreadLocal 기반이라 **스레드가 바뀌면 컨텍스트가 끊깁니다.**
- 특히 `@Async`/ExecutorService 사용 시 전파가 필요합니다.

관련(전파 패턴)
- `docs/learning-points/15-async-mdc-propagation.md`

---

## 3) 동시성 제어 vs 동시성 처리(구분)

- **동시성 처리(Concurrency Handling)**: 스레드를 만들어 일을 “동시에 돌리는 방법”
  - `Thread`, `Runnable`, `ExecutorService`, `Future`, `CompletableFuture`
- **동시성 제어(Concurrency Control)**: 동시에 접근할 때 “정합성”을 지키는 방법
  - `synchronized`, `ReentrantLock`, CAS/Atomic, 동시 컬렉션, DB Lock/Redis Lock 등

---

## 4) 자바 동시성 처리 도구(대표만)

### ExecutorService
- 스레드 풀로 작업을 제출/실행하는 표준 방식
- 직접 Thread를 만드는 것보다 운영(제어/종료/자원 관리)이 낫다

### Future
- 비동기 결과를 “나중에” 받는 핸들
- `get()`은 블로킹이라, 무분별하게 쓰면 병목이 될 수 있다

### CompletableFuture
- 비동기 파이프라인(thenApply/thenCompose 등)로 합성하기 좋다
- 다만 디버깅/스레드풀 선택/예외 처리 규칙을 이해해야 한다

---

## 5) 자바 동시성 제어 도구(대표만)

### synchronized
- 가장 단순하고 안전한 기본기(모니터 락)
- 락 범위가 커지면 병목이 되기 쉬움

### ReentrantLock
- 타임아웃/공정성/조건 변수 등 더 세밀한 제어가 필요할 때 선택

### Atomic/CAS
- 단순 카운터/플래그 같은 경우 락 없이 원자적 업데이트가 가능
- 복잡한 복합 연산에는 주의(원자성 범위 한계)

### ConcurrentHashMap 등 동시 컬렉션
- 읽기/쓰기 경쟁이 많은 구조에서 기본 컬렉션보다 안전하고 성능이 좋다

---

## 6) 체크리스트 답변용 요약(3줄)

1. ThreadLocal은 스레드별 저장소이고, 스레드풀에서는 값 정리가 안 되면 요청 간 오염이 생긴다.  
2. MDC는 로그 컨텍스트인데 ThreadLocal 기반이라 @Async에서 전파가 끊겨 TaskDecorator 같은 패턴이 필요하다.  
3. 동시성 처리는 Executor/Future/CompletableFuture, 동시성 제어는 synchronized/Lock/Atomic/동시 컬렉션처럼 “정합성”을 지키는 도구다.

