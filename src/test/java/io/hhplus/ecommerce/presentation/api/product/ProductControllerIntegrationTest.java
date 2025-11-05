package io.hhplus.ecommerce.presentation.api.product;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        // Setup test products
        Product product1 = Product.create("P001", "노트북", "고성능 게이밍 노트북", 1500000L, "전자제품", 50);
        Product product2 = Product.create("P002", "마우스", "무선 게이밍 마우스", 80000L, "전자제품", 100);
        Product product3 = Product.create("P003", "키보드", "기계식 키보드", 120000L, "전자제품", 75);
        Product product4 = Product.create("P004", "모니터", "4K UHD 모니터", 500000L, "전자제품", 30);
        Product product5 = Product.create("P005", "의자", "게이밍 의자", 350000L, "가구", 20);

        productRepository.save(product1);
        productRepository.save(product2);
        productRepository.save(product3);
        productRepository.save(product4);
        productRepository.save(product5);
    }

    @Test
    @DisplayName("상품 조회 API - 성공")
    void getProduct_성공() throws Exception {
        mockMvc.perform(get("/api/products/P001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("P001"))
                .andExpect(jsonPath("$.name").value("노트북"))
                .andExpect(jsonPath("$.description").value("고성능 게이밍 노트북"))
                .andExpect(jsonPath("$.price").value(1500000L))
                .andExpect(jsonPath("$.category").value("전자제품"))
                .andExpect(jsonPath("$.stock").value(50));
    }

    @Test
    @DisplayName("상품 조회 API - 존재하지 않는 상품")
    void getProduct_실패_존재하지않는상품() throws Exception {
        mockMvc.perform(get("/api/products/INVALID_PRODUCT"))
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
                .andExpect(jsonPath("$.products[0].productId").value("P002")) // 80000 (마우스)
                .andExpect(jsonPath("$.products[1].productId").value("P003")); // 120000 (키보드)
    }

    @Test
    @DisplayName("인기 상품 조회 API - 성공")
    void getTopProducts_성공() throws Exception {
        mockMvc.perform(get("/api/products/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("3days"))
                .andExpect(jsonPath("$.products").isArray());
    }
}
