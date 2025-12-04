package io.hhplus.ecommerce.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 주요 비즈니스 메트릭을 수집하는 컴포넌트
 *
 * 수집 메트릭:
 * - orders_total: 주문 성공/실패 카운터
 * - order_duration_seconds: 주문 처리 시간 (P50, P95, P99)
 * - stock_errors_total: 재고 부족 에러 카운터
 * - coupon_issue_total: 쿠폰 발급 성공/실패 카운터
 * - payment_total: 결제 성공/실패 카운터
 */
@Component
public class MetricsCollector {

    private final MeterRegistry meterRegistry;

    // 주문 관련 메트릭
    private final Counter orderSuccessCounter;
    private final Counter orderFailureCounter;
    private final Timer orderDurationTimer;

    // 재고 관련 메트릭
    private final Counter stockErrorCounter;

    // 쿠폰 관련 메트릭
    private final Counter couponReservationSuccessCounter;  // 선착순 예약
    private final Counter couponReservationFailureCounter;
    private final Counter couponIssueSuccessCounter;        // 실제 발급
    private final Counter couponIssueFailureCounter;

    // 결제 관련 메트릭
    private final Counter paymentSuccessCounter;
    private final Counter paymentFailureCounter;
    private final Timer paymentDurationTimer;

    public MetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 주문 메트릭 초기화
        this.orderSuccessCounter = Counter.builder("orders_total")
                .tag("status", "success")
                .description("Total number of successful orders")
                .register(meterRegistry);

        this.orderFailureCounter = Counter.builder("orders_total")
                .tag("status", "failure")
                .description("Total number of failed orders")
                .register(meterRegistry);

        this.orderDurationTimer = Timer.builder("order_duration_seconds")
                .description("Order processing duration")
                .publishPercentiles(0.5, 0.95, 0.99)  // P50, P95, P99
                .register(meterRegistry);

        // 재고 메트릭 초기화
        this.stockErrorCounter = Counter.builder("stock_errors_total")
                .description("Total number of stock shortage errors")
                .register(meterRegistry);

        // 쿠폰 메트릭 초기화
        this.couponReservationSuccessCounter = Counter.builder("coupon_reservation_total")
                .tag("status", "success")
                .description("Total number of successful coupon reservations")
                .register(meterRegistry);

        this.couponReservationFailureCounter = Counter.builder("coupon_reservation_total")
                .tag("status", "failure")
                .description("Total number of failed coupon reservations")
                .register(meterRegistry);

        this.couponIssueSuccessCounter = Counter.builder("coupon_issue_total")
                .tag("status", "success")
                .description("Total number of successful coupon issues")
                .register(meterRegistry);

        this.couponIssueFailureCounter = Counter.builder("coupon_issue_total")
                .tag("status", "failure")
                .description("Total number of failed coupon issues")
                .register(meterRegistry);

        // 결제 메트릭 초기화
        this.paymentSuccessCounter = Counter.builder("payment_total")
                .tag("status", "success")
                .description("Total number of successful payments")
                .register(meterRegistry);

        this.paymentFailureCounter = Counter.builder("payment_total")
                .tag("status", "failure")
                .description("Total number of failed payments")
                .register(meterRegistry);

        this.paymentDurationTimer = Timer.builder("payment_duration_seconds")
                .description("Payment processing duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    // ============================================================
    // 주문 관련 메트릭
    // ============================================================

    public void recordOrderSuccess() {
        orderSuccessCounter.increment();
    }

    public void recordOrderFailure() {
        orderFailureCounter.increment();
    }

    public void recordOrderDuration(long startTimeMs) {
        long duration = System.currentTimeMillis() - startTimeMs;
        orderDurationTimer.record(duration, TimeUnit.MILLISECONDS);
    }

    public <T> T recordOrderDuration(OrderOperation<T> operation) throws Exception {
        return orderDurationTimer.recordCallable(() -> {
            try {
                return operation.execute();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ============================================================
    // 재고 관련 메트릭
    // ============================================================

    public void recordStockError() {
        stockErrorCounter.increment();
    }

    // ============================================================
    // 쿠폰 관련 메트릭
    // ============================================================

    public void recordCouponReservationSuccess() {
        couponReservationSuccessCounter.increment();
    }

    public void recordCouponReservationFailure() {
        couponReservationFailureCounter.increment();
    }

    public void recordCouponIssueSuccess() {
        couponIssueSuccessCounter.increment();
    }

    public void recordCouponIssueFailure() {
        couponIssueFailureCounter.increment();
    }

    // ============================================================
    // 결제 관련 메트릭
    // ============================================================

    public void recordPaymentSuccess() {
        paymentSuccessCounter.increment();
    }

    public void recordPaymentFailure() {
        paymentFailureCounter.increment();
    }

    public void recordPaymentDuration(long startTimeMs) {
        long duration = System.currentTimeMillis() - startTimeMs;
        paymentDurationTimer.record(duration, TimeUnit.MILLISECONDS);
    }

    public <T> T recordPaymentDuration(OrderOperation<T> operation) throws Exception {
        return paymentDurationTimer.recordCallable(() -> {
            try {
                return operation.execute();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ============================================================
    // 헬퍼 인터페이스
    // ============================================================

    @FunctionalInterface
    public interface OrderOperation<T> {
        T execute() throws Exception;
    }
}
