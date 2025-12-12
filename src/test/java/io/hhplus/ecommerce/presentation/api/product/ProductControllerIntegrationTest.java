package io.hhplus.ecommerce.presentation.api.product;

import io.hhplus.ecommerce.config.TestContainersConfig;
import org.springframework.context.annotation.Import;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.product.ProductSalesAggregate;
import io.hhplus.ecommerce.domain.product.ProductSalesAggregateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestContainersConfig.class)

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductSalesAggregateRepository productSalesAggregateRepository;

    private Long productId1;
    private Long productId2;
    private Long productId3;
    private Long productId4;
    private Long productId5;

    @BeforeEach
    void setUp() {
        // Setup test products and extract IDs
        Product product1 = Product.create("P001", "노트북", "고성능 게이밍 노트북", 1500000L, "전자제품", 50);
        Product product2 = Product.create("P002", "마우스", "무선 게이밍 마우스", 80000L, "전자제품", 100);
        Product product3 = Product.create("P003", "키보드", "기계식 키보드", 120000L, "전자제품", 75);
        Product product4 = Product.create("P004", "모니터", "4K UHD 모니터", 500000L, "전자제품", 30);
        Product product5 = Product.create("P005", "의자", "게이밍 의자", 350000L, "가구", 20);

        productId1 = productRepository.save(product1).getId();
        productId2 = productRepository.save(product2).getId();
        productId3 = productRepository.save(product3).getId();
        productId4 = productRepository.save(product4).getId();
        productId5 = productRepository.save(product5).getId();
    }

    @Test
    @DisplayName("상품 조회 API - 성공")
    void getProduct_성공() throws Exception {
        mockMvc.perform(get("/api/products/" + productId1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId1))
                .andExpect(jsonPath("$.name").value("노트북"))
                .andExpect(jsonPath("$.description").value("고성능 게이밍 노트북"))
                .andExpect(jsonPath("$.price").value(1500000L))
                .andExpect(jsonPath("$.category").value("전자제품"))
                .andExpect(jsonPath("$.stock").value(50));
    }

    @Test
    @DisplayName("상품 조회 API - 존재하지 않는 상품")
    void getProduct_실패_존재하지않는상품() throws Exception {
        mockMvc.perform(get("/api/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    @DisplayName("상품 목록 조회 API - 전체 조회")
    void getProducts_전체조회() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products.length()").value(5))
                .andExpect(jsonPath("$.totalCount").value(5));
    }

    @Test
    @DisplayName("상품 목록 조회 API - 카테고리 필터")
    void getProducts_카테고리필터() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("category", "전자제품"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products.length()").value(4))
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.products[0].category").value("전자제품"));
    }

    @Test
    @DisplayName("상품 목록 조회 API - 가격 정렬")
    void getProducts_가격정렬() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].productId").value(productId2)) // 80000 (마우스)
                .andExpect(jsonPath("$.products[1].productId").value(productId3)); // 120000 (키보드)
    }

    @Test
    @DisplayName("인기 상품 조회 API - 최근 3일 판매량 기준 Top 5")
    void getTopProducts_실제집계() throws Exception {
        // Given: ProductSalesAggregate 데이터 생성 (Rollup 전략)
        // GetTopProductsUseCase는 ProductSalesAggregate 테이블을 조회하므로 집계 데이터를 직접 생성
        LocalDate today = LocalDate.now();

        // 노트북 - 오늘 5개 판매
        ProductSalesAggregate agg1 = ProductSalesAggregate.create(
                productId1, "노트북", today, 5, 7500000L
        );
        productSalesAggregateRepository.save(agg1);

        // 마우스 - 오늘 4개 판매
        ProductSalesAggregate agg2 = ProductSalesAggregate.create(
                productId2, "마우스", today, 4, 320000L
        );
        productSalesAggregateRepository.save(agg2);

        // 키보드 - 오늘 2개 판매
        ProductSalesAggregate agg3 = ProductSalesAggregate.create(
                productId3, "키보드", today, 2, 240000L
        );
        productSalesAggregateRepository.save(agg3);

        // When & Then
        mockMvc.perform(get("/api/products/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("3days"))
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products.length()").value(3))
                // 첫 번째: 노트북 (5개 판매)
                .andExpect(jsonPath("$.products[0].rank").value(1))
                .andExpect(jsonPath("$.products[0].productId").value(productId1))
                .andExpect(jsonPath("$.products[0].name").value("노트북"))
                .andExpect(jsonPath("$.products[0].salesCount").value(5))
                .andExpect(jsonPath("$.products[0].revenue").value(7500000L))
                // 두 번째: 마우스 (4개 판매)
                .andExpect(jsonPath("$.products[1].rank").value(2))
                .andExpect(jsonPath("$.products[1].productId").value(productId2))
                .andExpect(jsonPath("$.products[1].salesCount").value(4))
                // 세 번째: 키보드 (2개 판매)
                .andExpect(jsonPath("$.products[2].rank").value(3))
                .andExpect(jsonPath("$.products[2].productId").value(productId3))
                .andExpect(jsonPath("$.products[2].salesCount").value(2));
    }
}
