package io.hhplus.ecommerce.infrastructure.batch;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRanking;
import io.hhplus.ecommerce.domain.product.ProductRankingBackup;
import io.hhplus.ecommerce.domain.product.ProductRankingBackupRepository;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.infrastructure.redis.ProductRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class RankingBackupScheduler {

    private final ProductRankingRepository redisRankingRepository;
    private final ProductRankingBackupRepository dbRankingRepository;
    private final ProductRepository productRepository;

    @Scheduled(fixedRateString = "${ranking.backup.schedule.rate:600000}")
    @Transactional
    public void backupRanking() {
        LocalDate today = LocalDate.now();
        log.info("Starting ranking backup for date: {}", today);

        try {
            // 1. Get top 100 rankings from Redis
            List<ProductRanking> redisRankings = redisRankingRepository.getTopN(today, 100);
            if (redisRankings.isEmpty()) {
                log.info("No ranking data in Redis for today. Skipping backup.");
                return;
            }

            // 2. Get product information from DB

            List<Long> productIds = redisRankings.stream().map(ProductRanking::getProductId).toList();
            Map<Long, Product> productMap = productRepository.findAll().stream()
                    .filter(p -> productIds.contains(p.getId()))
                    .collect(Collectors.toMap(Product::getId, Function.identity()));

            // 3. Create backup objects
            List<ProductRankingBackup> backups = redisRankings.stream()
                    .map(ranking -> {
                        Product product = productMap.get(ranking.getProductId());
                        String productName = (product != null) ? product.getName() : "Unknown Product";
                        int rank = redisRankings.indexOf(ranking) + 1;
                        return new ProductRankingBackup(
                                ranking.getProductId(),
                                productName,
                                ranking.getSalesCount(),
                                rank,
                                today
                        );
                    })
                    .collect(Collectors.toList());

            // 4. Save to DB (delete and insert)
            dbRankingRepository.saveAll(backups);

            log.info("Successfully backed up {} ranking entries for date: {}", backups.size(), today);
        } catch (Exception e) {
            log.error("Error during ranking backup for date: {}", today, e);
        }
    }
}
