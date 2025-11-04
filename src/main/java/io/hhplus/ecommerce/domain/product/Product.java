package io.hhplus.ecommerce.domain.product;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 상품 엔티티 (Rich Domain Model)
 * Week 3: Pure Java Entity (JPA 어노테이션 없음)
 *
 * 비즈니스 규칙:
 * - 재고 차감/복구 로직 포함
 * - 가격은 양수여야 함
 * - 재고는 0 이상이어야 함
 */
@Getter
@AllArgsConstructor
public class Product {

    private String id;
    private String name;
    private String description;
    private Long price;
    private String category;
    private Integer stock;  // Week 3: Product에 stock 직접 포함
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 상품 생성 (Factory Method)
     */
    public static Product create(String id, String name, String description, Long price, String category, Integer stock) {
        validatePrice(price);
        validateStock(stock);

        LocalDateTime now = LocalDateTime.now();
        return new Product(id, name, description, price, category, stock, now, now);
    }

    /**
     * 재고 차감 (비즈니스 로직)
     *
     * @param quantity 차감할 수량
     * @throws BusinessException 수량이 0 이하이거나 재고가 부족한 경우
     */
    public void decreaseStock(int quantity) {
        validateQuantity(quantity);
        validateSufficientStock(quantity);

        this.stock -= quantity;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재고 복구 (주문 취소 시)
     *
     * @param quantity 복구할 수량
     * @throws BusinessException 수량이 0 이하인 경우
     */
    public void increaseStock(int quantity) {
        validateQuantity(quantity);

        this.stock += quantity;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재고 확인
     */
    public boolean hasEnoughStock(int quantity) {
        return this.stock >= quantity;
    }

    /**
     * 상품 정보 업데이트
     */
    public void update(String name, String description, Long price, String category) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (price != null) {
            validatePrice(price);
            this.price = price;
        }
        if (category != null) {
            this.category = category;
        }
        this.updatedAt = LocalDateTime.now();
    }

    // ====================================
    // Validation Methods
    // ====================================

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        }
    }

    private void validateSufficientStock(int quantity) {
        if (this.stock < quantity) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_STOCK,
                String.format("재고 부족: 현재 재고 %d, 요청 수량 %d", this.stock, quantity)
            );
        }
    }

    private static void validatePrice(Long price) {
        if (price == null || price <= 0) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "가격은 0보다 커야 합니다"
            );
        }
    }

    private static void validateStock(Integer stock) {
        if (stock == null || stock < 0) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "재고는 0 이상이어야 합니다"
            );
        }
    }
}
