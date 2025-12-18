package io.hhplus.ecommerce.application.usecase.cart;

import io.hhplus.ecommerce.application.cart.dto.AddCartItemRequest;
import io.hhplus.ecommerce.application.cart.dto.CartResponse;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.cart.Cart;
import io.hhplus.ecommerce.domain.cart.CartItem;
import io.hhplus.ecommerce.domain.cart.CartItemRepository;
import io.hhplus.ecommerce.domain.cart.CartRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class AddToCartUseCaseTest {

    @Autowired
    private AddToCartUseCase addToCartUseCase;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.cart.JpaCartRepository jpaCartRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.cart.JpaCartItemRepository jpaCartItemRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.product.JpaProductRepository jpaProductRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.user.JpaUserRepository jpaUserRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.order.JpaOrderItemRepository jpaOrderItemRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.order.JpaOrderRepository jpaOrderRepository;

    @BeforeEach
    void setUp() {
        // 테이블 초기화
        jpaOrderItemRepository.deleteAll();
        jpaOrderRepository.deleteAll();
        jpaCartItemRepository.deleteAll();
        jpaCartRepository.deleteAll();
        jpaProductRepository.deleteAll();
        jpaUserRepository.deleteAll();
    }

    @Test
    @DisplayName("정상 추가 - 새 아이템을 장바구니에 추가")
    void shouldAddNewItemToCart() {
        // given
        User user = User.create("cart@test.com", "cart-user");
        userRepository.save(user);

        Product product = Product.create("P001", "상품1", "설명", 10000L, "전자기기", 100);
        productRepository.save(product);

        AddCartItemRequest request = new AddCartItemRequest(user.getId(), product.getId(), 5);

        // when
        CartResponse response = addToCartUseCase.execute(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo(product.getId());
        assertThat(response.items().get(0).quantity()).isEqualTo(5);

        // 장바구니 자동 생성 확인
        Optional<Cart> cartOpt = cartRepository.findByUserId(user.getId());
        assertThat(cartOpt).isPresent();
    }

    @Test
    @DisplayName("기존 아이템 수량 증가 - 동일 상품 추가 시 수량만 증가")
    void shouldIncreaseQuantityForExistingItem() {
        // given
        User user = User.create("cart@test.com", "cart-user");
        userRepository.save(user);

        Product product = Product.create("P001", "상품1", "설명", 10000L, "전자기기", 100);
        productRepository.save(product);

        // 첫 번째 추가 (5개)
        AddCartItemRequest request1 = new AddCartItemRequest(user.getId(), product.getId(), 5);
        addToCartUseCase.execute(request1);

        // when - 두 번째 추가 (3개 더)
        AddCartItemRequest request2 = new AddCartItemRequest(user.getId(), product.getId(), 3);
        CartResponse response = addToCartUseCase.execute(request2);

        // then
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(8); // 5 + 3
        assertThat(response.items().get(0).productId()).isEqualTo(product.getId());
    }

    @Test
    @DisplayName("재고 부족 - 요청 수량이 재고보다 많으면 실패")
    void shouldFailWhenInsufficientStock() {
        // given
        User user = User.create("cart@test.com", "cart-user");
        userRepository.save(user);

        Product product = Product.create("P001", "상품1", "설명", 10000L, "전자기기", 10); // 재고 10개
        productRepository.save(product);

        AddCartItemRequest request = new AddCartItemRequest(user.getId(), product.getId(), 15); // 15개 요청

        // when & then
        assertThatThrownBy(() -> addToCartUseCase.execute(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK)
            .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    @DisplayName("재고 부족 - 기존 수량 + 추가 수량이 재고 초과 시 실패")
    void shouldFailWhenTotalQuantityExceedsStock() {
        // given
        User user = User.create("cart@test.com", "cart-user");
        userRepository.save(user);

        Product product = Product.create("P001", "상품1", "설명", 10000L, "전자기기", 10); // 재고 10개
        productRepository.save(product);

        // 첫 번째 추가 (7개)
        AddCartItemRequest request1 = new AddCartItemRequest(user.getId(), product.getId(), 7);
        addToCartUseCase.execute(request1);

        // when & then - 두 번째 추가 (5개 더) → 총 12개 (재고 10개 초과)
        AddCartItemRequest request2 = new AddCartItemRequest(user.getId(), product.getId(), 5);
        assertThatThrownBy(() -> addToCartUseCase.execute(request2))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK)
            .hasMessageContaining("재고가 부족합니다");

        // 기존 수량은 유지되는지 확인 (실패 후 원래 상태 유지)
        // GetCartUseCase를 호출하거나 트랜잭션 내에서 확인 필요
        // 여기서는 예외 발생만으로 충분
    }

    @Test
    @DisplayName("사용자 없음 - 존재하지 않는 사용자 ID로 추가 시도")
    void shouldFailWhenUserNotFound() {
        // given
        Product product = Product.create("P001", "상품1", "설명", 10000L, "전자기기", 100);
        productRepository.save(product);

        Long nonExistentUserId = 99999L;
        AddCartItemRequest request = new AddCartItemRequest(nonExistentUserId, product.getId(), 5);

        // when & then
        assertThatThrownBy(() -> addToCartUseCase.execute(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("상품 없음 - 존재하지 않는 상품 ID로 추가 시도")
    void shouldFailWhenProductNotFound() {
        // given
        User user = User.create("cart@test.com", "cart-user");
        userRepository.save(user);

        Long nonExistentProductId = 99999L;
        AddCartItemRequest request = new AddCartItemRequest(user.getId(), nonExistentProductId, 5);

        // when & then
        assertThatThrownBy(() -> addToCartUseCase.execute(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("장바구니 자동 생성 - Cart가 없으면 자동으로 생성")
    void shouldCreateCartAutomaticallyIfNotExists() {
        // given
        User user = User.create("cart@test.com", "cart-user");
        userRepository.save(user);

        Product product = Product.create("P001", "상품1", "설명", 10000L, "전자기기", 100);
        productRepository.save(product);

        // Cart가 없는 상태 확인
        Optional<Cart> cartBefore = cartRepository.findByUserId(user.getId());
        assertThat(cartBefore).isEmpty();

        AddCartItemRequest request = new AddCartItemRequest(user.getId(), product.getId(), 5);

        // when
        CartResponse response = addToCartUseCase.execute(request);

        // then
        assertThat(response).isNotNull();

        // Cart가 자동 생성되었는지 확인
        Optional<Cart> cartAfter = cartRepository.findByUserId(user.getId());
        assertThat(cartAfter).isPresent();
        assertThat(cartAfter.get().getUserId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("여러 상품 추가 - 서로 다른 상품을 장바구니에 추가")
    void shouldAddMultipleProductsToCart() {
        // given
        User user = User.create("cart@test.com", "cart-user");
        userRepository.save(user);

        Product product1 = Product.create("P001", "상품1", "설명1", 10000L, "전자기기", 100);
        Product product2 = Product.create("P002", "상품2", "설명2", 20000L, "의류", 50);
        productRepository.save(product1);
        productRepository.save(product2);

        AddCartItemRequest request1 = new AddCartItemRequest(user.getId(), product1.getId(), 3);
        AddCartItemRequest request2 = new AddCartItemRequest(user.getId(), product2.getId(), 2);

        // when
        addToCartUseCase.execute(request1);
        CartResponse response = addToCartUseCase.execute(request2);

        // then
        assertThat(response.items()).hasSize(2);

        // CartResponse에서 각 상품 확인
        boolean hasProduct1 = response.items().stream()
            .anyMatch(item -> item.productId().equals(product1.getId()) && item.quantity() == 3);
        boolean hasProduct2 = response.items().stream()
            .anyMatch(item -> item.productId().equals(product2.getId()) && item.quantity() == 2);

        assertThat(hasProduct1).isTrue();
        assertThat(hasProduct2).isTrue();
    }
}
