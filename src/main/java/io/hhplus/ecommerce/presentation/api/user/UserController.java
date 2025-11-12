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
            @PathVariable Long userId
    ) {
        UserResponse response = userService.getUser(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable Long userId
    ) {
        BalanceResponse response = userService.getBalance(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/balance/charge")
    public ResponseEntity<ChargeBalanceResponse> chargeBalance(
            @PathVariable Long userId,
            @Valid @RequestBody ChargeBalanceRequest request
    ) {
        ChargeBalanceResponse response = userService.chargeBalance(userId, request);
        return ResponseEntity.ok(response);
    }
}
