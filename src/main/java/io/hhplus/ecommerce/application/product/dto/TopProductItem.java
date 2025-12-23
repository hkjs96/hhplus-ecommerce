package io.hhplus.ecommerce.application.product.dto;

public record TopProductItem(
    Integer rank,
    Long productId,
    String name,
    Integer salesCount,
    Long revenue
) {
}
