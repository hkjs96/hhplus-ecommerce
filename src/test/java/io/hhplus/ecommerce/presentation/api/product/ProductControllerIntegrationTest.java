package io.hhplus.ecommerce.presentation.api.product;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
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

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    private Long productId1;
    private Long productId2;
    private Long productId3;
    private Long productId4;
    private Long productId5;

    @BeforeEach
    void setUp() {
        // Clear existing data
        if (orderRepository instanceof io.hhplus.ecommerce.infrastructure.persistence.order.InMemoryOrderRepository) {
            ((io.hhplus.ecommerce.infrastructure.persistence.order.InMemoryOrderRepository) orderRepository).clear();
        }
        if (orderItemRepository instanceof io.hhplus.ecommerce.infrastructure.persistence.order.InMemoryOrderItemRepository) {
            ((io.hhplus.ecommerce.infrastructure.persistence.order.InMemoryOrderItemRepository) orderItemRepository).clear();
        }

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
        // Given: 완료된 주문 생성
        Order order1 = Order.create("O001", 1L, 1500000L, 0L);
        order1.complete();  // COMPLETED 상태
        Order savedOrder1 = orderRepository.save(order1);
        Long orderId1 = savedOrder1.getId();

        OrderItem item1 = OrderItem.create(orderId1, productId1, 5, 1500000L);  // 노트북 5개
        orderItemRepository.save(item1);

        Order order2 = Order.create("O002", 1L, 240000L, 0L);
        order2.complete();
        Order savedOrder2 = orderRepository.save(order2);
        Long orderId2 = savedOrder2.getId();

        OrderItem item2 = OrderItem.create(orderId2, productId3, 2, 120000L);  // 키보드 2개
        orderItemRepository.save(item2);

        Order order3 = Order.create("O003", 1L, 320000L, 0L);
        order3.complete();
        Order savedOrder3 = orderRepository.save(order3);
        Long orderId3 = savedOrder3.getId();

        OrderItem item3 = OrderItem.create(orderId3, productId2, 4, 80000L);  // 마우스 4개
        orderItemRepository.save(item3);

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
