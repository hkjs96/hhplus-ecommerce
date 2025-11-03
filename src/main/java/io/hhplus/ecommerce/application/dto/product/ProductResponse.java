package io.hhplus.ecommerce.application.dto.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductResponse {
    private String productId;
    private String name;
    private String description;
    private Long price;
    private Integer stock;
    private String category;

    // 목록 조회용 생성자 (description 없음)
    public ProductResponse(String productId, String name, Long price, Integer stock, String category) {
        this.productId = productId;
        this.name = name;
        this.description = null;
        this.price = price;
        this.stock = stock;
        this.category = category;
    }
}
