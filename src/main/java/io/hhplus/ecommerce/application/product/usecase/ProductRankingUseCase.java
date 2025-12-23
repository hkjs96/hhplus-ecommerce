package io.hhplus.ecommerce.application.product.usecase;

import io.hhplus.ecommerce.application.product.dto.RankingItem;
import io.hhplus.ecommerce.application.product.dto.RankingResponse;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRanking;
import io.hhplus.ecommerce.domain.product.ProductRankingBackup;
import io.hhplus.ecommerce.domain.product.ProductRankingBackupRepository;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.infrastructure.redis.ProductRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 상품 랭킹 조회 UseCase
 *
 * 책임:
 * - Redis에서 랭킹 조회
 * - Redis 장애 시 DB 백업 조회 (Fallback)
 * - DB에서 상품 정보 조회
 * - 랭킹 + 상품 정보 병합
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductRankingUseCase {

    private final ProductRankingRepository redisRankingRepository;
    private final ProductRankingBackupRepository dbRankingRepository;
    private final ProductRepository productRepository;

    /**
     * 일간 상위 N개 상품 조회
     *
     * @param date  조회 날짜 (null이면 오늘)
     * @param limit 조회 개수 (기본 10개)
     * @return 랭킹 응답 (순위, 상품명, 판매량 포함)
     */
    @Transactional(readOnly = true)
    public RankingResponse getTopProducts(LocalDate date, int limit) {
        LocalDate targetDate = date != null ? date : LocalDate.now();

        try {
            // 1. Redis에서 Top N 조회 시도
            log.debug("Redis에서 상위 {}개 상품 랭킹 조회를 시도합니다. (날짜: {})", limit, targetDate);
            List<ProductRanking> rankings = redisRankingRepository.getTopN(targetDate, limit);

            if (rankings.isEmpty()) {
                log.info("Redis에서 랭킹 데이터가 없습니다. DB 백업 데이터로 대체 시도합니다.");
                return getTopProductsFromDb(targetDate, limit);
            }

            // 2. 상품 ID 목록 추출
            List<Long> productIds = rankings.stream()
                .map(ProductRanking::getProductId)
                .collect(Collectors.toList());

            // 3. DB에서 상품 정보 Batch 조회 (N+1 방지)
            Map<Long, Product> productMap = productRepository.findAll().stream()
                .filter(product -> productIds.contains(product.getId()))
                .collect(Collectors.toMap(Product::getId, Function.identity()));

            // 4. 랭킹 + 상품 정보 병합
            List<RankingItem> items = rankings.stream()
                .map(ranking -> {
                    Product product = productMap.get(ranking.getProductId());
                    int rank = rankings.indexOf(ranking) + 1;  // 순위 (1부터 시작)

                    return RankingItem.of(
                        rank,
                        ranking.getProductId(),
                        product != null ? product.getName() : "상품 없음",
                        ranking.getSalesCount()
                    );
                })
                .collect(Collectors.toList());

            log.info("Redis에서 상위 {}개 랭킹을 성공적으로 조회했습니다. (조회된 항목 수: {}, 날짜: {})", limit, items.size(), targetDate);
            return RankingResponse.of(targetDate, items);

        } catch (Exception e) {
            log.error("Redis에서 랭킹 조회에 실패했습니다. (날짜: {}, 조회 개수: {}) DB 백업 데이터로 대체 시도합니다.", targetDate, limit, e);
            return getTopProductsFromDb(targetDate, limit);
        }
    }

    private RankingResponse getTopProductsFromDb(LocalDate date, int limit) {
        try {
            log.debug("DB 백업에서 상위 {}개 상품 랭킹 조회를 시도합니다. (날짜: {})", limit, date);
            List<ProductRankingBackup> backupRankings = dbRankingRepository.findByAggregatedDate(date);

            if (backupRankings.isEmpty()) {
                log.warn("DB 백업에서도 랭킹 데이터가 없습니다. (날짜: {})", date);
                return RankingResponse.of(date, List.of());
            }

            List<RankingItem> items = backupRankings.stream()
                .limit(limit)
                .map(backup -> RankingItem.of(
                        backup.getRanking(),
                        backup.getProductId(),
                        backup.getProductName(),
                        backup.getSalesCount()
                ))
                .collect(Collectors.toList());

            log.warn("DB 백업에서 랭킹을 성공적으로 조회했습니다. (조회된 항목 수: {}, 날짜: {})", items.size(), date);
            return RankingResponse.of(date, items);
        } catch (Exception dbError) {
            log.error("CRITICAL: Redis와 DB 백업 모두에서 랭킹 조회에 실패했습니다. (날짜: {})", date, dbError);
            return RankingResponse.of(date, List.of());
        }
    }


    /**
     * 특정 상품의 순위 조회
     *
     * @param date      조회 날짜
     * @param productId 상품 ID
     * @return 순위 (1부터 시작, 없으면 -1)
     */
    public int getProductRank(LocalDate date, Long productId) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return redisRankingRepository.getRank(targetDate, productId.toString());
    }
}
