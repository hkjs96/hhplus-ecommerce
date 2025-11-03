package io.hhplus.ecommerce.application.dto.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PopularProductResponse {
    private Integer rank;
    private String productId;
    private String name;
    private Integer salesCount;
    private Long revenue;
}
