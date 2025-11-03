package io.hhplus.ecommerce.application.dto.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class TopProductsResponse {
    private String period;
    private List<PopularProductResponse> products;
}
