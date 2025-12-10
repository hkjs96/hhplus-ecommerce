package io.hhplus.ecommerce.domain.product;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "product_ranking_backup")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRankingBackup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;

    private String productName;

    private int salesCount;

    private int ranking;

    private LocalDate aggregatedDate;

    public ProductRankingBackup(Long productId, String productName, int salesCount, int ranking, LocalDate aggregatedDate) {
        this.productId = productId;
        this.productName = productName;
        this.salesCount = salesCount;
        this.ranking = ranking;
        this.aggregatedDate = aggregatedDate;
    }
}
