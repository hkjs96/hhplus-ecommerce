package io.hhplus.ecommerce.application.user;

import io.hhplus.ecommerce.application.user.dto.ChargeBalanceRequest;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceResponse;
import io.hhplus.ecommerce.application.user.dto.UserResponse;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UserService 단위 테스트
 * - Mock Repository 사용
 * - 포인트 충전 비즈니스 로직 검증
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("사용자 조회 성공")
    void getUser_성공() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // When
        UserResponse response = userService.getUser(userId);

        // Then
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getUsername()).isEqualTo("김항해");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getBalance()).isEqualTo(0L);

        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("사용자 조회 실패 - 존재하지 않는 사용자")
    void getUser_실패_존재하지않는사용자() {
        // Given
        String userId = "INVALID";
        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getUser(userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("포인트 충전 성공")
    void chargeBalance_성공() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");
        ChargeBalanceRequest request = new ChargeBalanceRequest(500000L);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        // When
        ChargeBalanceResponse response = userService.chargeBalance(userId, request);

        // Then
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getBalance()).isEqualTo(500000L);
        assertThat(response.getChargedAmount()).isEqualTo(500000L);
        assertThat(response.getChargedAt()).isNotNull();

        // Entity 상태 변경 확인
        assertThat(user.getBalance()).isEqualTo(500000L);

        verify(userRepository).findById(userId);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("포인트 충전 실패 - 존재하지 않는 사용자")
    void chargeBalance_실패_존재하지않는사용자() {
        // Given
        String userId = "INVALID";
        ChargeBalanceRequest request = new ChargeBalanceRequest(500000L);

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.chargeBalance(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(userRepository).findById(userId);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("포인트 충전 실패 - 잘못된 충전 금액 (0 이하)")
    void chargeBalance_실패_잘못된금액() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");
        ChargeBalanceRequest request = new ChargeBalanceRequest(0L);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // When & Then
        assertThatThrownBy(() -> userService.chargeBalance(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CHARGE_AMOUNT);

        verify(userRepository).findById(userId);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("포인트 충전 실패 - 음수 금액")
    void chargeBalance_실패_음수금액() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");
        ChargeBalanceRequest request = new ChargeBalanceRequest(-1000L);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // When & Then
        assertThatThrownBy(() -> userService.chargeBalance(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CHARGE_AMOUNT);

        verify(userRepository).findById(userId);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("포인트 여러 번 충전 - 누적")
    void chargeBalance_여러번충전() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        // When - 첫 번째 충전
        ChargeBalanceRequest request1 = new ChargeBalanceRequest(500000L);
        ChargeBalanceResponse response1 = userService.chargeBalance(userId, request1);

        // Then - 첫 번째 충전 확인
        assertThat(response1.getBalance()).isEqualTo(500000L);
        assertThat(user.getBalance()).isEqualTo(500000L);

        // When - 두 번째 충전
        ChargeBalanceRequest request2 = new ChargeBalanceRequest(300000L);
        ChargeBalanceResponse response2 = userService.chargeBalance(userId, request2);

        // Then - 두 번째 충전 확인 (누적)
        assertThat(response2.getBalance()).isEqualTo(800000L);
        assertThat(user.getBalance()).isEqualTo(800000L);

        verify(userRepository, times(2)).findById(userId);
        verify(userRepository, times(2)).save(user);
    }
}
