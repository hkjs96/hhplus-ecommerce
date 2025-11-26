package io.hhplus.ecommerce.application.usecase.order;

import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentResponse;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.redis.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 결제 트랜잭션 처리 서비스 (Spring AOP Proxy 적용)
 * <p>
 * ProcessPaymentUseCase에서 호출되는 @Transactional 메서드들을 별도 서비스로 분리.
 * 이렇게 하면 external call이 되어 Spring AOP proxy를 거치게 됨.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * Step 1: 잔액 차감 (트랜잭션)
     * <p>
     * DB 트랜잭션 내에서 수행:
     * - 주문 조회 및 검증
     * - 잔액 차감 (Pessimistic Lock + 분산락)
     * - 재고 차감 (Pessimistic Lock + 분산락)
     * - 주문 상태 PENDING 유지 (결제 대기)
     * <p>
     * 분산락 적용:
     * - 락 키: "balance:user:{userId}" (충전과 동일한 키 사용!)
     * - 여러 상품의 재고를 동시에 차감할 때 데드락 방지
     * - 상품 ID를 오름차순 정렬하여 처리 순서 통일
     * <p>
     * ⚠️ 중요: 잔액 충전/차감은 동일한 락 키 사용 필수!
     * - 충전: "balance:user:{userId}" (ChargeBalanceUseCase.chargeBalance)
     * - 차감: "balance:user:{userId}" (이 메서드)
     * - 서로 다른 키 사용 시 Lost Update 발생 위험
     * <p>
     * 트랜잭션 보유 시간: 약 50ms (외부 API 제외)
     *
     * @param orderId 주문 ID
     * @param request 결제 요청
     * @return 주문 엔티티
     */
    @DistributedLock(
            key = "'balance:user:' + #request.userId()",
            waitTime = 10,
            leaseTime = 30
    )
    @Transactional
    public Order reservePayment(Long orderId, PaymentRequest request) {
        log.debug("Reserving payment for order: {}", orderId);

        // 1. 주문 조회 및 사용자 조회 (Pessimistic Lock)
        Order order = orderRepository.findByIdOrThrow(orderId);
        User user = userRepository.findByIdWithLockOrThrow(request.userId());

        // 2. 주문 소유자 검증
        if (!order.getUserId().equals(user.getId())) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "주문한 사용자와 결제 요청 사용자가 다릅니다."
            );
        }

        // 3. 주문 상태 검증
        if (order.getStatus() != io.hhplus.ecommerce.domain.order.OrderStatus.PENDING) {
            throw new BusinessException(
                ErrorCode.INVALID_ORDER_STATUS,
                "결제할 수 없는 주문 상태입니다. 현재 상태: " + order.getStatus()
            );
        }

        // 4. 잔액 검증
        if (user.getBalance() < order.getTotalAmount()) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_BALANCE,
                String.format("잔액이 부족합니다. (필요: %d원, 보유: %d원)",
                    order.getTotalAmount(), user.getBalance())
            );
        }

        // 5. 재고 차감 (결제 성공 시에만 재고 감소)
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : orderItems) {
            Product product = productRepository.findByIdWithLockOrThrow(item.getProductId());
            product.decreaseStock(item.getQuantity());
            productRepository.save(product);
        }

        // 6. 잔액 차감
        user.deduct(order.getTotalAmount());
        userRepository.save(user);

        log.debug("Payment reserved. orderId: {}, amount: {}", orderId, order.getTotalAmount());
        return order;
    }

    /**
     * Step 3: 결제 성공 시 상태 업데이트 및 응답 생성 (트랜잭션)
     * <p>
     * PG 승인 성공 후 DB 상태 업데이트:
     * - 주문 상태 → COMPLETED
     * - 결제 완료 시간 기록
     * - User 잔액 조회
     * - PaymentResponse 생성
     * <p>
     * 트랜잭션 보유 시간: 약 50ms
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param pgTransactionId PG사 트랜잭션 ID
     * @return PaymentResponse
     */
    @Transactional
    public PaymentResponse updatePaymentSuccessAndCreateResponse(
            Long orderId,
            Long userId,
            String pgTransactionId) {
        log.debug("Updating payment success. orderId: {}, txId: {}", orderId, pgTransactionId);

        // 주문 상태 업데이트
        Order order = orderRepository.findByIdOrThrow(orderId);
        order.complete();
        orderRepository.save(order);

        // 사용자 잔액 조회
        User user = userRepository.findByIdOrThrow(userId);

        log.info("Payment status updated to COMPLETED. orderId: {}, txId: {}", orderId, pgTransactionId);

        // 응답 생성
        return PaymentResponse.of(
            order.getId(),
            order.getTotalAmount(),
            user.getBalance(),  // 결제 후 잔액
            "SUCCESS",
            "PG_APPROVED: " + pgTransactionId,
            order.getPaidAt()
        );
    }

    /**
     * Step 4: 결제 실패 시 보상 트랜잭션 (트랜잭션)
     * <p>
     * 잔액 차감은 성공했지만 PG 승인 실패 시:
     * - 잔액 복구 (user.charge)
     * - 재고 복구 (product.increaseStock)
     * <p>
     * 트랜잭션 보유 시간: 약 50ms
     * <p>
     * ⚠️ 보상 실패 시 수동 개입 필요 (로그 남김)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     */
    @Transactional
    public void compensatePayment(Long orderId, Long userId) {
        log.warn("Compensating payment. orderId: {}, userId: {}", orderId, userId);

        try {
            // 1. 주문 조회
            Order order = orderRepository.findByIdOrThrow(orderId);

            // 2. 재고 복구
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
            for (OrderItem item : orderItems) {
                Product product = productRepository.findByIdOrThrow(item.getProductId());
                product.increaseStock(item.getQuantity());
                productRepository.save(product);
            }

            // 3. 잔액 복구
            User user = userRepository.findByIdOrThrow(userId);
            user.charge(order.getTotalAmount());
            userRepository.save(user);

            log.info("Payment compensation completed. orderId: {}, refundedAmount: {}",
                orderId, order.getTotalAmount());

        } catch (Exception e) {
            log.error("CRITICAL: Compensation failed for orderId: {}. Manual intervention required!",
                orderId, e);
            throw e;  // 보상 실패 시 예외 발생 (수동 처리 필요)
        }
    }
}
