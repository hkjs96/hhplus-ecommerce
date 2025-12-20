# Spring Boot와 Kafka 연동

> **목표**: Spring Boot 애플리케이션에서 Kafka Producer/Consumer를 구현하고, 트랜잭션과 연동하는 방법을 익힌다.

---

## 📋 목차

1. [의존성 설정](#의존성-설정)
2. [Kafka Configuration](#kafka-configuration)
3. [Producer 구현](#producer-구현)
4. [Consumer 구현](#consumer-구현)
5. [트랜잭션 연동](#트랜잭션-연동)
6. [에러 처리](#에러-처리)
7. [테스트](#테스트)

---

## 의존성 설정

### build.gradle

```gradle
dependencies {
    // Spring Kafka
    implementation 'org.springframework.kafka:spring-kafka'

    // JSON 직렬화 (선택)
    implementation 'com.fasterxml.jackson.core:jackson-databind'

    // 테스트
    testImplementation 'org.springframework.kafka:spring-kafka-test'
    testImplementation 'org.testcontainers:kafka'
}
```

### application.yml

```yaml
spring:
  kafka:
    # 공통 설정
    bootstrap-servers: localhost:9092

    # Producer 설정
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all  # 모든 Replica 확인
      retries: 3  # 실패 시 재시도
      properties:
        linger.ms: 10  # 배치 대기 시간
        batch.size: 16384  # 배치 크기

    # Consumer 설정
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      group-id: ecommerce-service
      auto-offset-reset: earliest  # Offset 없을 때 처음부터
      enable-auto-commit: false  # 수동 Commit
      properties:
        spring.json.trusted.packages: "*"  # 역직렬화 허용 패키지

    # Listener 설정
    listener:
      ack-mode: manual  # 수동 ACK
      concurrency: 3  # 동시 처리 스레드 수

# 로깅
logging:
  level:
    org.apache.kafka: INFO
    org.springframework.kafka: DEBUG
```

---

## Kafka Configuration

### KafkaProducerConfig.java

```java
package io.hhplus.ecommerce.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();

        // Kafka 서버 주소
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Serializer 설정
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // ACK 설정 (all = 모든 Replica 확인)
        config.put(ProducerConfig.ACKS_CONFIG, "all");

        // 재시도 설정
        config.put(ProducerConfig.RETRIES_CONFIG, 3);

        // Idempotence (멱등성 보장)
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // 배치 설정
        config.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

### KafkaConsumerConfig.java

```java
package io.hhplus.ecommerce.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();

        // Kafka 서버 주소
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Consumer Group ID
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        // Deserializer 설정
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Offset Reset 정책
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Auto Commit 비활성화 (수동 Commit)
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // JSON Deserializer 설정
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Object.class);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // 수동 ACK 모드
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // 동시 처리 스레드 수
        factory.setConcurrency(3);

        // 배치 처리 (선택)
        // factory.setBatchListener(true);

        return factory;
    }
}
```

### KafkaTopics.java (Topic 상수 관리)

```java
package io.hhplus.ecommerce.config;

public final class KafkaTopics {

    private KafkaTopics() {}

    // 주문 관련
    public static final String ORDER_COMPLETED = "order-completed";
    public static final String ORDER_CANCELLED = "order-cancelled";

    // 결제 관련
    public static final String PAYMENT_COMPLETED = "payment-completed";

    // 쿠폰 관련
    public static final String COUPON_ISSUED = "coupon-issued";

    // 상품 관련
    public static final String PRODUCT_STOCK_CHANGED = "product-stock-changed";

    // 알림 관련
    public static final String NOTIFICATION_REQUEST = "notification-request";
}
```

---

## Producer 구현

### 1. 메시지 DTO

```java
package io.hhplus.ecommerce.infrastructure.kafka.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

### 2. Producer 구현

```java
package io.hhplus.ecommerce.infrastructure.kafka.producer;

import io.hhplus.ecommerce.config.KafkaTopics;
import io.hhplus.ecommerce.infrastructure.kafka.message.OrderCompletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCompleted(OrderCompletedMessage message) {
        String topic = KafkaTopics.ORDER_COMPLETED;
        String key = message.getOrderId();  // 주문 ID를 키로 사용

        log.info("Publishing order completed event: orderId={}", key);

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, key, message);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Message sent successfully: topic={}, partition={}, offset={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send message: topic={}, key={}", topic, key, ex);
            }
        });
    }

    // 동기 방식 (테스트 또는 중요한 메시지)
    public void publishOrderCompletedSync(OrderCompletedMessage message) {
        String topic = KafkaTopics.ORDER_COMPLETED;
        String key = message.getOrderId();

        try {
            SendResult<String, Object> result = kafkaTemplate.send(topic, key, message).get();
            log.info("Message sent successfully: topic={}, offset={}",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().offset());
        } catch (Exception e) {
            log.error("Failed to send message synchronously", e);
            throw new RuntimeException("Kafka message send failed", e);
        }
    }
}
```

---

## Consumer 구현

### 1. Consumer 구현

```java
package io.hhplus.ecommerce.infrastructure.kafka.consumer;

import io.hhplus.ecommerce.config.KafkaTopics;
import io.hhplus.ecommerce.infrastructure.kafka.message.OrderCompletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final DataPlatformClient dataPlatformClient;

    @KafkaListener(
            topics = KafkaTopics.ORDER_COMPLETED,
            groupId = "data-platform",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCompleted(
            @Payload OrderCompletedMessage message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack
    ) {
        log.info("Received order completed event: orderId={}, partition={}, offset={}",
                message.getOrderId(), partition, offset);

        try {
            // 비즈니스 로직 처리
            dataPlatformClient.sendOrderData(message);

            // 처리 성공 시 ACK
            ack.acknowledge();

            log.info("Order data sent to data platform: orderId={}", message.getOrderId());
        } catch (Exception e) {
            log.error("Failed to process order completed event: orderId={}",
                    message.getOrderId(), e);
            // ACK 하지 않음 → 재처리됨
        }
    }
}
```

### 2. 배치 Consumer (대량 처리)

```java
package io.hhplus.ecommerce.infrastructure.kafka.consumer;

import io.hhplus.ecommerce.config.KafkaTopics;
import io.hhplus.ecommerce.infrastructure.kafka.message.OrderCompletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBatchConsumer {

    private final OrderService orderService;

    @KafkaListener(
            topics = KafkaTopics.ORDER_COMPLETED,
            groupId = "batch-processor",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void handleBatch(
            List<OrderCompletedMessage> messages,
            Acknowledgment ack
    ) {
        log.info("Received batch of {} orders", messages.size());

        try {
            // 배치 처리
            orderService.processBatch(messages);

            // 전체 배치 성공 시 ACK
            ack.acknowledge();

            log.info("Batch processed successfully: count={}", messages.size());
        } catch (Exception e) {
            log.error("Failed to process batch", e);
            // ACK 하지 않음 → 전체 재처리
        }
    }
}
```

**배치 Consumer Factory 설정**
```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, Object> batchKafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(consumerFactory());
    factory.setBatchListener(true);  // 배치 모드
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

    return factory;
}
```

---

## 트랜잭션 연동

### 1. Application Event와 통합

**트랜잭션 커밋 후 Kafka 발행**

```java
package io.hhplus.ecommerce.application.order;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.event.OrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderUseCase {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Order createOrder(CreateOrderCommand command) {
        // 1. 비즈니스 로직 수행
        Order order = Order.create(command);
        orderRepository.save(order);

        // 2. 이벤트 발행 (트랜잭션 커밋 후 처리됨)
        eventPublisher.publishEvent(new OrderCompletedEvent(order));

        return order;
    }
}
```

**Event Listener (AFTER_COMMIT)**

```java
package io.hhplus.ecommerce.infrastructure.kafka.listener;

import io.hhplus.ecommerce.domain.order.event.OrderCompletedEvent;
import io.hhplus.ecommerce.infrastructure.kafka.message.OrderCompletedMessage;
import io.hhplus.ecommerce.infrastructure.kafka.producer.OrderEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderEventProducer orderEventProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        log.info("Transaction committed, publishing to Kafka: orderId={}",
                event.getOrder().getId());

        OrderCompletedMessage message = OrderCompletedMessage.from(event.getOrder());
        orderEventProducer.publishOrderCompleted(message);
    }
}
```

### 2. Transactional Outbox 패턴 (고급)

**Outbox Entity**

```java
package io.hhplus.ecommerce.infrastructure.event;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String aggregateType;  // ORDER, PAYMENT, COUPON

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;  // ORDER_COMPLETED, PAYMENT_COMPLETED

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;  // JSON

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;  // INIT, PUBLISHED

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    public EventOutbox(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.INIT;
        this.createdAt = LocalDateTime.now();
    }

    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public enum OutboxStatus {
        INIT, PUBLISHED
    }
}
```

**Outbox Publisher (Scheduler)**

```java
package io.hhplus.ecommerce.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)  // 5초마다 실행
    @Transactional
    public void publishEvents() {
        List<EventOutbox> events = outboxRepository.findByStatus(EventOutbox.OutboxStatus.INIT);

        for (EventOutbox event : events) {
            try {
                // Kafka 발행
                kafkaTemplate.send(event.getEventType(), event.getAggregateId(), event.getPayload()).get();

                // 상태 업데이트
                event.markAsPublished();
                outboxRepository.save(event);

                log.info("Outbox event published: id={}, eventType={}", event.getId(), event.getEventType());
            } catch (Exception e) {
                log.error("Failed to publish outbox event: id={}", event.getId(), e);
            }
        }
    }
}
```

---

## 에러 처리

### 1. DLQ (Dead Letter Queue)

```java
package io.hhplus.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // DLQ로 실패한 메시지 전송
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    // DLQ Topic 이름: {원본 토픽}.DLT
                    return new org.apache.kafka.common.TopicPartition(
                            record.topic() + ".DLT",
                            record.partition()
                    );
                }
        );

        // 재시도 설정: 3번, 1초 간격
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(1000L, 3)
        );

        // 재시도하지 않을 예외
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler errorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
```

### 2. DLQ Consumer

```java
package io.hhplus.ecommerce.infrastructure.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DLQConsumer {

    private final FailedMessageRepository failedMessageRepository;

    @KafkaListener(
            topics = "order-completed.DLT",
            groupId = "dlq-handler"
    )
    public void handleDLQ(String message) {
        log.error("Received message from DLQ: {}", message);

        // DB에 저장하여 수동 재처리 가능하게
        FailedMessage failedMessage = new FailedMessage(
                "order-completed",
                message,
                LocalDateTime.now()
        );
        failedMessageRepository.save(failedMessage);

        log.info("Failed message saved to DB: id={}", failedMessage.getId());
    }
}
```

---

## 테스트

### 1. 통합 테스트 (Testcontainers)

```java
package io.hhplus.ecommerce.infrastructure.kafka;

import io.hhplus.ecommerce.config.KafkaTopics;
import io.hhplus.ecommerce.infrastructure.kafka.message.OrderCompletedMessage;
import io.hhplus.ecommerce.infrastructure.kafka.producer.OrderEventProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class KafkaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.3")
    );

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private OrderEventProducer orderEventProducer;

    private CountDownLatch latch = new CountDownLatch(1);
    private OrderCompletedMessage receivedMessage;

    @Test
    void shouldPublishAndConsumeMessage() throws InterruptedException {
        // Given
        OrderCompletedMessage message = OrderCompletedMessage.builder()
                .orderId("order-123")
                .userId("user-456")
                .totalAmount(50000L)
                .build();

        // When
        orderEventProducer.publishOrderCompleted(message);

        // Then
        boolean messageReceived = latch.await(10, TimeUnit.SECONDS);
        assertThat(messageReceived).isTrue();
        assertThat(receivedMessage.getOrderId()).isEqualTo("order-123");
    }

    @KafkaListener(topics = KafkaTopics.ORDER_COMPLETED, groupId = "test-group")
    public void receive(OrderCompletedMessage message) {
        receivedMessage = message;
        latch.countDown();
    }
}
```

### 2. Producer 단위 테스트

```java
package io.hhplus.ecommerce.infrastructure.kafka.producer;

import io.hhplus.ecommerce.config.KafkaTopics;
import io.hhplus.ecommerce.infrastructure.kafka.message.OrderCompletedMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderEventProducer orderEventProducer;

    @Test
    void shouldPublishOrderCompletedEvent() {
        // Given
        OrderCompletedMessage message = OrderCompletedMessage.builder()
                .orderId("order-123")
                .userId("user-456")
                .totalAmount(50000L)
                .build();

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);

        // When
        orderEventProducer.publishOrderCompleted(message);

        // Then
        verify(kafkaTemplate).send(
                eq(KafkaTopics.ORDER_COMPLETED),
                eq("order-123"),
                eq(message)
        );
    }
}
```

---

## 체크리스트

### ✅ 기본 설정
- [ ] `spring-kafka` 의존성 추가
- [ ] `application.yml` Kafka 설정
- [ ] Producer/Consumer Configuration 작성
- [ ] Topic 상수 관리 클래스 작성

### ✅ Producer 구현
- [ ] 메시지 DTO 작성
- [ ] Producer 클래스 구현
- [ ] 비동기 발행 및 콜백 처리
- [ ] 로깅 추가

### ✅ Consumer 구현
- [ ] Consumer 클래스 구현
- [ ] 수동 ACK 처리
- [ ] 예외 처리
- [ ] 로깅 추가

### ✅ 트랜잭션 연동
- [ ] `@TransactionalEventListener(AFTER_COMMIT)` 사용
- [ ] Application Event 발행
- [ ] Kafka 발행을 Event Listener에서 처리

### ✅ 에러 처리
- [ ] DLQ 설정
- [ ] DLQ Consumer 구현
- [ ] 재시도 정책 설정
- [ ] 실패 메시지 저장

### ✅ 테스트
- [ ] Testcontainers 통합 테스트
- [ ] Producer 단위 테스트
- [ ] Consumer 단위 테스트
- [ ] DLQ 테스트

---

## 다음 단계

- [ ] [비즈니스 프로세스 개선](./kafka-use-cases.md)
- [ ] [베스트 프랙티스](./kafka-best-practices.md)

---

**Last Updated**: 2024-12-18
