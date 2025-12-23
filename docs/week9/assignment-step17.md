# Step 17: Kafka 기초 학습 및 활용

> **목표**: Kafka의 핵심 개념을 학습하고, Spring Boot 애플리케이션에서 메시지를 발행/소비하는 기본 기능을 구현한다.

---

## 📋 과제 개요

### 학습 목표
1. Kafka 핵심 개념 이해 (Broker, Topic, Partition, Producer, Consumer, Consumer Group)
2. 로컬 환경에 Kafka 설치 및 실행
3. Spring Boot와 Kafka 연동
4. 주문 완료 이벤트를 Kafka로 발행
5. Consumer를 통한 메시지 소비

### 예상 소요 시간
- **최소 (기본 과제)**: 3시간
- **권장 (학습 포함)**: 5-6시간

---

## 🎯 과제 요구사항

### 필수 요구사항 (Pass 조건)

#### 1. Kafka 개념 학습 및 문서 작성 (30%)

**요구사항**
- Kafka의 핵심 개념을 이해하고 문서로 정리
- 최소 포함 내용:
  - Producer와 Consumer
  - Broker와 Cluster
  - Topic과 Partition
  - Offset과 Consumer Group

**산출물**
```
docs/week9/
└── kafka-learning.md (또는 README.md)
    ├── Kafka란 무엇인가?
    ├── 핵심 구성 요소 설명
    ├── 메시지 흐름 설명
    └── Producer, Consumer, Partition 수에 따른 데이터 흐름
```

**평가 기준**
- Kafka의 핵심 개념을 정확히 이해하고 설명
- 구성 요소 간의 관계를 명확히 설명
- 다이어그램 또는 예시 코드 포함

#### 2. 로컬 Kafka 실행 (20%)

**요구사항**
- Docker Compose를 사용하여 Kafka 실행
- CLI로 Topic 생성 및 메시지 송수신 테스트
- 실행 로그 및 테스트 결과 문서화

**체크리스트**
- [ ] `docker-compose.yml` 작성
- [ ] Kafka 컨테이너 실행 (`docker-compose up -d`)
- [ ] Kafka UI 접속 확인 (선택)
- [ ] CLI로 Topic 생성
- [ ] CLI로 메시지 발행
- [ ] CLI로 메시지 소비
- [ ] 실행 로그 캡처 및 문서화

**예시 명령어**
```bash
# Topic 생성
docker exec -it kafka kafka-topics --create \
  --topic test-topic \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1

# Producer
docker exec -it kafka kafka-console-producer \
  --topic test-topic \
  --bootstrap-server localhost:9092

# Consumer
docker exec -it kafka kafka-console-consumer \
  --topic test-topic \
  --from-beginning \
  --bootstrap-server localhost:9092
```

#### 3. Spring Boot와 Kafka 연동 (30%)

**요구사항**
- Spring Kafka 의존성 추가
- Producer와 Consumer Configuration 작성
- 간단한 메시지 발행/소비 예제 구현

**체크리스트**
- [ ] `build.gradle`에 `spring-kafka` 의존성 추가
- [ ] `application.yml`에 Kafka 설정 추가
- [ ] `KafkaProducerConfig` 작성
- [ ] `KafkaConsumerConfig` 작성
- [ ] Producer 클래스 구현
- [ ] Consumer 클래스 구현
- [ ] 테스트 코드 작성

**예시 코드**
```java
// Producer
@Component
public class TestMessageProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;

    public void send(String message) {
        kafkaTemplate.send("test-topic", message);
    }
}

// Consumer
@Component
public class TestMessageConsumer {

    @KafkaListener(topics = "test-topic", groupId = "test-group")
    public void listen(String message) {
        log.info("Received message: {}", message);
    }
}
```

#### 4. 주문 완료 이벤트 Kafka 발행 (20%)

**요구사항**
- 기존 mockAPI 호출을 Kafka 메시지 발행으로 전환
- 트랜잭션 커밋 후 메시지 발행 (AFTER_COMMIT)
- Consumer에서 메시지 수신 및 로그 출력

**시나리오**
```
[Before]
OrderService (주문 생성)
  → @TransactionalEventListener(AFTER_COMMIT)
  → DataPlatformClient.sendOrderData() (HTTP API)

[After]
OrderService (주문 생성)
  → @TransactionalEventListener(AFTER_COMMIT)
  → KafkaProducer.publishOrderCompleted() (Kafka)
  → DataPlatformConsumer.handleOrderCompleted() (Consumer)
```

**체크리스트**
- [ ] `OrderCompletedMessage` DTO 작성
- [ ] `OrderEventProducer` 구현
- [ ] `OrderEventConsumer` 구현
- [ ] `@TransactionalEventListener(AFTER_COMMIT)` 사용
- [ ] 테스트: 주문 생성 → 메시지 발행 → 메시지 소비 확인

---

## 📝 구현 가이드

### 1단계: Kafka 개념 학습

**권장 학습 자료**
- [kafka-basics.md](./kafka-basics.md)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Confluent Kafka 101](https://developer.confluent.io/learn-kafka/)

**학습 체크리스트**
- [ ] Kafka의 정의와 특징 이해
- [ ] Producer와 Consumer 역할 이해
- [ ] Topic과 Partition의 관계 이해
- [ ] Consumer Group과 Offset 개념 이해
- [ ] Replication과 고가용성 이해

**문서 작성 팁**
- 개념을 단순히 나열하지 말고, **왜 필요한지** 설명
- 다이어그램 활용 (Mermaid 또는 이미지)
- 실습 예제 포함
- 실무에서 어떻게 활용되는지 설명

### 2단계: Docker로 Kafka 실행

**docker-compose.yml 작성**

[kafka-setup.md](./kafka-setup.md)의 Docker Compose 설정 참고

**실행 및 확인**
```bash
# 1. Kafka 실행
docker-compose up -d

# 2. 로그 확인
docker-compose logs -f kafka

# 3. 상태 확인
docker-compose ps

# 4. Kafka UI 접속 (선택)
open http://localhost:8090
```

**CLI 테스트**
```bash
# Topic 생성
docker exec -it kafka kafka-topics --create \
  --topic order-completed \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1

# Topic 목록 확인
docker exec -it kafka kafka-topics --list \
  --bootstrap-server localhost:9092

# Producer 테스트
docker exec -it kafka kafka-console-producer \
  --topic order-completed \
  --bootstrap-server localhost:9092

# Consumer 테스트 (새 터미널)
docker exec -it kafka kafka-console-consumer \
  --topic order-completed \
  --from-beginning \
  --bootstrap-server localhost:9092
```

**문서화 예시**
```markdown
## Kafka 실행 로그

### Kafka 컨테이너 상태
```
NAME         IMAGE                                PORTS
kafka        confluentinc/cp-kafka:7.5.3         0.0.0.0:9092->9092/tcp
zookeeper    confluentinc/cp-zookeeper:7.5.3     0.0.0.0:2181->2181/tcp
```

### Topic 생성 결과
```
Created topic order-completed.
```

### 메시지 송수신 테스트
- Producer: "Hello Kafka!"
- Consumer: "Hello Kafka!" (정상 수신)
```

### 3단계: Spring Boot 연동

**의존성 추가**
```gradle
dependencies {
    implementation 'org.springframework.kafka:spring-kafka'
}
```

**application.yml 설정**
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      group-id: ecommerce-service
      auto-offset-reset: earliest
      enable-auto-commit: false
      properties:
        spring.json.trusted.packages: "*"
    listener:
      ack-mode: manual
```

**Configuration 작성**

[kafka-spring-integration.md](./kafka-spring-integration.md)의 Configuration 참고

**Producer 구현**
```java
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCompleted(OrderCompletedMessage message) {
        kafkaTemplate.send("order-completed", message.getOrderId(), message)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Message sent successfully: offset={}",
                            result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to send message", ex);
                }
            });
    }
}
```

**Consumer 구현**
```java
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    @KafkaListener(topics = "order-completed", groupId = "data-platform")
    public void handleOrderCompleted(
            OrderCompletedMessage message,
            Acknowledgment ack
    ) {
        log.info("Received order completed event: orderId={}", message.getOrderId());

        try {
            // 비즈니스 로직 처리
            processOrderData(message);

            // 처리 성공 시 ACK
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process message", e);
            // ACK 하지 않음 → 재처리
        }
    }
}
```

### 4단계: 주문 완료 이벤트 전환

**메시지 DTO 작성**
```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCompletedMessage {
    private String orderId;
    private String userId;
    private Long totalAmount;
    private LocalDateTime completedAt;

    public static OrderCompletedMessage from(Order order) {
        return OrderCompletedMessage.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .completedAt(LocalDateTime.now())
                .build();
    }
}
```

**Application Event Listener 수정**
```java
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderEventProducer orderEventProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        log.info("Publishing order completed event to Kafka: orderId={}",
                event.getOrder().getId());

        OrderCompletedMessage message = OrderCompletedMessage.from(event.getOrder());
        orderEventProducer.publishOrderCompleted(message);
    }
}
```

**Consumer 구현**
```java
@Component
@RequiredArgsConstructor
public class DataPlatformConsumer {

    private final DataPlatformClient dataPlatformClient;

    @KafkaListener(topics = "order-completed", groupId = "data-platform")
    public void handleOrderCompleted(
            OrderCompletedMessage message,
            Acknowledgment ack
    ) {
        log.info("Sending order data to data platform: orderId={}", message.getOrderId());

        try {
            dataPlatformClient.sendOrderData(message);
            ack.acknowledge();
            log.info("Order data sent successfully: orderId={}", message.getOrderId());
        } catch (Exception e) {
            log.error("Failed to send order data: orderId={}", message.getOrderId(), e);
        }
    }
}
```

---

## 🧪 테스트

### 통합 테스트

```java
@SpringBootTest
@Testcontainers
class OrderKafkaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.3")
    );

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private OrderUseCase orderUseCase;

    private CountDownLatch latch = new CountDownLatch(1);
    private OrderCompletedMessage receivedMessage;

    @Test
    void 주문_완료시_Kafka_메시지_발행() throws InterruptedException {
        // Given
        CreateOrderCommand command = new CreateOrderCommand("user-123", ...);

        // When
        Order order = orderUseCase.createOrder(command);

        // Then
        boolean messageReceived = latch.await(10, TimeUnit.SECONDS);
        assertThat(messageReceived).isTrue();
        assertThat(receivedMessage.getOrderId()).isEqualTo(order.getId());
    }

    @KafkaListener(topics = "order-completed", groupId = "test-group")
    public void receive(OrderCompletedMessage message) {
        receivedMessage = message;
        latch.countDown();
    }
}
```

---

## 📊 평가 기준

### Pass 조건 (모두 충족 필요)

| 항목 | 배점 | 기준 |
|------|------|------|
| **Kafka 개념 이해** | 30% | Producer, Consumer, Partition, Offset 등 핵심 개념 정확히 설명 |
| **로컬 Kafka 실행** | 20% | Docker로 Kafka 실행, CLI로 메시지 송수신 성공 |
| **Spring 연동** | 30% | 애플리케이션에서 메시지 발행/소비 성공 |
| **트랜잭션 연동** | 20% | AFTER_COMMIT 후 메시지 발행 검증 |

### Fail 사유
- Kafka 핵심 개념을 잘못 이해
- Docker로 Kafka 실행 실패
- 메시지 발행/소비 실패
- 트랜잭션 커밋 전 메시지 발행

---

## 💡 팁과 주의사항

### 자주 하는 실수

#### 1. 트랜잭션 커밋 전 메시지 발행
```java
// ❌ Bad
@Transactional
public void createOrder(Order order) {
    orderRepository.save(order);
    kafkaProducer.send("order-completed", order);  // 커밋 전 발행
    // 커밋 실패 시 메시지만 발행됨
}

// ✅ Good
@Transactional
public void createOrder(Order order) {
    orderRepository.save(order);
    eventPublisher.publishEvent(new OrderCompletedEvent(order));
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOrderCompleted(OrderCompletedEvent event) {
    kafkaProducer.send("order-completed", event.getOrder());
}
```

#### 2. Auto Commit 사용
```yaml
# ❌ Bad (메시지 유실 가능)
spring:
  kafka:
    consumer:
      enable-auto-commit: true

# ✅ Good
spring:
  kafka:
    consumer:
      enable-auto-commit: false
    listener:
      ack-mode: manual
```

#### 3. 예외 처리 없음
```java
// ❌ Bad
@KafkaListener(topics = "order-completed")
public void handle(OrderMessage message) {
    processOrder(message);  // 예외 발생 시?
}

// ✅ Good
@KafkaListener(topics = "order-completed")
public void handle(OrderMessage message, Acknowledgment ack) {
    try {
        processOrder(message);
        ack.acknowledge();
    } catch (Exception e) {
        log.error("Failed to process message", e);
        // ACK 하지 않음 → 재처리
    }
}
```

### 디버깅 체크리스트
- [ ] Kafka 컨테이너 정상 실행 확인
- [ ] Topic 생성 확인
- [ ] Producer 로그에서 메시지 발행 성공 확인
- [ ] Consumer 로그에서 메시지 수신 확인
- [ ] Offset 증가 확인
- [ ] Consumer Group Lag 확인

```bash
# Consumer Group 상태 확인
docker exec -it kafka kafka-consumer-groups --describe \
  --group ecommerce-service \
  --bootstrap-server localhost:9092
```

---

## 📚 참고 자료

### 필수 읽기
- [kafka-basics.md](./kafka-basics.md)
- [kafka-setup.md](./kafka-setup.md)
- [kafka-spring-integration.md](./kafka-spring-integration.md)

### 추가 학습
- [Spring for Apache Kafka](https://spring.io/projects/spring-kafka)
- [Kafka CLI Reference](https://kafka.apache.org/documentation/#cli)
- [Testcontainers Kafka Module](https://www.testcontainers.org/modules/kafka/)

---

## ✅ 제출 체크리스트

### 코드
- [ ] `docker-compose.yml` 작성
- [ ] Kafka Configuration (`KafkaProducerConfig`, `KafkaConsumerConfig`)
- [ ] `OrderCompletedMessage` DTO
- [ ] `OrderEventProducer` 구현
- [ ] `OrderEventConsumer` 구현
- [ ] `@TransactionalEventListener(AFTER_COMMIT)` 사용
- [ ] 테스트 코드

### 문서
- [ ] Kafka 개념 학습 문서
- [ ] Kafka 실행 로그 캡처
- [ ] CLI 테스트 결과
- [ ] 메시지 발행/소비 로그

### 테스트
- [ ] 주문 생성 → 메시지 발행 확인
- [ ] Consumer에서 메시지 수신 확인
- [ ] 통합 테스트 통과

---

## 🚀 다음 단계

Step 17을 완료하셨다면:
- [ ] [Step 18: Kafka를 활용한 비즈니스 프로세스 개선](./assignment-step18.md)

---

**Last Updated**: 2024-12-18
