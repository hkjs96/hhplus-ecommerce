package io.hhplus.ecommerce.application.user;

import io.hhplus.ecommerce.application.user.dto.BalanceResponse;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceRequest;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceResponse;
import io.hhplus.ecommerce.application.user.dto.UserResponse;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.persistence.user.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UserServiceTest {

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        userService = new UserService(userRepository);
    }

    @Test
    @DisplayName("사용자 조회 성공")
    void getUser_성공() {
        // Given
        User user = User.create("test@example.com", "김항해");
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        // When
        UserResponse response = userService.getUser(userId);

        // Then
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getUsername()).isEqualTo("김항해");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getBalance()).isEqualTo(0L);
    }

    @Test
    @DisplayName("사용자 조회 실패 - 존재하지 않는 사용자")
    void getUser_실패_존재하지않는사용자() {
        // Given
        Long invalidUserId = 99999L;

        // When & Then
        assertThatThrownBy(() -> userService.getUser(invalidUserId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("포인트 충전 성공")
    void chargeBalance_성공() {
        // Given
        User user = User.create("test@example.com", "김항해");
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        ChargeBalanceRequest request = new ChargeBalanceRequest(500000L);

        // When
        ChargeBalanceResponse response = userService.chargeBalance(userId, request);

        // Then
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getBalance()).isEqualTo(500000L);
        assertThat(response.getChargedAmount()).isEqualTo(500000L);
        assertThat(response.getChargedAt()).isNotNull();

        User reloadedUser = userRepository.findById(userId).orElseThrow();
        assertThat(reloadedUser.getBalance()).isEqualTo(500000L);
    }

    @Test
    @DisplayName("포인트 충전 실패 - 존재하지 않는 사용자")
    void chargeBalance_실패_존재하지않는사용자() {
        // Given
        Long invalidUserId = 99999L;
        ChargeBalanceRequest request = new ChargeBalanceRequest(500000L);

        // When & Then
        assertThatThrownBy(() -> userService.chargeBalance(invalidUserId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("포인트 충전 실패 - 잘못된 충전 금액 (0 이하)")
    void chargeBalance_실패_잘못된금액() {
        // Given
        User user = User.create("test@example.com", "김항해");
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        ChargeBalanceRequest request = new ChargeBalanceRequest(0L);

        // When & Then
        assertThatThrownBy(() -> userService.chargeBalance(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CHARGE_AMOUNT);
    }

    @Test
    @DisplayName("포인트 충전 실패 - 음수 금액")
    void chargeBalance_실패_음수금액() {
        // Given
        User user = User.create("test@example.com", "김항해");
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        ChargeBalanceRequest request = new ChargeBalanceRequest(-1000L);

        // When & Then
        assertThatThrownBy(() -> userService.chargeBalance(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CHARGE_AMOUNT);
    }

    @Test
    @DisplayName("포인트 여러 번 충전 - 누적")
    void chargeBalance_여러번충전() {
        // Given
        User user = User.create("test@example.com", "김항해");
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        // When
        ChargeBalanceRequest request1 = new ChargeBalanceRequest(500000L);
        ChargeBalanceResponse response1 = userService.chargeBalance(userId, request1);

        // Then
        assertThat(response1.getBalance()).isEqualTo(500000L);

        User reloadedUser1 = userRepository.findById(userId).orElseThrow();
        assertThat(reloadedUser1.getBalance()).isEqualTo(500000L);

        // When
        ChargeBalanceRequest request2 = new ChargeBalanceRequest(300000L);
        ChargeBalanceResponse response2 = userService.chargeBalance(userId, request2);

        // Then
        assertThat(response2.getBalance()).isEqualTo(800000L);

        User reloadedUser2 = userRepository.findById(userId).orElseThrow();
        assertThat(reloadedUser2.getBalance()).isEqualTo(800000L);
    }

    @Test
    @DisplayName("포인트 조회 성공")
    void getBalance_성공() {
        // Given
        User user = User.create("test@example.com", "김항해");
        user.charge(100000L);
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        // When
        BalanceResponse response = userService.getBalance(userId);

        // Then
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getBalance()).isEqualTo(100000L);
    }

    @Test
    @DisplayName("포인트 조회 실패 - 존재하지 않는 사용자")
    void getBalance_실패_존재하지않는사용자() {
        // Given
        Long invalidUserId = 99999L;

        // When & Then
        assertThatThrownBy(() -> userService.getBalance(invalidUserId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("포인트 조회 - 충전 후 잔액 확인")
    void getBalance_충전후_잔액확인() {
        // Given
        User user = User.create("test@example.com", "김항해");
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        // When - 초기 잔액 조회
        BalanceResponse initialBalance = userService.getBalance(userId);
        assertThat(initialBalance.getBalance()).isEqualTo(0L);

        // When - 충전
        ChargeBalanceRequest request = new ChargeBalanceRequest(500000L);
        userService.chargeBalance(userId, request);

        // Then - 충전 후 잔액 조회
        BalanceResponse afterChargeBalance = userService.getBalance(userId);
        assertThat(afterChargeBalance.getBalance()).isEqualTo(500000L);
    }
}
