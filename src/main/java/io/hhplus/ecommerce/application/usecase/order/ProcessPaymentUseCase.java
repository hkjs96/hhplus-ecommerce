package io.hhplus.ecommerce.application.usecase.order;

import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 결제 처리 UseCase
 * <p>
 * 동시성 제어: Pessimistic Lock (SELECT FOR UPDATE)
 * - 잔액 차감: Pessimistic Lock (Lost Update 절대 불가, 돈 손실 방지)
 * - 재고 차감: Pessimistic Lock (충돌 빈번, 크리티컬)
 * - 결제는 한 번 실패하면 재시도가 불가능하므로 정확성 최우선
 * <p>
 * 참고: 잔액 충전은 Optimistic Lock 사용 (ChargeBalanceUseCase)
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class ProcessPaymentUseCase {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public PaymentResponse execute(Long orderId, PaymentRequest request) {
        log.debug("Processing payment for order: {}, user: {}", orderId, request.userId());

        // 1. 주문 조회 및 사용자 조회 (Pessimistic Lock)
        // 동시성 제어: 잔액 차감 시 Pessimistic Lock (SELECT FOR UPDATE)
        // - 5명 관점: 김데이터(O), 박트래픽(X:Optimistic), 이금융(O), 최아키텍트(X:Event), 정스타트업(O)
        // - 최종 선택: Pessimistic Lock (돈 관련은 정확성 최우선)
        //   · 차감은 Lost Update 절대 불가 (돈 손실 방지)
        //   · 충돌 빈번 (결제는 동시 요청 가능성)
        //   · 재시도 불가 (한 번 실패하면 재결제 필요)
        // - 충전과 대조: 충전은 Optimistic Lock (ChargeBalanceUseCase 참고)
        Order order = orderRepository.findByIdOrThrow(orderId);
        User user = userRepository.findByIdWithLockOrThrow(request.userId());  // Pessimistic Lock

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
        // 동시성 제어: Pessimistic Lock (SELECT FOR UPDATE)
        // - 5명 관점: 김데이터(O), 박트래픽(X:Optimistic), 이금융(O), 최아키텍트(X:Event), 정스타트업(O)
        // - 최종 선택: Pessimistic Lock (충돌 빈번 + 크리티컬)
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : orderItems) {
            Product product = productRepository.findByIdWithLockOrThrow(item.getProductId());  // Pessimistic Lock
            product.decreaseStock(item.getQuantity());
            productRepository.save(product);
        }

        // 6. 잔액 차감
        user.deduct(order.getTotalAmount());
        userRepository.save(user);

        // 7. 주문 완료 처리
        order.complete();
        orderRepository.save(order);

        log.info("Payment processed successfully. orderId: {}, amount: {}", orderId, order.getTotalAmount());

        // 8. 외부 데이터 전송 (향후 비동기 처리 예정)
        String dataTransmission = "SUCCESS";

        return PaymentResponse.of(
                order.getId(),
                order.getTotalAmount(),
                user.getBalance(),
                "SUCCESS",
                dataTransmission,
                order.getPaidAt()
        );
    }
}
