package io.hhplus.ecommerce.presentation.api.user;

import io.hhplus.ecommerce.application.usecase.user.ChargeBalanceUseCase;
import io.hhplus.ecommerce.application.usecase.user.GetBalanceUseCase;
import io.hhplus.ecommerce.application.usecase.user.GetUserUseCase;
import io.hhplus.ecommerce.application.user.dto.BalanceResponse;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceRequest;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceResponse;
import io.hhplus.ecommerce.application.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final GetUserUseCase getUserUseCase;
    private final GetBalanceUseCase getBalanceUseCase;
    private final ChargeBalanceUseCase chargeBalanceUseCase;

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(
            @PathVariable Long userId
    ) {
        UserResponse response = getUserUseCase.execute(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable Long userId
    ) {
        BalanceResponse response = getBalanceUseCase.execute(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/balance/charge")
    public ResponseEntity<ChargeBalanceResponse> chargeBalance(
            @PathVariable Long userId,
            @Valid @RequestBody ChargeBalanceRequest request
    ) {
        ChargeBalanceResponse response = chargeBalanceUseCase.execute(userId, request);
        return ResponseEntity.ok(response);
    }
}
