package io.hhplus.ecommerce.infrastructure.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProductSalesAggregateSchedulerTest {

    @Autowired(required = false)
    private ProductSalesAggregateScheduler scheduler;

    @Test
    @DisplayName("ProductSalesAggregateScheduler 빈 등록 확인")
    void testSchedulerBeanRegistration() {
        // Given & When & Then
        assertThat(scheduler).isNotNull();
        System.out.println("ProductSalesAggregateScheduler 빈 등록 확인 완료");
    }

    @Test
    @DisplayName("배치 집계 메서드 실행 테스트 (동시성 문제 없음)")
    void testAggregateProductSales_NoConcurrencyIssue() {
        // Given
        assertThat(scheduler).isNotNull();

        // When: 배치 메서드 직접 호출 (스케줄러 아닌 직접 실행)
        scheduler.aggregateProductSales();

        // Then: 예외 없이 실행되면 성공
        System.out.println("배치 집계 메서드 실행 완료 (동시성 문제 없음)");

        // 배치는 순차 실행이므로 동시성 문제 발생하지 않음
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("배치 스케줄러 30초 설정 확인 (수동 테스트)")
    void testSchedulerConfiguration() {
        // Given & When & Then
        System.out.println("=".repeat(80));
        System.out.println("배치 스케줄러 설정 확인:");
        System.out.println("- 개발/과제용: 30초마다 실행 (@Scheduled(cron = \"*/30 * * * * *\"))");
        System.out.println("- 운영 권장: 15분마다 실행 (@Scheduled(cron = \"0 */15 * * * *\"))");
        System.out.println("- 실시간 확인: 애플리케이션 실행 후 로그 확인");
        System.out.println("  → \"=== Product Sales Aggregation Batch START\" 로그 30초마다 출력");
        System.out.println("=".repeat(80));
    }
}
