package io.hhplus.ecommerce.application.product.dto;

import java.util.List;

public record ProductListResponse(
    List<ProductResponse> products,
    Integer totalCount
) {
    public static ProductListResponse of(List<ProductResponse> products) {
        return new ProductListResponse(products, products.size());
    }
}
