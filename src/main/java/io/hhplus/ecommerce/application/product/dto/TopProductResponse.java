package io.hhplus.ecommerce.application.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 인기 상품 조회 응답 DTO
 * - 최근 3일간 Top 5 상품
 */
@Getter
@Builder
@AllArgsConstructor
public class TopProductResponse {
    private String period;
    private List<TopProductItem> products;

    public static TopProductResponse of(List<TopProductItem> products) {
        return TopProductResponse.builder()
                .period("3days")
                .products(products)
                .build();
    }
}
