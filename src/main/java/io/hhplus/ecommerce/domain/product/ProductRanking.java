package io.hhplus.ecommerce.domain.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 상품 랭킹 Value Object
 *
 * Redis Sorted Set에서 조회한 랭킹 정보를 표현
 */
@Getter
@AllArgsConstructor
public class ProductRanking {
    private final Long productId;
    private final int salesCount;

    public static ProductRanking of(Long productId, int salesCount) {
        return new ProductRanking(productId, salesCount);
    }
}
