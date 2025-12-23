package io.hhplus.ecommerce.presentation.api.product;

import io.hhplus.ecommerce.application.product.dto.RankingResponse;
import io.hhplus.ecommerce.application.product.usecase.ProductRankingUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 상품 랭킹 API Controller
 *
 * 엔드포인트:
 * - GET /api/products/ranking/top - 일간 상위 N개 조회
 *
 * 특징:
 * - Redis 기반 실시간 랭킹
 * - 날짜별 조회 가능
 * - Redis 장애 시에도 정상 응답 (빈 목록)
 */
@RestController
@RequestMapping("/api/products/ranking")
@RequiredArgsConstructor
@Slf4j
public class ProductRankingController {

    private final ProductRankingUseCase productRankingUseCase;

    /**
     * 일간 상위 N개 상품 조회
     *
     * @param date  조회 날짜 (yyyy-MM-dd, 기본값: 오늘)
     * @param limit 조회 개수 (기본값: 10, 최대: 100)
     * @return 랭킹 응답 (순위, 상품명, 판매량 포함)
     *
     * 예시:
     * - GET /api/products/ranking/top
     * - GET /api/products/ranking/top?date=2025-12-03&limit=20
     */
    @GetMapping("/top")
    public ResponseEntity<RankingResponse> getTopProducts(
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
        @RequestParam(defaultValue = "10") int limit
    ) {
        // Validation: limit 범위 체크
        if (limit < 1 || limit > 100) {
            log.warn("Invalid limit parameter: {}, using default 10", limit);
            limit = 10;
        }

        // 날짜 기본값: 오늘
        LocalDate targetDate = date != null ? date : LocalDate.now();

        log.info("랭킹 조회 요청: date={}, limit={}", targetDate, limit);

        RankingResponse response = productRankingUseCase.getTopProducts(targetDate, limit);

        return ResponseEntity.ok(response);
    }

    /**
     * 특정 상품의 순위 조회
     *
     * @param productId 상품 ID
     * @param date      조회 날짜 (yyyy-MM-dd, 기본값: 오늘)
     * @return 순위 (1부터 시작, 없으면 -1)
     *
     * 예시:
     * - GET /api/products/ranking/product/123
     * - GET /api/products/ranking/product/123?date=2025-12-03
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<ProductRankResponse> getProductRank(
        @PathVariable Long productId,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now();

        log.info("상품 순위 조회 요청: productId={}, date={}", productId, targetDate);

        int rank = productRankingUseCase.getProductRank(targetDate, productId);

        ProductRankResponse response = new ProductRankResponse(productId, targetDate, rank);

        return ResponseEntity.ok(response);
    }

    /**
     * 상품 순위 응답 DTO
     */
    public record ProductRankResponse(
        Long productId,
        LocalDate date,
        int rank  // 1부터 시작, 없으면 -1
    ) {}
}
