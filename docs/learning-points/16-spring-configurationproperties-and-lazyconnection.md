# Spring 설정 바인딩 & 트랜잭션 커넥션: `@ConfigurationProperties`와 `LazyConnectionDataSourceProxy`

> 목적: 체크리스트의 `ConfigurationProperties` 활용과 `LazyConnectionDataSourceProxy`의 의미/효과를 “왜 쓰는지” 중심으로 이해한다.

---

## 1) `@ConfigurationProperties`: 설정을 타입으로 다루기

### 왜 필요한가?
- `application.yml`의 값을 문자열로 여기저기 흩뿌리면 변경에 취약해집니다.
- 스프링은 설정을 **타입 안전하게** 바인딩해서, 컴파일 단계/IDE 단계에서 실수를 줄일 수 있습니다.

### 핵심 개념
- `@ConfigurationProperties(prefix = "coupon.issue")` 같은 방식으로 `coupon.issue.*` 설정을 객체로 매핑합니다.
- 보통 `record` 또는 POJO로 설정 클래스를 만들고, 빈으로 등록합니다.

### 예시(개념)

```java
@ConfigurationProperties(prefix = "coupon.issue")
public record CouponIssueProperties(String publisher) {}
```

```java
@Configuration
@EnableConfigurationProperties(CouponIssueProperties.class)
public class CouponConfig {}
```

### 언제 쓰면 좋은가?
- 도메인/기능별로 설정이 여러 개인 경우(예: Kafka 토픽명, 재시도 횟수, 캐시 TTL 등)
- “설정 스키마”가 문서 역할까지 해야 하는 경우(이름/타입/기본값)

---

## 2) `LazyConnectionDataSourceProxy`: 커넥션을 “진짜 필요할 때” 잡기

### 문제 상황(자주 발생)
스프링 트랜잭션은 일반적으로 “트랜잭션 시작” 시점에 DB 커넥션을 잡을 수 있습니다.  
그런데 아래처럼 “DB를 쓰지 않는 경로”도 트랜잭션으로 묶여 있으면:

- 트랜잭션은 시작됐는데 DB를 안 씀 → **커넥션을 불필요하게 점유**
- 트래픽이 크면 커넥션 풀이 빨리 고갈될 수 있음

### 해결 아이디어
`LazyConnectionDataSourceProxy`는 **실제 JDBC 커넥션 획득을 지연**시킵니다.

- 트랜잭션은 시작하되,
- “실제로 DB를 첫 번째로 접근하는 시점”에 커넥션을 빌림

### 기대 효과
- read-only 트랜잭션이나, 조건 분기로 DB 접근이 없는 경로에서 **커넥션 점유를 줄일 수 있음**
- 전체적으로 커넥션 풀의 pressure를 낮출 수 있음

### 주의
- “커넥션을 덜 잡는다”가 아니라 “필요할 때 잡는다”입니다.
- 이미 DB를 반드시 사용하는 경로라면 체감 효과는 제한적일 수 있습니다.

---

## 3) 이 레포 기준 메모

- 현재 `application.yml`에는 여러 설정이 존재합니다(예: `spring.datasource.hikari.*`, `springdoc.*`, `coupon.issue.publisher` 등).
- 현재 코드에서 `@ConfigurationProperties`를 적극적으로 쓰고 있지는 않습니다. (필요 시 기능별 설정 클래스로 정리 가능)
- `LazyConnectionDataSourceProxy`는 “트랜잭션 + 커넥션 풀” 이슈가 핵심인 시점(동시성/부하)에서 특히 의미가 커집니다.

