package io.hhplus.ecommerce.presentation.api.product;

import io.hhplus.ecommerce.application.dto.product.*;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.presentation.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/products")
@Tag(name = "1. 상품", description = "상품 조회 API")
public class ProductController {

    // Mock 데이터 저장소
    private final Map<String, ProductData> productStore = new ConcurrentHashMap<>();

    public ProductController() {
        // 초기 Mock 데이터 생성
        productStore.put("P001", new ProductData("P001", "노트북", "고성능 게이밍 노트북", 890000L, 10, "전자제품", 150, 133500000L));
        productStore.put("P002", new ProductData("P002", "키보드", "기계식 키보드", 120000L, 50, "주변기기", 120, 14400000L));
        productStore.put("P003", new ProductData("P003", "모니터", "27인치 QHD 모니터", 450000L, 20, "전자제품", 80, 36000000L));
        productStore.put("P004", new ProductData("P004", "마우스", "무선 게이밍 마우스", 75000L, 100, "주변기기", 320, 24000000L));
        productStore.put("P005", new ProductData("P005", "헤드셋", "노이즈 캔슬링 헤드셋", 180000L, 30, "주변기기", 95, 17100000L));
    }

    /**
     * 1.1 상품 목록 조회
     * GET /products
     */
    @Operation(
        summary = "상품 목록 조회",
        description = "전체 상품 목록을 조회합니다. 카테고리 필터링 및 정렬 기능을 제공합니다."
    )
    @GetMapping
    public ApiResponse<ProductListResponse> getProducts(
        @Parameter(description = "카테고리 필터 (예: 전자제품, 주변기기)")
        @RequestParam(required = false) String category,
        @Parameter(description = "정렬 방식 (price, popularity, newest)")
        @RequestParam(required = false) String sort
    ) {
        log.info("GET /products - category: {}, sort: {}", category, sort);

        List<ProductData> products = new ArrayList<>(productStore.values());

        // 카테고리 필터링
        if (category != null && !category.isEmpty()) {
            products = products.stream()
                .filter(p -> p.category.equals(category))
                .collect(Collectors.toList());
        }

        // 정렬
        if ("price".equals(sort)) {
            products.sort((a, b) -> a.price.compareTo(b.price));
        } else if ("popularity".equals(sort)) {
            products.sort((a, b) -> b.salesCount.compareTo(a.salesCount));
        }

        List<ProductResponse> productResponses = products.stream()
            .map(p -> new ProductResponse(p.productId, p.name, p.price, p.stock, p.category))
            .collect(Collectors.toList());

        ProductListResponse response = new ProductListResponse(productResponses, productResponses.size());
        return ApiResponse.success(response);
    }

    /**
     * 1.2 상품 상세 조회
     * GET /products/{productId}
     */
    @Operation(
        summary = "상품 상세 조회",
        description = "특정 상품의 상세 정보를 조회합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "상품을 찾을 수 없음 (P001)",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> getProduct(
        @Parameter(description = "상품 ID (예: P001)", required = true)
        @PathVariable String productId
    ) {
        log.info("GET /products/{}", productId);

        ProductData product = productStore.get(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        ProductResponse response = new ProductResponse(
            product.productId,
            product.name,
            product.description,
            product.price,
            product.stock,
            product.category
        );

        return ApiResponse.success(response);
    }

    /**
     * 1.3 인기 상품 조회
     * GET /products/top
     */
    @Operation(
        summary = "인기 상품 조회",
        description = "최근 3일간 판매량 기준 Top 5 상품을 조회합니다. (실시간 집계)"
    )
    @GetMapping("/top")
    public ApiResponse<TopProductsResponse> getTopProducts() {
        log.info("GET /products/top");

        // 판매량 기준 Top 5 정렬
        List<PopularProductResponse> topProducts = productStore.values().stream()
            .sorted((a, b) -> b.salesCount.compareTo(a.salesCount))
            .limit(5)
            .map(p -> new PopularProductResponse(
                0, // rank는 나중에 설정
                p.productId,
                p.name,
                p.salesCount,
                p.revenue
            ))
            .collect(Collectors.toList());

        // rank 설정
        for (int i = 0; i < topProducts.size(); i++) {
            PopularProductResponse product = topProducts.get(i);
            topProducts.set(i, new PopularProductResponse(
                i + 1,
                product.getProductId(),
                product.getName(),
                product.getSalesCount(),
                product.getRevenue()
            ));
        }

        TopProductsResponse response = new TopProductsResponse("3days", topProducts);
        return ApiResponse.success(response);
    }

    // Mock 데이터 클래스
    private static class ProductData {
        String productId;
        String name;
        String description;
        Long price;
        Integer stock;
        String category;
        Integer salesCount;  // 판매량 (인기 상품용)
        Long revenue;        // 매출 (인기 상품용)

        ProductData(String productId, String name, String description, Long price, Integer stock, String category, Integer salesCount, Long revenue) {
            this.productId = productId;
            this.name = name;
            this.description = description;
            this.price = price;
            this.stock = stock;
            this.category = category;
            this.salesCount = salesCount;
            this.revenue = revenue;
        }
    }
}
