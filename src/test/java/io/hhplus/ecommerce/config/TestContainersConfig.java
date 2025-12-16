package io.hhplus.ecommerce.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * TestContainers 설정
 *
 * 통합 테스트에서 사용할 MySQL과 Redis 컨테이너를 설정합니다.
 * Singleton 패턴으로 전체 테스트 세션 동안 컨테이너를 재사용합니다.
 */
@TestConfiguration
public class TestContainersConfig {

    // 컨테이너는 static 블록에서 한 번만 기동 (JUnit 확장 없이도 동작하도록)
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
        .withDatabaseName("test_ecommerce")
        .withUsername("test")
        .withPassword("test")
        .withCommand(
            "--character-set-server=utf8mb4",
            "--collation-server=utf8mb4_unicode_ci",
            "--max_connections=300"  // 테스트 환경에서는 과도한 커넥션 풀을 줄여 안정성 확보
        )
        .withReuse(true);

    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379)
        .withCommand("redis-server", "--maxmemory", "256mb")
        .withReuse(true);

    static {
        mysql.start();
        redis.start();
        // SpringBootTest 컨텍스트 로딩 전에 우선순위 높게 주입 (DynamicPropertySource는 테스트 클래스 한정이라 import 환경 대비)
        System.setProperty("spring.datasource.url", mysql.getJdbcUrl());
        System.setProperty("spring.datasource.username", mysql.getUsername());
        System.setProperty("spring.datasource.password", mysql.getPassword());
        System.setProperty("spring.data.redis.host", redis.getHost());
        System.setProperty("spring.data.redis.port", String.valueOf(redis.getMappedPort(6379)));
    }

    @DynamicPropertySource
    static void datasourceAndRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
