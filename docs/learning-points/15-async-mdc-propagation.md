# @Async에서 MDC(TraceId) 전파하기: 왜 필요한가, 어떻게 하는가

> 목적: 8주차 체크리스트의 “Async MDC 설정”을 실무 관점(운영/추적)에서 이해하고, 스프링에서 적용하는 대표 패턴(TaskDecorator)을 정리한다.

---

## 1) MDC가 뭐고 왜 필요한가?

`MDC(Mapped Diagnostic Context)`는 “현재 스레드”에 붙는 Key-Value 컨텍스트입니다.  
로그 패턴에 `%X{traceId}` 같은 값을 넣으면, 같은 요청 흐름의 로그를 쉽게 묶을 수 있습니다.

문제는 `@Async`가 **다른 스레드 풀**에서 실행되기 때문에, 기본적으로는 요청 스레드의 MDC가 **전파되지 않는다**는 점입니다.

증상(운영에서 자주 봄)
- 요청 로그에는 `traceId`가 있는데, 비동기 리스너 로그에는 `traceId`가 비어있다.
- “같은 사건” 로그를 타임라인으로 연결하기가 어려워진다.

---

## 2) 왜 전파가 안 되나?

MDC는 보통 `ThreadLocal` 기반입니다.  
즉, **스레드가 바뀌면 컨텍스트도 같이 바뀐다(= 사라진다)**고 이해하면 됩니다.

---

## 3) 스프링에서 가장 흔한 해결: TaskDecorator

스프링의 `ThreadPoolTaskExecutor`는 `TaskDecorator`를 지원합니다.  
아이디어는 단순합니다.

1. 비동기 작업을 스케줄링하는 시점(원래 스레드)에서 MDC를 복사해 두고
2. 실제 작업을 실행하는 시점(풀 스레드)에서 MDC를 세팅한 다음
3. 작업이 끝나면 MDC를 원복/정리한다

### 예시 코드(개념)

```java
public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (contextMap != null) MDC.setContextMap(contextMap);
                else MDC.clear();
                runnable.run();
            } finally {
                if (previous != null) MDC.setContextMap(previous);
                else MDC.clear();
            }
        };
    }
}
```

그리고 `ThreadPoolTaskExecutor`에 적용합니다.

```java
executor.setTaskDecorator(new MdcTaskDecorator());
```

---

## 4) 이 레포에서의 적용 포인트(현황)

- 비동기 실행 설정: `src/main/java/io/hhplus/ecommerce/config/AsyncConfig.java`
  - 현재는 스레드풀/RejectedPolicy 설정은 되어 있으나, **MDC TaskDecorator는 설정되어 있지 않다.**
- 개선 아이디어(문서에 이미 존재): `docs/week8/IMPROVEMENT_PLAN.md`에 MDC Propagation 예시가 포함되어 있다.

학습 체크리스트 관점에서의 결론
- “왜 전파가 필요한지 / 왜 기본으로 안 되는지 / 스프링에서 어떻게 붙이는지(TaskDecorator)”를 설명할 수 있으면 학습 목표는 충족.
- 실제 운영 품질까지 올리려면 `AsyncConfig`에 TaskDecorator를 적용하고, 로그 패턴에 `traceId`를 포함하는 형태로 연결한다.

---

## 5) 주의사항(실무에서 흔한 함정)

- **스레드 풀은 재사용**된다 → 작업이 끝난 후 MDC를 정리하지 않으면 “다른 요청의 MDC가 섞이는” 사고가 날 수 있다.
- `@Async`가 붙어 있어도 **Self-invocation(내부 호출)**이면 비동기가 동작하지 않을 수 있다(프록시 기반).
- MDC 전파는 “관측/추적” 품질에 큰 영향을 주지만, 그 자체가 성능 병목이 되지 않도록 Key/Value 크기 관리가 필요하다.

