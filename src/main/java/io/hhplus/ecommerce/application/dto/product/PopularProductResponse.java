package io.hhplus.ecommerce.application.dto.product;

public record PopularProductResponse(
    Integer rank,
    String productId,
    String name,
    Integer salesCount,
    Long revenue
) {
}
