package io.hhplus.ecommerce.application.dto.product;

import java.util.List;

public record ProductListResponse(
    List<ProductResponse> products,
    Integer totalCount
) {
}
