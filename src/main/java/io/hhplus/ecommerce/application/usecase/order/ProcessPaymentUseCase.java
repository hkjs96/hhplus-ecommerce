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

        // 1. 주문 및 사용자 조회
        Order order = orderRepository.findByIdOrThrow(orderId);
        User user = userRepository.findByIdOrThrow(request.userId());

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

        // 5. 잔액 차감
        user.deduct(order.getTotalAmount());
        userRepository.save(user);

        // 6. 주문 완료 처리
        // Note: 재고는 CreateOrderUseCase에서 이미 차감되었음 (주문 생성 시 재고 예약)
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
