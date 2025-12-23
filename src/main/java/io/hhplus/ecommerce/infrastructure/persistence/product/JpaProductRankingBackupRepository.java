package io.hhplus.ecommerce.infrastructure.persistence.product;

import io.hhplus.ecommerce.domain.product.ProductRankingBackup;
import io.hhplus.ecommerce.domain.product.ProductRankingBackupRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface JpaProductRankingBackupRepository extends JpaRepository<ProductRankingBackup, Long>, ProductRankingBackupRepository {
    List<ProductRankingBackup> findByAggregatedDate(LocalDate date);

    @Transactional
    void deleteAllByAggregatedDate(LocalDate date);

    @Override
    default void saveAll(List<ProductRankingBackup> backups) {
        if (backups == null || backups.isEmpty()) {
            return;
        }
        // 기존 데이터 삭제 후 새로 저장
        deleteAllByAggregatedDate(backups.get(0).getAggregatedDate());
        backups.forEach(this::save);
    }
}
