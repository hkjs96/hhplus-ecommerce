package io.hhplus.ecommerce.application.usecase.user;

import io.hhplus.ecommerce.application.user.dto.ChargeBalanceRequest;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceResponse;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class ChargeBalanceUseCaseTest {

    @Autowired
    private ChargeBalanceUseCase chargeBalanceUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChargeBalanceIdempotencyRepository idempotencyRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.user.JpaUserRepository jpaUserRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.user.JpaChargeBalanceIdempotencyRepository jpaIdempotencyRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // 테이블 초기화
        jpaIdempotencyRepository.deleteAll();
        jpaUserRepository.deleteAll();

        // Redis 전체 Flush (테스트 간 오염 방지)
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("정상 충전 - 잔액 증가 및 멱등성 기록")
    void shouldChargeBalanceSuccessfully() throws InterruptedException {
        // given
        User user = User.create("balance@test.com", "balance-user");
        user.charge(10000L); // 초기 잔액 10,000원
        userRepository.save(user);

        String idempotencyKey = "charge-key-001";
        ChargeBalanceRequest request = new ChargeBalanceRequest(5000L, idempotencyKey);

        // when
        ChargeBalanceResponse response = chargeBalanceUseCase.execute(user.getId(), request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(user.getId());
        assertThat(response.balance()).isEqualTo(15000L); // 10,000 + 5,000
        assertThat(response.chargedAmount()).isEqualTo(5000L);

        // @TransactionalEventListener(AFTER_COMMIT)는 비동기이므로 대기
        Thread.sleep(500);

        // 멱등성 기록 확인
        Optional<ChargeBalanceIdempotency> idempotencyOpt =
            idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        assertThat(idempotencyOpt).isPresent();
        assertThat(idempotencyOpt.get().isCompleted()).isTrue();

        // DB 잔액 확인
        User updatedUser = userRepository.findByIdOrThrow(user.getId());
        assertThat(updatedUser.getBalance()).isEqualTo(15000L);
    }

    @Test
    @DisplayName("멱등성 보장 - 동일 IdempotencyKey로 재요청 시 캐시된 응답 반환")
    void shouldReturnCachedResponseForSameIdempotencyKey() throws InterruptedException {
        // given
        User user = User.create("balance@test.com", "balance-user");
        user.charge(10000L);
        userRepository.save(user);

        String idempotencyKey = "charge-key-002";
        ChargeBalanceRequest request = new ChargeBalanceRequest(5000L, idempotencyKey);

        // 첫 번째 충전
        ChargeBalanceResponse firstResponse = chargeBalanceUseCase.execute(user.getId(), request);

        // @TransactionalEventListener(AFTER_COMMIT)는 비동기이므로 대기 (PROCESSING → COMPLETED)
        Thread.sleep(500);

        // when - 동일 IdempotencyKey로 재요청
        ChargeBalanceResponse secondResponse = chargeBalanceUseCase.execute(user.getId(), request);

        // then
        assertThat(secondResponse.balance()).isEqualTo(firstResponse.balance());
        assertThat(secondResponse.chargedAmount()).isEqualTo(firstResponse.chargedAmount());

        // 잔액이 중복 증가하지 않았는지 확인
        User updatedUser = userRepository.findByIdOrThrow(user.getId());
        assertThat(updatedUser.getBalance()).isEqualTo(15000L); // 10,000 + 5,000 (한 번만)
    }

    @Test
    @DisplayName("사용자 없음 - 존재하지 않는 사용자 ID로 충전 시도")
    void shouldFailWhenUserNotFound() {
        // given
        Long nonExistentUserId = 99999L;
        String idempotencyKey = "charge-key-003";
        ChargeBalanceRequest request = new ChargeBalanceRequest(5000L, idempotencyKey);

        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(nonExistentUserId, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("잘못된 금액 - 음수 금액 충전 시도")
    void shouldFailWhenChargeNegativeAmount() {
        // given
        User user = User.create("balance@test.com", "balance-user");
        user.charge(10000L);
        userRepository.save(user);

        String idempotencyKey = "charge-key-004";
        ChargeBalanceRequest request = new ChargeBalanceRequest(-5000L, idempotencyKey);

        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(user.getId(), request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("잘못된 금액 - 0원 충전 시도")
    void shouldFailWhenChargeZeroAmount() {
        // given
        User user = User.create("balance@test.com", "balance-user");
        user.charge(10000L);
        userRepository.save(user);

        String idempotencyKey = "charge-key-005";
        ChargeBalanceRequest request = new ChargeBalanceRequest(0L, idempotencyKey);

        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(user.getId(), request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("초기 잔액 0원에서 충전")
    void shouldChargeFromZeroBalance() {
        // given
        User user = User.create("balance@test.com", "balance-user");
        // 초기 잔액 0원
        userRepository.save(user);

        String idempotencyKey = "charge-key-006";
        ChargeBalanceRequest request = new ChargeBalanceRequest(10000L, idempotencyKey);

        // when
        ChargeBalanceResponse response = chargeBalanceUseCase.execute(user.getId(), request);

        // then
        assertThat(response.balance()).isEqualTo(10000L);
        assertThat(response.chargedAmount()).isEqualTo(10000L);

        User updatedUser = userRepository.findByIdOrThrow(user.getId());
        assertThat(updatedUser.getBalance()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("여러 번 충전 - 각각 다른 IdempotencyKey")
    void shouldChargeMultipleTimesWithDifferentKeys() {
        // given
        User user = User.create("balance@test.com", "balance-user");
        user.charge(10000L); // 초기 10,000원
        userRepository.save(user);

        // when - 3번 충전 (각각 다른 IdempotencyKey)
        ChargeBalanceRequest request1 = new ChargeBalanceRequest(5000L, "key-1");
        ChargeBalanceRequest request2 = new ChargeBalanceRequest(3000L, "key-2");
        ChargeBalanceRequest request3 = new ChargeBalanceRequest(2000L, "key-3");

        chargeBalanceUseCase.execute(user.getId(), request1);
        chargeBalanceUseCase.execute(user.getId(), request2);
        ChargeBalanceResponse response3 = chargeBalanceUseCase.execute(user.getId(), request3);

        // then
        assertThat(response3.balance()).isEqualTo(20000L); // 10,000 + 5,000 + 3,000 + 2,000

        User updatedUser = userRepository.findByIdOrThrow(user.getId());
        assertThat(updatedUser.getBalance()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("대량 충전 - 큰 금액 충전")
    void shouldChargeLargeAmount() {
        // given
        User user = User.create("balance@test.com", "balance-user");
        userRepository.save(user);

        String idempotencyKey = "charge-key-large";
        ChargeBalanceRequest request = new ChargeBalanceRequest(1_000_000L, idempotencyKey); // 100만원

        // when
        ChargeBalanceResponse response = chargeBalanceUseCase.execute(user.getId(), request);

        // then
        assertThat(response.balance()).isEqualTo(1_000_000L);
        assertThat(response.chargedAmount()).isEqualTo(1_000_000L);
    }
}
