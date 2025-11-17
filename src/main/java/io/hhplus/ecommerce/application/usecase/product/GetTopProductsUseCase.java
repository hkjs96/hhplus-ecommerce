package io.hhplus.ecommerce.application.usecase.product;

import io.hhplus.ecommerce.application.product.dto.TopProductItem;
import io.hhplus.ecommerce.application.product.dto.TopProductResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.product.TopProductProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@UseCase
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetTopProductsUseCase {

    private final ProductRepository productRepository;

    public TopProductResponse execute() {
        log.info("Getting top products (last 3 days) using optimized Native Query");

        // 1. Native Query로 DB에서 집계된 결과 조회 (Single Query)
        List<TopProductProjection> topProductsFromDb = productRepository.findTopProductsByPeriod();

        if (topProductsFromDb.isEmpty()) {
            log.debug("No top products found in last 3 days");
            return TopProductResponse.of(List.of());
        }

        // 2. Projection → DTO 변환 및 rank 설정
        AtomicInteger rank = new AtomicInteger(1);
        List<TopProductItem> rankedProducts = topProductsFromDb.stream()
            .map(projection -> new TopProductItem(
                rank.getAndIncrement(),
                projection.getProductId(),
                projection.getProductName(),
                projection.getSalesCount(),
                projection.getRevenue()
            ))
            .toList();

        log.info("Found {} top products using optimized query", rankedProducts.size());
        return TopProductResponse.of(rankedProducts);
    }
}
