package io.hhplus.ecommerce.domain.product;

import java.time.LocalDate;
import java.util.List;

public interface ProductRankingBackupRepository {
    List<ProductRankingBackup> findByAggregatedDate(LocalDate date);

    void saveAll(List<ProductRankingBackup> backups);
}
