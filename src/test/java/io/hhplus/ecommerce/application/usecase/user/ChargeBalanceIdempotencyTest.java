package io.hhplus.ecommerce.application.usecase.user;

import io.hhplus.ecommerce.application.user.dto.ChargeBalanceRequest;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceResponse;
import io.hhplus.ecommerce.application.user.listener.BalanceChargedEventHandler;
import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.user.ChargeBalanceIdempotency;
import io.hhplus.ecommerce.domain.user.ChargeBalanceIdempotencyRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 잔액 충전 멱등성 테스트
 * <p>
 * 시나리오:
 * 1. 같은 idempotencyKey로 두 번 요청 → 두 번째는 캐시된 응답 반환
 * 2. 다른 idempotencyKey로 두 번 요청 → 각각 성공
 * 3. 네트워크 타임아웃 후 재시도 → 캐시된 응답 반환
 * <p>
 * Testcontainers 사용 (MySQL + Redis)
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChargeBalanceIdempotencyTest {
    // TestContainersConfig에서 자동으로 설정됨

    @Autowired
    private ChargeBalanceUseCase chargeBalanceUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChargeBalanceIdempotencyRepository idempotencyRepository;

    @Autowired(required = false)
    private BalanceChargedEventHandler balanceChargedEventHandler;

    private User testUser;
    private static int userCounter = 0;

    @BeforeEach
    void setUp() {
        // 핸들러가 등록되어 있는지 확인
        if (balanceChargedEventHandler == null) {
            throw new IllegalStateException("BalanceChargedEventHandler가 Spring 컨텍스트에 등록되지 않았습니다!");
        }

        // 테스트 사용자 생성 (잔액 100,000원) - 매번 다른 이메일 사용
        testUser = User.create("test" + (++userCounter) + "@example.com", "테스트유저");
        testUser.charge(100_000L);
        testUser = userRepository.save(testUser); // save 자체가 트랜잭션으로 ID 부여
    }

    @Test
    @DisplayName("같은 idempotencyKey로 두 번 요청 → 중복 충전 방지")
    void 멱등성_키로_중복_충전_방지() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        ChargeBalanceRequest request = new ChargeBalanceRequest(10_000L, idempotencyKey);

        // When: 첫 번째 요청
        ChargeBalanceResponse response1 = chargeBalanceUseCase.execute(testUser.getId(), request);

        // Then: 충전 성공
        assertThat(response1.balance()).isEqualTo(110_000L);
        assertThat(response1.chargedAmount()).isEqualTo(10_000L);

        // 멱등성 완료 대기 (이벤트 핸들러 비동기 처리)
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    ChargeBalanceIdempotency idempotency = idempotencyRepository
                            .findByIdempotencyKey(idempotencyKey)
                            .orElseThrow();
                    assertThat(idempotency.isCompleted()).isTrue();
                });

        // When: 두 번째 요청 (같은 idempotencyKey)
        ChargeBalanceResponse response2 = chargeBalanceUseCase.execute(testUser.getId(), request);

        // Then: 캐시된 응답 반환 (중복 충전 안 됨!)
        assertThat(response2.balance()).isEqualTo(110_000L);  // 동일한 잔액
        assertThat(response2.chargedAmount()).isEqualTo(10_000L);

        // 최종 잔액 확인: 110,000원 (한 번만 충전됨)
        User user = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(user.getBalance()).isEqualTo(110_000L);
    }

    @Test
    @DisplayName("다른 idempotencyKey로 두 번 요청 → 각각 성공")
    void 다른_멱등성_키로_충전_각각_성공() {
        // Given
        String idempotencyKey1 = UUID.randomUUID().toString();
        String idempotencyKey2 = UUID.randomUUID().toString();

        // When: 첫 번째 요청
        ChargeBalanceRequest request1 = new ChargeBalanceRequest(10_000L, idempotencyKey1);
        ChargeBalanceResponse response1 = chargeBalanceUseCase.execute(testUser.getId(), request1);

        // Then
        assertThat(response1.balance()).isEqualTo(110_000L);

        // When: 두 번째 요청 (다른 idempotencyKey)
        ChargeBalanceRequest request2 = new ChargeBalanceRequest(20_000L, idempotencyKey2);
        ChargeBalanceResponse response2 = chargeBalanceUseCase.execute(testUser.getId(), request2);

        // Then: 각각 성공
        assertThat(response2.balance()).isEqualTo(130_000L);  // 110,000 + 20,000

        // 최종 잔액 확인: 130,000원
        User user = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(user.getBalance()).isEqualTo(130_000L);
    }

    @Test
    @DisplayName("캐시된 응답 반환 시 DB 업데이트 없음")
    void 캐시된_응답_반환시_DB_업데이트_없음() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        ChargeBalanceRequest request = new ChargeBalanceRequest(10_000L, idempotencyKey);

        // When: 첫 번째 요청
        chargeBalanceUseCase.execute(testUser.getId(), request);

        // 멱등성 완료 대기 (이벤트 핸들러 비동기 처리)
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    ChargeBalanceIdempotency idempotency = idempotencyRepository
                            .findByIdempotencyKey(idempotencyKey)
                            .orElseThrow();
                    assertThat(idempotency.isCompleted()).isTrue();
                });

        // 잔액 확인
        Long balanceAfterFirstRequest = userRepository.findById(testUser.getId()).orElseThrow().getBalance();
        assertThat(balanceAfterFirstRequest).isEqualTo(110_000L);

        // When: 두 번째 요청 (캐시된 응답 반환)
        chargeBalanceUseCase.execute(testUser.getId(), request);

        // Then: 잔액 변화 없음 (DB 업데이트 없음)
        Long balanceAfterSecondRequest = userRepository.findById(testUser.getId()).orElseThrow().getBalance();
        assertThat(balanceAfterSecondRequest).isEqualTo(110_000L);  // 동일
    }
}
