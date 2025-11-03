# 애플리케이션 레벨 가용성 패턴

## 개요

> **가용성 보장**: 외부/내부 일부 컴포넌트가 느리거나 실패해도, **핵심 유스케이스가 중단되지 않도록** 애플리케이션 레벨에서 설계·구현·운영하는 것

---

## 🎯 핵심 원칙

### 1. Fail Fast, Recover Fast
- 실패를 빨리 감지하고 빠르게 복구
- 전체 시스템을 느리게 만들지 않음

### 2. Graceful Degradation
- 일부 기능 실패 시 전체 서비스 중단 X
- 축소된 기능이라도 서비스 제공

### 3. Isolation (격리)
- 한 컴포넌트의 장애가 다른 컴포넌트로 전파되지 않도록

---

## 🔧 적용 패턴

## 1. Timeout ⏱️

### 개념
모든 외부 호출은 **반드시 Timeout 설정**하여 무한 대기를 방지합니다.

### 설정 예시

```java
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory factory =
            new HttpComponentsClientHttpRequestFactory();

        factory.setConnectTimeout(2000);    // 연결 타임아웃: 2초
        factory.setReadTimeout(3000);       // 읽기 타임아웃: 3초

        return new RestTemplate(factory);
    }
}
```

### 적용 대상
- ✅ 외부 데이터 플랫폼 API
- ✅ 외부 배송 조회 API (확장 기능)
- ✅ 외부 알림 서비스 (확장 기능)

---

## 2. Retry 🔄

### 개념
일시적인 네트워크 오류나 외부 서비스의 순간적인 장애에 대해 **자동으로 재시도**합니다.

### 구현 방법

**Spring Retry 사용**:
```java
@Service
public class ExternalService {

    @Retryable(
        value = {RestClientException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void callExternalApi() {
        restTemplate.postForObject("https://api.example.com", data, Void.class);
    }

    @Recover
    public void recover(RestClientException ex) {
        log.error("최대 재시도 횟수 초과", ex);
        // Fallback 로직 실행
    }
}
```

**application.yml 설정**:
```yaml
spring:
  retry:
    max-attempts: 3
    backoff:
      initial-interval: 1000ms
      multiplier: 2
      max-interval: 30000ms
```

### 재시도 전략

| 시도 | 대기 시간 | 누적 시간 |
|------|---------|---------|
| 1차  | 0초     | 0초     |
| 2차  | 1초     | 1초     |
| 3차  | 2초     | 3초     |
| 4차  | 4초     | 7초     |

### 재시도하면 안 되는 경우
- ❌ 400 Bad Request (잘못된 요청)
- ❌ 401 Unauthorized (인증 실패)
- ❌ 404 Not Found (리소스 없음)
- ✅ 500 Internal Server Error (서버 오류)
- ✅ 503 Service Unavailable (일시적 장애)
- ✅ Timeout Exception

---

## 3. Fallback 🛡️

### 개념
주 기능 실패 시 **대체 동작**을 실행하여 사용자에게 최소한의 서비스를 제공합니다.

### 적용 예시

#### 3.1 외부 데이터 플랫폼 전송 실패
```java
@Service
public class OrderService {

    @Async
    public void sendToDataPlatform(Order order) {
        try {
            dataPlatformClient.send(order);
        } catch (Exception ex) {
            log.warn("외부 전송 실패. 재시도 큐에 저장", ex);
            // Fallback: Outbox 패턴으로 저장
            outboxRepository.save(new OutboxMessage(order));
        }
    }
}
```

#### 3.2 인기 상품 조회 실패
```java
@Service
public class ProductService {

    public List<PopularProductDTO> getPopularProducts() {
        try {
            return productRepository.findPopularProducts();
        } catch (Exception ex) {
            log.error("인기 상품 조회 실패", ex);
            // Fallback: 빈 배열 반환
            return Collections.emptyList();
        }
    }
}
```

### Fallback 전략

| 실패 케이스 | Fallback 동작 |
|-----------|-------------|
| 외부 데이터 플랫폼 전송 실패 | 재시도 큐에 저장 |
| 인기 상품 조회 실패 | 빈 배열 반환 |
| 알림 전송 실패 | 로그 기록 (비핵심 기능) |

---

## 4. Async Processing ⚡

### 개념
**비핵심 작업은 비동기로** 처리하여 주 프로세스를 블로킹하지 않습니다.

### 설정

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "externalApiExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("external-api-");
        executor.initialize();
        return executor;
    }
}
```

### 적용 예시

```java
@Service
public class DataPlatformClient {

    @Async("externalApiExecutor")
    public void sendOrderData(Order order) {
        try {
            restTemplate.postForObject(
                dataplatformUrl,
                OrderDataRequest.from(order),
                Void.class
            );
            log.info("외부 데이터 전송 성공: orderId={}", order.getId());
        } catch (Exception ex) {
            log.error("외부 데이터 전송 실패: orderId={}", order.getId(), ex);
            // Fallback: 재시도 큐에 저장
            outboxService.save(order);
        }
    }
}
```

### 적용 대상
- ✅ 외부 데이터 플랫폼 전송
- ✅ 알림 발송 (이메일, SMS)
- ✅ 로그 집계

### 주의사항
- ❌ 주문 생성은 비동기로 하면 안 됨 (결과를 즉시 반환해야 함)
- ❌ 결제 처리는 비동기로 하면 안 됨 (트랜잭션 관리 필요)
- ✅ 외부 API 호출만 비동기로 처리

---

## 📋 적용 체크리스트

### 기본 (필수)

- [ ] **Timeout**: 모든 외부 API 호출에 3초 Timeout 설정
- [ ] **Retry**: 외부 API 실패 시 최대 3회 재시도 (Exponential Backoff)
- [ ] **Fallback**: 외부 API 실패 시 재시도 큐에 저장 (Outbox 패턴)
- [ ] **Async**: 외부 데이터 플랫폼 전송을 비동기로 처리

### 확장 (선택)

- [ ] 알림 전송 비동기 처리
- [ ] 인기 상품 조회 Fallback (빈 배열)
- [ ] 배송 조회 API Fallback (마지막 상태 반환)

---

## 🎯 이커머스 시스템 적용 시나리오

### 시나리오 1: 주문 완료 후 외부 데이터 전송

```
주문 완료 (Order 생성)
  ↓
결제 처리 (Payment 완료)
  ↓
재고 차감 (Stock 업데이트)
  ↓
주문 완료 응답 반환 (사용자에게)
  ↓
【비동기】외부 데이터 플랫폼 전송 시작
  ├─ Timeout: 3초
  ├─ Retry: 최대 3회
  └─ Fallback: 실패 시 Outbox 저장
```

**핵심**: 외부 전송 실패가 주문 완료를 막지 않음 ✅

### 시나리오 2: 인기 상품 조회

```
사용자 요청: GET /api/products/top
  ↓
MySQL 집계 쿼리 실행
  ├─ 성공 → Top 5 상품 반환
  └─ 실패 → Fallback: 빈 배열 반환
```

**핵심**: 조회 실패 시에도 사용자에게 에러 없이 응답 ✅

---

## 🧪 테스트 시나리오

### Timeout 테스트
```java
@Test
void shouldTimeoutAfter3Seconds() {
    // Given: 외부 API가 5초 걸림
    mockServer.expect(requestTo(apiUrl))
        .andRespond(withSuccess().after(5, TimeUnit.SECONDS));

    // When: API 호출
    assertThrows(ResourceAccessException.class, () -> {
        client.callApi();
    });

    // Then: 3초에 Timeout
}
```

### Retry 테스트
```java
@Test
void shouldRetryThreeTimes() {
    // Given: 처음 2번은 실패, 3번째 성공
    mockServer.expect(times(2), requestTo(apiUrl))
        .andRespond(withServerError());
    mockServer.expect(once(), requestTo(apiUrl))
        .andRespond(withSuccess());

    // When: API 호출
    client.callApi();

    // Then: 총 3번 호출됨
    mockServer.verify();
}
```

### Fallback 테스트
```java
@Test
void shouldSaveToOutboxWhenFailed() {
    // Given: 외부 API 실패
    mockServer.expect(requestTo(apiUrl))
        .andRespond(withServerError());

    // When: 데이터 전송
    service.sendData(order);

    // Then: Outbox에 저장됨
    verify(outboxRepository).save(any(OutboxMessage.class));
}
```

---

## 📊 모니터링

### 체크 포인트

1. **Timeout 발생률**
   - 외부 API별 Timeout 빈도 모니터링
   - 3% 이상 시 알림

2. **Retry 성공률**
   - 재시도 후 성공률 추적
   - 재시도 없이 성공: 95%+
   - 1회 재시도 성공: 3%
   - 2~3회 재시도 성공: 1%
   - 모두 실패: 1% 미만

3. **Fallback 실행 빈도**
   - Outbox 큐 사이즈 모니터링
   - 100건 이상 누적 시 알림

4. **Async 큐 상태**
   - 스레드 풀 사용률
   - 큐 대기 시간

---

## 참고 자료

- [Spring Retry 공식 문서](https://docs.spring.io/spring-retry/docs/current/reference/html5/)
- [RestTemplate Timeout 설정](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [Spring @Async](https://spring.io/guides/gs/async-method/)
- [Microservices Patterns](https://microservices.io/patterns/index.html)
