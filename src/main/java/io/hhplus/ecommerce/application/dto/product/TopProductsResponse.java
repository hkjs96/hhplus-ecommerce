package io.hhplus.ecommerce.application.dto.product;

import java.util.List;

public record TopProductsResponse(
    String period,
    List<PopularProductResponse> products
) {
}
