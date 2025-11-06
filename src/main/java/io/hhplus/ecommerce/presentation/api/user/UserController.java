package io.hhplus.ecommerce.presentation.api.user;

import io.hhplus.ecommerce.application.user.UserService;
import io.hhplus.ecommerce.application.user.dto.BalanceResponse;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceRequest;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceResponse;
import io.hhplus.ecommerce.application.user.dto.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(
            @NotBlank(message = "사용자 ID는 필수입니다") @PathVariable String userId
    ) {
        UserResponse response = userService.getUser(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @NotBlank(message = "사용자 ID는 필수입니다") @PathVariable String userId
    ) {
        BalanceResponse response = userService.getBalance(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/balance/charge")
    public ResponseEntity<ChargeBalanceResponse> chargeBalance(
            @NotBlank(message = "사용자 ID는 필수입니다") @PathVariable String userId,
            @Valid @RequestBody ChargeBalanceRequest request
    ) {
        ChargeBalanceResponse response = userService.chargeBalance(userId, request);
        return ResponseEntity.ok(response);
    }
}
