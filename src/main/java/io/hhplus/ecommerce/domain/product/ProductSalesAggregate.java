package io.hhplus.ecommerce.domain.product;

import io.hhplus.ecommerce.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * ProductSalesAggregate Entity
 *
 * 상품 판매 통계 집계 테이블 (Rollup Table)
 *
 * 목적:
 * - 인기 상품 조회 시 매번 order_items 전체를 스캔하지 않고 사전 집계된 데이터를 활용
 * - COUNT(*) + ORDER BY 대신 인덱스를 활용한 효율적인 조회
 *
 * 집계 전략:
 * - 일 단위로 상품별 판매 실적 집계
 * - 배치 작업으로 주기적 업데이트 (예: 매일 자정, 5분마다 등)
 *
 * 조회 최적화:
 * - aggregation_date + sales_count 복합 인덱스로 최근 3일 데이터를 빠르게 조회
 * - 계산 컬럼(sales_count)을 실제 컬럼으로 저장하여 정렬 시 인덱스 활용
 */
@Entity
@Table(
    name = "product_sales_aggregates",
    indexes = {
        @Index(name = "idx_date_sales", columnList = "aggregation_date, sales_count DESC"),
        @Index(name = "idx_product_date", columnList = "product_id, aggregation_date")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
