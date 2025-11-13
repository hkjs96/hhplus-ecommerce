package io.hhplus.ecommerce.application.usecase.product;

import io.hhplus.ecommerce.application.product.dto.TopProductItem;
import io.hhplus.ecommerce.application.product.dto.TopProductResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@UseCase
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetTopProductsUseCase {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public TopProductResponse execute() {
        log.info("Getting top products (last 3 days)");

        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);

        // 1. 최근 3일간 완료된 주문 조회
        List<Long> completedOrderIds = orderRepository.findAll().stream()
            .filter(Order::isCompleted)
            .filter(order -> order.getPaidAt() != null && order.getPaidAt().isAfter(threeDaysAgo))
            .map(Order::getId)
            .toList();

        if (completedOrderIds.isEmpty()) {
            log.debug("No completed orders in last 3 days");
            return TopProductResponse.of(List.of());
        }

        // 2. 해당 주문들의 OrderItem 조회 및 집계
        Map<Long, ProductSales> salesByProduct = orderItemRepository.findAll().stream()
            .filter(item -> completedOrderIds.contains(item.getOrderId()))
            .collect(Collectors.groupingBy(
                OrderItem::getProductId,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    items -> new ProductSales(
                        items.stream().mapToInt(OrderItem::getQuantity).sum(),
                        items.stream().mapToLong(OrderItem::getSubtotal).sum()
                    )
                )
            ));

        // 3. 판매량 기준 Top 5 추출
        List<TopProductItem> topProducts = salesByProduct.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue().salesCount, e1.getValue().salesCount))
            .limit(5)
            .map(entry -> {
                Long productId = entry.getKey();
                ProductSales sales = entry.getValue();

                // Product 정보 조회
                Product product = productRepository.findById(productId)
                    .orElse(null);

                if (product == null) {
                    return null;
                }

                return TopProductItem.builder()
                    .rank(0)  // rank는 나중에 설정
                    .productId(productId)
                    .name(product.getName())
                    .salesCount(sales.salesCount)
                    .revenue(sales.revenue)
                    .build();
            })
            .filter(Objects::nonNull)
            .toList();

        // 4. rank 설정
        List<TopProductItem> rankedProducts = new ArrayList<>();
        for (int i = 0; i < topProducts.size(); i++) {
            TopProductItem item = topProducts.get(i);
            rankedProducts.add(TopProductItem.builder()
                .rank(i + 1)
                .productId(item.getProductId())
                .name(item.getName())
                .salesCount(item.getSalesCount())
                .revenue(item.getRevenue())
                .build());
        }

        log.debug("Found {} top products", rankedProducts.size());
        return TopProductResponse.of(rankedProducts);
    }

    private static class ProductSales {
        final int salesCount;
        final long revenue;

        ProductSales(int salesCount, long revenue) {
            this.salesCount = salesCount;
            this.revenue = revenue;
        }
    }
}
