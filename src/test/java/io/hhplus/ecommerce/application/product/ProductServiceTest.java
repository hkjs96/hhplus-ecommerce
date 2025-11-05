package io.hhplus.ecommerce.application.product;

import io.hhplus.ecommerce.application.product.dto.ProductListResponse;
import io.hhplus.ecommerce.application.product.dto.ProductResponse;
import io.hhplus.ecommerce.application.product.dto.TopProductResponse;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ProductService 단위 테스트
 * - Mock Repository 사용
 * - 비즈니스 플로우 검증
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("상품 조회 성공")
    void getProduct_성공() {
        // Given
        String productId = "P001";
        Product product = Product.create(productId, "노트북", "고성능 노트북", 890000L, "전자제품", 10);

        when(productRepository.findById(productId))
                .thenReturn(Optional.of(product));

        // When
        ProductResponse response = productService.getProduct(productId);

        // Then
        assertThat(response.getProductId()).isEqualTo(productId);
        assertThat(response.getName()).isEqualTo("노트북");
        assertThat(response.getPrice()).isEqualTo(890000L);
        assertThat(response.getStock()).isEqualTo(10);

        // 행위 검증
        verify(productRepository).findById(productId);
    }

    @Test
    @DisplayName("상품 조회 실패 - 존재하지 않는 상품")
    void getProduct_실패_존재하지않는상품() {
        // Given
        String productId = "INVALID";
        when(productRepository.findById(productId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> productService.getProduct(productId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);

        verify(productRepository).findById(productId);
    }

    @Test
    @DisplayName("상품 목록 조회 성공")
    void getProducts_성공() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Product p1 = new Product("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10, now, now);
        Product p2 = new Product("P002", "키보드", "기계식 키보드", 120000L, "주변기기", 50, now, now);

        when(productRepository.findAll())
                .thenReturn(List.of(p1, p2));

        // When
        ProductListResponse response = productService.getProducts(null, null);

        // Then
        assertThat(response.getProducts()).hasSize(2);
        assertThat(response.getTotalCount()).isEqualTo(2);

        verify(productRepository).findAll();
    }

    @Test
    @DisplayName("상품 목록 조회 - 카테고리 필터링")
    void getProducts_카테고리필터링() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Product p1 = new Product("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10, now, now);
        Product p2 = new Product("P002", "키보드", "기계식 키보드", 120000L, "주변기기", 50, now, now);
        Product p3 = new Product("P003", "마우스", "무선 마우스", 45000L, "주변기기", 30, now, now);

        when(productRepository.findAll())
                .thenReturn(List.of(p1, p2, p3));

        // When
        ProductListResponse response = productService.getProducts("주변기기", null);

        // Then
        assertThat(response.getProducts()).hasSize(2);
        assertThat(response.getProducts())
                .extracting("category")
                .containsOnly("주변기기");

        verify(productRepository).findAll();
    }

    @Test
    @DisplayName("상품 목록 조회 - 가격순 정렬")
    void getProducts_가격순정렬() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Product p1 = new Product("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10, now, now);
        Product p2 = new Product("P002", "키보드", "기계식 키보드", 120000L, "주변기기", 50, now, now);
        Product p3 = new Product("P003", "마우스", "무선 마우스", 45000L, "주변기기", 30, now, now);

        when(productRepository.findAll())
                .thenReturn(List.of(p1, p2, p3));

        // When
        ProductListResponse response = productService.getProducts(null, "price");

        // Then
        assertThat(response.getProducts()).hasSize(3);
        assertThat(response.getProducts())
                .extracting("price")
                .containsExactly(45000L, 120000L, 890000L);  // 가격 오름차순

        verify(productRepository).findAll();
    }

    @Test
    @DisplayName("인기 상품 조회 - Week 3 빈 리스트 반환")
    void getTopProducts_Week3빈리스트() {
        // When
        TopProductResponse response = productService.getTopProducts();

        // Then
        assertThat(response.getPeriod()).isEqualTo("3days");
        assertThat(response.getProducts()).isEmpty();

        // Week 3에서는 Repository 호출 없음 (주문 데이터 없음)
        verifyNoInteractions(productRepository);
    }
}
