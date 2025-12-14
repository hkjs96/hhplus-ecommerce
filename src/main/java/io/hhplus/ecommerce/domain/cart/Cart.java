package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.common.BaseTimeEntity;
import io.hhplus.ecommerce.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Cart Entity (장바구니 집합 루트)
 *
 * 개선 사항:
 * 1. BaseTimeEntity 상속으로 created_at, updated_at 자동 관리
 * 2. CartItem과 양방향 관계 설정 (1:N)
 * 3. cascade, orphanRemoval로 CartItem 라이프사이클 관리
 */
@Entity
@Table(
    name = "carts",
    indexes = {
        @Index(name = "idx_user_id", columnList = "user_id")
    }
)
@Getter
@NoArgsConstructor
public class Cart extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;  // FK to users

    /**
     * 양방향 관계: Cart 1 : N CartItem
     * - mappedBy: CartItem.cart 필드가 관계의 주인
     * - cascade ALL: Cart 저장/삭제 시 CartItem도 함께 처리
     * - orphanRemoval: 연관관계가 끊긴 CartItem 자동 삭제
     * - fetch LAZY: 필요시에만 CartItem 로딩 (N+1 방지)
     */
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CartItem> cartItems = new ArrayList<>();

    public static Cart create(User user) {
        validateUserId(user.getId());

        Cart cart = new Cart();
        cart.userId = user.getId();
        // createdAt, updatedAt은 JPA Auditing이 자동 처리

        return cart;
    }

    /**
     * CartItem 추가 (양방향 관계 동기화)
     */
    public void addCartItem(CartItem cartItem) {
        this.cartItems.add(cartItem);
        cartItem.setCart(this);
    }

    /**
     * CartItem 제거 (양방향 관계 동기화)
     */
    public void removeCartItem(CartItem cartItem) {
        this.cartItems.remove(cartItem);
        cartItem.setCart(null);
    }

    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "사용자 ID는 필수입니다"
            );
        }
    }
}
