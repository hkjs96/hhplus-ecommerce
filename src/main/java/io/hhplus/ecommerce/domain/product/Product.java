package io.hhplus.ecommerce.domain.product;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "products",
    indexes = {
        @Index(name = "idx_product_code", columnList = "product_code"),
        @Index(name = "idx_category_created", columnList = "category, created_at")
    }
)
@Getter
@NoArgsConstructor
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_code", unique = true, length = 20, nullable = false)
    private String productCode;  // Business ID (외부 노출용, e.g., "PROD-001")

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Long price;

    @Column(length = 50)
    private String category;

    @Column(nullable = false)
    private Integer stock;  // Week 4: Product에 stock 직접 포함

    @Version
    private Long version;  // Optimistic Lock for concurrent stock updates

    public static Product create(String productCode, String name, String description, Long price, String category, Integer stock) {
        validateProductCode(productCode);
        validatePrice(price);
        validateStock(stock);

        Product product = new Product();
        product.productCode = productCode;
        product.name = name;
        product.description = description;
        product.price = price;
        product.category = category;
        product.stock = stock;
        // createdAt, updatedAt은 JPA Auditing이 자동 처리

        return product;
    }

    public void decreaseStock(int quantity) {
        validateQuantity(quantity);
        validateSufficientStock(quantity);

        this.stock -= quantity;
        // updatedAt은 JPA Auditing이 자동 처리
    }

    public void increaseStock(int quantity) {
        validateQuantity(quantity);

        this.stock += quantity;
        // updatedAt은 JPA Auditing이 자동 처리
    }

    public boolean hasEnoughStock(int quantity) {
        return this.stock >= quantity;
    }

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
        // updatedAt은 JPA Auditing이 자동 처리
    }

    // ====================================
    // Validation Methods
    // ====================================

    private static void validateProductCode(String productCode) {
        if (productCode == null || productCode.trim().isEmpty()) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "상품 코드는 필수입니다"
            );
        }
    }

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
