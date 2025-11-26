package io.hhplus.ecommerce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 및 Redisson 설정
 *
 * Redisson을 사용한 분산 락 및 캐싱을 위한 설정입니다.
 * - 분산 락: RLock을 통한 동시성 제어
 * - 캐싱: RBucket을 통한 Cache-Aside 패턴
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * Redisson 클라이언트 설정
     *
     * Jackson Codec을 사용하여 객체를 JSON으로 직렬화합니다.
     * Java 8 Time API (LocalDateTime 등)를 지원하도록 설정합니다.
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // Jackson Codec 설정 (JSON 직렬화)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        config.setCodec(new JsonJacksonCodec(objectMapper));

        // Redis 서버 설정
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setConnectionPoolSize(50)          // 커넥션 풀 크기
                .setConnectionMinimumIdleSize(10)   // 최소 유휴 커넥션
                .setRetryAttempts(3)                // 재시도 횟수
                .setRetryInterval(1500)             // 재시도 간격 (ms)
                .setTimeout(3000)                   // 응답 타임아웃 (ms)
                .setPingConnectionInterval(30000);  // Ping 간격 (30초)

        return Redisson.create(config);
    }
}
