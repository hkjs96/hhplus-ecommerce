package io.hhplus.ecommerce.domain.product;

import io.hhplus.ecommerce.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(
    name = "product_sales_aggregates",
    indexes = {
        @Index(name = "idx_date_sales", columnList = "aggregation_date, sales_count DESC"),
        @Index(name = "idx_product_date", columnList = "product_id, aggregation_date")
    }
)
@Getter
@NoArgsConstructor
public class ProductSalesAggregate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "aggregation_date", nullable = false)
    private LocalDate aggregationDate;  // 집계 기준일

    @Column(name = "sales_count", nullable = false)
    private Integer salesCount;  // 판매 건수

    @Column(name = "revenue", nullable = false)
    private Long revenue;  // 매출액

    public static ProductSalesAggregate create(
        Long productId,
        String productName,
        LocalDate aggregationDate,
        Integer salesCount,
        Long revenue
    ) {
        ProductSalesAggregate aggregate = new ProductSalesAggregate();
        aggregate.productId = productId;
        aggregate.productName = productName;
        aggregate.aggregationDate = aggregationDate;
        aggregate.salesCount = salesCount;
        aggregate.revenue = revenue;
        return aggregate;
    }

    public void update(Integer salesCount, Long revenue) {
        this.salesCount = salesCount;
        this.revenue = revenue;
    }
}
