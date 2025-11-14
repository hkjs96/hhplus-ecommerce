package io.hhplus.ecommerce.application.product.dto;

import io.hhplus.ecommerce.domain.product.Product;

public record ProductResponse(
    Long productId,
    String name,
    String description,
    Long price,
    Integer stock,
    String category
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            product.getCategory()
        );
    }
}
