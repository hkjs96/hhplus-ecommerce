package io.hhplus.ecommerce.domain.product;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Optional<Product> findById(String id);

    List<Product> findAll();

    Product save(Product product);

    /**
     * ID로 Product를 조회하고, 존재하지 않으면 예외를 발생시킵니다.
     *
     * @param id Product ID
     * @return Product 엔티티
     * @throws BusinessException 상품을 찾을 수 없을 때
     */
    default Product findByIdOrThrow(String id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.PRODUCT_NOT_FOUND,
                "상품을 찾을 수 없습니다. productId: " + id
            ));
    }
}
