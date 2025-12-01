package io.hhplus.ecommerce.application.usecase.product;

import io.hhplus.ecommerce.application.product.dto.ProductListResponse;
import io.hhplus.ecommerce.application.product.dto.ProductResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@UseCase
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetProductsUseCase {

    private final ProductRepository productRepository;

    /**
     * 상품 목록 조회 (캐시 적용)
     *
     * 캐시 키: "products::{category}:{sort}"
     * - category와 sort 파라미터 조합으로 캐시 키 생성
     * - null 값은 "all", "default"로 치환
     *
     * TTL: 1시간 (CacheConfig 설정)
     * - 상품 정보는 자주 변경되지 않음
     *
     * sync=true: Thundering Herd 방지
     * - 동일 키에 대한 동시 요청 시 첫 요청만 DB 조회
     * - 나머지 요청은 결과를 기다림
     */
    @Cacheable(
            value = "products",
            // category와 sort를 모두 포함하도록 키를 구성해 캐시 충돌을 방지
            key = "(#category != null ? #category : 'all') + ':' + (#sort != null ? #sort : 'default')",
            sync = true
    )
    public ProductListResponse execute(String category, String sort) {
        log.info("Getting products - category: {}, sort: {}", category, sort);

        // 1. 전체 상품 조회
        List<Product> products = productRepository.findAll();

        // 2. 카테고리 필터링
        Stream<Product> productStream = products.stream();
        if (category != null && !category.isEmpty()) {
            productStream = productStream.filter(p -> p.getCategory().equals(category));
        }

        // 3. 정렬
        if (sort != null) {
            productStream = switch (sort) {
                case "price", "price_asc" -> productStream.sorted(Comparator.comparing(Product::getPrice));
                case "price_desc" -> productStream.sorted(Comparator.comparing(Product::getPrice).reversed());
                case "newest" -> productStream.sorted(Comparator.comparing(Product::getCreatedAt).reversed());
                default -> productStream;
            };
        }

        List<ProductResponse> productResponses = productStream
            .map(ProductResponse::from)
            .toList();

        log.debug("Found {} products", productResponses.size());
        return ProductListResponse.of(productResponses);
    }
}
