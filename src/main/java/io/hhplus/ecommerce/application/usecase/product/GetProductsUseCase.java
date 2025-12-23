package io.hhplus.ecommerce.application.usecase.product;

import io.hhplus.ecommerce.application.product.dto.ProductListResponse;
import io.hhplus.ecommerce.application.product.dto.ProductResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
