package io.hhplus.ecommerce.infrastructure.redis;

import io.hhplus.ecommerce.config.TestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("CouponIssueReservationStore 테스트")
class CouponIssueReservationStoreTest {

    @Autowired
    private CouponIssueReservationStore couponReservationStore;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    @DisplayName("예약 성공 → 품절 → 보상 후 재예약 → 확정 후 중복 발급 불가")
    void reserve_compensate_confirm_flow() {
        Long couponId = 1L;
        Duration ttl = Duration.ofSeconds(30);

        // reserve: 2개만 가능
        var r1 = couponReservationStore.reserve(couponId, 101L, 2, ttl);
        var r2 = couponReservationStore.reserve(couponId, 102L, 2, ttl);
        var r3 = couponReservationStore.reserve(couponId, 103L, 2, ttl);

        assertThat(r1.result()).isEqualTo(CouponIssueReservationStore.ReserveResult.RESERVED);
        assertThat(r2.result()).isEqualTo(CouponIssueReservationStore.ReserveResult.RESERVED);
        assertThat(r3.result()).isEqualTo(CouponIssueReservationStore.ReserveResult.SOLD_OUT);

        // compensate: 1명 실패로 보상
        boolean compensated = couponReservationStore.compensateReservation(couponId, 102L);
        assertThat(compensated).isTrue();

        // reserve again: 보상 후에는 가능
        var r4 = couponReservationStore.reserve(couponId, 103L, 2, ttl);
        assertThat(r4.result()).isEqualTo(CouponIssueReservationStore.ReserveResult.RESERVED);

        // confirm: DB 반영 성공 가정
        boolean confirmed = couponReservationStore.confirmIssued(couponId, 101L, Duration.ofDays(7));
        assertThat(confirmed).isTrue();

        // already issued: 재시도 불가
        var r5 = couponReservationStore.reserve(couponId, 101L, 2, ttl);
        assertThat(r5.result()).isEqualTo(CouponIssueReservationStore.ReserveResult.ALREADY_ISSUED);
    }
}
