package io.hhplus.ecommerce.domain.product;

import io.hhplus.ecommerce.application.product.dto.TopProductItem;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ProductSalesAggregate Repository Interface (Domain Layer)
 *
 * 인기 상품 조회를 위한 ROLLUP 전략 Repository
 *
 * 구현체: JpaProductSalesAggregateRepository (Infrastructure Layer)
 */
public interface ProductSalesAggregateRepository {

    /**
     * 상품 ID와 집계 날짜로 집계 데이터 조회
     */
    Optional<ProductSalesAggregate> findByProductIdAndAggregationDate(Long productId, LocalDate aggregationDate);

    /**
     * 특정 날짜의 인기 상품 TOP 5 조회 (동등 조건, 최고 성능)
     *
     * @param date 조회할 날짜
     * @return 인기 상품 TOP 5
     */
    List<TopProductProjection> findTopProductsByDate(LocalDate date);

    /**
     * 여러 날짜의 인기 상품 TOP 5 조회 (IN 조건)
     *
     * @param dates 조회할 날짜 리스트 (예: 최근 3일)
     * @return 인기 상품 TOP 5
     */
    List<TopProductProjection> findTopProductsByDates(List<LocalDate> dates);

    /**
     * 기간별 인기 상품 TOP 5 조회 (범위 조건)
     *
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 인기 상품 TOP 5
     */
    List<TopProductProjection> findTopProductsByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * 여러 날짜의 인기 상품 TOP 5 조회 (DTO 변환 포함)
     * <p>
     * 코치 피드백 반영: Projection → DTO 변환을 Repository에서 수행
     * - UseCase 책임 감소
     * - 테스트 복잡도 감소
     * - rank 자동 설정
     *
     * @param dates 조회할 날짜 리스트 (예: 최근 3일)
     * @return 인기 상품 TOP 5 (DTO, rank 포함)
     */
    List<TopProductItem> findTopProductItemsByDates(List<LocalDate> dates);

    /**
     * 집계 데이터 저장
     */
    ProductSalesAggregate save(ProductSalesAggregate aggregate);

    /**
     * 특정 날짜의 집계 데이터 삭제
     */
    void deleteByAggregationDate(LocalDate aggregationDate);
}
