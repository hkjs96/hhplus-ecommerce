package io.hhplus.ecommerce.application.product;

import io.hhplus.ecommerce.application.product.dto.ProductListResponse;
import io.hhplus.ecommerce.application.product.dto.ProductResponse;
import io.hhplus.ecommerce.application.product.dto.TopProductResponse;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public ProductResponse getProduct(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PRODUCT_NOT_FOUND,
                        "상품을 찾을 수 없습니다. productId: " + productId
                ));

        return ProductResponse.from(product);
    }

    public ProductListResponse getProducts(String category, String sort) {
        List<Product> products = productRepository.findAll();

        Stream<Product> productStream = products.stream();
        if (category != null && !category.isEmpty()) {
            productStream = productStream.filter(p -> p.getCategory().equals(category));
        }

        if (sort != null) {
            productStream = switch (sort) {
                case "price", "price_asc" -> productStream.sorted(Comparator.comparing(Product::getPrice));
                case "price_desc" -> productStream.sorted(Comparator.comparing(Product::getPrice).reversed());
                case "newest" -> productStream.sorted(Comparator.comparing(Product::getCreatedAt).reversed());
                default -> productStream;
            };
        }

        List<ProductResponse> productResponses = productStream
                .map(ProductResponse::from)
                .toList();

        return ProductListResponse.of(productResponses);
    }

    public TopProductResponse getTopProducts() {
        return TopProductResponse.of(List.of());
    }
}
