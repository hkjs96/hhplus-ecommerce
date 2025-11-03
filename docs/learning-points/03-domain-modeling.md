# 3. 도메인 모델링 (Domain Modeling)

## 📌 핵심 개념

**Rich Domain Model**: Entity가 데이터뿐만 아니라 비즈니스 로직(행위)을 함께 가지는 모델

---

## 🎭 Rich Domain Model vs Anemic Domain Model

### Anemic Domain Model (빈혈 모델) ❌

**특징**: getter/setter만 있고 비즈니스 로직이 없는 Entity

```java
// Anemic Entity (나쁨)
public class Product {
    private String id;
    private String name;
    private Integer stock;
    private Long price;

    // getter/setter만 존재
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
}

// Service에 비즈니스 로직 집중
@Service
public class ProductService {
    public void decreaseStock(Product product, int quantity) {
        // 모든 비즈니스 로직이 Service에 위치
        if (product.getStock() == null) {
            throw new BusinessException("재고 정보가 없습니다");
        }
        if (quantity <= 0) {
            throw new BusinessException("수량은 0보다 커야 합니다");
        }
        if (product.getStock() < quantity) {
            throw new BusinessException("재고가 부족합니다");
        }

        product.setStock(product.getStock() - quantity);
    }
}
```

**문제점:**
- 🚫 Entity는 단순 데이터 컨테이너 (객체의 능동성 상실)
- 🚫 비즈니스 로직이 Service에 흩어짐 (God Service)
- 🚫 테스트하기 어려움 (Service 전체를 테스트해야 함)
- 🚫 재사용 불가능 (다른 곳에서 같은 로직 복사/붙여넣기)

---

### Rich Domain Model (풍부한 모델) ✅

**특징**: Entity가 스스로 행동하며 비즈니스 규칙을 캡슐화

```java
// Rich Entity (좋음)
@Getter
@AllArgsConstructor
public class Product {
    private String id;
    private String name;
    private Integer stock;
    private Long price;

    /**
     * 비즈니스 로직: 재고 차감
     * Entity가 스스로 행동 (능동성)
     */
    public void decreaseStock(int quantity) {
        validateQuantity(quantity);
        validateStock(quantity);
        this.stock -= quantity;
    }

    /**
     * 비즈니스 로직: 재고 복구
     */
    public void restoreStock(int quantity) {
        validateQuantity(quantity);
        this.stock += quantity;
    }

    /**
     * 비즈니스 로직: 재고 확인
     */
    public boolean hasStock(int quantity) {
        return stock >= quantity;
    }

    // private 메서드로 검증 로직 캡슐화
    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(
                ErrorCode.INVALID_QUANTITY,
                "수량은 0보다 커야 합니다"
            );
        }
    }

    private void validateStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_STOCK,
                String.format("재고가 부족합니다 (요청: %d, 재고: %d)", quantity, stock)
            );
        }
    }
}

// Service는 단순히 호출만
@Service
@RequiredArgsConstructor
public class ProductService {
    public void decreaseStock(Product product, int quantity) {
        product.decreaseStock(quantity);  // Entity 메서드 호출
    }
}
```

**장점:**
- ✅ 객체의 능동성 (Entity가 스스로 행동)
- ✅ 테스트 용이성 (Entity 메서드만 단독 테스트)
- ✅ 로직 분산 (Service 간소화)
- ✅ 재사용 가능 (어디서든 `product.decreaseStock()` 호출)

---

## 🎯 Entity에 비즈니스 로직을 두는 이유

### 로이코치님 조언
> "Entity에 로직을 두면: 객체의 능동성, 테스트 용이성, 로직 분산 효과가 있습니다."

### 1. 객체의 능동성 (Active Object)
```java
// ❌ 수동적 객체 (Passive)
product.setStock(product.getStock() - 10);  // 외부에서 조작

// ✅ 능동적 객체 (Active)
product.decreaseStock(10);  // 스스로 행동
```

### 2. 테스트 용이성
```java
// Entity 메서드만 단독 테스트 (의존성 없음)
@Test
void 재고_차감_성공() {
    // Given
    Product product = new Product("P001", "노트북", 10, 890000L);

    // When
    product.decreaseStock(3);

    // Then
    assertThat(product.getStock()).isEqualTo(7);
}

@Test
void 재고_부족시_예외_발생() {
    // Given
    Product product = new Product("P001", "노트북", 5, 890000L);

    // When & Then
    assertThatThrownBy(() -> product.decreaseStock(10))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);
}
```

### 3. 로직 분산 (God Service 방지)
```java
// ❌ God Service (모든 로직 집중)
@Service
public class ProductService {
    public void decreaseStock(...) { /* 100 lines */ }
    public void restoreStock(...) { /* 100 lines */ }
    public void validatePrice(...) { /* 100 lines */ }
    public void calculateDiscount(...) { /* 100 lines */ }
    // 500+ lines...
}

// ✅ 로직 분산
@Service
public class ProductService {
    // 단순 위임
    public void decreaseStock(Product product, int quantity) {
        product.decreaseStock(quantity);  // Entity에 위임
    }
}

public class Product {
    // Entity가 자신의 로직 담당
    public void decreaseStock(int quantity) { /* 20 lines */ }
    public void restoreStock(int quantity) { /* 10 lines */ }
}
```

---

## 📋 비즈니스 로직 배치 가이드

### Entity에 두어야 할 로직
- ✅ 자신의 상태 변경 (`decreaseStock`, `activate`, `cancel`)
- ✅ 자신의 상태 검증 (`hasStock`, `isExpired`, `isValid`)
- ✅ 자신의 상태 기반 계산 (`calculateTotal`, `getDiscountedPrice`)

### DomainService에 두어야 할 로직
- ✅ 여러 Entity를 조합한 로직 (`validateOrder`, `calculateShippingFee`)
- ✅ 외부 정책 적용 (`applyPromotionRule`, `checkEligibility`)

### UseCase에 두어야 할 로직
- ✅ 워크플로우 조율 (조회 → 검증 → 저장)
- ✅ DTO 변환 (Entity → Response DTO)
- ✅ 트랜잭션 관리

---

## 🔨 실전 예시

### 주문 Entity
```java
@Getter
@AllArgsConstructor
public class Order {
    private String id;
    private String userId;
    private List<OrderItem> items;
    private OrderStatus status;
    private Long totalAmount;
    private LocalDateTime createdAt;

    /**
     * 주문 생성 (Factory Method)
     */
    public static Order create(String userId, List<OrderItemRequest> items) {
        String orderId = generateOrderId();
        List<OrderItem> orderItems = items.stream()
            .map(OrderItem::from)
            .toList();

        return new Order(
            orderId,
            userId,
            orderItems,
            OrderStatus.PENDING,
            0L,  // 금액은 나중에 계산
            LocalDateTime.now()
        );
    }

    /**
     * 비즈니스 로직: 주문 완료 처리
     */
    public void complete() {
        validateCompletable();
        this.status = OrderStatus.COMPLETED;
    }

    /**
     * 비즈니스 로직: 주문 취소
     */
    public void cancel() {
        validateCancelable();
        this.status = OrderStatus.CANCELLED;
    }

    /**
     * 비즈니스 로직: 총 금액 설정
     */
    public void setTotalAmount(Long amount) {
        validateAmount(amount);
        this.totalAmount = amount;
    }

    // Private 검증 메서드들
    private void validateCompletable() {
        if (status != OrderStatus.PENDING) {
            throw new BusinessException(
                ErrorCode.INVALID_ORDER_STATUS,
                "PENDING 상태의 주문만 완료할 수 있습니다"
            );
        }
    }

    private void validateCancelable() {
        if (status == OrderStatus.COMPLETED) {
            throw new BusinessException(
                ErrorCode.INVALID_ORDER_STATUS,
                "완료된 주문은 취소할 수 없습니다"
            );
        }
    }

    private void validateAmount(Long amount) {
        if (amount == null || amount < 0) {
            throw new BusinessException(
                ErrorCode.INVALID_AMOUNT,
                "주문 금액은 0 이상이어야 합니다"
            );
        }
    }
}
```

### 쿠폰 Entity
```java
@Getter
public class Coupon {
    private String id;
    private String name;
    private Integer discountRate;
    private Integer totalQuantity;
    private AtomicInteger issuedQuantity;
    private LocalDateTime expiresAt;

    /**
     * 비즈니스 로직: 발급 가능 여부 검증
     */
    public void validateIssuable() {
        if (isExpired()) {
            throw new BusinessException(ErrorCode.EXPIRED_COUPON);
        }
        if (isSoldOut()) {
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }
    }

    /**
     * 비즈니스 로직: 쿠폰 발급 시도 (동시성 제어 포함)
     */
    public boolean tryIssue() {
        while (true) {
            int current = issuedQuantity.get();

            // 수량 초과 체크
            if (current >= totalQuantity) {
                return false;
            }

            // CAS (Compare-And-Swap) 연산
            if (issuedQuantity.compareAndSet(current, current + 1)) {
                return true;
            }
            // 실패하면 재시도
        }
    }

    /**
     * 비즈니스 로직: 할인 금액 계산
     */
    public long calculateDiscount(long originalPrice) {
        return originalPrice * discountRate / 100;
    }

    // Private 검증 메서드들
    private boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    private boolean isSoldOut() {
        return issuedQuantity.get() >= totalQuantity;
    }

    public int getRemainingQuantity() {
        return totalQuantity - issuedQuantity.get();
    }
}
```

---

## ✅ Pass 기준

### 도메인 모델 설계
- [ ] Entity가 비즈니스 로직 메서드를 포함
- [ ] getter/setter만 있는 Anemic Model이 아님
- [ ] 검증 로직이 Entity 내부에 캡슐화됨

### 비즈니스 로직 배치
- [ ] 재고 차감 로직이 Product Entity에 위치
- [ ] 주문 상태 변경 로직이 Order Entity에 위치
- [ ] 쿠폰 발급 로직이 Coupon Entity에 위치

### 코드 품질
- [ ] Entity 메서드가 단독으로 테스트 가능
- [ ] Service가 간소화됨 (God Service 아님)
- [ ] 비즈니스 규칙이 명확히 드러남

---

## ❌ Fail 사유

### Anemic Domain Model
- ❌ Entity에 getter/setter만 존재
- ❌ 모든 비즈니스 로직이 Service에 위치
- ❌ Entity가 단순 데이터 컨테이너

### 로직 배치 오류
- ❌ Controller에 비즈니스 로직 작성
- ❌ Repository에 비즈니스 로직 작성
- ❌ UseCase에 도메인 규칙 직접 작성

---

## 🎯 학습 체크리스트

### 이론 이해
- [ ] Rich Domain Model과 Anemic Domain Model의 차이를 설명할 수 있다
- [ ] Entity에 로직을 두는 이유 3가지를 설명할 수 있다
- [ ] 비즈니스 로직 배치 원칙을 설명할 수 있다

### 실전 적용
- [ ] Entity에 비즈니스 로직 메서드를 작성할 수 있다
- [ ] private 검증 메서드로 로직을 캡슐화할 수 있다
- [ ] Entity 메서드를 단독으로 테스트할 수 있다

### 토론 주제
- "재고 차감 로직을 어디에 구현했나요? 그 이유는?"
- "Anemic Domain Model의 문제점은 무엇인가요?"
- "Entity에 setter를 두지 않는 이유는 무엇인가요?"

---

## 📚 참고 자료

- [Anemic Domain Model - Martin Fowler](https://martinfowler.com/bliki/AnemicDomainModel.html)
- [Domain-Driven Design - Eric Evans](https://www.domainlanguage.com/ddd/)
- CLAUDE.md - Q4. Anemic Domain Model은 무엇인가요?

---

## 💡 실전 팁

### Setter 사용 지양
```java
// ❌ 나쁜 예 (Setter 사용)
product.setStock(product.getStock() - 10);  // 비즈니스 규칙 없음

// ✅ 좋은 예 (비즈니스 메서드)
product.decreaseStock(10);  // 검증 로직 포함
```

### Lombok 활용
```java
@Getter  // getter만 생성
@AllArgsConstructor  // 생성자 생성
public class Product {
    private String id;
    private Integer stock;

    // Setter 없음 (불변성 유지)
    // 비즈니스 메서드로만 상태 변경
    public void decreaseStock(int quantity) {
        // ...
    }
}
```

---

**이전 학습**: [02. 유스케이스 패턴](./02-usecase-pattern.md)
**다음 학습**: [04. Repository 패턴](./04-repository-pattern.md)
