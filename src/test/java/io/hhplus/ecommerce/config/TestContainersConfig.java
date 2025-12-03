package io.hhplus.ecommerce.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * TestContainers 설정
 *
 * 통합 테스트에서 사용할 MySQL과 Redis 컨테이너를 설정합니다.
 * DynamicPropertySource를 사용하여 연결 정보를 동적으로 구성합니다.
 */
@TestConfiguration
public class TestContainersConfig {

    private static MySQLContainer<?> mysql;
    private static GenericContainer<?> redis;

    static {
        // MySQL Container
        mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("test_ecommerce")
                .withUsername("test")
                .withPassword("test")
                .withCommand(
                        "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_unicode_ci"
                );
        mysql.start();

        // Redis Container
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .withCommand("redis-server", "--maxmemory", "256mb");
        redis.start();
    }

    /**
     * 동적으로 프로퍼티 설정
     */
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // MySQL 설정
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        // Hikari Connection Pool 설정 (동시성 테스트용)
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 150);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 50);
        registry.add("spring.datasource.hikari.connection-timeout", () -> 30000);  // 30초
        registry.add("spring.datasource.hikari.idle-timeout", () -> 600000);       // 10분
        registry.add("spring.datasource.hikari.max-lifetime", () -> 1800000);      // 30분

        // Redis 설정
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Bean
    public MySQLContainer<?> mysqlContainer() {
        return mysql;
    }

    @Bean
    public GenericContainer<?> redisContainer() {
        return redis;
    }
}
