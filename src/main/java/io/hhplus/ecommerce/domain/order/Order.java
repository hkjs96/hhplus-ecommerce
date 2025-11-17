package io.hhplus.ecommerce.domain.order;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Order Entity
 *
 * JPA Entity 생성자가 protected인 이유:
 * 1. JPA 스펙 요구사항: 리플렉션을 통한 인스턴스 생성을 위해 기본 생성자 필요
 * 2. 도메인 무결성 보호: public 생성자 노출 방지로 정적 팩토리 메서드(create)를 통한 생성 강제
 * 3. 프록시 생성 지원: Hibernate가 지연 로딩 프록시 객체 생성 시 사용
 */
@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_user_status", columnList = "user_id, status"),
        @Index(name = "idx_status_paid", columnList = "status, paid_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", unique = true, length = 30, nullable = false)
    private String orderNumber;  // Business ID (외부 노출용, e.g., "ORD-20250111-001")

    @Column(name = "user_id", nullable = false)
    private Long userId;  // FK to users

    /**
     * Order-OrderItem 양방향 연관관계 (1:N)
     * - mappedBy: OrderItem의 order 필드가 연관관계 주인
     * - cascade: Order 저장/삭제 시 OrderItem도 함께 처리
     * - orphanRemoval: Order에서 제거된 OrderItem 자동 삭제
     * - fetch: LAZY로 설정하여 필요할 때만 조회 (N+1 방지는 fetch join 또는 batch size로 해결)
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Column(name = "subtotal_amount", nullable = false)
    private Long subtotalAmount;   // 소계 (할인 전 금액)

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;   // 할인 금액

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;      // 최종 금액 (소계 - 할인)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public static Order create(String orderNumber, Long userId, Long subtotalAmount, Long discountAmount) {
        validateOrderNumber(orderNumber);
        validateUserId(userId);
        validateAmounts(subtotalAmount, discountAmount);

        Long totalAmount = subtotalAmount - discountAmount;

        Order order = new Order();
        order.orderNumber = orderNumber;
        order.userId = userId;
        order.subtotalAmount = subtotalAmount;
        order.discountAmount = discountAmount;
        order.totalAmount = totalAmount;
        order.status = OrderStatus.PENDING;
        order.paidAt = null;  // 결제 완료 시 설정
        // createdAt은 JPA Auditing이 자동 처리

        return order;
    }

    public void complete() {
        validateStatusForComplete();

        this.status = OrderStatus.COMPLETED;
        this.paidAt = LocalDateTime.now();
    }

    public void cancel() {
        validateStatusForCancel();

        this.status = OrderStatus.CANCELLED;
    }

    public boolean isPending() {
        return this.status == OrderStatus.PENDING;
    }

    public boolean isCompleted() {
        return this.status == OrderStatus.COMPLETED;
    }

    public boolean isCancelled() {
        return this.status == OrderStatus.CANCELLED;
    }

    // ====================================
    // Validation Methods
    // ====================================

    private static void validateOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "주문 번호는 필수입니다"
            );
        }
    }

    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "사용자 ID는 필수입니다"
            );
        }
    }

    private static void validateAmounts(Long subtotalAmount, Long discountAmount) {
        if (subtotalAmount == null || subtotalAmount <= 0) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "주문 금액은 0보다 커야 합니다"
            );
        }

        if (discountAmount == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "할인 금액은 필수입니다 (0일 수 있음)"
            );
        }

        if (discountAmount < 0) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "할인 금액은 0 이상이어야 합니다"
            );
        }

        if (discountAmount > subtotalAmount) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "할인 금액이 주문 금액보다 클 수 없습니다"
            );
        }
    }

    private void validateStatusForComplete() {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException(
                ErrorCode.INVALID_ORDER_STATUS,
                String.format("결제 대기 중인 주문만 완료할 수 있습니다. 현재 상태: %s", this.status)
            );
        }
    }

    private void validateStatusForCancel() {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException(
                ErrorCode.INVALID_ORDER_STATUS,
                String.format("결제 대기 중인 주문만 취소할 수 있습니다. 현재 상태: %s", this.status)
            );
        }
    }
}
