# Spring Web 핵심: DispatcherServlet / Filter / Interceptor / AOP / PSA

> 목적: 3주차 체크리스트의 “스프링 웹 요청 처리”와 “Triangle(AOP, DI/IoC, PSA)”를 면접/리뷰에서 1~2분 내 설명할 수 있도록 한 페이지로 정리한다.

---

## 1) Spring Triangle 한 줄 요약

- **DI/IoC**: 객체 생성/연결을 컨테이너(Spring)가 관리해서 결합도를 낮춘다.
- **AOP**: 핵심 로직과 공통 관심사(트랜잭션/로깅/보안 등)를 분리해 적용한다(프록시 기반이 흔함).
- **PSA**: “스프링이 제공하는 추상화”를 통해 기술 교체/조합을 쉽게 만든다(예: `@Transactional`, Spring Cache, Spring MVC).

---

## 2) DispatcherServlet: 요청의 “컨트롤 타워”

Spring MVC의 요청 흐름은 대략 아래처럼 이해하면 충분합니다.

1. 요청이 들어오면 `DispatcherServlet`이 받는다.
2. 어떤 컨트롤러/핸들러가 처리할지 찾는다(`HandlerMapping`).
3. 실제 호출을 수행한다(`HandlerAdapter`).
4. 예외가 나면 예외 처리 체인으로 넘어간다(`HandlerExceptionResolver`).
5. 응답을 만든다(JSON이면 보통 `HttpMessageConverter`가 직렬화).

핵심 포인트
- “컨트롤러가 바로 실행된다”가 아니라, **DispatcherServlet이 전체 파이프라인을 조립**한다.
- 그래서 공통 처리(예외 매핑, 메시지 변환, 인터셉터 등)가 일관되게 동작한다.

---

## 3) Filter vs Interceptor: 어디서 무엇을 해야 하나?

둘 다 “요청 전/후에 끼어드는 장치”지만, 레벨이 다릅니다.

### Filter (Servlet 표준)
- **동작 위치**: Servlet 컨테이너 레벨 (Spring MVC 밖/앞쪽)
- **대상**: 모든 요청(정적 리소스 포함 가능), 서블릿 전체 파이프라인
- **주요 용도**: 보안/인증(일부), CORS, Request/Response 래핑, XSS 방어, 로깅(요청 단위), 인코딩
- **특징**: 스프링 MVC의 핸들러/컨트롤러 정보를 모른다.

### Interceptor (Spring MVC)
- **동작 위치**: DispatcherServlet 내부, 컨트롤러 호출 전후
- **대상**: “핸들러(컨트롤러)” 중심
- **주요 용도**: 인증/인가(컨트롤러 기준), 사용자 컨텍스트 설정, 핸들러 기준 로깅/메트릭, 요청 전처리/후처리
- **특징**: 어떤 핸들러가 호출되는지 알 수 있다(메서드/매핑 정보 등).

### 빠른 선택 기준
- “스프링 MVC에 들어오기 전/서블릿 레벨에서 공통 처리” → Filter
- “컨트롤러 호출 전후, 핸들러 정보를 활용” → Interceptor

---

## 4) AOP: 공통 관심사를 “핵심 로직 밖으로”

대표 사례
- `@Transactional` (트랜잭션 경계)
- 로깅/메트릭 수집
- 분산락 같은 cross-cutting concern

주의(체크리스트에 자주 나오는 함정)
- 스프링 AOP는 보통 **프록시 기반**이라, 같은 클래스 내부 메서드 호출(Self-invocation)에서는 어노테이션이 적용되지 않을 수 있다.
  - 레포 예시(사례/분석): `docs/week6/README.md`

---

## 5) PSA(Portable Service Abstraction): “기술 교체”를 쉽게 만드는 스프링식 추상화

PSA는 “인터페이스 한 겹” 수준이 아니라, 스프링이 제공하는 **프로그래밍 모델(애노테이션/템플릿/스프링 빈 구성)**까지 포함하는 감각입니다.

예시
- `@Transactional`을 쓰면, 내부적으로는 JDBC/JPA 등 구현이 달라도 “트랜잭션”이라는 개념으로 동일하게 다룰 수 있다.
- `@Cacheable`을 쓰면, 캐시 구현체(로컬/Redis 등) 변화에도 코드가 크게 흔들리지 않게 설계할 수 있다.

---

## 6) 체크리스트 답변용 3줄 요약

1. `DispatcherServlet`이 요청을 받아 핸들러 매핑/호출/예외처리/응답 변환까지 파이프라인을 관리한다.
2. Filter는 서블릿 레벨(스프링 밖), Interceptor는 스프링 MVC 레벨(컨트롤러 전후)이라 “할 수 있는 것/보이는 정보”가 다르다.
3. Triangle 관점에서 DI/IoC로 결합도를 낮추고, AOP로 공통 관심사를 분리하며, PSA로 기술을 추상화해 교체 가능한 구조를 만든다.

