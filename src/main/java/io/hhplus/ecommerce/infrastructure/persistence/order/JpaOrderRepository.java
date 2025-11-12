package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Order JPA Repository
 *
 * Spring Data JPA의 JpaRepository를 상속받아 기본 CRUD 기능을 제공하고,
 * Domain의 OrderRepository 인터페이스를 구현하여 비즈니스 로직에서 사용할 수 있도록 합니다.
 *
 * @Primary:
 * - OrderRepository 타입의 빈이 여러 개 있을 때 (JpaOrderRepository, InMemoryOrderRepository)
 * - 기본적으로 JpaOrderRepository를 주입하도록 설정
 * - InMemoryOrderRepository는 @Profile("inmemory")로 분리되어 있어 기본 프로필에서는 비활성화
 *
 * 주의사항:
 * - @Repository 어노테이션은 JpaRepository를 상속받으면 자동으로 적용되지만 명시적으로 추가
 * - 메서드 네이밍 규칙을 따르면 자동으로 쿼리가 생성됨 (Query Method)
 * - 복잡한 쿼리는 @Query 어노테이션으로 직접 작성 가능
 */
@Repository
@Primary
public interface JpaOrderRepository extends JpaRepository<Order, Long>, OrderRepository {

    // Explicitly declare methods to resolve ambiguity with OrderRepository
    @Override
    Optional<Order> findById(Long id);

    @Override
    Order save(Order order);

    @Override
    List<Order> findAll();

    /**
     * 주문 번호(Business ID)로 주문 조회
     *
     * Query Method 네이밍 규칙:
     * - findBy + 필드명 → WHERE 절 생성
     * - OrderNumber는 Order Entity의 orderNumber 필드와 매핑
     *
     * @param orderNumber 주문 번호 (e.g., "ORD-20250111-001")
     * @return Optional<Order>
     */
    @Override
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * 사용자 ID로 주문 목록 조회 (최신순 정렬)
     *
     * Query Method 네이밍 규칙:
     * - findByUserId → WHERE user_id = ?
     * - OrderByCreatedAtDesc → ORDER BY created_at DESC
     *
     * @param userId 사용자 ID
     * @return List<Order> (최신순 정렬)
     */
    @Override
    @Query("SELECT o FROM Order o WHERE o.userId = :userId ORDER BY o.createdAt DESC")
    List<Order> findByUserId(@Param("userId") Long userId);

    // JpaRepository에서 이미 제공하는 메서드들:
    // - delete(Order order) : void
    // - existsById(Long id) : boolean
    // - count() : long
}
