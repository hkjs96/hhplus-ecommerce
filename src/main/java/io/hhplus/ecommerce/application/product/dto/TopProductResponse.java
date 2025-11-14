package io.hhplus.ecommerce.application.product.dto;

import java.util.List;

public record TopProductResponse(
    String period,
    List<TopProductItem> products
) {
    public static TopProductResponse of(List<TopProductItem> products) {
        return new TopProductResponse("3days", products);
    }
}
