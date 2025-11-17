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
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        // initOrders();        // 주문 내역 (Order는 JPA화 안 했으므로 주석 처리)

        log.info("✅ Initial data loading completed!");
    }

    private boolean isDataAlreadyLoaded() {
        // Product 테이블에 데이터가 있으면 이미 초기화된 것으로 판단
        return !productRepository.findAll().isEmpty();
    }

    private void initUsers() {
        log.info("📝 Creating test users...");

        // User 1: 김항해 (잔액 충분)
        User user1 = User.create("hanghae@example.com", "김항해");
        user1.charge(1000000L);  // 100만원 충전
        userRepository.save(user1);

        // User 2: 이플러스 (일반 잔액)
        User user2 = User.create("plus@example.com", "이플러스");
        user2.charge(500000L);  // 50만원 충전
        userRepository.save(user2);

        // User 3: 박백엔드 (적은 잔액)
        User user3 = User.create("backend@example.com", "박백엔드");
        user3.charge(100000L);  // 10만원 충전
        userRepository.save(user3);

        // 동시성 테스트용 추가 사용자 10명
        for (int i = 4; i <= 13; i++) {
            User user = User.create("testuser" + i + "@example.com", "테스트사용자" + i);
            user.charge(1000000L);  // 각 100만원 충전
            userRepository.save(user);
        }

        log.info("   ✓ Created 13 test users (기본 3명 + 동시성 테스트 10명)");
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

        // Coupon 1: 10% 할인 (수량 많음)
        Coupon coupon1 = Coupon.create(
                "WELCOME10",
                "신규 가입 10% 할인",
                10,  // 10% 할인
                1000,  // 총 1000개
                now,
                now.plusMonths(3)  // 3개월 유효
        );
        couponRepository.save(coupon1);

        // Coupon 2: 20% 할인 (수량 적음)
        Coupon coupon2 = Coupon.create(
                "VIP20",
                "VIP 회원 20% 할인",
                20,  // 20% 할인
                100,  // 총 100개 (선착순)
                now,
                now.plusMonths(1)  // 1개월 유효
        );
        couponRepository.save(coupon2);

        // Coupon 3: 15% 할인 (곧 만료)
        Coupon coupon3 = Coupon.create(
                "EARLYBIRD15",
                "얼리버드 15% 할인",
                15,  // 15% 할인
                50,  // 총 50개
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
        CartItem cartItem1 = CartItem.create(savedCart1.getId(), product1, 1);  // Product 엔티티 직접 전달
        cartItemRepository.save(cartItem1);

        // 마우스 2개 담기
        Product product2 = productRepository.findByProductCode("P002").orElseThrow();
        CartItem cartItem2 = CartItem.create(savedCart1.getId(), product2, 2);  // Product 엔티티 직접 전달
        cartItemRepository.save(cartItem2);

        // User 2 (이플러스)의 장바구니
        User user2 = userRepository.findByEmail("plus@example.com").orElseThrow();
        Cart cart2 = Cart.create(user2.getId());
        Cart savedCart2 = cartRepository.save(cart2);

        // 키보드 1개 담기
        Product product3 = productRepository.findByProductCode("P003").orElseThrow();
        CartItem cartItem3 = CartItem.create(savedCart2.getId(), product3, 1);  // Product 엔티티 직접 전달
        cartItemRepository.save(cartItem3);

        log.info("   ✓ Created 2 pre-filled carts (User 1: 2 items, User 2: 1 item)");
    }
}
