package io.hhplus.ecommerce.domain.product;

public interface TopProductProjection {

    Long getProductId();

    String getProductName();

    Integer getSalesCount();

    Long getRevenue();
}
