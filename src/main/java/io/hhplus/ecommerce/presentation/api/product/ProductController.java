package io.hhplus.ecommerce.presentation.api.product;

import io.hhplus.ecommerce.application.product.dto.ProductListResponse;
import io.hhplus.ecommerce.application.product.dto.ProductResponse;
import io.hhplus.ecommerce.application.product.dto.TopProductResponse;
import io.hhplus.ecommerce.application.usecase.product.GetProductUseCase;
import io.hhplus.ecommerce.application.usecase.product.GetProductsUseCase;
import io.hhplus.ecommerce.application.usecase.product.GetTopProductsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final GetProductUseCase getProductUseCase;
    private final GetProductsUseCase getProductsUseCase;
    private final GetTopProductsUseCase getTopProductsUseCase;

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long productId) {
        ProductResponse response = getProductUseCase.execute(productId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<ProductListResponse> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort
    ) {
        ProductListResponse response = getProductsUseCase.execute(category, sort);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/top")
    public ResponseEntity<TopProductResponse> getTopProducts() {
        TopProductResponse response = getTopProductsUseCase.execute();
        return ResponseEntity.ok(response);
    }
}
