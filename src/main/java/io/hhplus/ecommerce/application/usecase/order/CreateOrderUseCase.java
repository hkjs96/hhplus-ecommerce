package io.hhplus.ecommerce.application.usecase.order;

import io.hhplus.ecommerce.application.order.dto.CreateOrderRequest;
import io.hhplus.ecommerce.application.order.dto.CreateOrderResponse;
import io.hhplus.ecommerce.application.order.dto.OrderItemRequest;
import io.hhplus.ecommerce.application.order.dto.OrderItemResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.metrics.MetricsCollector;
import io.hhplus.ecommerce.infrastructure.redis.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final MetricsCollector metricsCollector;

    /**
     * 주문 생성
     * <p>
     * 동시성 제어: 분산락 + Pessimistic Lock
     * - 분산락: 사용자별 주문 생성 직렬화 (동시 주문 방지)
     * - Pessimistic Lock: 재고 확인 시 정확성 보장
     * <p>
     * TOCTOU 갭 해결:
     * - Time-of-Check: 재고 확인
     * - Time-of-Use: 주문 생성
     * - 분산락으로 이 갭을 보호하여 경쟁 상태 방지
     * <p>
     * 데드락 방지:
     * - 여러 상품 주문 시 상품 ID 오름차순 정렬
     * - 모든 트랜잭션이 동일한 순서로 락 획득
     * <p>
     * 락 키: "order:create:user:{userId}"
     * - 같은 사용자의 주문 생성은 직렬화
     * - 다른 사용자의 주문은 병렬 처리 가능
     */
    @DistributedLock(
            key = "'order:create:user:' + #request.userId()",
            waitTime = 10,
            leaseTime = 30
    )
    @Transactional
    public CreateOrderResponse execute(CreateOrderRequest request) {
        long startTime = System.currentTimeMillis();
        log.debug("Creating order for user: {}", request.userId());

        try {
            // 1. 사용자 검증
            User user = userRepository.findByIdOrThrow(request.userId());

            // 2. 데드락 방지: 상품 ID 오름차순 정렬
            List<OrderItemRequest> sortedItems = request.items().stream()
                    .sorted(Comparator.comparing(OrderItemRequest::productId))
                    .collect(Collectors.toList());

            // 3. 상품 재고 확인 및 금액 계산 (Pessimistic Lock)
            List<OrderItemResponse> itemResponses = new ArrayList<>();
            long subtotalAmount = 0L;

            for (OrderItemRequest itemReq : sortedItems) {
                // Pessimistic Lock으로 재고 조회 (TOCTOU 갭 방지)
                Product product = productRepository.findByIdWithLockOrThrow(itemReq.productId());

                // 재고 확인
                if (product.getStock() < itemReq.quantity()) {
                    metricsCollector.recordStockError();
                    throw new BusinessException(
                            ErrorCode.INSUFFICIENT_STOCK,
                            String.format("재고가 부족합니다. 상품: %s, 요청: %d, 재고: %d",
                                    product.getName(), itemReq.quantity(), product.getStock())
                    );
                }

                long itemSubtotal = product.getPrice() * itemReq.quantity();
                subtotalAmount += itemSubtotal;

                itemResponses.add(OrderItemResponse.of(
                        product.getId(),
                        product.getName(),
                        itemReq.quantity(),
                        product.getPrice(),
                        itemSubtotal
                ));
            }

            // 4. 쿠폰 검증 및 할인 계산
            long discountAmount = 0L;
            if (request.couponId() != null) {
                Coupon coupon = couponRepository.findByIdOrThrow(request.couponId());

                coupon.validateIssuable();

                if (!userCouponRepository.existsByUserIdAndCouponId(user.getId(), coupon.getId())) {
                    throw new BusinessException(
                            ErrorCode.INVALID_COUPON,
                            "보유하지 않은 쿠폰입니다."
                    );
                }

                discountAmount = (long) (subtotalAmount * coupon.getDiscountRate() / 100.0);
            }

            // 5. 주문 생성
            String orderNumber = "ORDER-" + UUID.randomUUID().toString().substring(0, 8);
            Order order = Order.create(orderNumber, user.getId(), subtotalAmount, discountAmount);
            orderRepository.save(order);

            // 6. 주문 아이템 생성 (재고는 결제 시 감소)
            // Note: sortedItems를 사용하여 정렬된 순서 유지
            for (OrderItemRequest itemReq : sortedItems) {
                // 이미 위에서 Pessimistic Lock으로 조회했으므로 일반 조회 사용
                Product product = productRepository.findByIdOrThrow(itemReq.productId());

                // Note: 재고는 ProcessPaymentUseCase에서 차감 (결제 성공 시에만 차감)
                OrderItem orderItem = OrderItem.create(
                        order,         // Order 엔티티 직접 전달
                        product,       // Product 엔티티 직접 전달
                        itemReq.quantity(),
                        product.getPrice()
                );
                orderItemRepository.save(orderItem);
            }

            log.info("Order created successfully. orderId: {}, userId: {}", order.getId(), user.getId());

            // 메트릭 기록: 주문 성공
            metricsCollector.recordOrderSuccess();
            metricsCollector.recordOrderDuration(startTime);

            return CreateOrderResponse.of(order, itemResponses);

        } catch (BusinessException e) {
            // 메트릭 기록: 주문 실패
            metricsCollector.recordOrderFailure();
            throw e;
        } catch (Exception e) {
            // 메트릭 기록: 주문 실패
            metricsCollector.recordOrderFailure();
            throw e;
        }
    }
}
