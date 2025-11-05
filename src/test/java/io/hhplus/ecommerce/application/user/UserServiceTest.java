package io.hhplus.ecommerce.application.user;

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
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");

        userRepository.save(user);

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
        String userId = "INVALID";

        // When & Then
        assertThatThrownBy(() -> userService.getUser(userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("포인트 충전 성공")
    void chargeBalance_성공() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");
        ChargeBalanceRequest request = new ChargeBalanceRequest(500000L);

        userRepository.save(user);

        // When
        ChargeBalanceResponse response = userService.chargeBalance(userId, request);

        // Then
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getBalance()).isEqualTo(500000L);
        assertThat(response.getChargedAmount()).isEqualTo(500000L);
        assertThat(response.getChargedAt()).isNotNull();

        User savedUser = userRepository.findById(userId).orElseThrow();
        assertThat(savedUser.getBalance()).isEqualTo(500000L);
    }

    @Test
    @DisplayName("포인트 충전 실패 - 존재하지 않는 사용자")
    void chargeBalance_실패_존재하지않는사용자() {
        // Given
        String userId = "INVALID";
        ChargeBalanceRequest request = new ChargeBalanceRequest(500000L);

        // When & Then
        assertThatThrownBy(() -> userService.chargeBalance(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("포인트 충전 실패 - 잘못된 충전 금액 (0 이하)")
    void chargeBalance_실패_잘못된금액() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");
        ChargeBalanceRequest request = new ChargeBalanceRequest(0L);

        userRepository.save(user);

        // When & Then
        assertThatThrownBy(() -> userService.chargeBalance(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CHARGE_AMOUNT);
    }

    @Test
    @DisplayName("포인트 충전 실패 - 음수 금액")
    void chargeBalance_실패_음수금액() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");
        ChargeBalanceRequest request = new ChargeBalanceRequest(-1000L);

        userRepository.save(user);

        // When & Then
        assertThatThrownBy(() -> userService.chargeBalance(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CHARGE_AMOUNT);
    }

    @Test
    @DisplayName("포인트 여러 번 충전 - 누적")
    void chargeBalance_여러번충전() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");

        userRepository.save(user);

        // When
        ChargeBalanceRequest request1 = new ChargeBalanceRequest(500000L);
        ChargeBalanceResponse response1 = userService.chargeBalance(userId, request1);

        // Then
        assertThat(response1.getBalance()).isEqualTo(500000L);

        User savedUser1 = userRepository.findById(userId).orElseThrow();
        assertThat(savedUser1.getBalance()).isEqualTo(500000L);

        // When
        ChargeBalanceRequest request2 = new ChargeBalanceRequest(300000L);
        ChargeBalanceResponse response2 = userService.chargeBalance(userId, request2);

        // Then
        assertThat(response2.getBalance()).isEqualTo(800000L);

        User savedUser2 = userRepository.findById(userId).orElseThrow();
        assertThat(savedUser2.getBalance()).isEqualTo(800000L);
    }
}
