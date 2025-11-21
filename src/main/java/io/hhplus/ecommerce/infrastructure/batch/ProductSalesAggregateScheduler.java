package io.hhplus.ecommerce.infrastructure.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 상품 판매 집계 배치 스케줄러
 * <p>
 * 동시성 문제 없음: 배치 방식으로 처리하므로 동시 업데이트 발생하지 않음
 * - 7명 합의: 배치 방식 유지 (실시간 집계 불필요, 성능 우선)
 * <p>
 * 스케줄 설정:
 * - 개발/과제: 30초마다 실행 (빠른 확인용)
 * - 운영 권장: 15분마다 실행 (cron = "0 *\/15 * * * *")
 * <p>
 * 배치 작업 흐름:
 * 1. 전날 주문 데이터 집계
 * 2. ProductSalesAggregate 테이블에 저장/업데이트
 * 3. 실패 시 재시도 로직 (TODO)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSalesAggregateScheduler {

    // TODO: ProductSalesAggregateService 주입 필요
    // private final ProductSalesAggregateService aggregateService;

    /**
     * 상품 판매 집계 배치 (개발용: 30초, 운영 권장: 15분)
     * <p>
     * 개발/과제용: 30초마다 실행 (빠른 확인)
     * - @Scheduled(cron = "30 * * * * *")
     * <p>
     * 운영 권장: 15분마다 실행 (성능 고려)
     * - @Scheduled(cron = "0 *\/15 * * * *")
     * - 0분, 15분, 30분, 45분에 실행
     * <p>
     * 실시간 집계가 필요하다면:
     * - Redis Cache 병행 (5분마다 갱신)
     * - Event-driven 방식 (OrderCompletedEvent → Kafka → 실시간 집계)
     */
    @Scheduled(cron = "*/30 * * * * *")  // 30초마다 (개발용)
    // @Scheduled(cron = "0 */15 * * * *")  // 15분마다 (운영 권장)
    public void aggregateProductSales() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        log.info("=== Product Sales Aggregation Batch START (target date: {}) ===", targetDate);

        try {
            // TODO: 실제 집계 로직 구현
            // 1. 전날 주문 데이터 조회
            // 2. 상품별로 판매 수량, 매출액 집계
            // 3. ProductSalesAggregate 테이블에 저장/업데이트

            log.info("Product sales aggregation completed successfully for date: {}", targetDate);
            log.info("=== Product Sales Aggregation Batch END (SUCCESS) ===");

        } catch (Exception e) {
            log.error("Product sales aggregation failed for date: {}, error: {}",
                targetDate, e.getMessage(), e);
            log.error("=== Product Sales Aggregation Batch END (FAILED) ===");

            // TODO: 실패 처리
            // 1. 재시도 로직 (Exponential Backoff)
            // 2. Slack 알림
            // 3. Dead Letter Queue에 저장
        }
    }
}
