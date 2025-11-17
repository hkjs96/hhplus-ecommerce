package io.hhplus.ecommerce.performance;

import io.hhplus.ecommerce.domain.cart.Cart;
import io.hhplus.ecommerce.domain.cart.CartItem;
import io.hhplus.ecommerce.domain.cart.CartItemRepository;
import io.hhplus.ecommerce.domain.cart.CartRepository;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class PerformanceTestDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(PerformanceTestDataGenerator.class);

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    private final Random random = new Random(42); // 재현 가능한 난수

    public PerformanceTestDataGenerator(
        UserRepository userRepository,
        ProductRepository productRepository,
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        CartRepository cartRepository,
        CartItemRepository cartItemRepository,
        CouponRepository couponRepository,
        UserCouponRepository userCouponRepository
    ) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
    }

    @Transactional
    public void generateFullDataset() {
        log.info("=".repeat(80));
        log.info("Starting FULL dataset generation for performance testing");
        log.info("=".repeat(80));

        long startTime = System.currentTimeMillis();

        // 1. 사용자 생성 (10,000명)
        List<User> users = generateUsers(10_000);
        log.info("✓ Generated {} users", users.size());

        // 2. 상품 생성 (1,000개)
        List<Product> products = generateProducts(1_000);
        log.info("✓ Generated {} products", products.size());

        // 3. 주문 생성 (100,000건)
        List<Order> orders = generateOrders(users, 100_000);
        log.info("✓ Generated {} orders", orders.size());

        // 4. 주문 상세 생성 (300,000건)
        List<OrderItem> orderItems = generateOrderItems(orders, products, 300_000);
        log.info("✓ Generated {} order items", orderItems.size());

        // 5. 장바구니 생성 (5,000개)
        List<Cart> carts = generateCarts(users, 5_000);
        log.info("✓ Generated {} carts", carts.size());

        // 6. 장바구니 아이템 생성 (15,000개)
        List<CartItem> cartItems = generateCartItems(carts, products, 15_000);
        log.info("✓ Generated {} cart items", cartItems.size());

        // 7. 쿠폰 생성 (100개)
        List<Coupon> coupons = generateCoupons(100);
        log.info("✓ Generated {} coupons", coupons.size());

        // 8. 사용자 쿠폰 생성 (50,000건)
        List<UserCoupon> userCoupons = generateUserCoupons(users, coupons, 50_000);
        log.info("✓ Generated {} user coupons", userCoupons.size());

        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;

        log.info("=".repeat(80));
        log.info("Dataset generation completed in {} seconds", duration);
        log.info("Total records: {}",
            users.size() + products.size() + orders.size() + orderItems.size() +
            carts.size() + cartItems.size() + coupons.size() + userCoupons.size());
        log.info("=".repeat(80));
    }

    @Transactional
    public void generateSmallDataset() {
        log.info("Generating SMALL dataset for quick testing");

        List<User> users = generateUsers(100);
        List<Product> products = generateProducts(50);
        List<Order> orders = generateOrders(users, 500);
        List<OrderItem> orderItems = generateOrderItems(orders, products, 1_500);
        List<Cart> carts = generateCarts(users, 50);
        List<CartItem> cartItems = generateCartItems(carts, products, 150);
        List<Coupon> coupons = generateCoupons(10);
        List<UserCoupon> userCoupons = generateUserCoupons(users, coupons, 500);

        log.info("Small dataset generated: {} total records",
            users.size() + products.size() + orders.size() + orderItems.size() +
            carts.size() + cartItems.size() + coupons.size() + userCoupons.size());
    }

    // ============================================================
    // 개별 데이터 생성 메서드
    // ============================================================

    private List<User> generateUsers(int count) {
        log.debug("Generating {} users...", count);
        List<User> users = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            User user = User.create(
                "user" + i + "@test.com",
                "테스트사용자" + i
            );
            // 초기 잔액: 1,000,000원 ~ 10,000,000원
            long initialBalance = 1_000_000L + random.nextInt(9_000_000);
            user.charge(initialBalance);
            users.add(userRepository.save(user));

            if (i % 1000 == 0) {
                log.debug("  Progress: {}/{} users created", i, count);
            }
        }

        return users;
    }

    private List<Product> generateProducts(int count) {
        log.debug("Generating {} products...", count);
        List<Product> products = new ArrayList<>();
        String[] categories = {"ELECTRONICS", "FASHION", "FOOD", "SPORTS", "BOOKS"};

        for (int i = 1; i <= count; i++) {
            String category = categories[random.nextInt(categories.length)];
            Long price = (long) (10_000 + random.nextInt(990_000)); // 10,000 ~ 1,000,000원
            Integer stock = 50 + random.nextInt(950); // 50 ~ 1000개

            Product product = Product.create(
                "PROD" + String.format("%06d", i),
                "테스트상품" + i,
                "상품 설명 " + i,
                price,
                category,
                stock
            );
            products.add(productRepository.save(product));

            if (i % 100 == 0) {
                log.debug("  Progress: {}/{} products created", i, count);
            }
        }

        return products;
    }

    private List<Order> generateOrders(List<User> users, int count) {
        log.debug("Generating {} orders...", count);
        List<Order> orders = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();

        for (int i = 1; i <= count; i++) {
            User user = users.get(random.nextInt(users.size()));

            // 주문 생성 시간: 최근 30일 내 랜덤
            LocalDateTime createdAt = now.minusDays(random.nextInt(30));

            Long subtotal = (long) (50_000 + random.nextInt(950_000)); // 50,000 ~ 1,000,000원
            Long discount = (long) (random.nextInt(10_000)); // 0 ~ 10,000원

            Order order = Order.create(
                "ORD" + String.format("%08d", i),
                user.getId(),
                subtotal,
                discount
            );

            // 80% 주문은 완료 상태
            if (random.nextDouble() < 0.8) {
                order.complete();
            }

            orders.add(orderRepository.save(order));

            if (i % 10_000 == 0) {
                log.debug("  Progress: {}/{} orders created", i, count);
            }
        }

        return orders;
    }

    private List<OrderItem> generateOrderItems(List<Order> orders, List<Product> products, int count) {
        log.debug("Generating {} order items...", count);
        List<OrderItem> orderItems = new ArrayList<>();

        // 각 주문에 평균 3개 상품 할당
        int itemsPerOrder = Math.max(1, count / orders.size());

        for (Order order : orders) {
            int numItems = Math.min(itemsPerOrder + random.nextInt(3), 5); // 1~5개 상품

            for (int i = 0; i < numItems && orderItems.size() < count; i++) {
                Product product = products.get(random.nextInt(products.size()));
                Integer quantity = 1 + random.nextInt(4); // 1~5개

                OrderItem orderItem = OrderItem.create(
                    order.getId(),
                    product.getId(),
                    quantity,
                    product.getPrice()
                );
                orderItems.add(orderItemRepository.save(orderItem));
            }

            if (orderItems.size() % 30_000 == 0) {
                log.debug("  Progress: {}/{} order items created", orderItems.size(), count);
            }
        }

        return orderItems;
    }

    private List<Cart> generateCarts(List<User> users, int count) {
        log.debug("Generating {} carts...", count);
        List<Cart> carts = new ArrayList<>();

        for (int i = 0; i < count && i < users.size(); i++) {
            User user = users.get(i);
            Cart cart = Cart.create(user.getId());
            carts.add(cartRepository.save(cart));

            if ((i + 1) % 1000 == 0) {
                log.debug("  Progress: {}/{} carts created", i + 1, count);
            }
        }

        return carts;
    }

    private List<CartItem> generateCartItems(List<Cart> carts, List<Product> products, int count) {
        log.debug("Generating {} cart items...", count);
        List<CartItem> cartItems = new ArrayList<>();

        // 각 장바구니에 평균 3개 상품
        int itemsPerCart = Math.max(1, count / carts.size());

        for (Cart cart : carts) {
            int numItems = Math.min(itemsPerCart + random.nextInt(3), 10); // 1~10개 상품
            List<Long> addedProductIds = new ArrayList<>();  // 중복 방지용

            for (int i = 0; i < numItems && cartItems.size() < count; i++) {
                // 중복되지 않은 상품 찾기
                Product product;
                int attempts = 0;
                do {
                    product = products.get(random.nextInt(products.size()));
                    attempts++;
                } while (addedProductIds.contains(product.getId()) && attempts < 20);

                // 중복 체크
                if (addedProductIds.contains(product.getId())) {
                    continue;  // 이미 추가된 상품이면 스킵
                }

                Integer quantity = 1 + random.nextInt(4); // 1~5개

                CartItem cartItem = CartItem.create(
                    cart.getId(),
                    product.getId(),
                    quantity
                );
                cartItems.add(cartItemRepository.save(cartItem));
                addedProductIds.add(product.getId());  // 추가한 상품 기록
            }

            if (cartItems.size() % 1500 == 0) {
                log.debug("  Progress: {}/{} cart items created", cartItems.size(), count);
            }
        }

        return cartItems;
    }

    private List<Coupon> generateCoupons(int count) {
        log.debug("Generating {} coupons...", count);
        List<Coupon> coupons = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();

        for (int i = 1; i <= count; i++) {
            Integer discountRate = 5 + random.nextInt(16); // 5% ~ 20%
            Integer quantity = 100 + random.nextInt(900); // 100 ~ 1000개
            LocalDateTime expiresAt = now.plusDays(30 + random.nextInt(60)); // 30~90일 후 만료

            Coupon coupon = Coupon.create(
                "COUPON" + String.format("%04d", i),
                "테스트쿠폰" + i,
                discountRate,
                quantity,
                now,                // startDate
                expiresAt           // endDate
            );
            coupons.add(couponRepository.save(coupon));
        }

        return coupons;
    }

    private List<UserCoupon> generateUserCoupons(List<User> users, List<Coupon> coupons, int count) {
        log.debug("Generating {} user coupons...", count);
        List<UserCoupon> userCoupons = new ArrayList<>();
        List<String> issuedPairs = new ArrayList<>();  // 중복 방지용 (userId:couponId)

        for (int i = 0; i < count; i++) {
            User user;
            Coupon coupon;
            String pair;
            int attempts = 0;

            // 중복되지 않은 (user, coupon) 조합 찾기
            do {
                user = users.get(random.nextInt(users.size()));
                coupon = coupons.get(random.nextInt(coupons.size()));
                pair = user.getId() + ":" + coupon.getId();
                attempts++;
            } while (issuedPairs.contains(pair) && attempts < 50);

            // 중복 체크
            if (issuedPairs.contains(pair)) {
                continue;  // 이미 발급된 조합이면 스킵
            }

            UserCoupon userCoupon = UserCoupon.create(
                user.getId(),
                coupon.getId(),
                LocalDateTime.now().plusDays(30)  // expiresAt
            );
            issuedPairs.add(pair);  // 발급된 조합 기록

            // 30% 확률로 사용 처리
            if (random.nextDouble() < 0.3) {
                userCoupon.use();
            }

            userCoupons.add(userCouponRepository.save(userCoupon));

            if ((i + 1) % 5000 == 0) {
                log.debug("  Progress: {}/{} user coupons created", i + 1, count);
            }
        }

        return userCoupons;
    }

    public void printStatistics() {
        log.info("=".repeat(80));
        log.info("Database Statistics");
        log.info("=".repeat(80));
        log.info("Test data generation completed");
        // JPA Repository의 count() 메서드를 사용하려면 JPA Repository 타입으로 캐스팅 필요
        // log.info("Users: {}", ((JpaRepository)userRepository).count());
        log.info("=".repeat(80));
    }
}
