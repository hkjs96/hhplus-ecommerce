package io.hhplus.ecommerce.application.usecase.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import io.hhplus.ecommerce.domain.order.OrderIdempotency;
import io.hhplus.ecommerce.domain.order.OrderIdempotencyRepository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 주문 생성 UseCase
 * <p>
 * 동시성 제어: 분산락 + Pessimistic Lock + 멱등성
 * - 분산락: 사용자별 주문 생성 직렬화
 * - Pessimistic Lock: 재고 확인 시 정확성 보장
 * - 멱등성: 중복 주문 방지
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
 * 멱등성 보장:
 * - idempotencyKey로 중복 요청 탐지
 * - COMPLETED: 캐시된 응답 반환
 * - PROCESSING: 409 Conflict
 * - FAILED: 재처리 가능
 */
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
    private final OrderIdempotencyRepository idempotencyRepository;
    private final MetricsCollector metricsCollector;
    private final IdempotencySaveService idempotencySaveService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * 주문 생성 (멱등성 보장)
     * <p>
     * 락 키: idempotencyKey 기준 직렬화
     * - 동일 idempotencyKey는 직렬화하고, 다른 키는 병렬 처리 허용
     * - idempotencyKey가 없으면 사용자 기준으로 폴백
     */
    @Transactional
    @DistributedLock(
            key = "(#request.idempotencyKey() != null ? 'order:create:idem:' + #request.idempotencyKey() : 'order:create:user:' + #request.userId())",
            waitTime = 10,
            leaseTime = 60
    )
    public CreateOrderResponse execute(CreateOrderRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Creating order for user: {}, idempotencyKey: {}",
            request.userId(), request.idempotencyKey());

        // 1. 멱등성 키 조회
        Optional<OrderIdempotency> existingIdempotency =
                idempotencyRepository.findByIdempotencyKey(request.idempotencyKey());

        if (existingIdempotency.isPresent()) {
            OrderIdempotency idempotency = existingIdempotency.get();

            // 1-1. 이미 완료된 요청 → 캐시된 응답 반환
            if (idempotency.isCompleted()) {
                log.info("Returning cached response for idempotencyKey: {}", request.idempotencyKey());
                metricsCollector.recordOrderSuccess();
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

        // 1-4. 상품을 락과 함께 미리 로드해 중복 조회·락 승급을 방지
        OrderPreparationContext preparationContext = prepareOrderContext(request);

        // 2. 총 금액 미리 계산 (멱등성 키 생성용)
        long totalAmount = preparationContext.subtotalAmount();

        // 3. 멱등성 키 생성 (PROCESSING 상태)
        OrderIdempotency idempotency = OrderIdempotency.create(
                request.idempotencyKey(),
                request.userId(),
                totalAmount
        );
        idempotency = idempotencySaveService.saveProcessing(idempotency);

        try {
            // 4. 주문 생성 처리
            CreateOrderResponse response = createOrderInternal(request, preparationContext, startTime);

            // 5. 이벤트 발행 (Phase 2: 멱등성 완료 처리를 이벤트로 분리)
            eventPublisher.publishEvent(
                new io.hhplus.ecommerce.domain.order.OrderCreatedEvent(
                    request.idempotencyKey(),
                    response
                )
            );

            log.info("Order created successfully. orderId: {}, idempotencyKey: {}",
                response.orderId(), request.idempotencyKey());
            return response;

        } catch (Exception e) {
            // 6. 실패 처리 (별도 트랜잭션으로 저장)
            idempotencySaveService.saveFailedIdempotency(request.idempotencyKey(), e.getMessage());

            // 메트릭 기록: 주문 실패
            metricsCollector.recordOrderFailure();
            throw e;
        }
    }

    /**
     * 주문 생성 내부 로직
     *
     * Note: 트랜잭션은 execute() 메서드에 적용됨
     */
    protected CreateOrderResponse createOrderInternal(CreateOrderRequest request,
                                                      OrderPreparationContext context,
                                                      long startTime) {
        try {
            // 1. 사용자 검증
            User user = userRepository.findByIdOrThrow(request.userId());

            // 2. 데드락 방지: 상품 ID 오름차순 정렬 (준비 단계에서 이미 정렬)
            List<OrderItemRequest> sortedItems = context.sortedItems();

            // 3. 상품 재고 확인 및 금액 계산 (Pessimistic Lock)
            List<OrderItemResponse> itemResponses = new ArrayList<>();
            long subtotalAmount = context.subtotalAmount();

            for (OrderItemRequest itemReq : sortedItems) {
                // Pessimistic Lock으로 확보한 Product 재사용 (락 승급 방지)
                Product product = context.productById().get(itemReq.productId());

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
            Order order = Order.create(orderNumber, user, subtotalAmount, discountAmount);
            orderRepository.save(order);

            // 6. 주문 아이템 생성 (재고는 결제 시 감소)
            for (OrderItemRequest itemReq : sortedItems) {
                Product product = context.productById().get(itemReq.productId());

                // Note: 재고는 ProcessPaymentUseCase에서 차감 (결제 성공 시에만 차감)
                OrderItem orderItem = OrderItem.create(
                        order,
                        product,
                        itemReq.quantity(),
                        product.getPrice()
                );
                orderItemRepository.save(orderItem);
            }

            // 메트릭 기록: 주문 성공
            metricsCollector.recordOrderSuccess();
            metricsCollector.recordOrderDuration(startTime);

            return CreateOrderResponse.of(order, itemResponses);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("주문 생성 중 오류가 발생했습니다", e);
        }
    }

    /**
     * 주문에 필요한 상품들을 비관적 락으로 미리 불러와 정렬된 상태와 총액을 준비한다.
     * 이후 단계에서는 동일 엔티티를 재사용해 추가 조회나 락 승급을 방지한다.
     */
    private OrderPreparationContext prepareOrderContext(CreateOrderRequest request) {
        List<OrderItemRequest> sortedItems = request.items().stream()
                .sorted(Comparator.comparing(OrderItemRequest::productId))
                .collect(Collectors.toList());

        Map<Long, Product> productById = new HashMap<>();
        long subtotalAmount = 0L;

        for (OrderItemRequest itemReq : sortedItems) {
            // 재고 차감은 결제 단계에서 비관적 락으로 수행하므로,
            // 주문 생성 단계에서는 불필요한 PESSIMISTIC_WRITE를 피하고 단순 조회만 수행한다.
            Product product = productRepository.findByIdOrThrow(itemReq.productId());
            productById.put(product.getId(), product);
            subtotalAmount += product.getPrice() * itemReq.quantity();
        }

        return new OrderPreparationContext(sortedItems, productById, subtotalAmount);
    }

    private record OrderPreparationContext(
            List<OrderItemRequest> sortedItems,
            Map<Long, Product> productById,
            long subtotalAmount
    ) {
    }

    /**
     * JSON 직렬화 (응답 → JSON)
     */
    private String serializeResponse(CreateOrderResponse response) {
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
    private CreateOrderResponse deserializeResponse(String json) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        try {
            return objectMapper.readValue(json, CreateOrderResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("응답 역직렬화 실패", e);
        }
    }
}
