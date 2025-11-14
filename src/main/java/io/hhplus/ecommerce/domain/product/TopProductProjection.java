package io.hhplus.ecommerce.domain.product;

/**
 * 인기 상품 조회 Native Query Projection
 *
 * 용도: GetTopProductsUseCase에서 사용
 * 쿼리: Native Query로 order_items + orders + products JOIN 결과 매핑
 */
public interface TopProductProjection {

    Long getProductId();

    String getProductName();

    Integer getSalesCount();

    Long getRevenue();
}
