package io.hhplus.ecommerce.application.product;

import io.hhplus.ecommerce.application.product.dto.ProductListResponse;
import io.hhplus.ecommerce.application.product.dto.ProductResponse;
import io.hhplus.ecommerce.application.product.dto.TopProductResponse;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.infrastructure.persistence.order.InMemoryOrderItemRepository;
import io.hhplus.ecommerce.infrastructure.persistence.order.InMemoryOrderRepository;
import io.hhplus.ecommerce.infrastructure.persistence.product.InMemoryProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ProductServiceTest {

    private ProductRepository productRepository;
    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        orderRepository = new InMemoryOrderRepository();
        orderItemRepository = new InMemoryOrderItemRepository();
        productService = new ProductService(productRepository, orderRepository, orderItemRepository);
    }

    @Test
    @DisplayName("상품 조회 성공")
    void getProduct_성공() {
        // Given
        String productId = "P001";
        Product product = Product.create(productId, "노트북", "고성능 노트북", 890000L, "전자제품", 10);

        productRepository.save(product);

        // When
        ProductResponse response = productService.getProduct(productId);

        // Then
        assertThat(response.getProductId()).isEqualTo(productId);
        assertThat(response.getName()).isEqualTo("노트북");
        assertThat(response.getPrice()).isEqualTo(890000L);
        assertThat(response.getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("상품 조회 실패 - 존재하지 않는 상품")
    void getProduct_실패_존재하지않는상품() {
        // Given
        String productId = "INVALID";

        // When & Then
        assertThatThrownBy(() -> productService.getProduct(productId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("상품 목록 조회 성공")
    void getProducts_성공() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Product p1 = new Product("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10, now, now);
        Product p2 = new Product("P002", "키보드", "기계식 키보드", 120000L, "주변기기", 50, now, now);

        productRepository.save(p1);
        productRepository.save(p2);

        // When
        ProductListResponse response = productService.getProducts(null, null);

        // Then
        assertThat(response.getProducts()).hasSize(2);
        assertThat(response.getTotalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("상품 목록 조회 - 카테고리 필터링")
    void getProducts_카테고리필터링() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Product p1 = new Product("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10, now, now);
        Product p2 = new Product("P002", "키보드", "기계식 키보드", 120000L, "주변기기", 50, now, now);
        Product p3 = new Product("P003", "마우스", "무선 마우스", 45000L, "주변기기", 30, now, now);

        productRepository.save(p1);
        productRepository.save(p2);
        productRepository.save(p3);

        // When
        ProductListResponse response = productService.getProducts("주변기기", null);

        // Then
        assertThat(response.getProducts()).hasSize(2);
        assertThat(response.getProducts())
                .extracting("category")
                .containsOnly("주변기기");
    }

    @Test
    @DisplayName("상품 목록 조회 - 가격순 정렬")
    void getProducts_가격순정렬() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Product p1 = new Product("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10, now, now);
        Product p2 = new Product("P002", "키보드", "기계식 키보드", 120000L, "주변기기", 50, now, now);
        Product p3 = new Product("P003", "마우스", "무선 마우스", 45000L, "주변기기", 30, now, now);

        productRepository.save(p1);
        productRepository.save(p2);
        productRepository.save(p3);

        // When
        ProductListResponse response = productService.getProducts(null, "price");

        // Then
        assertThat(response.getProducts()).hasSize(3);
        assertThat(response.getProducts())
                .extracting("price")
                .containsExactly(45000L, 120000L, 890000L);
    }

    @Test
    @DisplayName("인기 상품 조회 - 주문이 없을 때 빈 리스트 반환")
    void getTopProducts_주문없음() {
        // When
        TopProductResponse response = productService.getTopProducts();

        // Then
        assertThat(response.getPeriod()).isEqualTo("3days");
        assertThat(response.getProducts()).isEmpty();
    }

    @Test
    @DisplayName("인기 상품 조회 - 최근 3일 내 완료된 주문만 집계")
    void getTopProducts_최근3일집계() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // 상품 생성
        Product p1 = new Product("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 100, now, now);
        Product p2 = new Product("P002", "키보드", "기계식 키보드", 120000L, "주변기기", 100, now, now);
        Product p3 = new Product("P003", "마우스", "무선 마우스", 45000L, "주변기기", 100, now, now);

        productRepository.save(p1);
        productRepository.save(p2);
        productRepository.save(p3);

        // 최근 3일 내 완료된 주문 생성
        io.hhplus.ecommerce.domain.order.Order order1 = io.hhplus.ecommerce.domain.order.Order.create("O001", "U001", 1780000L, 0L);
        order1.complete();  // COMPLETED 상태로 변경
        orderRepository.save(order1);

        io.hhplus.ecommerce.domain.order.OrderItem item1 = io.hhplus.ecommerce.domain.order.OrderItem.create("OI001", "O001", "P001", 2, 890000L);
        orderItemRepository.save(item1);

        io.hhplus.ecommerce.domain.order.Order order2 = io.hhplus.ecommerce.domain.order.Order.create("O002", "U002", 360000L, 0L);
        order2.complete();
        orderRepository.save(order2);

        io.hhplus.ecommerce.domain.order.OrderItem item2 = io.hhplus.ecommerce.domain.order.OrderItem.create("OI002", "O002", "P002", 3, 120000L);
        orderItemRepository.save(item2);

        // When
        TopProductResponse response = productService.getTopProducts();

        // Then
        assertThat(response.getPeriod()).isEqualTo("3days");
        assertThat(response.getProducts()).hasSize(2);

        // 첫 번째: 키보드 (수량 3개)
        assertThat(response.getProducts().get(0).getRank()).isEqualTo(1);
        assertThat(response.getProducts().get(0).getProductId()).isEqualTo("P002");
        assertThat(response.getProducts().get(0).getName()).isEqualTo("키보드");
        assertThat(response.getProducts().get(0).getSalesCount()).isEqualTo(3);
        assertThat(response.getProducts().get(0).getRevenue()).isEqualTo(360000L);

        // 두 번째: 노트북 (수량 2개)
        assertThat(response.getProducts().get(1).getRank()).isEqualTo(2);
        assertThat(response.getProducts().get(1).getProductId()).isEqualTo("P001");
        assertThat(response.getProducts().get(1).getName()).isEqualTo("노트북");
        assertThat(response.getProducts().get(1).getSalesCount()).isEqualTo(2);
        assertThat(response.getProducts().get(1).getRevenue()).isEqualTo(1780000L);
    }

    @Test
    @DisplayName("인기 상품 조회 - PENDING 상태 주문은 제외")
    void getTopProducts_PENDING주문제외() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        Product p1 = new Product("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 100, now, now);
        productRepository.save(p1);

        // PENDING 상태 주문
        io.hhplus.ecommerce.domain.order.Order order1 = io.hhplus.ecommerce.domain.order.Order.create("O001", "U001", 890000L, 0L);
        orderRepository.save(order1);  // PENDING 상태 그대로

        io.hhplus.ecommerce.domain.order.OrderItem item1 = io.hhplus.ecommerce.domain.order.OrderItem.create("OI001", "O001", "P001", 1, 890000L);
        orderItemRepository.save(item1);

        // When
        TopProductResponse response = productService.getTopProducts();

        // Then
        assertThat(response.getProducts()).isEmpty();
    }

    @Test
    @DisplayName("인기 상품 조회 - Top 5만 반환")
    void getTopProducts_최대5개() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // 6개 상품 생성 및 판매
        for (int i = 1; i <= 6; i++) {
            Product product = new Product("P00" + i, "상품" + i, "설명" + i, 100000L, "카테고리", 100, now, now);
            productRepository.save(product);

            io.hhplus.ecommerce.domain.order.Order order = io.hhplus.ecommerce.domain.order.Order.create("O00" + i, "U001", (long) (100000 * i), 0L);
            order.complete();
            orderRepository.save(order);

            // 판매 수량을 역순으로 (P006이 가장 많이 팔림)
            io.hhplus.ecommerce.domain.order.OrderItem item = io.hhplus.ecommerce.domain.order.OrderItem.create("OI00" + i, "O00" + i, "P00" + i, 7 - i, 100000L);
            orderItemRepository.save(item);
        }

        // When
        TopProductResponse response = productService.getTopProducts();

        // Then
        assertThat(response.getProducts()).hasSize(5);  // 최대 5개만
        assertThat(response.getProducts().get(0).getSalesCount()).isEqualTo(6);  // P001: 6개
        assertThat(response.getProducts().get(4).getSalesCount()).isEqualTo(2);  // P005: 2개
    }
}
