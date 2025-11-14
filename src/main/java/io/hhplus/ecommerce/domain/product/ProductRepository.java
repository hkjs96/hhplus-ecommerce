package io.hhplus.ecommerce.domain.product;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Optional<Product> findById(Long id);

    Optional<Product> findByProductCode(String productCode);

    List<Product> findAll();

    Product save(Product product);

    /**
     * 인기 상품 조회 (최근 3일간 판매량 기준 Top 5)
     * STEP 08 성능 최적화 Native Query
     */
    List<TopProductProjection> findTopProductsByPeriod();

    default Product findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.PRODUCT_NOT_FOUND,
                "상품을 찾을 수 없습니다. productId: " + id
            ));
    }

    default Product findByProductCodeOrThrow(String productCode) {
        return findByProductCode(productCode)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.PRODUCT_NOT_FOUND,
                "상품을 찾을 수 없습니다. productCode: " + productCode
            ));
    }
}
