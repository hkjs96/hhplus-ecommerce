package io.hhplus.ecommerce.application.product.dto;

import io.hhplus.ecommerce.domain.product.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 상품 응답 DTO
 * - 상품 상세 조회, 목록 조회에서 사용
 */
@Getter
@Builder
@AllArgsConstructor
public class ProductResponse {
    private String productId;
    private String name;
    private String description;
    private Long price;
    private Integer stock;
    private String category;

    /**
     * Domain Entity → DTO 변환
     */
    public static ProductResponse from(Product product) {
        return ProductResponse.builder()
                .productId(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .category(product.getCategory())
                .build();
    }
}
