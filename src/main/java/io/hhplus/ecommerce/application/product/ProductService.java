package io.hhplus.ecommerce.application.product;

import io.hhplus.ecommerce.application.product.dto.ProductListResponse;
import io.hhplus.ecommerce.application.product.dto.ProductResponse;
import io.hhplus.ecommerce.application.product.dto.TopProductResponse;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Product Application Service
 * - 상품 조회 유스케이스 구현
 * - API 명세를 Service 메서드로 구현
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * 상품 상세 조회
     * API: GET /products/{productId}
     */
    public ProductResponse getProduct(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PRODUCT_NOT_FOUND,
                        "상품을 찾을 수 없습니다. productId: " + productId
                ));

        return ProductResponse.from(product);
    }

    /**
     * 상품 목록 조회
     * API: GET /products?category={category}&sort={sort}
     *
     * @param category 카테고리 필터 (optional)
     * @param sort 정렬 방식: price, popularity, newest (optional)
     */
    public ProductListResponse getProducts(String category, String sort) {
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
                case "price" -> productStream.sorted(Comparator.comparing(Product::getPrice));
                case "newest" -> productStream.sorted(Comparator.comparing(Product::getCreatedAt).reversed());
                // popularity는 Week 3에서는 판매량 데이터가 없으므로 기본 정렬
                default -> productStream;
            };
        }

        // 4. DTO 변환
        List<ProductResponse> productResponses = productStream
                .map(ProductResponse::from)
                .toList();

        return ProductListResponse.of(productResponses);
    }

    /**
     * 인기 상품 조회
     * API: GET /products/top
     *
     * Week 3 구현: 빈 리스트 반환 (주문 데이터가 없으므로)
     * Week 4+: 실제 집계 구현
     */
    public TopProductResponse getTopProducts() {
        // Week 3: 주문 데이터가 없으므로 빈 리스트 반환
        // Week 4+: OrderItem에서 집계하여 Top 5 반환
        return TopProductResponse.of(List.of());
    }
}
