# Coach Park Jisu Feedback (Week 4)

> Target: Week 3-4 Assignment (Layered Architecture & Database Integration)

---

## ✅ What Went Well

### 1. Domain Model Pattern Implementation
**Evaluation:**
- Implemented business logic within domain entities (Order class) to increase cohesion
- Validation methods like `validateAmounts` and `validateStatusForComplete` are well implemented

**Significance:**
- ✅ Successfully implemented Rich Domain Model
- ✅ Avoided Anemic Domain Model
- ✅ Proper placement of business logic

---

### 2. High Test Coverage
**Metrics:**
- 94% instruction coverage
- 89% branch coverage
- Total of 230 tests written

**Coach's Advice:**
> Consider not just the quantity of tests, but also the quality. Focus on meaningful assertions, edge case coverage, and thorough business rule validation.

**Considerations:**
- ✅ Quantitative goal achieved (70% → 94%)
- ⚠️ Qualitative improvement needed
  - Meaningful assertions
  - Edge case coverage
  - Business rule validation thoroughness

---

## 🔧 Areas for Improvement

### 1. Remove Duplicate Dependencies

#### Issue: Duplicate spring-boot-starter
**Location:** `build.gradle:29-30`

```gradle
// ❌ Current (Duplicate)
implementation 'org.springframework.boot:spring-boot-starter'
implementation 'org.springframework.boot:spring-boot-starter-web'
```

**Reason:**
- `spring-boot-starter-web` already includes `spring-boot-starter`
- Spring Boot Starters pull in dependencies together

**Solution:**
```gradle
// ✅ Improved
implementation 'org.springframework.boot:spring-boot-starter-web'  // Remove standalone starter
```

**Related Code:**
- File: `build.gradle`
- Lines: 29-30

---

#### Issue: Duplicate springdoc-openapi
**Location:** `build.gradle:32, 37`

```gradle
// ❌ Current (Duplicate)
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0'  // Line 32
// ... (other dependencies)
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0'  // Line 37 (duplicate)
```

**Solution:**
```gradle
// ✅ Improved (Remove duplicate)
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0'  // Only once
```

**Coach's Advice:**
> Duplicate libraries in build.gradle make code harder to read and maintain.

**Related Code:**
- File: `build.gradle`
- Lines: 32, 37

---

### 2. Remove Unnecessary Comments

**Coach's Advice:**
> Comments should only explain intent or help understand complex logic. Avoid comments that merely repeat what the code already says.

**Principles:**
- ❌ Don't repeat what's obvious from code
- ✅ Explain intent of complex logic
- ✅ Explain business rule rationale
- ✅ Use TODO, FIXME for action items

**Examples:**
```java
// ❌ Bad comment (duplicates code)
// Find user
User user = userRepository.findById(userId).orElseThrow();

// ✅ Good comment (explains intent)
// First-come-first-served coupons have 1-per-user limit (business rule)
if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
    throw new BusinessException(ErrorCode.ALREADY_ISSUED);
}
```

---

### 3. Consistent Use of .toList()

**Praise:**
**Location:** `CouponService:102`

```java
// ✅ Good example
return coupons.stream()
    .map(UserCouponResponse::from)
    .toList();  // Java 16+ (Stream.toList())
```

**Coach's Advice:**
> Great use of `.toList()`. Apply this pattern consistently throughout the codebase.

**How to Apply:**
```java
// ❌ Before
.collect(Collectors.toList())

// ✅ After
.toList()  // More concise and clear
```

**Advantages:**
- Code brevity
- Java 16+ standard API
- Returns immutable list (by default)

**Note:**
- Only available in Java 16+
- Project uses Java 17 → No issue

---

### 4. Refactor Repeated Patterns ⭐ (Most Important)

#### Issue: Repeated Optional.findById().orElseThrow() Pattern

**Repeated in:**
- CouponService
- CartService
- PointService
- Other Services

**Current Code (Repetitive):**
```java
// CouponService
Coupon coupon = couponRepository.findById(couponId)
    .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

// CartService
Cart cart = cartRepository.findById(cartId)
    .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

// PointService (User)
User user = userRepository.findById(userId)
    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
```

**Coach's Advice:**
> Extract repeated code into a common method. Implement in Repository layer and reuse to reduce code duplication.

#### Solutions

##### Option 1: Add Custom Method to Repository (Recommended)

```java
// Domain Repository Interface
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    // Add custom method
    default Coupon findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.COUPON_NOT_FOUND,
                "Coupon not found. couponId: " + id
            ));
    }
}
```

**Usage:**
```java
// ✅ After improvement (concise!)
Coupon coupon = couponRepository.findByIdOrThrow(couponId);
```

---

##### Option 2: Common Utility Method

```java
// Common Utility Class
public class RepositoryUtils {

    public static <T, ID> T findByIdOrThrow(
        JpaRepository<T, ID> repository,
        ID id,
        ErrorCode errorCode
    ) {
        return repository.findById(id)
            .orElseThrow(() -> new BusinessException(errorCode));
    }
}
```

**Usage:**
```java
// ✅ After improvement
Coupon coupon = RepositoryUtils.findByIdOrThrow(
    couponRepository,
    couponId,
    ErrorCode.COUPON_NOT_FOUND
);
```

---

##### Option 3: Base Repository Interface (Advanced)

```java
// Base Repository Interface
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {

    default T findByIdOrThrow(ID id, ErrorCode errorCode) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(errorCode));
    }
}

// Each Repository extends
public interface CouponRepository extends BaseRepository<Coupon, Long> {
    // Only add custom methods
}
```

**Usage:**
```java
// ✅ After improvement
Coupon coupon = couponRepository.findByIdOrThrow(couponId, ErrorCode.COUPON_NOT_FOUND);
```

---

#### Recommended: Option 1 (Repository Custom Method)

**Reasons:**
1. ✅ Embeds appropriate ErrorCode per Repository
2. ✅ Most concise usage
3. ✅ Type safety
4. ✅ IDE autocomplete support

**Apply to:**
- [ ] CouponRepository
- [ ] CartRepository
- [ ] UserRepository (PointService)
- [ ] ProductRepository
- [ ] OrderRepository

---

### 5. Input Validation Strategy

**Coach's Advice:**
> Separate input validation into two categories: business logic validation and data format validation.

#### Validation Layer Separation

```
Input Validation Flow:
Controller (Format) → UseCase (Business) → Entity (Domain Rules)

1. Controller: @Valid, @NotNull, @Min, @Max, etc.
2. UseCase: Existence, Authorization, State validation
3. Entity: Domain rules (insufficient stock, quantity limits, etc.)
```

**Example:**
```java
// 1. Controller - Format validation
@PostMapping("/orders")
public ApiResponse<OrderResponse> createOrder(
    @Valid @RequestBody CreateOrderRequest request  // @Valid
) {
    return ApiResponse.success(orderUseCase.createOrder(request));
}

// Request DTO
public class CreateOrderRequest {
    @NotBlank(message = "User ID is required")
    private String userId;

    @NotEmpty(message = "At least one order item is required")
    @Size(min = 1, max = 10, message = "Maximum 10 items allowed")
    private List<OrderItemRequest> items;

    @Min(value = 0, message = "Coupon ID must be non-negative")
    private Long couponId;
}

// 2. UseCase - Business validation
public OrderResponse createOrder(CreateOrderRequest request) {
    // User existence
    User user = userRepository.findByIdOrThrow(request.getUserId());

    // Coupon validity (optional)
    if (request.getCouponId() != null) {
        validateCoupon(request.getUserId(), request.getCouponId());
    }

    // ...
}

// 3. Entity - Domain rules
public class Product {
    public void decreaseStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
        this.stock -= quantity;
    }
}
```

---

### 6. Concurrency Control Comparison (ReentrantLock vs CAS)

**Coach's Advice:**
> Besides synchronized, ReentrantLock might be better in some cases. Compare the advantages and disadvantages of each approach.

#### Comparison Table

| Method | Advantages | Disadvantages | When to Use |
|--------|-----------|---------------|-------------|
| **synchronized** | Simple implementation | Locks entire method, no fairness guarantee | Simple logic |
| **ReentrantLock** | Fairness guarantee possible, condition variables available | Complex implementation, finally required | Fine-grained control needed |
| **CAS (AtomicInteger)** | Lock-free, no lock overhead, efficient with low contention | Only for simple increment/decrement | Counters, quantity management |

#### ReentrantLock Advantages

**1. Fairness Guarantee**
```java
// Fair Lock (FIFO order guarantee)
private final ReentrantLock lock = new ReentrantLock(true);  // fair = true

public UserCoupon issueCoupon(String userId, String couponId) {
    lock.lock();
    try {
        // First waiting thread acquires first (fairness)
    } finally {
        lock.unlock();
    }
}
```

**2. Condition Variables**
```java
private final ReentrantLock lock = new ReentrantLock();
private final Condition condition = lock.newCondition();

public void waitForCouponAvailable() throws InterruptedException {
    lock.lock();
    try {
        while (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
            condition.await();  // Wait
        }
        // Issue coupon
    } finally {
        lock.unlock();
    }
}

public void notifyCouponAvailable() {
    lock.lock();
    try {
        condition.signalAll();  // Wake up waiting threads
    } finally {
        lock.unlock();
    }
}
```

**3. tryLock (Timeout)**
```java
if (lock.tryLock(1, TimeUnit.SECONDS)) {
    try {
        // Lock acquired successfully
    } finally {
        lock.unlock();
    }
} else {
    // Lock acquisition failed (timeout)
    throw new BusinessException(ErrorCode.COUPON_BUSY);
}
```

#### CAS (AtomicInteger) Advantages

**1. Lock-free (No lock overhead)**
- No thread blocking
- No context switching cost

**2. Efficient with low contention**
```java
public boolean tryIssue() {
    while (true) {
        int current = issuedQuantity.get();

        if (current >= totalQuantity) {
            return false;
        }

        if (issuedQuantity.compareAndSet(current, current + 1)) {
            return true;  // Success
        }
        // Retry on failure (fast with low contention)
    }
}
```

#### Best for Current Project

**Week 3 Assignment: CAS (AtomicInteger) Recommended**
- ✅ First-come-first-served coupons are simple counter increments
- ✅ Lock-free, fastest performance
- ✅ Fairness not mandatory (first-come-first-served)

**For Production Expansion: Consider ReentrantLock**
- Complex logic (multiple validation steps)
- Fairness needed (FIFO guarantee)
- Condition variables needed

---

### 7. Test Isolation Strategy

**Coach's Advice:**
> There are various test isolation methods. Consider creating a superclass for test configuration or using annotations to intervene at initialization time.

**Reference:**
- https://toss.tech/article/test-strategy-server

#### Method 1: Superclass Pattern

```java
@SpringBootTest
@Transactional
public abstract class IntegrationTestSupport {

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected UserRepository userRepository;

    @BeforeEach
    void setUpCommon() {
        // Common test data
        initTestData();
    }

    protected void initTestData() {
        productRepository.save(Product.create("Laptop", 890000L, 10, "Electronics"));
        userRepository.save(User.create("TestUser", 1000000L));
    }
}

// Usage
class OrderIntegrationTest extends IntegrationTestSupport {

    @Test
    void createOrder_success() {
        // Common data automatically loaded
    }
}
```

#### Method 2: Custom Annotation

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public @interface IntegrationTest {
}

// Usage
@IntegrationTest
class OrderIntegrationTest {
    // ...
}
```

#### Method 3: TestContainers (Advanced)

```java
@Testcontainers
@SpringBootTest
public abstract class ContainerTestSupport {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
}
```

---

### 8. Repository Pattern Implementation (Praise)

**Coach's Advice:**
> Since there's a 100% chance of DB expansion in the future, separating Interface and Implementation is a rational choice. Using ConcurrentHashMap is also appropriate.

**Significance:**
- ✅ Design considering extensibility
- ✅ Easy transition from In-Memory → Database
- ✅ Thread-safety consideration (ConcurrentHashMap)

**Week 3 → Week 4 Transition:**
```
Week 3:
InMemoryProductRepository (ConcurrentHashMap)

Week 4:
JpaProductRepository (H2/MySQL)
```

---

## 📋 Action Items (Priority)

### High Priority (Apply Immediately)
- [ ] Remove duplicate dependencies in build.gradle
  - [ ] Remove spring-boot-starter
  - [ ] Remove springdoc-openapi duplicate
- [ ] Refactor Optional.findById().orElseThrow() pattern
  - [ ] Add Repository custom method (findByIdOrThrow)
  - [ ] Apply to all Services

### Medium Priority (Apply During Week 4)
- [ ] Remove unnecessary comments (during code review)
- [ ] Maintain .toList() consistency (collect(Collectors.toList()) → toList())
- [ ] Clarify validation layer separation

### Low Priority (Learning & Improvement)
- [ ] Study ReentrantLock vs CAS comparison
- [ ] Study test isolation strategy (Toss article)
- [ ] Consider qualitative test improvements

---

## 📚 References

1. **Toss Test Strategy:** https://toss.tech/article/test-strategy-server
2. **Spring Boot Starter Dependencies:** [Spring Boot Reference Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.build-systems.starters)
3. **Java Concurrency:** [Java Concurrency in Practice](https://jcip.net/)
4. **TestContainers:** https://www.testcontainers.org/

---

## 📝 Notes

- Overall high-quality implementation ✨
- 94% test coverage achieved 🎉
- Domain model pattern well implemented
- Most improvements relate to code quality and consistency
- Recommend gradual improvements during Week 4 progress
