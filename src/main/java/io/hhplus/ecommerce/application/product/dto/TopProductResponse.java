package io.hhplus.ecommerce.application.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

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
