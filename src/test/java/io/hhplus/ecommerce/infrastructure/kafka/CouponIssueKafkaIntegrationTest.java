package io.hhplus.ecommerce.infrastructure.kafka;

import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.kafka.message.CouponIssueRequestedMessage;
import io.hhplus.ecommerce.infrastructure.redis.CouponIssueReservationStore;
import io.hhplus.ecommerce.infrastructure.persistence.coupon.JpaCouponRepository;
import io.hhplus.ecommerce.infrastructure.persistence.coupon.JpaUserCouponRepository;
import io.hhplus.ecommerce.infrastructure.persistence.user.JpaUserRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@DisplayName("쿠폰 발급 Kafka 통합 테스트")
class CouponIssueKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private CouponIssueReservationStore couponIssueReservationStore;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private JpaCouponRepository jpaCouponRepository;

    @Autowired
    private JpaUserRepository jpaUserRepository;

    @Autowired
    private JpaUserCouponRepository jpaUserCouponRepository;

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
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
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.listener.auto-startup", () -> "true");

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        jpaUserCouponRepository.deleteAll();
        jpaCouponRepository.deleteAll();
        jpaUserRepository.deleteAll();
    }

    @Test
    @DisplayName("요청 토픽 소비 → DB 발급 성공 → Redis issued 확정")
    void issue_success() {
        Coupon coupon = Coupon.create(
            "COUP-KAFKA-1",
            "Kafka 쿠폰",
            10,
            1,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        coupon = couponRepository.save(coupon);

        User user = User.create("kafka-user@test.com", "kafka-user");
        user = userRepository.save(user);

        couponIssueReservationStore.reserve(coupon.getId(), user.getId(), coupon.getTotalQuantity(), Duration.ofMinutes(5));

        kafkaTemplate.send(
            "coupon-issue-requested",
            CouponIssueRequestedMessage.of(coupon.getId(), user.getId(), UUID.randomUUID().toString())
        );

        Long couponId = coupon.getId();
        Long userId = user.getId();
        String issuedKey = "coupon:" + couponId + ":issued";
        String reservationKey = "coupon:" + couponId + ":reservation:" + userId;

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).isTrue();
            assertThat(redisTemplate.opsForSet().isMember(issuedKey, String.valueOf(userId))).isTrue();
            assertThat(redisTemplate.opsForValue().get(reservationKey)).isNull();
        });
    }

    @Test
    @DisplayName("중복 발급(UK 충돌)은 멱등 처리되어 remaining 보상 없이 issued 확정")
    void issue_duplicate_is_idempotent_and_doesNotCompensateRemaining() {
        Coupon coupon = Coupon.create(
            "COUP-KAFKA-2",
            "Kafka 쿠폰 2",
            10,
            1,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        coupon = couponRepository.save(coupon);

        User user = User.create("kafka-user2@test.com", "kafka-user2");
        user = userRepository.save(user);

        // reserve remaining: 1 -> 0
        couponIssueReservationStore.reserve(coupon.getId(), user.getId(), coupon.getTotalQuantity(), Duration.ofMinutes(5));

        // DB 중복을 미리 만들어서 발급 실패(내부 오류) 유도
        UserCoupon userCoupon = UserCoupon.create(user.getId(), coupon.getId(), coupon.getExpiresAt());
        userCouponRepository.save(userCoupon);

        kafkaTemplate.send(
            "coupon-issue-requested",
            CouponIssueRequestedMessage.of(coupon.getId(), user.getId(), UUID.randomUUID().toString())
        );

        Long couponId = coupon.getId();
        Long userId = user.getId();
        String remainingKey = "coupon:" + couponId + ":remaining";
        String issuedKey = "coupon:" + couponId + ":issued";
        String reservationKey = "coupon:" + couponId + ":reservation:" + userId;

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(redisTemplate.opsForValue().get(remainingKey)).isEqualTo("0");
            assertThat(redisTemplate.opsForSet().isMember(issuedKey, String.valueOf(userId))).isTrue();
            assertThat(redisTemplate.opsForValue().get(reservationKey)).isNull();
        });
    }

    @Test
    @DisplayName("영구 실패(쿠폰 없음) → 재시도 초과 → DLT로 이동 → DLT 컨슈머가 remaining 보상")
    void issue_invalidCoupon_goesToDlt_andCompensatesRemaining() {
        Long invalidCouponId = 9999L;

        User user = User.create("kafka-user3@test.com", "kafka-user3");
        user = userRepository.save(user);

        couponIssueReservationStore.reserve(invalidCouponId, user.getId(), 1, Duration.ofMinutes(5));

        kafkaTemplate.send(
            "coupon-issue-requested",
            CouponIssueRequestedMessage.of(invalidCouponId, user.getId(), UUID.randomUUID().toString())
        );

        Long userId = user.getId();
        String remainingKey = "coupon:" + invalidCouponId + ":remaining";
        String reservationKey = "coupon:" + invalidCouponId + ":reservation:" + userId;

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(redisTemplate.opsForValue().get(remainingKey)).isEqualTo("1");
            assertThat(redisTemplate.opsForValue().get(reservationKey)).isNull();
        });
    }
}
