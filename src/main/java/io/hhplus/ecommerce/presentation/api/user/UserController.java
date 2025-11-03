package io.hhplus.ecommerce.presentation.api.user;

import io.hhplus.ecommerce.application.dto.user.*;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.presentation.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/users")
@Tag(name = "5. 사용자", description = "사용자 포인트 관리 API")
public class UserController {

    // Mock 데이터 저장소
    private final Map<String, UserData> userStore = new ConcurrentHashMap<>();

    public UserController() {
        // 사용자 초기화
        userStore.put("U001", new UserData("U001", "김항해", 50000));
        userStore.put("U002", new UserData("U002", "이플러스", 100000));
        userStore.put("U003", new UserData("U003", "박백엔드", 30000));
    }

    /**
     * 5.1 잔액 조회
     * GET /users/{userId}/balance
     */
    @Operation(
        summary = "잔액 조회",
        description = "사용자의 현재 포인트 잔액을 조회합니다."
    )
    @GetMapping("/{userId}/balance")
    public ApiResponse<BalanceResponse> getBalance(
        @Parameter(description = "사용자 ID", required = true)
        @PathVariable String userId
    ) {
        log.info("GET /users/{}/balance", userId);

        // 사용자 조회
        UserData user = userStore.get(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        BalanceResponse response = new BalanceResponse(userId, user.balance);
        return ApiResponse.success(response);
    }

    /**
     * 5.2 잔액 충전
     * POST /users/{userId}/balance/charge
     */
    @Operation(
        summary = "잔액 충전",
        description = "사용자의 포인트를 충전합니다. 최소 충전 금액은 1000원입니다."
    )
    @PostMapping("/{userId}/balance/charge")
    public ApiResponse<ChargeBalanceResponse> chargeBalance(
        @Parameter(description = "사용자 ID", required = true)
        @PathVariable String userId,
        @Valid @RequestBody ChargeBalanceRequest request
    ) {
        log.info("POST /users/{}/balance/charge - amount: {}", userId, request.getAmount());

        // 사용자 조회
        UserData user = userStore.get(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 잔액 충전
        user.balance += request.getAmount();
        LocalDateTime chargedAt = LocalDateTime.now();

        ChargeBalanceResponse response = new ChargeBalanceResponse(
            userId,
            request.getAmount(),
            user.balance,
            chargedAt
        );

        return ApiResponse.success(response);
    }

    // Mock 데이터 클래스
    private static class UserData {
        String userId;
        String name;
        Integer balance;

        UserData(String userId, String name, Integer balance) {
            this.userId = userId;
            this.name = name;
            this.balance = balance;
        }
    }
}
