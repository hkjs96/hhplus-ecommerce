package io.hhplus.ecommerce.application.usecase.product;

import io.hhplus.ecommerce.application.product.dto.ProductResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@UseCase
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetProductUseCase {

    private final ProductRepository productRepository;

    /**
     * 상품 상세 조회 (캐시 적용)
     *
     * 캐시 키: "product::{productId}"
     * - 상품 ID별로 개별 캐시
     *
     * TTL: 1시간 (CacheConfig 설정)
     * - 상품 상세 정보는 자주 변경되지 않음
     *
     * sync=true: Thundering Herd 방지
     * - 동일 상품에 대한 동시 요청 시 첫 요청만 DB 조회
     */
    @Cacheable(value = "product", key = "#productId", sync = true)
    public ProductResponse execute(Long productId) {
        log.info("Getting product detail for productId: {}", productId);

        Product product = productRepository.findByIdOrThrow(productId);
        return ProductResponse.from(product);
    }
}
