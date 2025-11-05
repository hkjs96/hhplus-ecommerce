package io.hhplus.ecommerce.application.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 인기 상품 항목 DTO
 * - 순위, 판매량, 매출액 정보 포함
 */
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
