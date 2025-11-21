package io.hhplus.ecommerce.infrastructure.config;

import io.hhplus.ecommerce.domain.cart.Cart;
import io.hhplus.ecommerce.domain.cart.CartItem;
import io.hhplus.ecommerce.domain.cart.CartItemRepository;
import io.hhplus.ecommerce.domain.cart.CartRepository;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.product.ProductSalesAggregate;
import io.hhplus.ecommerce.domain.product.ProductSalesAggregateRepository;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.order.OrderStatus;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@Profile("!test")  // 테스트 환경에서는 비활성화
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final ProductSalesAggregateRepository aggregateRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("🚀 Starting initial data loading...");

        // 중복 방지: 이미 데이터가 존재하면 건너뜀
        if (isDataAlreadyLoaded()) {
            log.info("⏭️  Initial data already exists. Skipping data initialization.");
            return;
        }

        // 1. 기본 도메인 데이터 생성
        initUsers();
        initProducts();
        initCoupons();

        // 2. 관계 데이터 생성 (시나리오 테스트용)
        initUserCoupons();      // 미리 발급된 쿠폰
        initCarts();            // 미리 담긴 장바구니
        initOrders();           // 주문 내역

        // 3. 쿼리 최적화를 위한 ROLLUP 테이블 데이터 생성
        initProductSalesAggregates();  // 인기 상품 집계 데이터

        log.info("✅ Initial data loading completed!");
    }

    private boolean isDataAlreadyLoaded() {
        // Product 테이블에 데이터가 있으면 이미 초기화된 것으로 판단
        return !productRepository.findAll().isEmpty();
    }

    private void initUsers() {
        log.info("📝 Creating test users...");

        // User 1: 김항해 (K6 부하 테스트용 - 잔액 충분)
        User user1 = User.create("hanghae@example.com", "김항해");
        user1.charge(100000000L);  // 1억원 충전 (K6 부하 테스트용)
        userRepository.save(user1);

        // User 2: 이플러스 (일반 잔액)
        User user2 = User.create("plus@example.com", "이플러스");
        user2.charge(500000L);  // 50만원 충전
        userRepository.save(user2);

        // User 3: 박백엔드 (적은 잔액)
        User user3 = User.create("backend@example.com", "박백엔드");
        user3.charge(100000L);  // 10만원 충전
        userRepository.save(user3);

        // K6 부하 테스트용 추가 사용자 100명 (각 1억원) - 동시성 테스트용
        for (int i = 4; i <= 103; i++) {
            User user = User.create("testuser" + i + "@example.com", "테스트사용자" + i);
            user.charge(100000000L);  // 각 1억원 충전 (K6 부하 테스트용)
            userRepository.save(user);
        }

        log.info("   ✓ Created 103 test users (기본 3명 + K6 테스트 100명)");
        log.info("   💰 K6 test users (1-103): 각 100,000,000원 (지속적인 부하 테스트 가능)");
    }

    private void initProducts() {
        log.info("📦 Creating test products...");

        // 전자제품 카테고리 (7개)
        productRepository.save(Product.create("P001", "노트북", "고성능 게이밍 노트북", 1500000L, "전자제품", 50));
        productRepository.save(Product.create("P002", "마우스", "무선 게이밍 마우스", 80000L, "전자제품", 100));
        productRepository.save(Product.create("P003", "키보드", "기계식 키보드", 120000L, "전자제품", 75));
        productRepository.save(Product.create("P004", "모니터", "27인치 4K 모니터", 500000L, "전자제품", 30));
        productRepository.save(Product.create("P005", "헤드셋", "노이즈 캔슬링 헤드셋", 250000L, "전자제품", 60));
        productRepository.save(Product.create("P011", "웹캠", "4K 화상 회의용 웹캠", 150000L, "전자제품", 45));
        productRepository.save(Product.create("P012", "스피커", "블루투스 무선 스피커", 95000L, "전자제품", 80));
        productRepository.save(Product.create("P013", "마이크", "USB 스트리밍 마이크", 180000L, "전자제품", 0));  // ⚠️ 품절 상품

        // 가구 카테고리 (3개)
        productRepository.save(Product.create("P006", "의자", "게이밍 의자", 350000L, "가구", 20));
        productRepository.save(Product.create("P007", "책상", "높이 조절 책상", 450000L, "가구", 15));
        productRepository.save(Product.create("P014", "모니터암", "듀얼 모니터 거치대", 75000L, "가구", 2));  // ⚠️ 재고 적음 (선착순)

        // 도서 카테고리 (5개)
        productRepository.save(Product.create("P008", "자바 프로그래밍", "Java 완벽 가이드", 45000L, "도서", 200));
        productRepository.save(Product.create("P009", "스프링 부트", "Spring Boot 실전 가이드", 38000L, "도서", 150));
        productRepository.save(Product.create("P010", "DDD", "도메인 주도 설계", 42000L, "도서", 100));
        productRepository.save(Product.create("P015", "클린 아키텍처", "소프트웨어 설계 원칙", 35000L, "도서", 180));
        productRepository.save(Product.create("P016", "리팩토링", "코드 품질 개선 가이드", 40000L, "도서", 120));

        // 의류 카테고리 (3개) - 새로운 카테고리
        productRepository.save(Product.create("P017", "프로그래머 티셔츠", "Hello World 디자인", 25000L, "의류", 300));
        productRepository.save(Product.create("P018", "후드티", "개발자 전용 후드티", 55000L, "의류", 150));
        productRepository.save(Product.create("P019", "코딩 양말", "이진수 패턴 양말", 12000L, "의류", 500));

        // 극단 가격 상품 (Edge Case 테스트용)
        productRepository.save(Product.create("P020", "개발자 스티커", "Git 명령어 스티커", 1000L, "잡화", 1000));  // ⚠️ 최저가
        productRepository.save(Product.create("P021", "워크스테이션", "전문가용 고성능 워크스테이션", 15000000L, "전자제품", 3));  // ⚠️ 최고가

        log.info("   ✓ Created 21 test products (전자제품: 9, 가구: 3, 도서: 5, 의류: 3, 잡화: 1)");
        log.info("   ⚠️ Edge cases: P013(품절), P014(재고 2개), P020(최저가 1,000원), P021(최고가 15,000,000원)");
    }

    private void initCoupons() {
        log.info("🎟️ Creating test coupons...");

        LocalDateTime now = LocalDateTime.now();

        // Coupon 1: 10% 할인 (K6 동시성 테스트용 - 100명 vs 200개)
        Coupon coupon1 = Coupon.create(
                "WELCOME10",
                "신규 가입 10% 할인",
                10,  // 10% 할인
                200,  // 총 200개 (동시성 테스트: 100명이 200개 쟁탈)
                now,
                now.plusMonths(3)  // 3개월 유효
        );
        couponRepository.save(coupon1);

        // Coupon 2: 20% 할인 (K6 동시성 테스트용)
        Coupon coupon2 = Coupon.create(
                "VIP20",
                "VIP 회원 20% 할인",
                20,  // 20% 할인
                200,  // 총 200개 (동시성 테스트)
                now,
                now.plusMonths(1)  // 1개월 유효
        );
        couponRepository.save(coupon2);

        // Coupon 3: 15% 할인 (K6 동시성 테스트용)
        Coupon coupon3 = Coupon.create(
                "EARLYBIRD15",
                "얼리버드 15% 할인",
                15,  // 15% 할인
                200,  // 총 200개 (동시성 테스트)
                now.minusDays(20),  // 20일 전부터 시작
                now.plusDays(10)  // 10일 후 만료
        );
        couponRepository.save(coupon3);

        // Coupon 4: 품절 쿠폰 (Edge Case: 수량 1로 생성 후 발급하여 품절 처리)
        Coupon soldOutCoupon = Coupon.create(
                "SOLDOUT",
                "품절 테스트용 쿠폰",
                25,  // 25% 할인
                1,  // 초기 수량 1개로 생성
                now,
                now.plusMonths(1)
        );
        soldOutCoupon.issue();  // 1개 발급하여 품절 처리
        couponRepository.save(soldOutCoupon);

        // Coupon 5: 만료된 쿠폰 (Edge Case: 이미 만료)
        Coupon expiredCoupon = Coupon.create(
                "EXPIRED30",
                "만료된 30% 할인",
                30,  // 30% 할인
                100,
                now.minusMonths(2),  // 2개월 전 시작
                now.minusDays(1)     // ⚠️ 어제 만료됨
        );
        couponRepository.save(expiredCoupon);

        log.info("   ✓ Created 5 test coupons");
        log.info("   🎫 K6 test coupons (1-3): 각 200개 (동시성 테스트: 100명 vs 200개 경합)");
        log.info("   ⚠️ Edge cases: SOLDOUT(품절), EXPIRED30(만료됨)");
    }

    private void initUserCoupons() {
        log.info("🎫 Creating pre-issued coupons for users...");

        // User 1 (김항해)에게 WELCOME10 쿠폰 발급
        User user1 = userRepository.findByEmail("hanghae@example.com").orElseThrow();
        Coupon coupon1 = couponRepository.findByCouponCode("WELCOME10").orElseThrow();

        UserCoupon userCoupon1 = UserCoupon.create(user1.getId(), coupon1.getId(), coupon1.getExpiresAt());
        userCouponRepository.save(userCoupon1);
        coupon1.issue();  // 수량 차감
        couponRepository.save(coupon1);

        // User 2 (이플러스)에게 VIP20 쿠폰 발급
        User user2 = userRepository.findByEmail("plus@example.com").orElseThrow();
        Coupon coupon2 = couponRepository.findByCouponCode("VIP20").orElseThrow();

        UserCoupon userCoupon2 = UserCoupon.create(user2.getId(), coupon2.getId(), coupon2.getExpiresAt());
        userCouponRepository.save(userCoupon2);
        coupon2.issue();  // 수량 차감
        couponRepository.save(coupon2);

        // User 3 (박백엔드)에게 EARLYBIRD15 쿠폰 발급 후 사용 처리 (Edge Case: 이미 사용됨)
        User user3 = userRepository.findByEmail("backend@example.com").orElseThrow();
        Coupon coupon3 = couponRepository.findByCouponCode("EARLYBIRD15").orElseThrow();

        UserCoupon userCoupon3 = UserCoupon.create(user3.getId(), coupon3.getId(), coupon3.getExpiresAt());
        userCoupon3.use();  // ⚠️ 이미 사용 처리
        userCouponRepository.save(userCoupon3);
        coupon3.issue();  // 수량 차감
        couponRepository.save(coupon3);

        log.info("   ✓ Pre-issued 3 coupons (User 1: WELCOME10, User 2: VIP20, User 3: EARLYBIRD15-사용됨)");
    }

    private void initCarts() {
        log.info("🛒 Creating pre-filled carts...");

        // User 1 (김항해)의 장바구니
        User user1 = userRepository.findByEmail("hanghae@example.com").orElseThrow();
        Cart cart1 = Cart.create(user1.getId());
        Cart savedCart1 = cartRepository.save(cart1);

        // 노트북 1개 담기
        Product product1 = productRepository.findByProductCode("P001").orElseThrow();
        CartItem cartItem1 = CartItem.create(savedCart1, product1, 1);  // Cart 엔티티 직접 전달
        cartItemRepository.save(cartItem1);

        // 마우스 2개 담기
        Product product2 = productRepository.findByProductCode("P002").orElseThrow();
        CartItem cartItem2 = CartItem.create(savedCart1, product2, 2);  // Cart 엔티티 직접 전달
        cartItemRepository.save(cartItem2);

        // User 2 (이플러스)의 장바구니
        User user2 = userRepository.findByEmail("plus@example.com").orElseThrow();
        Cart cart2 = Cart.create(user2.getId());
        Cart savedCart2 = cartRepository.save(cart2);

        // 키보드 1개 담기
        Product product3 = productRepository.findByProductCode("P003").orElseThrow();
        CartItem cartItem3 = CartItem.create(savedCart2, product3, 1);  // Cart 엔티티 직접 전달
        cartItemRepository.save(cartItem3);

        log.info("   ✓ Created 2 pre-filled carts (User 1: 2 items, User 2: 1 item)");
    }

    private void initOrders() {
        log.info("📦 Creating test orders...");

        User user1 = userRepository.findByEmail("hanghae@example.com").orElseThrow();
        User user2 = userRepository.findByEmail("plus@example.com").orElseThrow();
        User user3 = userRepository.findByEmail("backend@example.com").orElseThrow();

        // 전체 상품 목록 가져오기
        Product laptop = productRepository.findByProductCode("P001").orElseThrow();
        Product mouse = productRepository.findByProductCode("P002").orElseThrow();
        Product keyboard = productRepository.findByProductCode("P003").orElseThrow();
        Product monitor = productRepository.findByProductCode("P004").orElseThrow();
        Product headset = productRepository.findByProductCode("P005").orElseThrow();
        Product webcam = productRepository.findByProductCode("P006").orElseThrow();
        Product speaker = productRepository.findByProductCode("P007").orElseThrow();

        int orderCount = 0;

        // User 1 (김항해): 10개의 주문 생성
        for (int i = 1; i <= 10; i++) {
            String orderNumber = String.format("ORD-20250118-%03d", ++orderCount);

            // 주문마다 3-5개의 상품 포함
            Long subtotal;

            if (i % 3 == 0) {
                // 노트북 + 마우스 + 키보드
                subtotal = laptop.getPrice() + (mouse.getPrice() * 2) + keyboard.getPrice();
            } else if (i % 3 == 1) {
                // 모니터 + 헤드셋 + 웹캠
                subtotal = monitor.getPrice() + (headset.getPrice() * 2) + webcam.getPrice();
            } else {
                // 스피커 + 마우스 + 키보드 + 웹캠
                subtotal = speaker.getPrice() + mouse.getPrice() + keyboard.getPrice() + webcam.getPrice();
            }

            Order order = Order.create(orderNumber, user1.getId(), subtotal, 0L);

            if (i % 3 == 0) {
                OrderItem.create(order, laptop, 1, laptop.getPrice());
                OrderItem.create(order, mouse, 2, mouse.getPrice());
                OrderItem.create(order, keyboard, 1, keyboard.getPrice());
            } else if (i % 3 == 1) {
                OrderItem.create(order, monitor, 1, monitor.getPrice());
                OrderItem.create(order, headset, 2, headset.getPrice());
                OrderItem.create(order, webcam, 1, webcam.getPrice());
            } else {
                OrderItem.create(order, speaker, 1, speaker.getPrice());
                OrderItem.create(order, mouse, 1, mouse.getPrice());
                OrderItem.create(order, keyboard, 1, keyboard.getPrice());
                OrderItem.create(order, webcam, 1, webcam.getPrice());
            }

            // 70% 확률로 완료 처리
            if (i <= 7) {
                order.complete();
            }

            orderRepository.save(order);
        }

        // User 2 (이플러스): 5개의 주문 생성
        for (int i = 1; i <= 5; i++) {
            String orderNumber = String.format("ORD-20250118-%03d", ++orderCount);
            Long subtotal;

            if (i % 2 == 0) {
                subtotal = laptop.getPrice() + monitor.getPrice();
            } else {
                subtotal = (keyboard.getPrice() * 2) + (mouse.getPrice() * 3);
            }

            Order order = Order.create(orderNumber, user2.getId(), subtotal, 0L);

            if (i % 2 == 0) {
                OrderItem.create(order, laptop, 1, laptop.getPrice());
                OrderItem.create(order, monitor, 1, monitor.getPrice());
            } else {
                OrderItem.create(order, keyboard, 2, keyboard.getPrice());
                OrderItem.create(order, mouse, 3, mouse.getPrice());
            }

            if (i <= 3) {
                order.complete();
            }

            orderRepository.save(order);
        }

        // User 3 (박백엔드): 3개의 주문 생성
        for (int i = 1; i <= 3; i++) {
            String orderNumber = String.format("ORD-20250118-%03d", ++orderCount);
            Long subtotal = headset.getPrice() + webcam.getPrice();

            Order order = Order.create(orderNumber, user3.getId(), subtotal, 0L);

            OrderItem.create(order, headset, 1, headset.getPrice());
            OrderItem.create(order, webcam, 1, webcam.getPrice());

            if (i <= 2) {
                order.complete();
            }

            orderRepository.save(order);
        }

        log.info("   ✓ Created 18 test orders (User 1: 10, User 2: 5, User 3: 3)");
        log.info("   ℹ️ Average 3-4 items per order for realistic N+1 demonstration");
        log.info("   📊 Expected queries WITHOUT Fetch Join: ~55+ queries");
        log.info("   📊 Expected queries WITH Fetch Join: 1 query");
    }

    private void initProductSalesAggregates() {
        log.info("📊 Creating product sales aggregates (ROLLUP table)...");

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);

        // 상품별 3일간 집계 데이터 생성
        // 노트북 (Product ID: 1) - 가장 인기
        aggregateRepository.save(ProductSalesAggregate.create(1L, "노트북", twoDaysAgo, 15, 22500000L));
        aggregateRepository.save(ProductSalesAggregate.create(1L, "노트북", yesterday, 20, 30000000L));
        aggregateRepository.save(ProductSalesAggregate.create(1L, "노트북", today, 25, 37500000L));

        // 무선 마우스 (Product ID: 2) - 2위
        aggregateRepository.save(ProductSalesAggregate.create(2L, "무선 마우스", twoDaysAgo, 25, 625000L));
        aggregateRepository.save(ProductSalesAggregate.create(2L, "무선 마우스", yesterday, 30, 750000L));
        aggregateRepository.save(ProductSalesAggregate.create(2L, "무선 마우스", today, 35, 875000L));

        // 기계식 키보드 (Product ID: 3) - 3위
        aggregateRepository.save(ProductSalesAggregate.create(3L, "기계식 키보드", twoDaysAgo, 20, 2000000L));
        aggregateRepository.save(ProductSalesAggregate.create(3L, "기계식 키보드", yesterday, 22, 2200000L));
        aggregateRepository.save(ProductSalesAggregate.create(3L, "기계식 키보드", today, 28, 2800000L));

        // 무선 헤드셋 (Product ID: 5) - 4위
        aggregateRepository.save(ProductSalesAggregate.create(5L, "무선 헤드셋", twoDaysAgo, 18, 2700000L));
        aggregateRepository.save(ProductSalesAggregate.create(5L, "무선 헤드셋", yesterday, 15, 2250000L));
        aggregateRepository.save(ProductSalesAggregate.create(5L, "무선 헤드셋", today, 20, 3000000L));

        // 27인치 모니터 (Product ID: 4) - 5위
        aggregateRepository.save(ProductSalesAggregate.create(4L, "27인치 모니터", twoDaysAgo, 10, 3000000L));
        aggregateRepository.save(ProductSalesAggregate.create(4L, "27인치 모니터", yesterday, 12, 3600000L));
        aggregateRepository.save(ProductSalesAggregate.create(4L, "27인치 모니터", today, 15, 4500000L));

        log.info("   ✓ Created 15 sales aggregates (5 products × 3 days)");
        log.info("   📈 Top Products (3-day total):");
        log.info("      1. 무선 마우스: 90건 / 2,250,000원");
        log.info("      2. 기계식 키보드: 70건 / 7,000,000원");
        log.info("      3. 노트북: 60건 / 90,000,000원");
        log.info("      4. 무선 헤드셋: 53건 / 7,950,000원");
        log.info("      5. 27인치 모니터: 37건 / 11,100,000원");
        log.info("   ℹ️ Use GET /api/products/top to verify optimized query");
    }
}
