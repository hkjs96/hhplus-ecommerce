package io.hhplus.ecommerce.application.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 상품 목록 응답 DTO
 * - 상품 목록 조회 API에서 사용
 */
@Getter
@Builder
@AllArgsConstructor
public class ProductListResponse {
    private List<ProductResponse> products;
    private Integer totalCount;

    public static ProductListResponse of(List<ProductResponse> products) {
        return ProductListResponse.builder()
                .products(products)
                .totalCount(products.size())
                .build();
    }
}
