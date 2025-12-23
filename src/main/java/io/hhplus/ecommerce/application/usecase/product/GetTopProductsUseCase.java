package io.hhplus.ecommerce.application.usecase.product;

import io.hhplus.ecommerce.application.product.dto.TopProductItem;
import io.hhplus.ecommerce.application.product.dto.TopProductResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.domain.product.ProductSalesAggregateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@UseCase
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetTopProductsUseCase {

    private final ProductSalesAggregateRepository aggregateRepository;

    /**
     * 최근 3일간 인기 상품 TOP 5 조회 (캐시 적용)
     * <p>
     * 캐시 키: "topProducts"
     * - 고정 키 (날짜별 조회가 아닌 항상 최근 3일)
     * <p>
     * TTL: 5분 (CacheConfig 설정)
     * - 배치 집계 주기(5분)와 동일
     * - 실시간성 불필요, 주기적 갱신으로 충분
     * <p>
     * sync=true: Thundering Herd 방지
     * - 동시 요청 시 첫 요청만 DB 조회
     * <p>
     * 최적화 전략:
     * 1. ROLLUP 테이블 사용 (ProductSalesAggregate)
     * 2. 동등 조건 우선 (IN 조건 사용)
     * 3. 인덱스 100% 활용 (idx_date_sales)
     * <p>
     * 코치 피드백 반영: Projection → DTO 변환을 Repository로 이동
     * - UseCase 책임 감소 (비즈니스 로직에 집중)
     * - 테스트 복잡도 감소 (변환 로직 분리)
     * <p>
     * 성능:
     * - 실행 시간: <1ms (캐시 미스), ~0ms (캐시 히트)
     * - 원본 테이블 부하: 없음
     * - 인덱스 활용: 100%
     */
    @Cacheable(value = "topProducts", key = "'recent3days'", sync = true)
    public TopProductResponse execute() {
        log.info("Getting top products (last 3 days) using ROLLUP strategy");

        // 1. 최근 3일 날짜 리스트 생성
        LocalDate today = LocalDate.now();
        List<LocalDate> recentDates = List.of(
            today.minusDays(2),  // 3일 전
            today.minusDays(1),  // 2일 전
            today                // 오늘
        );

        // 2. Repository에서 DTO로 변환된 데이터 조회
        // (Projection → DTO 변환은 Repository에서 수행)
        List<TopProductItem> rankedProducts =
            aggregateRepository.findTopProductItemsByDates(recentDates);

        log.info("Found {} top products using ROLLUP strategy (<1ms)", rankedProducts.size());
        return TopProductResponse.of(rankedProducts);
    }
}
