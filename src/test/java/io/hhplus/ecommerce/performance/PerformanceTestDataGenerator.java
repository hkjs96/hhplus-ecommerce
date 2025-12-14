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
import jakarta.persistence.EntityManager;
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
    private final EntityManager em;

    private final Random random = new Random(42); // 재현 가능한 난수

    public PerformanceTestDataGenerator(
        UserRepository userRepository,
        ProductRepository productRepository,
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        CartRepository cartRepository,
        CartItemRepository cartItemRepository,
        CouponRepository couponRepository,
        UserCouponRepository userCouponRepository,
        EntityManager em
    ) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.em = em;
    }

    @Transactional
    public void generateFullDataset() {
        log.info("=".repeat(80));
        log.info("Starting FULL dataset generation for performance testing");
        log.info("=".repeat(80));

        long startTime = System.currentTimeMillis();

        List<User> users = generateUsers(10_000);
        log.info("✓ Generated {} users", users.size());

        List<Product> products = generateProducts(1_000);
        log.info("✓ Generated {} products", products.size());

        List<Order> orders = generateOrders(users, 100_000);
        log.info("✓ Generated {} orders", orders.size());

        List<OrderItem> orderItems = generateOrderItems(orders, products, 300_000);
        log.info("✓ Generated {} order items", orderItems.size());

        List<Cart> carts = generateCarts(users, 5_000);
        log.info("✓ Generated {} carts", carts.size());

        List<CartItem> cartItems = generateCartItems(carts, products, 15_000);
        log.info("✓ Generated {} cart items", cartItems.size());

        List<Coupon> coupons = generateCoupons(100);
        log.info("✓ Generated {} coupons", coupons.size());

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

    private List<User> generateUsers(int count) {
        log.debug("Generating {} users...", count);
        List<User> users = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            User user = User.create(
                "user" + i + "@test.com",
                "테스트사용자" + i
            );
            long initialBalance = 1_000_000L + random.nextInt(9_000_000);
            user.charge(initialBalance);
            users.add(userRepository.save(user));
            em.flush(); // Flush to ensure ID is populated


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
            Long price = (long) (10_000 + random.nextInt(990_000));
            Integer stock = 50 + random.nextInt(950);

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

            Long subtotal = (long) (50_000 + random.nextInt(950_000));
            Long discount = (long) (random.nextInt(10_000));

            Order order = Order.create(
                "ORD" + String.format("%08d", i),
                user,
                subtotal,
                discount
            );

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
        int itemsPerOrder = Math.max(1, count / orders.size());

        for (Order order : orders) {
            int numItems = Math.min(itemsPerOrder + random.nextInt(3), 5);

            for (int i = 0; i < numItems && orderItems.size() < count; i++) {
                Product product = products.get(random.nextInt(products.size()));
                Integer quantity = 1 + random.nextInt(4);

                OrderItem orderItem = OrderItem.create(
                    order,
                    product,
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
            Cart cart = Cart.create(user);
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
        int itemsPerCart = Math.max(1, count / carts.size());

        for (Cart cart : carts) {
            int numItems = Math.min(itemsPerCart + random.nextInt(3), 10);
            List<Long> addedProductIds = new ArrayList<>();

            for (int i = 0; i < numItems && cartItems.size() < count; i++) {
                Product product;
                int attempts = 0;
                do {
                    product = products.get(random.nextInt(products.size()));
                    attempts++;
                } while (addedProductIds.contains(product.getId()) && attempts < 20);

                if (addedProductIds.contains(product.getId())) {
                    continue;
                }

                Integer quantity = 1 + random.nextInt(4);

                CartItem cartItem = CartItem.create(
                    cart,
                    product,
                    quantity
                );
                cartItems.add(cartItemRepository.save(cartItem));
                addedProductIds.add(product.getId());
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
            Integer discountRate = 5 + random.nextInt(16);
            Integer quantity = 100 + random.nextInt(900);
            LocalDateTime expiresAt = now.plusDays(30 + random.nextInt(60));

            Coupon coupon = Coupon.create(
                "COUPON" + String.format("%04d", i),
                "테스트쿠폰" + i,
                discountRate,
                quantity,
                now,
                expiresAt
            );
            coupons.add(couponRepository.save(coupon));
        }
        return coupons;
    }

    private List<UserCoupon> generateUserCoupons(List<User> users, List<Coupon> coupons, int count) {
        log.debug("Generating {} user coupons...", count);
        List<UserCoupon> userCoupons = new ArrayList<>();
        List<String> issuedPairs = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            User user;
            Coupon coupon;
            String pair = null;
            int attempts = 0;

            do {
                user = users.get(random.nextInt(users.size()));
                coupon = coupons.get(random.nextInt(coupons.size()));
                if (user.getId() != null && coupon.getId() != null) {
                    pair = user.getId() + ":" + coupon.getId();
                }
                attempts++;
            } while (pair == null || (issuedPairs.contains(pair) && attempts < 50));

            if (pair == null || issuedPairs.contains(pair)) {
                continue;
            }

            UserCoupon userCoupon = UserCoupon.create(
                user.getId(),
                coupon.getId(),
                LocalDateTime.now().plusDays(30)
            );
            issuedPairs.add(pair);

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
        log.info("=".repeat(80));
    }
}