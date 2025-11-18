package io.hhplus.ecommerce.application.usecase.product;

import io.hhplus.ecommerce.application.product.dto.ProductResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@UseCase
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetProductUseCase {

    private final ProductRepository productRepository;

    public ProductResponse execute(Long productId) {
        log.info("Getting product detail for productId: {}", productId);

        Product product = productRepository.findByIdOrThrow(productId);
        return ProductResponse.from(product);
    }
}
