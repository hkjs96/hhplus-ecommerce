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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    @Transactional
    public CreateOrderResponse execute(CreateOrderRequest request) {
        log.debug("Creating order for user: {}", request.getUserId());

        // 1. 사용자 검증
        User user = userRepository.findByIdOrThrow(request.getUserId());

        // 2. 상품 재고 확인 및 금액 계산
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        long subtotalAmount = 0L;

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findByIdOrThrow(itemReq.getProductId());

            // 재고 확인
            if (product.getStock() < itemReq.getQuantity()) {
                throw new BusinessException(
                        ErrorCode.INSUFFICIENT_STOCK,
                        String.format("재고가 부족합니다. 상품: %s, 요청: %d, 재고: %d",
                                product.getName(), itemReq.getQuantity(), product.getStock())
                );
            }

            long itemSubtotal = product.getPrice() * itemReq.getQuantity();
            subtotalAmount += itemSubtotal;

            itemResponses.add(OrderItemResponse.of(
                    product.getId(),
                    product.getName(),
                    itemReq.getQuantity(),
                    product.getPrice(),
                    itemSubtotal
            ));
        }

        // 3. 쿠폰 검증 및 할인 계산
        long discountAmount = 0L;
        if (request.getCouponId() != null) {
            Coupon coupon = couponRepository.findByIdOrThrow(request.getCouponId());

            coupon.validateIssuable();

            if (!userCouponRepository.existsByUserIdAndCouponId(user.getId(), coupon.getId())) {
                throw new BusinessException(
                        ErrorCode.INVALID_COUPON,
                        "보유하지 않은 쿠폰입니다."
                );
            }

            discountAmount = (long) (subtotalAmount * coupon.getDiscountRate() / 100.0);
        }

        // 4. 주문 생성
        String orderNumber = "ORDER-" + UUID.randomUUID().toString().substring(0, 8);
        Order order = Order.create(orderNumber, user.getId(), subtotalAmount, discountAmount);
        orderRepository.save(order);

        // 5. 주문 아이템 생성
        for (int i = 0; i < request.getItems().size(); i++) {
            OrderItemRequest itemReq = request.getItems().get(i);
            Product product = productRepository.findByIdOrThrow(itemReq.getProductId());

            OrderItem orderItem = OrderItem.create(
                    order.getId(),
                    product.getId(),
                    itemReq.getQuantity(),
                    product.getPrice()
            );
            orderItemRepository.save(orderItem);
        }

        log.info("Order created successfully. orderId: {}, userId: {}", order.getId(), user.getId());

        return CreateOrderResponse.of(
                order.getId(),
                order.getUserId(),
                itemResponses,
                order.getSubtotalAmount(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getStatus().name(),
                order.getCreatedAt()
        );
    }
}
