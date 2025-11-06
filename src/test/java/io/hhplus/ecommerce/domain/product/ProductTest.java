package io.hhplus.ecommerce.domain.product;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

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

    @Test
    @DisplayName("상품 생성 실패 - 가격이 null")
    void create_가격null_예외발생() {
        // When & Then
        assertThatThrownBy(() -> Product.create("P001", "노트북", "고성능 노트북", null, "전자제품", 10))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("상품 생성 실패 - 가격이 0")
    void create_가격0_예외발생() {
        // When & Then
        assertThatThrownBy(() -> Product.create("P001", "노트북", "고성능 노트북", 0L, "전자제품", 10))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("상품 생성 실패 - 가격이 음수")
    void create_가격음수_예외발생() {
        // When & Then
        assertThatThrownBy(() -> Product.create("P001", "노트북", "고성능 노트북", -1000L, "전자제품", 10))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("상품 생성 실패 - 재고가 null")
    void create_재고null_예외발생() {
        // When & Then
        assertThatThrownBy(() -> Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("상품 생성 실패 - 재고가 음수")
    void create_재고음수_예외발생() {
        // When & Then
        assertThatThrownBy(() -> Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", -1))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("상품 업데이트 성공 - 가격 변경")
    void update_가격변경_성공() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10);

        // When
        product.update(null, null, 950000L, null);

        // Then
        assertThat(product.getPrice()).isEqualTo(950000L);
    }

    @Test
    @DisplayName("상품 업데이트 실패 - 가격이 0")
    void update_가격0_예외발생() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10);

        // When & Then
        assertThatThrownBy(() -> product.update(null, null, 0L, null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("상품 업데이트 실패 - 가격이 음수")
    void update_가격음수_예외발생() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10);

        // When & Then
        assertThatThrownBy(() -> product.update(null, null, -1000L, null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }
}
