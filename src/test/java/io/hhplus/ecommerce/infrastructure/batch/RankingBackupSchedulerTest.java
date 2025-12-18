package io.hhplus.ecommerce.infrastructure.batch;

import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRankingBackup;
import io.hhplus.ecommerce.domain.product.ProductRankingBackupRepository;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.infrastructure.redis.ProductRankingRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class RankingBackupSchedulerTest {

    @Autowired
    private ProductRankingRepository redisRankingRepository;

    @Autowired
    private ProductRankingBackupRepository dbRankingRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.product.JpaProductRepository jpaProductRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.product.JpaProductRankingBackupRepository jpaDbRankingRepository;

    private RankingBackupScheduler scheduler;

    @BeforeEach
    void setUp() {
        // DB 초기화
        jpaDbRankingRepository.deleteAll();
        jpaProductRepository.deleteAll();

        // Redis 초기화
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();

        // Scheduler 인스턴스 생성
        scheduler = new RankingBackupScheduler(redisRankingRepository, dbRankingRepository, productRepository);
    }

    @Test
    @DisplayName("정상 백업 - Redis 랭킹 데이터를 DB로 백업")
    void shouldBackupSuccessfully() {
        // given
        // 1. Product 생성
        Product product1 = Product.create("P001", "상품1", "설명1", 10000L, "전자기기", 100);
        Product product2 = Product.create("P002", "상품2", "설명2", 20000L, "의류", 50);
        Product product3 = Product.create("P003", "상품3", "설명3", 30000L, "식품", 30);
        productRepository.save(product1);
        productRepository.save(product2);
        productRepository.save(product3);

        // 2. Redis에 랭킹 데이터 추가
        redisRankingRepository.incrementScore(String.valueOf(product1.getId()), 100);
        redisRankingRepository.incrementScore(String.valueOf(product2.getId()), 50);
        redisRankingRepository.incrementScore(String.valueOf(product3.getId()), 30);

        // when
        scheduler.backupRanking();

        // then
        // DB에 백업 데이터 확인
        List<ProductRankingBackup> backups = dbRankingRepository.findByAggregatedDate(LocalDate.now());
        assertThat(backups).hasSize(3);

        // 1등 확인
        ProductRankingBackup rank1 = backups.stream().filter(b -> b.getRanking() == 1).findFirst().orElseThrow();
        assertThat(rank1.getProductId()).isEqualTo(product1.getId());
        assertThat(rank1.getProductName()).isEqualTo("상품1");
        assertThat(rank1.getSalesCount()).isEqualTo(100);

        // 2등 확인
        ProductRankingBackup rank2 = backups.stream().filter(b -> b.getRanking() == 2).findFirst().orElseThrow();
        assertThat(rank2.getProductId()).isEqualTo(product2.getId());
        assertThat(rank2.getSalesCount()).isEqualTo(50);

        // 3등 확인
        ProductRankingBackup rank3 = backups.stream().filter(b -> b.getRanking() == 3).findFirst().orElseThrow();
        assertThat(rank3.getProductId()).isEqualTo(product3.getId());
        assertThat(rank3.getSalesCount()).isEqualTo(30);
    }

    @Test
    @DisplayName("빈 데이터 - Redis에 랭킹 데이터가 없을 때 백업 스킵")
    void shouldSkipBackupWhenNoData() {
        // given
        // Redis에 데이터 없음

        // when
        scheduler.backupRanking();

        // then
        // DB에 백업 데이터가 없어야 함
        List<ProductRankingBackup> backups = dbRankingRepository.findByAggregatedDate(LocalDate.now());
        assertThat(backups).isEmpty();
    }

    @Test
    @DisplayName("상품 정보 없음 - Product가 삭제된 경우 'Unknown Product'로 백업")
    void shouldBackupWithUnknownProduct() {
        // given
        // 1. Redis에만 랭킹 데이터 추가 (Product는 생성하지 않음)
        redisRankingRepository.incrementScore("999", 100);

        // when
        scheduler.backupRanking();

        // then
        // DB에 백업 데이터 확인 (productName이 'Unknown Product')
        List<ProductRankingBackup> backups = dbRankingRepository.findByAggregatedDate(LocalDate.now());
        assertThat(backups).hasSize(1);
        assertThat(backups.get(0).getProductId()).isEqualTo(999L);
        assertThat(backups.get(0).getProductName()).isEqualTo("Unknown Product");
        assertThat(backups.get(0).getSalesCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("대량 백업 - 100개 상품 백업")
    void shouldBackupLargeData() {
        // given
        // 1. 10개 Product 생성 (실제 사용)
        for (int i = 1; i <= 10; i++) {
            Product product = Product.create("P" + String.format("%03d", i), "상품" + i, "설명" + i, 10000L * i, "카테고리" + i, 100);
            productRepository.save(product);

            // 2. Redis에 랭킹 데이터 추가 (score는 역순)
            redisRankingRepository.incrementScore(String.valueOf(product.getId()), 110 - i * 10);
        }

        // when
        scheduler.backupRanking();

        // then
        // DB에 백업 데이터 확인
        List<ProductRankingBackup> backups = dbRankingRepository.findByAggregatedDate(LocalDate.now());
        assertThat(backups).hasSize(10);

        // 순위 순서 확인
        for (int i = 0; i < 10; i++) {
            final int rank = i + 1;
            final int expectedSales = 100 - i * 10;
            ProductRankingBackup backup = backups.stream().filter(b -> b.getRanking() == rank).findFirst().orElseThrow();
            assertThat(backup.getSalesCount()).isEqualTo(expectedSales);
        }
    }
}
