package io.hhplus.ecommerce.application.usecase.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceRequest;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceResponse;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.user.ChargeBalanceIdempotency;
import io.hhplus.ecommerce.domain.user.ChargeBalanceIdempotencyRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.redis.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 잔액 충전 UseCase
 * <p>
 * 동시성 제어: 분산락 + Optimistic Lock + 자동 재시도 (3중 방어)
 * - 1차 방어: 분산락 (인스턴스 간 동시성 제어)
 * - 2차 방어: Optimistic Lock (@Version, DB 레벨)
 * - 3차 방어: 자동 재시도 (일시적 충돌 해결)
 * <p>
 * 충전은 충돌 가능성이 낮고, 충돌 시 재시도 가능하므로 성능을 우선
 * - OptimisticLockingFailureException 발생 시 자동 재시도 (최대 10회)
 * - Exponential Backoff 전략: 50ms → 100ms → 200ms → 400ms ...
 * <p>
 * 제이 코치 피드백 반영:
 * "낙관적 락 충돌 시 자동 재시도 로직을 추가하면 더 안정적입니다."
 * <p>
 * ⚠️ Self-Invocation 문제 해결:
 * - @DistributedLock을 외부 메서드(execute)에 적용
 * - 내부 메서드(chargeBalanceInternal)는 @Transactional만 적용
 * - 재시도 로직은 분산락 획득 후 실행
 * <p>
 * 참고: 잔액 차감(결제)은 Pessimistic Lock 사용 (ProcessPaymentUseCase)
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class ChargeBalanceUseCase {

    private final UserRepository userRepository;
    private final OptimisticLockRetryService retryService;
    private final ChargeBalanceIdempotencyRepository idempotencyRepository;

    /**
     * 잔액 충전 (멱등성 보장)
     * <p>
     * 동시성 제어: 분산락 (1차 방어)
     * - 락 키: "balance:user:{userId}" (충전/차감/조회 모두 동일!)
     * - 여러 인스턴스 간 동시성 제어
     * - 락 획득 후 멱등성 체크 및 재시도 로직 실행
     * <p>
     * ⚠️ 중요: 잔액 충전/차감/조회는 동일한 락 키 사용 필수!
     * - 충전: "balance:user:{userId}" (이 메서드)
     * - 차감: "balance:user:{userId}" (PaymentTransactionService.reservePayment)
     * - 조회: "balance:user:{userId}" (GetBalanceUseCase)
     * - 서로 다른 키 사용 시 Lost Update 발생 위험!
     * <p>
     * 멱등성 보장: Idempotency Key + DB Unique Constraint
     * - 중복 요청 방지: 동일 키로 재시도 시 캐시된 응답 반환
     * - DB Unique Constraint로 동시 요청 차단
     * - 상태 관리: PROCESSING → COMPLETED
     */
    @DistributedLock(
            key = "'balance:user:' + #userId",
            waitTime = 10,
            leaseTime = 30
    )
    public ChargeBalanceResponse execute(Long userId, ChargeBalanceRequest request) {
        log.info("Charging balance for userId: {}, amount: {}, idempotencyKey: {}",
                userId, request.amount(), request.idempotencyKey());

        // 1. 멱등성 키 조회
        Optional<ChargeBalanceIdempotency> existingIdempotency =
                idempotencyRepository.findByIdempotencyKey(request.idempotencyKey());

        if (existingIdempotency.isPresent()) {
            ChargeBalanceIdempotency idempotency = existingIdempotency.get();

            // 1-1. 이미 완료된 요청 → 캐시된 응답 반환
            if (idempotency.isCompleted()) {
                log.info("Returning cached response for idempotencyKey: {}", request.idempotencyKey());
                return deserializeResponse(idempotency.getResponsePayload());
            }

            // 1-2. 처리 중인 요청 → 에러 (다른 요청이 처리 중)
            if (idempotency.isProcessing()) {
                throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "이미 처리 중인 요청입니다. idempotencyKey: " + request.idempotencyKey()
                );
            }

            // 1-3. 실패했거나 만료된 요청 → 재처리 가능
            log.info("Retrying expired/failed request. idempotencyKey: {}", request.idempotencyKey());
        }

        // 2. 멱등성 키 생성 (PROCESSING 상태)
        ChargeBalanceIdempotency idempotency =
                ChargeBalanceIdempotency.create(request.idempotencyKey(), userId, request.amount());
        idempotencyRepository.save(idempotency);

        try {
            // 3. 충전 처리 (재시도 로직 포함)
            ChargeBalanceResponse response =
                    retryService.executeWithRetry(() -> chargeBalanceInternal(userId, request), 10);

            // 4. 완료 처리 (응답 캐싱)
            idempotency.complete(serializeResponse(response));
            idempotencyRepository.save(idempotency);

            log.info("Charge completed successfully. idempotencyKey: {}", request.idempotencyKey());
            return response;

        } catch (Exception e) {
            // 5. 실패 처리
            idempotency.fail(e.getMessage());
            idempotencyRepository.save(idempotency);
            throw e;
        }
    }

    /**
     * 잔액 충전 실행 (트랜잭션 단위)
     * <p>
     * 동시성 제어: Optimistic Lock (2차 방어) + 자동 재시도 (3차 방어)
     * - Optimistic Lock: 충돌 가능성 낮음 (사용자별 데이터)
     * - 자동 재시도: 충돌 시 재시도로 해결 (최대 10회)
     * <p>
     * 차감과 대조: 차감(결제)은 Pessimistic Lock (ProcessPaymentUseCase 참고)
     */
    @Transactional
    protected ChargeBalanceResponse chargeBalanceInternal(Long userId, ChargeBalanceRequest request) {
        // 1. 사용자 조회 (Optimistic Lock)
        User user = userRepository.findByIdOrThrow(userId);

        // 2. 잔액 충전
        user.charge(request.amount());
        userRepository.save(user);

        log.debug("Balance charged successfully. userId: {}, new balance: {}", userId, user.getBalance());

        // 3. 충전 결과 반환
        return ChargeBalanceResponse.of(
            user.getId(),
            user.getBalance(),
            request.amount(),
            LocalDateTime.now()
        );
    }

    /**
     * JSON 직렬화 (응답 → JSON)
     */
    private String serializeResponse(ChargeBalanceResponse response) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("응답 직렬화 실패", e);
        }
    }

    /**
     * JSON 역직렬화 (JSON → 응답)
     */
    private ChargeBalanceResponse deserializeResponse(String json) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        try {
            return objectMapper.readValue(json, ChargeBalanceResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("응답 역직렬화 실패", e);
        }
    }
}
