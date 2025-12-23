package io.hhplus.ecommerce.application.product.usecase;

import io.hhplus.ecommerce.application.product.dto.RankingItem;
import io.hhplus.ecommerce.application.product.dto.RankingResponse;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRanking;
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
 * - DB에서 상품 정보 조회
 * - 랭킹 + 상품 정보 병합
 *
 * 특징:
 * - Redis 장애 시 빈 목록 반환 (서비스 정상 동작)
 * - Batch 조회로 N+1 방지
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductRankingUseCase {

    private final ProductRankingRepository rankingRepository;
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
            // 1. Redis에서 Top N 조회
            List<ProductRanking> rankings = rankingRepository.getTopN(targetDate, limit);

            if (rankings.isEmpty()) {
                log.debug("랭킹 데이터 없음: date={}, limit={}", targetDate, limit);
                return RankingResponse.of(targetDate, List.of());
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

            log.info("랭킹 조회 완료: date={}, count={}", targetDate, items.size());
            return RankingResponse.of(targetDate, items);

        } catch (Exception e) {
            log.error("랭킹 조회 실패: date={}, limit={}", targetDate, limit, e);
            // Redis 장애 시에도 서비스 정상 동작 (빈 목록 반환)
            return RankingResponse.of(targetDate, List.of());
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
        return rankingRepository.getRank(targetDate, productId.toString());
    }
}
