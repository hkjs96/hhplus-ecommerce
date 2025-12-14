package io.hhplus.ecommerce.infrastructure.redis;

import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.product.ProductRanking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductRankingRepository 통합 테스트
 *
 * Testcontainers Redis를 사용한 통합 테스트
 * - ZINCRBY 원자성 검증
 * - Top N 조회 검증
 * - 동시성 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class ProductRankingRepositoryTest {

    @Autowired
    private ProductRankingRepository rankingRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // Redis 전체 데이터 삭제
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("상품 판매량을 랭킹에 반영한다")
    void incrementScore() {
        // given
        String productId = "123";
        int quantity = 5;

        // when
        rankingRepository.incrementScore(productId, quantity);

        // then
        int score = rankingRepository.getScore(LocalDate.now(), productId);
        assertThat(score).isEqualTo(5);
    }

    @Test
    @DisplayName("동일 상품에 여러 번 판매량을 증가시킨다")
    void incrementScore_multiple_times() {
        // given
        String productId = "123";

        // when
        rankingRepository.incrementScore(productId, 5);
        rankingRepository.incrementScore(productId, 3);
        rankingRepository.incrementScore(productId, 2);

        // then
        int score = rankingRepository.getScore(LocalDate.now(), productId);
        assertThat(score).isEqualTo(10);
    }

    @Test
    @DisplayName("상위 N개 상품을 조회한다")
    void getTopN() {
        // given
        LocalDate today = LocalDate.now();
        rankingRepository.incrementScore("101", 100);
        rankingRepository.incrementScore("102", 50);
        rankingRepository.incrementScore("103", 30);
        rankingRepository.incrementScore("104", 20);

        // when
        List<ProductRanking> topProducts = rankingRepository.getTopN(today, 3);

        // then
        assertThat(topProducts).hasSize(3);
        assertThat(topProducts.get(0).getProductId()).isEqualTo(101L);
        assertThat(topProducts.get(0).getSalesCount()).isEqualTo(100);
        assertThat(topProducts.get(1).getProductId()).isEqualTo(102L);
        assertThat(topProducts.get(1).getSalesCount()).isEqualTo(50);
        assertThat(topProducts.get(2).getProductId()).isEqualTo(103L);
        assertThat(topProducts.get(2).getSalesCount()).isEqualTo(30);
    }

    @Test
    @DisplayName("특정 상품의 순위를 조회한다")
    void getRank() {
        // given
        LocalDate today = LocalDate.now();
        rankingRepository.incrementScore("101", 100);
        rankingRepository.incrementScore("102", 50);
        rankingRepository.incrementScore("103", 30);

        // when
        int rank = rankingRepository.getRank(today, "102");

        // then
        assertThat(rank).isEqualTo(2);  // 2등
    }

    @Test
    @DisplayName("동시에 100개 요청 시 score가 정확히 100 증가한다 (ZINCRBY 원자성)")
    void concurrency_test() throws InterruptedException {
        // given
        String productId = "999";
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    rankingRepository.incrementScore(productId, 1);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then
        int score = rankingRepository.getScore(LocalDate.now(), productId);
        assertThat(score).isEqualTo(100);  // 정확히 100

        executorService.shutdown();
    }

    @Test
    @DisplayName("랭킹 데이터가 없으면 빈 리스트를 반환한다")
    void getTopN_empty() {
        // given
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // when
        List<ProductRanking> topProducts = rankingRepository.getTopN(yesterday, 10);

        // then
        assertThat(topProducts).isEmpty();
    }
}
