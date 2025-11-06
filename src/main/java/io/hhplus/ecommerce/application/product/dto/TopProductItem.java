package io.hhplus.ecommerce.application.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TopProductItem {
    private Integer rank;
    private String productId;
    private String name;
    private Integer salesCount;
    private Long revenue;
}
