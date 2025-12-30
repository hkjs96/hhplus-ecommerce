# Optional 가이드: 올바른 사용법과 흔한 안티패턴

> 목적: “Optional을 왜 쓰고, 어디까지 써야 하는가?”를 정리하고, 실무에서 자주 발생하는 안티패턴을 피한다.

---

## 1) Optional은 무엇을 위한 도구인가?

`Optional<T>`는 “값이 없을 수 있음”을 **명시적으로 표현**하기 위한 도구입니다.

핵심 목적
- `null`을 숨기지 않고 “없을 수 있음”을 타입으로 드러낸다.
- 호출자가 “없음” 케이스를 처리하도록 유도한다.

---

## 2) 어디에 Optional을 쓰는 게 좋은가?

### ✅ Repository/조회 결과(없을 수 있는 조회)
- 예: `findById(...)` 결과는 없을 수 있다.
- 이 경우 `Optional<T>`는 의미가 명확합니다.

### ✅ 반환값에서 “없음”이 정상 흐름인 경우
- 예: “쿠폰이 없으면 그냥 할인 없이 계산” 같은 정책

---

## 3) 어디에 Optional을 쓰지 말아야 하나? (안티패턴)

### ❌ 필드(멤버 변수)에 Optional 저장
- `private Optional<Long> couponId;` 같은 형태는 권장되지 않습니다.
- 이유: 직렬화/ORM/JPA, 기본값 처리, 가독성 측면에서 비용이 큼

### ❌ 파라미터로 Optional 받기
- `method(Optional<String> name)`는 호출/사용이 불편해지고, 의미가 애매해지는 경우가 많습니다.
- 대신 오버로드/빌더/명시적 null 허용 정책 중 하나로 정리하는 편이 낫습니다.

### ❌ Optional.get() 남발
- `get()`은 “없으면 예외”를 던지므로 Optional을 쓰는 의미가 약해집니다.
- `orElseThrow`, `orElse`, `ifPresent`, `map/flatMap`을 우선 고려합니다.

---

## 4) Optional을 “의미 있게” 쓰는 패턴

### 1) 없으면 예외(정상적으로는 있어야 함)

```java
Product product = productRepository.findById(id)
    .orElseThrow(() -> new NotFoundException("product"));
```

### 2) 없으면 기본값/정책 적용

```java
BigDecimal discountRate = couponOpt
    .map(Coupon::discountRate)
    .orElse(BigDecimal.ZERO);
```

### 3) 체이닝(조건부 흐름)

```java
return userRepository.findById(userId)
    .flatMap(user -> couponRepository.findValidCoupon(userId))
    .map(coupon -> applyCoupon(user, coupon))
    .orElseGet(() -> applyNoCoupon(userId));
```

---

## 5) 이 레포 기준의 적용 감각

- “없으면 예외”인 케이스(예: 존재해야 하는 사용자/상품)는 `orElseThrow`가 가장 명확합니다.
- “정책적으로 없어도 정상”인 케이스(예: optional coupon)는 `orElse`/`map`으로 정책을 표현하는 편이 낫습니다.
- Optional을 과도하게 체이닝하면 가독성이 떨어질 수 있으니, 팀 컨벤션으로 “복잡해지면 if로 풀자” 같은 기준을 두는 것도 실무적입니다.

---

## 6) 체크리스트 답변용 요약(3줄)

1. Optional은 “없을 수 있음”을 타입으로 드러내고 호출자에게 처리를 강제하기 위한 도구다.  
2. Repository 조회 결과처럼 “없음이 자연스러운 반환”에 적합하고, 필드/파라미터/`get()` 남발은 안티패턴이다.  
3. 없으면 예외는 `orElseThrow`, 없으면 정책은 `map/orElse`로 표현하되, 복잡해지면 가독성 기준으로 풀어쓴다.

