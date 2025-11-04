# 8. 토론 주제 (Discussion Topics)

## 📌 개요

3주차 학습 과정에서 나올 수 있는 토론 주제를 Q&A 형식으로 정리한 문서입니다.
실제 면접이나 코드 리뷰, 동료와의 토론에서 활용할 수 있습니다.

---

## 🏗️ 레이어드 아키텍처

### Q1. "왜 Repository 인터페이스를 Domain에 두나요?"

**Short Answer:**
Domain이 Infrastructure를 의존하지 않기 위함입니다.

**Detailed Explanation:**

**의존성 역전 원칙 (DIP)**
```
Without DIP (❌):
Domain Layer
    ↓ depends on
Infrastructure Layer (구현체)

With DIP (✅):
Domain Layer (인터페이스 정의)
    ↑ implemented by
Infrastructure Layer (구현체)
```

**구체적 이유:**
1. **Domain의 독립성**: Domain은 비즈니스 로직만 담당, 기술 세부사항 모름
2. **테스트 용이성**: Mock Repository로 Domain 로직 테스트 가능
3. **구현 교체 가능**: In-Memory → JPA → MongoDB로 교체 시 Domain 코드 수정 불필요

**실전 예시:**
```java
// Domain Layer
package io.hhplus.ecommerce.domain.product;

public interface ProductRepository {  // 인터페이스는 Domain에
    Optional<Product> findById(String id);
}

// Infrastructure Layer
package io.hhplus.ecommerce.infrastructure.persistence.product;

public class InMemoryProductRepository implements ProductRepository {  // 구현체는 Infrastructure에
    // ConcurrentHashMap 구현
}

// 나중에 JPA로 교체
public class JpaProductRepository implements ProductRepository {  // Domain 코드 수정 없이 교체
    // JPA 구현
}
```

**토론 포인트:**
- "만약 Repository 인터페이스를 Infrastructure에 두면 어떤 문제가 생기나요?"
- "Domain이 Infrastructure를 의존하면 테스트가 왜 어려워지나요?"

---

### Q2. "Controller에서 직접 Repository를 호출하면 안 되는 이유는?"

**Short Answer:**
계층의 책임이 혼재되고, 비즈니스 로직이 흩어지기 때문입니다.

**Detailed Explanation:**

**잘못된 설계 (❌):**
```java
@RestController
@RequiredArgsConstructor
public class OrderController {
    private final ProductRepository productRepository;  // ❌ Controller가 Repository 직접 의존
    private final OrderRepository orderRepository;

    @PostMapping("/orders")
    public ApiResponse<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        // Controller에 비즈니스 로직 작성 (❌)
        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getStock() < request.getQuantity()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }

        product.setStock(product.getStock() - request.getQuantity());

        Order order = new Order(...);
        orderRepository.save(order);

        return ApiResponse.success(OrderResponse.from(order));
    }
}
```

**문제점:**
1. **책임 혼재**: Controller가 HTTP + 비즈니스 로직 담당
2. **재사용 불가**: 다른 곳에서 같은 로직 필요 시 복사/붙여넣기
3. **테스트 어려움**: HTTP 테스트와 비즈니스 로직 테스트가 섞임
4. **유지보수 어려움**: 비즈니스 로직 변경 시 Controller 수정

**올바른 설계 (✅):**
```java
@RestController
@RequiredArgsConstructor
public class OrderController {
    private final OrderUseCase orderUseCase;  // ✅ UseCase만 의존

    @PostMapping("/orders")
    public ApiResponse<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        return ApiResponse.success(orderUseCase.createOrder(request));  // 단순 위임
    }
}

@Service
@RequiredArgsConstructor
public class OrderUseCase {
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public OrderResponse createOrder(CreateOrderRequest request) {
        // 비즈니스 로직은 UseCase와 Entity에 위치
        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        product.decreaseStock(request.getQuantity());  // Entity 메서드 호출

        Order order = Order.create(request);
        orderRepository.save(order);

        return OrderResponse.from(order);
    }
}
```

**토론 포인트:**
- "Controller의 책임은 무엇인가요?"
- "비즈니스 로직이 여러 Controller에 중복되면 어떻게 하나요?"

---

### Q3. "UseCase에서 다른 UseCase를 호출하면 안 되는 이유는?"

**Short Answer:**
순환 참조 위험과 책임 혼재 문제 때문입니다.

**Detailed Explanation:**

**잘못된 설계 (❌):**
```java
@Service
@RequiredArgsConstructor
public class OrderUseCase {
    private final ProductUseCase productUseCase;  // ❌ UseCase가 다른 UseCase 의존
    private final PaymentUseCase paymentUseCase;

    public OrderResponse createOrder(CreateOrderRequest request) {
        // UseCase를 직접 호출
        ProductResponse product = productUseCase.getProduct(request.getProductId());  // ❌
        PaymentResponse payment = paymentUseCase.processPayment(...);  // ❌

        // ...
    }
}

@Service
@RequiredArgsConstructor
public class PaymentUseCase {
    private final OrderUseCase orderUseCase;  // ❌ 순환 참조 발생 가능

    public PaymentResponse processPayment(...) {
        // OrderUseCase 호출...
    }
}
```

**문제점:**
1. **순환 참조**: A → B → A 의존 구조 발생 가능
2. **책임 혼재**: UseCase의 경계가 모호해짐
3. **트랜잭션 복잡도**: 중첩된 트랜잭션 관리 어려움
4. **테스트 어려움**: Mock 체인이 길어짐

**올바른 설계 (✅):**
```java
// DomainService 활용
@Service
public class OrderService {  // Domain Layer
    public void validateOrder(Order order, Product product) {
        // 도메인 규칙 검증
    }
}

@Service
@RequiredArgsConstructor
public class OrderUseCase {  // Application Layer
    private final ProductRepository productRepository;  // Repository 직접 사용
    private final OrderService orderService;  // DomainService 사용

    public OrderResponse createOrder(CreateOrderRequest request) {
        // Repository를 직접 호출
        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(...);

        Order order = Order.create(request);

        // DomainService 호출
        orderService.validateOrder(order, product);

        // ...
    }
}
```

**토론 포인트:**
- "UseCase와 DomainService의 차이는 무엇인가요?"
- "여러 도메인을 조합해야 할 때는 어떻게 하나요?"

---

## 🎯 UseCase 패턴

### Q4. "단순 조회 API도 UseCase로 구현해야 하나요?"

**Short Answer:**
네, UseCase로 구현하되 단순한 경우 복잡하게 만들 필요는 없습니다.

**Detailed Explanation:**

**Case 1: 단순 조회**
```java
// 단순 조회도 UseCase로 일관성 유지
@Service
@RequiredArgsConstructor
public class ProductUseCase {
    private final ProductRepository productRepository;

    public ProductResponse getProduct(String productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        return ProductResponse.from(product);  // DTO 변환
    }
}
```

**Case 2: 복잡한 조회 (진정한 UseCase)**
```java
@Service
@RequiredArgsConstructor
public class ProductDetailUseCase {
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final StockRepository stockRepository;

    /**
     * 고객이 구매 결정을 내리는데 필요한 모든 정보 제공
     * - 상품 정보
     * - 재고 수량
     * - 리뷰 통계
     * - 추천 상품
     */
    public ProductDetailResponse getProductDetail(String productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        Integer stock = stockRepository.getAvailableStock(productId);
        ReviewStats stats = reviewRepository.getStatsByProduct(productId);
        List<Product> recommendations = productRepository.findRecommendations(productId);

        return ProductDetailResponse.of(product, stock, stats, recommendations);
    }
}
```

**핵심:**
- 단순 조회도 UseCase로 일관성 유지
- 하지만 불필요하게 복잡하게 만들지 않기
- 미래 확장성 고려 (나중에 복잡해질 수 있음)

**토론 포인트:**
- "모든 API를 UseCase로 만들면 코드가 너무 많아지지 않나요?"
- "단순 CRUD는 Service로 해도 되지 않나요?"

---

### Q5. "UseCase와 Service의 차이는 무엇인가요?"

**Short Answer:**
UseCase는 Application Layer의 워크플로우 조율자, Service는 Domain Layer의 비즈니스 로직 담당자입니다.

**Detailed Explanation:**

**비교표:**

| 항목 | UseCase (Application) | DomainService (Domain) |
|------|----------------------|------------------------|
| 위치 | Application Layer | Domain Layer |
| 역할 | 워크플로우 조율 | 도메인 로직 |
| 의존성 | Repository, DomainService | Entity, Value Object |
| 트랜잭션 | 관리함 | 관리 안 함 |
| DTO | 사용함 | 사용 안 함 (Entity만) |

**실전 예시:**
```java
// DomainService (Domain Layer)
@Service
public class OrderService {
    /**
     * 여러 Entity를 조합한 도메인 로직
     * 외부 의존성 없음 (순수 비즈니스 로직)
     */
    public long calculateTotalAmount(List<Product> products, List<OrderItem> items) {
        return items.stream()
            .mapToLong(item -> {
                Product product = findProduct(products, item.getProductId());
                return product.getPrice() * item.getQuantity();
            })
            .sum();
    }

    public void validateStock(List<Product> products, List<OrderItem> items) {
        for (OrderItem item : items) {
            Product product = findProduct(products, item.getProductId());
            if (!product.hasStock(item.getQuantity())) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
            }
        }
    }
}

// UseCase (Application Layer)
@Service
@RequiredArgsConstructor
@Transactional  // 트랜잭션 관리
public class OrderUseCase {
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;  // DomainService 사용

    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1. 데이터 조회 (Repository)
        List<Product> products = productRepository.findByIds(
            request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .toList()
        );

        Order order = Order.create(request.getUserId(), request.getItems());

        // 2. 도메인 로직 (DomainService)
        orderService.validateStock(products, order.getItems());
        long totalAmount = orderService.calculateTotalAmount(products, order.getItems());
        order.setTotalAmount(totalAmount);

        // 3. 재고 차감 (Entity)
        products.forEach(product ->
            product.decreaseStock(getQuantity(order.getItems(), product.getId()))
        );

        // 4. 저장 (Repository)
        Order savedOrder = orderRepository.save(order);

        // 5. DTO 변환
        return OrderResponse.from(savedOrder);
    }
}
```

**토론 포인트:**
- "DomainService 없이 UseCase만으로 구현하면 안 되나요?"
- "언제 DomainService를 만들어야 하나요?"

---

## 🎨 Domain Modeling

### Q6. "재고 차감 로직을 어디에 구현했나요? 그 이유는?"

**Short Answer:**
Product Entity에 구현했습니다. Entity가 자신의 상태를 관리하는 것이 객체지향 원칙에 맞기 때문입니다.

**Detailed Explanation:**

**잘못된 배치 (❌ Anemic):**
```java
// Entity는 데이터만
public class Product {
    private Integer stock;
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
}

// Service에 모든 로직
@Service
public class ProductService {
    public void decreaseStock(Product product, int quantity) {
        // 검증 로직
        if (product.getStock() < quantity) {
            throw new BusinessException("재고 부족");
        }
        // 상태 변경
        product.setStock(product.getStock() - quantity);
    }
}
```

**올바른 배치 (✅ Rich):**
```java
// Entity에 비즈니스 로직
@Getter
@AllArgsConstructor
public class Product {
    private String id;
    private Integer stock;

    /**
     * 재고 차감: Entity가 스스로 행동
     */
    public void decreaseStock(int quantity) {
        validateQuantity(quantity);
        validateStock(quantity);
        this.stock -= quantity;
    }

    public void restoreStock(int quantity) {
        validateQuantity(quantity);
        this.stock += quantity;
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        }
    }

    private void validateStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
    }
}
```

**Entity에 두는 이유:**
1. **캡슐화**: 재고는 Product의 상태 → Product가 관리
2. **응집도**: 관련 로직이 한 곳에 모임
3. **재사용성**: 어디서든 `product.decreaseStock()` 호출 가능
4. **테스트 용이성**: Entity 메서드만 단독 테스트

**테스트:**
```java
@Test
void 재고_차감_테스트() {
    Product product = new Product("P001", 10);
    product.decreaseStock(3);
    assertThat(product.getStock()).isEqualTo(7);
}
```

**토론 포인트:**
- "Entity가 아니라 Service에 두면 안 되나요?"
- "setter를 쓰면 더 간단하지 않나요?"

---

### Q7. "할인 계산 로직은 어느 계층에 있나요?"

**Short Answer:**
할인 대상에 따라 다릅니다. 단일 Entity면 Entity, 여러 Entity 조합이면 DomainService입니다.

**Detailed Explanation:**

**Case 1: 쿠폰 할인 (Entity)**
```java
@Getter
public class Coupon {
    private String id;
    private Integer discountRate;  // 10%

    /**
     * 단일 Entity의 로직 → Entity 메서드
     */
    public long calculateDiscount(long originalPrice) {
        return originalPrice * discountRate / 100;
    }
}

// 사용
long discount = coupon.calculateDiscount(10000);  // 1000원
```

**Case 2: 복합 할인 (DomainService)**
```java
// 여러 Entity를 조합 → DomainService
@Service
public class DiscountService {
    /**
     * 쿠폰 할인 + 회원 등급 할인 + 프로모션 할인
     */
    public long calculateTotalDiscount(
        Order order,
        Coupon coupon,
        User user,
        Promotion promotion
    ) {
        long couponDiscount = coupon != null ? coupon.calculateDiscount(order.getTotalAmount()) : 0;
        long memberDiscount = user.getMemberGrade().getDiscountAmount(order.getTotalAmount());
        long promotionDiscount = promotion != null ? promotion.calculateDiscount(order) : 0;

        // 할인 적용 규칙 (최대 할인액 제한 등)
        return Math.min(
            couponDiscount + memberDiscount + promotionDiscount,
            order.getTotalAmount() * 30 / 100  // 최대 30% 할인
        );
    }
}
```

**결정 기준:**
- 단일 Entity 로직 → **Entity 메서드**
- 여러 Entity 조합 → **DomainService**
- 워크플로우 조율 → **UseCase**

**토론 포인트:**
- "할인 계산을 UseCase에 두면 안 되나요?"
- "복잡한 할인 규칙은 어떻게 관리하나요?"

---

## 🗄️ Repository 패턴

### Q8. "Repository와 DAO의 차이는 무엇인가요?"

**Short Answer:**
Repository는 도메인 중심, DAO는 데이터베이스 중심입니다.

**Detailed Explanation:**

**비교표:**

| 항목 | Repository | DAO |
|------|-----------|-----|
| 개념 | 도메인 객체 컬렉션 | 데이터 접근 객체 |
| 관점 | 도메인 중심 | 데이터베이스 중심 |
| 메서드명 | `findById`, `findActiveUsers` | `selectById`, `selectAll` |
| 반환값 | Domain Entity | Data Entity (DTO) |
| 위치 | Interface in Domain | Implementation in Infrastructure |

**Repository (도메인 중심):**
```java
// 인터페이스: Domain Layer
public interface ProductRepository {
    Optional<Product> findById(String id);  // 도메인 용어
    List<Product> findAvailableProducts();  // 비즈니스 의미
    List<Product> findByCategory(String category);
}

// 사용
List<Product> products = productRepository.findAvailableProducts();
products.forEach(product -> product.decreaseStock(1));  // 도메인 객체로 동작
```

**DAO (데이터베이스 중심):**
```java
// DAO: Infrastructure Layer
public interface ProductDao {
    ProductEntity selectById(String id);  // DB 용어
    List<ProductEntity> selectAll();      // 기술 용어
    void insert(ProductEntity entity);
    void update(ProductEntity entity);
}

// 사용
ProductEntity entity = productDao.selectById("P001");
entity.setStock(entity.getStock() - 1);  // setter 사용
productDao.update(entity);
```

**핵심 차이:**
- **Repository**: "컬렉션처럼 사용" (도메인 모델 지원)
- **DAO**: "데이터베이스 테이블 접근" (CRUD 지원)

**토론 포인트:**
- "Repository를 DAO처럼 쓰면 안 되나요?"
- "실무에서는 어떤 걸 쓰나요?"

---

### Q9. "ConcurrentHashMap을 선택한 이유는?"

**Short Answer:**
Thread-safe하면서도 성능이 우수하기 때문입니다.

**Detailed Explanation:**

**컬렉션 비교:**

| 컬렉션 | Thread-Safe | 읽기 성능 | 쓰기 성능 | Week 3 적합 |
|--------|-------------|----------|----------|------------|
| HashMap | ❌ | ⚡⚡⚡ | ⚡⚡⚡ | ❌ Race Condition |
| Hashtable | ✅ | ⚡ | ⚡ | ❌ 너무 느림 |
| synchronizedMap | ✅ | ⚡⚡ | ⚡ | △ 괜찮음 |
| **ConcurrentHashMap** | ✅ | ⚡⚡⚡ | ⚡⚡ | ✅ **최적** |

**ConcurrentHashMap의 장점:**
```java
@Repository
public class InMemoryProductRepository implements ProductRepository {
    // Thread-safe + 고성능
    private final Map<String, Product> storage = new ConcurrentHashMap<>();

    @Override
    public Product save(Product product) {
        storage.put(product.getId(), product);  // 세그먼트 단위 락
        return product;
    }

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(storage.get(id));  // Lock-free 읽기
    }
}
```

**동작 원리:**
1. **세그먼트 분할**: 전체 잠금이 아닌 세그먼트 단위 잠금
2. **Lock-free 읽기**: 읽기 작업은 락 없이 수행
3. **CAS 연산**: Compare-And-Swap으로 원자적 업데이트

**로이코치님 조언:**
> "ConcurrentHashMap을 사용하면 어느 정도 동시성을 보장합니다."

**토론 포인트:**
- "HashMap + synchronized로는 안 되나요?"
- "ConcurrentHashMap도 완벽한 동시성을 보장하나요?"

---

## 🔒 동시성 제어

### Q10. "synchronized와 ReentrantLock의 차이는?"

**Short Answer:**
synchronized는 간단하지만 제어 옵션이 적고, ReentrantLock은 복잡하지만 세밀한 제어가 가능합니다.

**Detailed Explanation:**

**비교표:**

| 항목 | synchronized | ReentrantLock |
|------|-------------|---------------|
| 사용법 | 키워드 | 객체 |
| Lock 획득 | 자동 | 명시적 (lock.lock()) |
| Lock 해제 | 자동 | 명시적 (lock.unlock()) |
| 타임아웃 | 불가능 | 가능 (tryLock(timeout)) |
| 공정성 | 없음 | 선택 가능 (fair/unfair) |
| 조건 변수 | 1개 (wait/notify) | 여러 개 가능 (Condition) |
| 성능 | 비슷 | 비슷 |

**synchronized:**
```java
public class CouponService {
    // 메서드 전체 잠금
    public synchronized UserCoupon issueCoupon(String userId, String couponId) {
        // 자동으로 lock 획득/해제
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_COUPON));

        if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }

        coupon.increaseIssuedQuantity();
        return userCouponRepository.save(new UserCoupon(userId, couponId));
    }  // 메서드 종료 시 자동 unlock
}
```

**ReentrantLock:**
```java
public class CouponService {
    private final ReentrantLock lock = new ReentrantLock();

    public UserCoupon issueCoupon(String userId, String couponId) {
        // 타임아웃 설정 가능
        try {
            if (!lock.tryLock(3, TimeUnit.SECONDS)) {  // 3초 대기
                throw new BusinessException(ErrorCode.LOCK_TIMEOUT);
            }
        } catch (InterruptedException e) {
            throw new BusinessException(ErrorCode.INTERRUPTED);
        }

        try {
            Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_COUPON));

            if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
                throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
            }

            coupon.increaseIssuedQuantity();
            return userCouponRepository.save(new UserCoupon(userId, couponId));
        } finally {
            lock.unlock();  // 명시적 unlock (반드시 finally에서)
        }
    }
}
```

**선택 기준:**
- **synchronized**: 간단한 동시성 제어, 전체 메서드 잠금 OK
- **ReentrantLock**: 타임아웃 필요, 공정성 필요, 조건 변수 필요

**토론 포인트:**
- "어떤 상황에서 ReentrantLock을 선택하나요?"
- "tryLock()은 언제 사용하나요?"

---

### Q11. "AtomicInteger가 ConcurrentHashMap보다 빠른 이유는?"

**Short Answer:**
Lock을 전혀 사용하지 않고 CAS (Compare-And-Swap) 연산으로 동작하기 때문입니다.

**Detailed Explanation:**

**Lock 기반 (느림):**
```
Thread A: Lock 획득 → 작업 → Lock 해제
Thread B: Lock 대기 → Lock 획득 → 작업 → Lock 해제
Thread C: Lock 대기 → Lock 대기 → Lock 획득 → 작업 → Lock 해제
```

**Lock-free (빠름):**
```
Thread A: CAS 시도 → 성공 → 완료
Thread B: CAS 시도 → 실패 → 재시도 → 성공 → 완료
Thread C: CAS 시도 → 성공 → 완료
```

**AtomicInteger 구현:**
```java
public class Coupon {
    private AtomicInteger issuedQuantity = new AtomicInteger(0);

    public boolean tryIssue() {
        while (true) {
            int current = issuedQuantity.get();  // 현재 값 읽기

            if (current >= totalQuantity) {
                return false;  // 수량 초과
            }

            // CAS: "current 값이 그대로면 current+1로 변경"
            if (issuedQuantity.compareAndSet(current, current + 1)) {
                return true;  // 성공
            }
            // 실패 시 while loop로 재시도
        }
    }
}
```

**CAS (Compare-And-Swap) 동작:**
```java
// AtomicInteger.compareAndSet 의사 코드
public boolean compareAndSet(int expect, int update) {
    // 원자적으로 실행 (CPU 명령어 수준)
    if (this.value == expect) {
        this.value = update;
        return true;  // 성공
    } else {
        return false;  // 실패 (다른 스레드가 변경함)
    }
}
```

**성능 비교:**
- **synchronized**: Lock 획득/해제 오버헤드
- **ConcurrentHashMap**: 세그먼트 단위 락
- **AtomicInteger**: Lock 없음, CAS 연산만

**단점:**
- 복잡한 로직에는 부적합 (단순 증감만 가능)
- while loop 재시도로 CPU 사용량 증가 가능

**토론 포인트:**
- "CAS가 항상 빠른가요?"
- "복잡한 비즈니스 로직에도 AtomicInteger를 쓸 수 있나요?"

---

### Q12. "BlockingQueue 방식의 장단점은?"

**Short Answer:**
순차 처리로 안전하지만, 비동기 처리로 즉시 응답이 불가능합니다.

**Detailed Explanation:**

**장점:**
1. **동시성 문제 원천 차단**: 순차 처리로 Race Condition 없음
2. **간단한 구현**: 복잡한 Lock 로직 불필요
3. **부하 조절**: 큐 크기로 부하 제어

**단점:**
1. **즉시 응답 불가**: 비동기 처리로 결과를 바로 못 받음
2. **실패 처리 복잡**: 큐 처리 중 실패 시 사용자에게 알림 어려움
3. **순서 보장 필요**: 큐 순서가 곧 처리 순서

**구현 예시:**
```java
@Service
public class CouponService {
    private final BlockingQueue<CouponIssueRequest> queue = new LinkedBlockingQueue<>(1000);

    @PostConstruct
    public void init() {
        // 별도 스레드에서 큐 처리
        new Thread(() -> {
            while (true) {
                try {
                    CouponIssueRequest request = queue.take();  // 큐에서 꺼내기 (blocking)
                    processIssueCoupon(request);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    // 클라이언트 호출
    public void issueCoupon(String userId, String couponId) {
        queue.offer(new CouponIssueRequest(userId, couponId));  // 큐에 추가
        // 즉시 리턴 (비동기)
    }

    // 실제 처리 (순차적)
    private void processIssueCoupon(CouponIssueRequest request) {
        // 순차 처리로 Race Condition 없음
        Coupon coupon = couponRepository.findById(request.getCouponId())
            .orElseThrow(...);

        if (coupon.getIssuedQuantity() < coupon.getTotalQuantity()) {
            coupon.increaseIssuedQuantity();
            userCouponRepository.save(new UserCoupon(request.getUserId(), request.getCouponId()));
        }
    }
}
```

**사용 시나리오:**
- ✅ 비동기 처리 허용
- ✅ 높은 안정성 필요
- ❌ 즉시 응답 필요
- ❌ 실시간 피드백 필요

**개선 방안:**
```java
// WebSocket/SSE로 비동기 결과 전달
@Service
public class CouponService {
    private final SseEmitters sseEmitters;

    private void processIssueCoupon(CouponIssueRequest request) {
        try {
            // 쿠폰 발급 처리
            // ...

            // 성공 시 SSE로 클라이언트에 알림
            sseEmitters.send(request.getUserId(), "쿠폰 발급 성공");
        } catch (BusinessException e) {
            sseEmitters.send(request.getUserId(), "쿠폰 발급 실패: " + e.getMessage());
        }
    }
}
```

**토론 포인트:**
- "큐가 가득 찰 경우 어떻게 처리하나요?"
- "비동기 처리 결과를 어떻게 사용자에게 전달하나요?"

---

## 🧪 Testing

### Q13. "Domain Layer 테스트에서 Mock이 필요한가요?"

**Short Answer:**
아니요, Domain Layer는 순수한 비즈니스 로직이므로 Mock이 필요 없습니다.

**Detailed Explanation:**

**Domain Layer 특징:**
- 외부 의존성 없음 (Repository, External API 등)
- 순수 비즈니스 로직만 포함
- Entity 메서드는 self-contained

**Mock 불필요 (✅):**
```java
class ProductTest {

    @Test
    void 재고_차감_성공() {
        // Given: 순수 객체 생성 (Mock 불필요)
        Product product = new Product("P001", "노트북", 10, 890000L);

        // When: Entity 메서드 호출
        product.decreaseStock(3);

        // Then: 결과 검증
        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    void 재고_부족시_예외_발생() {
        Product product = new Product("P001", "노트북", 5, 890000L);

        assertThatThrownBy(() -> product.decreaseStock(10))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);
    }
}
```

**Mock 필요 (Application Layer):**
```java
@ExtendWith(MockitoExtension.class)
class ProductUseCaseTest {

    @Mock  // Repository는 Mock 필요
    private ProductRepository productRepository;

    @InjectMocks
    private ProductUseCase productUseCase;

    @Test
    void 상품_조회_성공() {
        // Given
        String productId = "P001";
        Product product = new Product(productId, "노트북", 10, 890000L);
        when(productRepository.findById(productId))
            .thenReturn(Optional.of(product));

        // When
        ProductResponse response = productUseCase.getProduct(productId);

        // Then
        assertThat(response.getProductId()).isEqualTo(productId);
        verify(productRepository).findById(productId);
    }
}
```

**핵심:**
- **Domain Layer**: Mock 불필요 (순수 로직)
- **Application Layer**: Mock 필요 (Repository 의존)
- **Integration Test**: Mock 불필요 (실제 객체 사용)

**토론 포인트:**
- "Domain Layer 테스트의 장점은 무엇인가요?"
- "Entity에 외부 의존성이 있으면 어떻게 테스트하나요?"

---

### Q14. "통합 테스트와 단위 테스트의 비율은 어떻게 가져갔나요?"

**Short Answer:**
단위 테스트 70%, 통합 테스트 30% 정도로 가져갔습니다.

**Detailed Explanation:**

**Testing Pyramid:**
```
        /\
       /  \  E2E (5%)
      /    \
     /------\ Integration (25%)
    /        \
   /----------\ Unit (70%)
  /______________\
```

**Week 3 권장 비율:**
```
Domain + Application 단위 테스트: 70%
  ├─ Domain Layer (Entity 메서드): 40%
  └─ Application Layer (UseCase): 30%

Integration Test: 30%
  ├─ 핵심 플로우 통합 테스트: 20%
  └─ 동시성 테스트: 10%
```

**실전 예시:**
```java
// Unit Test (70%)
class ProductTest { ... }                 // Domain
class OrderTest { ... }                   // Domain
class CouponTest { ... }                  // Domain
class ProductUseCaseTest { ... }          // Application (Mock)
class OrderUseCaseTest { ... }            // Application (Mock)

// Integration Test (30%)
class OrderIntegrationTest { ... }        // 주문 플로우 전체
class CouponConcurrencyTest { ... }       // 동시성 테스트
```

**로이코치님 조언:**
> "Entity + Service 테스트만으로 80-90%는 커버할 것입니다."

**비율 결정 요인:**
1. **프로젝트 복잡도**: 복잡할수록 단위 테스트 비중 증가
2. **팀 규모**: 작은 팀은 통합 테스트 비중 감소
3. **변경 빈도**: 자주 변경되는 코드는 단위 테스트 필수

**토론 포인트:**
- "통합 테스트만 작성하면 안 되나요?"
- "E2E 테스트는 왜 5%만 하나요?"

---

## 📚 추가 학습 자료

### 면접 준비 질문
1. "레이어드 아키텍처의 장단점은?"
2. "Repository 패턴을 사용하는 이유는?"
3. "동시성 제어를 하지 않으면 어떤 문제가 생기나요?"
4. "테스트 커버리지 100%를 달성해야 하나요?"

### 실전 시나리오
1. "재고 차감과 포인트 차감을 동시에 해야 한다면?"
2. "주문 생성 중 재고가 부족해지면 어떻게 처리하나요?"
3. "쿠폰 발급 중 예외가 발생하면?"
4. "데이터베이스를 In-Memory에서 JPA로 바꾸려면?"

---

**이전 학습**: [07. DTO 설계 전략](./07-dto-design.md)
**처음으로**: [README](./README.md)
