package io.hhplus.ecommerce.domain.product;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Product Entity 단위 테스트
 * Week 3: 핵심 비즈니스 로직 테스트 (재고 차감/복구)
 */
class ProductTest {

    @Test
    @DisplayName("재고 차감 성공")
    void decreaseStock_성공() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10);

        // When
        product.decreaseStock(3);

        // Then
        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("재고 차감 실패 - 재고 부족")
    void decreaseStock_재고부족_예외발생() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 5);

        // When & Then
        assertThatThrownBy(() -> product.decreaseStock(10))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);

        // 재고는 변경되지 않음
        assertThat(product.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("재고 차감 실패 - 수량이 0")
    void decreaseStock_수량0_예외발생() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10);

        // When & Then
        assertThatThrownBy(() -> product.decreaseStock(0))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("재고 차감 실패 - 수량이 음수")
    void decreaseStock_수량음수_예외발생() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10);

        // When & Then
        assertThatThrownBy(() -> product.decreaseStock(-1))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("재고 복구 성공")
    void increaseStock_성공() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 5);

        // When
        product.increaseStock(3);

        // Then
        assertThat(product.getStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("재고 복구 실패 - 수량이 0")
    void increaseStock_수량0_예외발생() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10);

        // When & Then
        assertThatThrownBy(() -> product.increaseStock(0))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("재고 복구 실패 - 수량이 음수")
    void increaseStock_수량음수_예외발생() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10);

        // When & Then
        assertThatThrownBy(() -> product.increaseStock(-1))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("재고 확인 - 충분함")
    void hasEnoughStock_충분함() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10);

        // When & Then
        assertThat(product.hasEnoughStock(5)).isTrue();
        assertThat(product.hasEnoughStock(10)).isTrue();
    }

    @Test
    @DisplayName("재고 확인 - 부족함")
    void hasEnoughStock_부족함() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10);

        // When & Then
        assertThat(product.hasEnoughStock(11)).isFalse();
    }
}
