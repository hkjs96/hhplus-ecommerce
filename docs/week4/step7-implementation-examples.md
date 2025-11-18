# Week 4 - STEP 7: 인프라 통합과 실제 구현 예시

> 📌 참고: 이 문서의 모든 코드는 예시입니다. 정답이 아니며, 다양한 방식으로 구현할 수 있습니다.

---

## 🎯 학습 목표

- Week 3의 Repository 인터페이스를 실제 데이터베이스와 연동할 수 있다
- 외부 데이터 플랫폼 연동을 구현할 수 있다
- 트랜잭션을 활용하여 주문/결제 일관성을 보장할 수 있다
- 통합 테스트를 작성하고 실행할 수 있다

---

## 📚 Week 3 연계

| Week 3 | Week 4 |
|--------|--------|
| Repository 인터페이스 | 실제 MySQL 구현 |
| Mock Repository | 실제 Repository로 교체 |
| 단위 테스트 | 통합 테스트로 확장 |
| In-Memory | Database (MySQL) |

---

## Step 1: 데이터베이스 연동

### 1.1 MySQL 연결 설정 (예시)

#### Java (Spring JDBC)

```java
// infrastructure/config/DataSourceConfig.java
package io.hhplus.ecommerce.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(System.getenv("DB_URL") != null ?
                System.getenv("DB_URL") : "jdbc:mysql://localhost:3306/ecommerce");
        dataSource.setUsername(System.getenv("DB_USER") != null ?
                System.getenv("DB_USER") : "root");
        dataSource.setPassword(System.getenv("DB_PASSWORD") != null ?
                System.getenv("DB_PASSWORD") : "password");
        return dataSource;
    }
}
```

#### application.yml (Spring Boot 표준)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ecommerce?serverTimezone=Asia/Seoul
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:password}
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        default_batch_fetch_size: 100
```

---

### 1.2 마이그레이션 스크립트 (예시)

Week 2에서 설계한 ERD를 실제 테이블로 생성합니다.

```sql
-- migrations/001_create_tables.sql

-- 사용자 테이블
CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  name VARCHAR(100) NOT NULL,
  balance DECIMAL(10, 2) DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 상품 테이블
CREATE TABLE IF NOT EXISTS products (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  price DECIMAL(10, 2) NOT NULL,
  stock INT NOT NULL DEFAULT 0,
  category VARCHAR(100),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_category (category),
  INDEX idx_created (created_at)
);

-- 주문 테이블
CREATE TABLE IF NOT EXISTS orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  total_amount DECIMAL(10, 2) NOT NULL,
  discount_amount DECIMAL(10, 2) DEFAULT 0,
  final_amount DECIMAL(10, 2) NOT NULL,
  status ENUM('PENDING', 'PAID', 'CANCELLED') DEFAULT 'PENDING',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  paid_at TIMESTAMP NULL,
  FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_user_status (user_id, status),
  INDEX idx_created (created_at)
);

-- 주문 상품 테이블
CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(10, 2) NOT NULL,
  subtotal DECIMAL(10, 2) NOT NULL,
  FOREIGN KEY (order_id) REFERENCES orders(id),
  FOREIGN KEY (product_id) REFERENCES products(id),
  INDEX idx_order (order_id),
  INDEX idx_product (product_id)
);

-- 쿠폰 테이블
CREATE TABLE IF NOT EXISTS coupons (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  discount_rate INT NOT NULL,
  total_quantity INT NOT NULL,
  issued_quantity INT DEFAULT 0,
  start_date DATETIME NOT NULL,
  end_date DATETIME NOT NULL,
  INDEX idx_dates (start_date, end_date)
);

-- 사용자 쿠폰 테이블
CREATE TABLE IF NOT EXISTS user_coupons (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  coupon_id BIGINT NOT NULL,
  status ENUM('AVAILABLE', 'USED', 'EXPIRED') DEFAULT 'AVAILABLE',
  issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  used_at TIMESTAMP NULL,
  expires_at TIMESTAMP NULL,
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (coupon_id) REFERENCES coupons(id),
  INDEX idx_user_status (user_id, status),
  INDEX idx_expires (expires_at),
  UNIQUE KEY uk_user_coupon (user_id, coupon_id)
);

-- Outbox 패턴을 위한 테이블
CREATE TABLE IF NOT EXISTS data_transmissions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  payload JSON NOT NULL,
  status ENUM('PENDING', 'SUCCESS', 'FAILED') DEFAULT 'PENDING',
  attempts INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  sent_at TIMESTAMP NULL,
  FOREIGN KEY (order_id) REFERENCES orders(id),
  INDEX idx_status_created (status, created_at)
);
```

### ✅ 체크포인트

- [ ] 데이터베이스 연결 풀이 설정되었나요?
- [ ] 모든 테이블이 생성되었나요?
- [ ] 인덱스가 적절히 설정되었나요?

---

## Step 2: Repository 구현

### 2.1 상품 Repository 구현 (예시)

Week 3의 `ProductRepository` 인터페이스를 구현합니다.

#### Java (Spring JDBC Template)

```java
// infrastructure/repositories/JdbcProductRepository.java
package io.hhplus.ecommerce.infrastructure.persistence.product;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcProductRepository implements ProductRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Product> productMapper = (rs, rowNum) -> {
        return Product.builder()
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .price(rs.getLong("price"))
                .stock(rs.getInt("stock"))
                .category(rs.getString("category"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build();
    };

    @Override
    public Optional<Product> findById(Long id) {
        List<Product> results = jdbcTemplate.query(
                "SELECT * FROM products WHERE id = ?",
                productMapper,
                id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<Product> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM products ORDER BY created_at DESC",
                productMapper
        );
    }

    @Override
    public List<Product> findByCategory(String category) {
        return jdbcTemplate.query(
                "SELECT * FROM products WHERE category = ? ORDER BY created_at DESC",
                productMapper,
                category
        );
    }

    @Override
    @Transactional
    public Product save(Product product) {
        if (product.getId() == null) {
            // Insert
            jdbcTemplate.update(
                    """
                    INSERT INTO products (name, description, price, stock, category)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    product.getName(),
                    product.getDescription(),
                    product.getPrice(),
                    product.getStock(),
                    product.getCategory()
            );

            // 생성된 ID 조회
            Long id = jdbcTemplate.queryForObject(
                    "SELECT LAST_INSERT_ID()",
                    Long.class
            );
            product.setId(id);
        } else {
            // Update (재고 변경)
            jdbcTemplate.update(
                    "UPDATE products SET stock = ? WHERE id = ?",
                    product.getStock(),
                    product.getId()
            );
        }
        return product;
    }

    @Override
    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM products WHERE id = ?", id);
    }

    public List<ProductSalesDTO> findTopSelling(LocalDateTime startDate, int limit) {
        return jdbcTemplate.query(
                """
                SELECT
                  p.id,
                  p.name,
                  SUM(oi.quantity) AS sales_count,
                  SUM(oi.subtotal) AS revenue
                FROM products p
                JOIN order_items oi ON p.id = oi.product_id
                JOIN orders o ON oi.order_id = o.id
                WHERE o.status = 'PAID' AND o.paid_at >= ?
                GROUP BY p.id, p.name
                ORDER BY sales_count DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new ProductSalesDTO(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getInt("sales_count"),
                        rs.getLong("revenue")
                ),
                startDate, limit
        );
    }

    // DTO 클래스
    public record ProductSalesDTO(
            Long id,
            String name,
            int salesCount,
            Long revenue
    ) {}
}
```

---

### 2.2 주문 Repository 구현 (예시)

```java
// infrastructure/repositories/JdbcOrderRepository.java
package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcOrderRepository implements OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<Order> findById(Long id) {
        // 1. 주문 정보 조회
        List<Order> orders = jdbcTemplate.query(
                "SELECT * FROM orders WHERE id = ?",
                (rs, rowNum) -> Order.builder()
                        .id(rs.getLong("id"))
                        .userId(rs.getLong("user_id"))
                        .totalAmount(rs.getLong("total_amount"))
                        .discountAmount(rs.getLong("discount_amount"))
                        .finalAmount(rs.getLong("final_amount"))
                        .status(OrderStatus.valueOf(rs.getString("status")))
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                        .build(),
                id
        );

        if (orders.isEmpty()) return Optional.empty();

        Order order = orders.get(0);

        // 2. 주문 항목 조회
        List<OrderItem> items = jdbcTemplate.query(
                "SELECT * FROM order_items WHERE order_id = ?",
                (rs, rowNum) -> OrderItem.builder()
                        .id(rs.getLong("id"))
                        .orderId(rs.getLong("order_id"))
                        .productId(rs.getLong("product_id"))
                        .quantity(rs.getInt("quantity"))
                        .unitPrice(rs.getLong("unit_price"))
                        .subtotal(rs.getLong("subtotal"))
                        .build(),
                id
        );

        order.setItems(items);
        return Optional.of(order);
    }

    @Override
    @Transactional
    public Order save(Order order) {
        if (order.getId() == null) {
            // 1. 주문 생성
            jdbcTemplate.update(
                    """
                    INSERT INTO orders (user_id, total_amount, discount_amount, final_amount, status)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    order.getUserId(),
                    order.getTotalAmount(),
                    order.getDiscountAmount(),
                    order.getFinalAmount(),
                    order.getStatus().name()
            );

            Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            order.setId(orderId);

            // 2. 주문 항목 저장
            for (OrderItem item : order.getItems()) {
                jdbcTemplate.update(
                        """
                        INSERT INTO order_items (order_id, product_id, quantity, unit_price, subtotal)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                        orderId,
                        item.getProductId(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()
                );
            }
        } else {
            // 주문 상태 업데이트
            jdbcTemplate.update(
                    "UPDATE orders SET status = ?, paid_at = ? WHERE id = ?",
                    order.getStatus().name(),
                    order.getPaidAt(),
                    order.getId()
            );
        }

        return order;
    }
}
```

### ✅ 체크포인트

- [ ] Repository가 Domain 인터페이스를 모두 구현했나요?
- [ ] 트랜잭션 처리가 필요한 곳에 `@Transactional`이 적용되었나요?
- [ ] 에러 처리가 적절한가요?

---

## Step 3: 외부 시스템 연동

### 3.1 데이터 플랫폼 전송 구현 (예시)

```java
// infrastructure/external/DataTransmissionService.java
package io.hhplus.ecommerce.infrastructure.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataTransmissionService {

    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final String apiUrl = System.getenv("DATA_PLATFORM_URL") != null
            ? System.getenv("DATA_PLATFORM_URL")
            : "http://localhost:4000";

    public Map<String, Object> send(Map<String, Object> orderData) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-Key", System.getenv("DATA_PLATFORM_API_KEY"));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(orderData, headers);

            // 외부 API POST 요청
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    apiUrl + "/api/orders",
                    request,
                    Map.class
            );

            log.info("데이터 전송 성공: orderId={}", orderData.get("orderId"));
            return response.getBody();

        } catch (Exception e) {
            log.error("데이터 전송 실패: Outbox에 저장됨", e);
            saveToOutbox(orderData);
            throw new RuntimeException("데이터 전송 실패", e);
        }
    }

    public void saveToOutbox(Map<String, Object> orderData) {
        try {
            String payload = objectMapper.writeValueAsString(orderData);

            jdbcTemplate.update(
                    "INSERT INTO data_transmissions (order_id, payload, status) VALUES (?, ?, 'PENDING')",
                    orderData.get("orderId"),
                    payload
            );

            log.info("Outbox에 저장됨: orderId={}", orderData.get("orderId"));
        } catch (Exception e) {
            log.error("Outbox 저장 실패", e);
        }
    }

    public void retryPendingTransmissions() {
        List<Map<String, Object>> pending = jdbcTemplate.queryForList(
                """
                SELECT * FROM data_transmissions
                WHERE status = 'PENDING' AND attempts < 3
                ORDER BY created_at
                LIMIT 10
                """
        );

        for (Map<String, Object> transmission : pending) {
            try {
                String payload = (String) transmission.get("payload");
                Map<String, Object> orderData = objectMapper.readValue(payload, Map.class);

                send(orderData);

                // 성공 시 상태 업데이트
                jdbcTemplate.update(
                        "UPDATE data_transmissions SET status = 'SUCCESS', sent_at = NOW() WHERE id = ?",
                        transmission.get("id")
                );

            } catch (Exception e) {
                // 실패 시 재시도 횟수 증가
                jdbcTemplate.update(
                        "UPDATE data_transmissions SET attempts = attempts + 1 WHERE id = ?",
                        transmission.get("id")
                );

                // 3회 실패 시 FAILED 마킹
                Integer attempts = (Integer) transmission.get("attempts");
                if (attempts != null && attempts >= 2) {
                    jdbcTemplate.update(
                            "UPDATE data_transmissions SET status = 'FAILED' WHERE id = ?",
                            transmission.get("id")
                    );
                }
            }
        }
    }
}
```

---

### 3.2 Mock 외부 서버 (테스트용)

```java
// test/mocks/MockDataPlatformServer.java
package io.hhplus.ecommerce.test.mocks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@SpringBootApplication
@RestController
@RequestMapping("/api/orders")
public class MockDataPlatformServer {

    private final List<Map<String, Object>> receivedOrders = new ArrayList<>();
    private final Random random = new Random();

    @PostMapping
    public Map<String, Object> receiveOrder(@RequestBody Map<String, Object> body) {
        // 간헐적 실패 (20% 확률)
        if (random.nextDouble() < 0.2) {
            throw new RuntimeException("Internal Server Error (Mock Failure)");
        }

        receivedOrders.add(body);

        return Map.of(
                "success", true,
                "id", System.currentTimeMillis()
        );
    }

    @GetMapping
    public List<Map<String, Object>> getOrders() {
        return receivedOrders;
    }

    public static void main(String[] args) {
        SpringApplication.run(MockDataPlatformServer.class, args);
    }
}
```

### ✅ 체크포인트

- [ ] 외부 API 호출이 구현되었나요?
- [ ] 실패 시 재시도 로직이 있나요?
- [ ] Outbox 패턴이 구현되었나요?

---

## Step 4: 트랜잭션 처리

### 4.1 주문 결제 트랜잭션 (예시)

```java
// application/order/PaymentUseCase.java
package io.hhplus.ecommerce.application.order;

import io.hhplus.ecommerce.domain.order.*;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.external.DataTransmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentUseCase {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final DataTransmissionService dataService;

    @Transactional
    public PaymentResponse processPayment(Long orderId, Long userId) {
        // 1. 주문 조회
        Order order = orderRepository.findByIdOrThrow(orderId);

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        // 2. 사용자 잔액 확인 및 차감
        User user = userRepository.findByIdOrThrow(userId);
        user.deductBalance(order.getFinalAmount());
        userRepository.save(user);

        // 3. 재고 차감
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findByIdOrThrow(item.getProductId());
            product.decreaseStock(item.getQuantity());
            productRepository.save(product);
        }

        // 4. 주문 상태 변경
        order.complete();
        orderRepository.save(order);

        // 5. 데이터 플랫폼 전송 (트랜잭션 외부)
        // 실패해도 주문은 완료 상태 유지
        try {
            dataService.send(Map.of(
                    "orderId", orderId,
                    "userId", userId,
                    "totalAmount", order.getTotalAmount(),
                    "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            // Outbox에 저장됨
        }

        return PaymentResponse.from(order, user);
    }
}
```

---

### 4.2 선착순 쿠폰 발급 (예시)

```java
// application/coupon/CouponUseCase.java
package io.hhplus.ecommerce.application.coupon;

import io.hhplus.ecommerce.domain.coupon.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponUseCase {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public IssueCouponResponse issueCoupon(Long userId, Long couponId) {
        // 1. 쿠폰 조회 (락)
        Coupon coupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        // 2. 발급 가능 여부 확인
        if (!coupon.canIssue()) {
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }

        // 3. 중복 발급 체크
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new BusinessException(ErrorCode.ALREADY_ISSUED);
        }

        // 4. 쿠폰 발급
        coupon.issue();
        couponRepository.save(coupon);

        // 5. 사용자 쿠폰 생성
        UserCoupon userCoupon = UserCoupon.create(userId, couponId);
        userCouponRepository.save(userCoupon);

        return IssueCouponResponse.from(userCoupon, coupon.getRemainingQuantity());
    }
}
```

### ✅ 체크포인트

- [ ] 트랜잭션 경계가 올바르게 설정되었나요?
- [ ] 동시성 제어가 구현되었나요? (FOR UPDATE)
- [ ] 롤백 처리가 적절한가요?

---

## Step 5: 통합 테스트

### 5.1 주문 플로우 통합 테스트

```java
// test/integration/OrderFlowIntegrationTest.java
package io.hhplus.ecommerce.integration;

import io.hhplus.ecommerce.application.order.OrderUseCase;
import io.hhplus.ecommerce.application.order.PaymentUseCase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderFlowIntegrationTest {

    @Autowired
    private OrderUseCase orderUseCase;

    @Autowired
    private PaymentUseCase paymentUseCase;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 준비
        userId = createUser("test@example.com", 100000L);
        productId = createProduct("노트북", 50000L, 10);
    }

    @Test
    @Order(1)
    @DisplayName("전체 주문 플로우")
    void 전체_주문_플로우() {
        // 1. 주문 생성
        CreateOrderRequest request = new CreateOrderRequest(
                userId,
                List.of(new OrderItemRequest(productId, 2)),
                null
        );

        OrderResponse order = orderUseCase.createOrder(request);

        assertThat(order.getTotalAmount()).isEqualTo(100000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        // 2. 결제 처리
        PaymentResponse payment = paymentUseCase.processPayment(order.getOrderId(), userId);

        assertThat(payment.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment.getRemainingBalance()).isEqualTo(0L);

        // 3. 재고 확인
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(8);  // 10 - 2
    }

    @Test
    @Order(2)
    @DisplayName("재고 부족 시 롤백")
    void 재고_부족_시_롤백() {
        // Given
        updateProductStock(productId, 1);  // 재고 1개만 남음

        CreateOrderRequest request = new CreateOrderRequest(
                userId,
                List.of(new OrderItemRequest(productId, 2)),  // 2개 주문
                null
        );

        // When & Then
        assertThatThrownBy(() -> orderUseCase.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);

        // 재고 변경 없음 확인
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(1);
    }
}
```

---

### 5.2 동시성 테스트

```java
// test/integration/ConcurrencyIntegrationTest.java
package io.hhplus.ecommerce.integration;

import io.hhplus.ecommerce.application.coupon.CouponUseCase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class ConcurrencyIntegrationTest {

    @Autowired
    private CouponUseCase couponUseCase;

    @Test
    @DisplayName("선착순 쿠폰 동시 발급 (100명 중 10명만 성공)")
    void 선착순_쿠폰_동시_발급() throws InterruptedException {
        // Given
        Long couponId = createCoupon("10% 할인", 10, 10);  // 10개 한정

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            final Long userId = (long) i;
            executorService.submit(() -> {
                try {
                    couponUseCase.issueCoupon(userId, couponId);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(90);

        // DB 확인
        Coupon coupon = couponRepository.findById(couponId).orElseThrow();
        assertThat(coupon.getIssuedQuantity()).isEqualTo(10);
    }
}
```

### ✅ 체크포인트

- [ ] 테스트 데이터베이스가 분리되어 있나요?
- [ ] 전체 플로우가 테스트되나요?
- [ ] 동시성 시나리오가 검증되나요?

---

## 📋 최종 체크리스트

### 필수 과제

- [ ] Week 3의 Repository 인터페이스 구현 완료
- [ ] MySQL 데이터베이스 연동
- [ ] 외부 데이터 플랫폼 연동 (Mock/Outbox)
- [ ] 트랜잭션 처리 구현
- [ ] 통합 테스트 작성
- [ ] 동시성 테스트 통과

### 심화 과제 (선택)

- [ ] 데이터베이스 성능 분석 (EXPLAIN)
- [ ] 인덱스 최적화
- [ ] Outbox 재시도 스케줄러 구현

---

## 💡 Week 3에서 Week 4로

### 발전된 부분

| Week 3 | Week 4 |
|--------|--------|
| Mock Repository | MySQL Repository |
| 메모리 저장 | 데이터베이스 영속성 |
| 단위 테스트 | 통합 테스트 |
| 로컬 환경 | 외부 시스템 연동 |

### Week 5 예고

- 동시성 제어 강화 (재고, 쿠폰)
- 성능 최적화 (쿼리 튜닝, 캐싱)
- 부하 테스트 및 모니터링

---

## 📚 참고 자료

- `.claude/commands/week4-step7.md`: 상세 구현 가이드
- `docs/week4/step7-integration-guide.md`: 환경 설정 가이드
- [Spring JDBC Template](https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#jdbc)
- [Testcontainers](https://testcontainers.com/)
