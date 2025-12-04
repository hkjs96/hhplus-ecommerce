package io.hhplus.ecommerce.infrastructure.redis;

import io.hhplus.ecommerce.config.TestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 연결 및 기본 동작 테스트
 *
 * 목적: Redis INCR가 제대로 작동하는지 검증
 */
@SpringBootTest
@Import(TestContainersConfig.class)
class RedisConnectionTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    @DisplayName("Redis 연결 및 INCR 동작 확인")
    void testRedisIncrement() {
        // Given
        String testKey = "test:sequence";

        // Redis 초기화
        redisTemplate.delete(testKey);

        // When: 10번 INCR 호출
        for (int i = 0; i < 10; i++) {
            Long value = redisTemplate.opsForValue().increment(testKey);
            System.out.println("INCR " + (i + 1) + ": " + value);

            // Then: 순차적으로 증가하는지 확인
            assertThat(value).isEqualTo((long) (i + 1));
        }

        // 최종 값 확인
        String finalValue = redisTemplate.opsForValue().get(testKey);
        System.out.println("Final value: " + finalValue);
        assertThat(finalValue).isEqualTo("10");

        // 정리
        redisTemplate.delete(testKey);
    }

    @Test
    @DisplayName("동시성 환경에서 Redis INCR 테스트")
    void testConcurrentRedisIncrement() throws InterruptedException {
        // Given
        String testKey = "test:concurrent:sequence";
        redisTemplate.delete(testKey);

        int threadCount = 100;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

        // When: 100개 스레드가 동시에 INCR 호출
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Long value = redisTemplate.opsForValue().increment(testKey);
                    if (value != null) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then: 모든 요청이 성공하고, 최종 값이 100이어야 함
        String finalValue = redisTemplate.opsForValue().get(testKey);
        System.out.println("Concurrent test - Success: " + successCount.get() + ", Final value: " + finalValue);

        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(finalValue).isEqualTo("100");

        // 정리
        redisTemplate.delete(testKey);
    }
}
