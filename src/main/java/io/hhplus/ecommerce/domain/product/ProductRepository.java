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
