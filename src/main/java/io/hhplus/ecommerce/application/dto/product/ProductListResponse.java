package io.hhplus.ecommerce.application.dto.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ProductListResponse {
    private List<ProductResponse> products;
    private Integer totalCount;
}
