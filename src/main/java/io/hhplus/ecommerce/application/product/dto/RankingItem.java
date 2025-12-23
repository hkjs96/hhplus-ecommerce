package io.hhplus.ecommerce.application.product.dto;

/**
 * 랭킹 개별 항목 DTO
 *
 * @param rank        순위 (1부터 시작)
 * @param productId   상품 ID
 * @param productName 상품명
 * @param salesCount  판매 수량
 */
public record RankingItem(
    int rank,
    Long productId,
    String productName,
    int salesCount
) {
    public static RankingItem of(int rank, Long productId, String productName, int salesCount) {
        return new RankingItem(rank, productId, productName, salesCount);
    }
}
