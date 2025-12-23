package io.hhplus.ecommerce.infrastructure.kafka;

import io.hhplus.ecommerce.infrastructure.kafka.message.OrderCompletedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@DisplayName("Kafka 통합 테스트")
class OrderKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0")
    )
        .withDatabaseName("ecommerce_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.3")
    );

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @BeforeEach
    void setUp() {
        // Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    @DisplayName("Happy Path: Kafka 메시지 발행 → Consumer 수신 → Redis 멱등성 키 저장")
    void kafkaMessage_isConsumed_andSavedToRedis() {
        // given
        OrderCompletedMessage message = new OrderCompletedMessage(
            100L,
            200L,
            50_000L,
            LocalDateTime.now()
        );

        // when: Kafka로 메시지 발행
        kafkaTemplate.send("order-completed", message);

        // then: Consumer가 메시지 수신 후 Redis에 멱등성 키 저장 (10초 타임아웃)
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                String key = "kafka:processed:order:100";
                String value = redisTemplate.opsForValue().get(key);
                assertThat(value).isEqualTo("1");
            });
    }

    @Test
    @DisplayName("멱등성: 중복 메시지는 무시되고 Redis에 1번만 저장")
    void duplicateMessages_areIgnored() {
        // given
        OrderCompletedMessage message = new OrderCompletedMessage(
            200L,
            300L,
            80_000L,
            LocalDateTime.now()
        );

        // when: 같은 메시지 2번 발행
        kafkaTemplate.send("order-completed", message);
        kafkaTemplate.send("order-completed", message);

        // then: Consumer가 2번 수신하지만 Redis에는 1번만 저장 (멱등성)
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                String key = "kafka:processed:order:200";
                String value = redisTemplate.opsForValue().get(key);
                assertThat(value).isEqualTo("1");
            });
    }

    @Test
    @DisplayName("메시지 포맷: OrderCompletedMessage의 모든 필드가 정확히 전달됨")
    void messageFormat_allFieldsAreSerialized() {
        // given
        Long expectedOrderId = 300L;
        Long expectedUserId = 400L;
        Long expectedTotalAmount = 120_000L;

        OrderCompletedMessage message = new OrderCompletedMessage(
            expectedOrderId,
            expectedUserId,
            expectedTotalAmount,
            LocalDateTime.now()
        );

        // when: 메시지 발행
        kafkaTemplate.send("order-completed", message);

        // then: Redis에 정상 저장 (모든 필드가 정확히 전달됨)
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                String key = "kafka:processed:order:300";
                String value = redisTemplate.opsForValue().get(key);
                assertThat(value).isEqualTo("1");
            });
    }
}
