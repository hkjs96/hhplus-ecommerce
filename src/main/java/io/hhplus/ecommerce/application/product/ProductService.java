package io.hhplus.ecommerce.application.product;

import io.hhplus.ecommerce.application.product.dto.ProductListResponse;
import io.hhplus.ecommerce.application.product.dto.ProductResponse;
import io.hhplus.ecommerce.application.product.dto.TopProductItem;
import io.hhplus.ecommerce.application.product.dto.TopProductResponse;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public ProductResponse getProduct(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PRODUCT_NOT_FOUND,
                        "상품을 찾을 수 없습니다. productId: " + productId
                ));

        return ProductResponse.from(product);
    }

    public ProductListResponse getProducts(String category, String sort) {
        List<Product> products = productRepository.findAll();

        Stream<Product> productStream = products.stream();
        if (category != null && !category.isEmpty()) {
            productStream = productStream.filter(p -> p.getCategory().equals(category));
        }

        if (sort != null) {
            productStream = switch (sort) {
                case "price", "price_asc" -> productStream.sorted(Comparator.comparing(Product::getPrice));
                case "price_desc" -> productStream.sorted(Comparator.comparing(Product::getPrice).reversed());
                case "newest" -> productStream.sorted(Comparator.comparing(Product::getCreatedAt).reversed());
                default -> productStream;
            };
        }

        List<ProductResponse> productResponses = productStream
                .map(ProductResponse::from)
                .toList();

        return ProductListResponse.of(productResponses);
    }

    public TopProductResponse getTopProducts() {
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);

        // 1. 최근 3일간 완료된 주문 조회
        List<String> completedOrderIds = orderRepository.findAll().stream()
                .filter(Order::isCompleted)
                .filter(order -> order.getPaidAt() != null && order.getPaidAt().isAfter(threeDaysAgo))
                .map(Order::getId)
                .toList();

        if (completedOrderIds.isEmpty()) {
            return TopProductResponse.of(List.of());
        }

        // 2. 해당 주문들의 OrderItem 조회 및 집계
        Map<String, ProductSales> salesByProduct = orderItemRepository.findAll().stream()
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
                    String productId = entry.getKey();
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

        return TopProductResponse.of(rankedProducts);
    }

    // 내부 클래스: 상품별 판매 집계
    private static class ProductSales {
        final int salesCount;
        final long revenue;

        ProductSales(int salesCount, long revenue) {
            this.salesCount = salesCount;
            this.revenue = revenue;
        }
    }
}
