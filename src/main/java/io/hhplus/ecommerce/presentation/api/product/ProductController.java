package io.hhplus.ecommerce.presentation.api.product;

import io.hhplus.ecommerce.application.product.ProductService;
import io.hhplus.ecommerce.application.product.dto.ProductListResponse;
import io.hhplus.ecommerce.application.product.dto.ProductResponse;
import io.hhplus.ecommerce.application.product.dto.TopProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long productId) {
        ProductResponse response = productService.getProduct(productId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<ProductListResponse> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort
    ) {
        ProductListResponse response = productService.getProducts(category, sort);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/top")
    public ResponseEntity<TopProductResponse> getTopProducts() {
        TopProductResponse response = productService.getTopProducts();
        return ResponseEntity.ok(response);
    }
}
