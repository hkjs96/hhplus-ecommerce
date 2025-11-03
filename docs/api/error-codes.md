# 에러 코드 표준

이커머스 시스템의 표준 에러 코드 체계입니다.

---

## 에러 응답 형식

### 공통 응답 포맷

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "재고가 부족합니다",
    "details": {
      "productId": "P001",
      "requestedQuantity": 10,
      "availableQuantity": 5
    }
  }
}
```

### 필드 설명

| 필드 | 타입 | 설명 |
|------|------|------|
| `success` | boolean | 성공 여부 (항상 false) |
| `data` | null | 에러 시 null |
| `error.code` | string | 비즈니스 에러 코드 |
| `error.message` | string | 사용자에게 보여줄 에러 메시지 |
| `error.details` | object | 추가 상세 정보 (선택) |

---

## HTTP Status Code 매핑

| Status | 설명 | 사용 시나리오 |
|--------|------|--------------|
| **200 OK** | 성공 | 조회, 수정 성공 |
| **201 Created** | 생성 성공 | 주문 생성, 쿠폰 발급 |
| **400 Bad Request** | 잘못된 요청 | 유효성 검증 실패, 비즈니스 규칙 위반 |
| **404 Not Found** | 리소스 없음 | 주문 없음, 사용자 없음, 상품 없음 |
| **409 Conflict** | 충돌 | 동시성 충돌, 중복 요청 |
| **500 Internal Server Error** | 서버 오류 | 예상치 못한 오류 |

---

## 에러 코드 정의 (Enum)

### Java Enum 구현

```java
package io.hhplus.ecommerce.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ================================
    // 상품 관련 (P0xx)
    // ================================
    PRODUCT_NOT_FOUND(
        "P001",
        "상품을 찾을 수 없습니다",
        HttpStatus.NOT_FOUND
    ),
    INSUFFICIENT_STOCK(
        "P002",
        "재고가 부족합니다",
        HttpStatus.BAD_REQUEST
    ),

    // ================================
    // 주문 관련 (O0xx)
    // ================================
    EMPTY_CART(
        "O001",
        "장바구니가 비어있습니다",
        HttpStatus.BAD_REQUEST
    ),
    ORDER_NOT_FOUND(
        "O002",
        "주문을 찾을 수 없습니다",
        HttpStatus.NOT_FOUND
    ),
    INVALID_QUANTITY(
        "O003",
        "유효하지 않은 수량입니다",
        HttpStatus.BAD_REQUEST
    ),
    ORDER_ALREADY_PAID(
        "O004",
        "이미 결제된 주문입니다",
        HttpStatus.BAD_REQUEST
    ),

    // ================================
    // 결제 관련 (PAY0xx)
    // ================================
    INSUFFICIENT_BALANCE(
        "PAY001",
        "잔액이 부족합니다",
        HttpStatus.BAD_REQUEST
    ),
    PAYMENT_FAILED(
        "PAY002",
        "결제에 실패했습니다",
        HttpStatus.BAD_REQUEST
    ),
    STOCK_DEDUCTION_FAILED(
        "PAY003",
        "재고 차감에 실패했습니다. 다시 시도해주세요",
        HttpStatus.CONFLICT
    ),

    // ================================
    // 쿠폰 관련 (C0xx)
    // ================================
    COUPON_NOT_FOUND(
        "C001",
        "쿠폰을 찾을 수 없습니다",
        HttpStatus.NOT_FOUND
    ),
    COUPON_SOLD_OUT(
        "C002",
        "쿠폰이 모두 소진되었습니다",
        HttpStatus.CONFLICT
    ),
    INVALID_COUPON(
        "C003",
        "유효하지 않은 쿠폰입니다",
        HttpStatus.BAD_REQUEST
    ),
    EXPIRED_COUPON(
        "C004",
        "만료된 쿠폰입니다",
        HttpStatus.BAD_REQUEST
    ),
    ALREADY_ISSUED(
        "C005",
        "이미 발급받은 쿠폰입니다",
        HttpStatus.CONFLICT
    ),
    COUPON_ISSUE_FAILED(
        "C006",
        "쿠폰 발급에 실패했습니다. 다시 시도해주세요",
        HttpStatus.CONFLICT
    ),

    // ================================
    // 사용자 관련 (U0xx)
    // ================================
    USER_NOT_FOUND(
        "U001",
        "사용자를 찾을 수 없습니다",
        HttpStatus.NOT_FOUND
    ),
    INVALID_AMOUNT(
        "U002",
        "유효하지 않은 금액입니다",
        HttpStatus.BAD_REQUEST
    ),

    // ================================
    // 장바구니 관련 (CART0xx)
    // ================================
    CART_ITEM_NOT_FOUND(
        "CART001",
        "장바구니 상품을 찾을 수 없습니다",
        HttpStatus.NOT_FOUND
    ),

    // ================================
    // 시스템 오류 (SYS0xx)
    // ================================
    INTERNAL_SERVER_ERROR(
        "SYS001",
        "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요",
        HttpStatus.INTERNAL_SERVER_ERROR
    ),
    EXTERNAL_API_ERROR(
        "SYS002",
        "외부 API 호출에 실패했습니다",
        HttpStatus.INTERNAL_SERVER_ERROR
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
```

---

## 에러 코드 목록

### 상품 관련 (P0xx)

| 코드 | 메시지 | HTTP Status | 상세 |
|------|--------|-------------|------|
| **P001** | 상품을 찾을 수 없습니다 | 404 Not Found | 존재하지 않는 productId 요청 시 |
| **P002** | 재고가 부족합니다 | 400 Bad Request | 요청 수량 > 현재 재고 |

#### 응답 예시

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "재고가 부족합니다",
    "details": {
      "productId": "P001",
      "productName": "상품A",
      "requestedQuantity": 10,
      "availableQuantity": 5
    }
  }
}
```

---

### 주문 관련 (O0xx)

| 코드 | 메시지 | HTTP Status | 상세 |
|------|--------|-------------|------|
| **O001** | 장바구니가 비어있습니다 | 400 Bad Request | 빈 장바구니로 주문 생성 시도 |
| **O002** | 주문을 찾을 수 없습니다 | 404 Not Found | 존재하지 않는 orderId 요청 시 |
| **O003** | 유효하지 않은 수량입니다 | 400 Bad Request | 수량 <= 0 또는 수량 > 100 |
| **O004** | 이미 결제된 주문입니다 | 400 Bad Request | COMPLETED 상태 주문에 재결제 시도 |

#### 응답 예시

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ORDER_NOT_FOUND",
    "message": "주문을 찾을 수 없습니다",
    "details": {
      "orderId": "ORD-20240115-999"
    }
  }
}
```

---

### 결제 관련 (PAY0xx)

| 코드 | 메시지 | HTTP Status | 상세 |
|------|--------|-------------|------|
| **PAY001** | 잔액이 부족합니다 | 400 Bad Request | 포인트 잔액 < 주문 금액 |
| **PAY002** | 결제에 실패했습니다 | 400 Bad Request | 일반적인 결제 실패 |
| **PAY003** | 재고 차감에 실패했습니다. 다시 시도해주세요 | 409 Conflict | Optimistic Lock 충돌 (재시도 필요) |

#### 응답 예시

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INSUFFICIENT_BALANCE",
    "message": "잔액이 부족합니다",
    "details": {
      "userId": "U001",
      "currentBalance": 100000,
      "requiredAmount": 250000,
      "shortfall": 150000
    }
  }
}
```

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "STOCK_DEDUCTION_FAILED",
    "message": "재고 차감에 실패했습니다. 다시 시도해주세요",
    "details": {
      "reason": "동시성 충돌 (Optimistic Lock)",
      "retryable": true
    }
  }
}
```

---

### 쿠폰 관련 (C0xx)

| 코드 | 메시지 | HTTP Status | 상세 |
|------|--------|-------------|------|
| **C001** | 쿠폰을 찾을 수 없습니다 | 404 Not Found | 존재하지 않는 couponId 요청 시 |
| **C002** | 쿠폰이 모두 소진되었습니다 | 409 Conflict | issued_quantity >= total_quantity |
| **C003** | 유효하지 않은 쿠폰입니다 | 400 Bad Request | 상태가 AVAILABLE이 아님 |
| **C004** | 만료된 쿠폰입니다 | 400 Bad Request | expires_at < 현재 시각 |
| **C005** | 이미 발급받은 쿠폰입니다 | 409 Conflict | Unique Constraint 위반 (1인 1매) |
| **C006** | 쿠폰 발급에 실패했습니다. 다시 시도해주세요 | 409 Conflict | Optimistic Lock 충돌 (재시도 필요) |

#### 응답 예시

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COUPON_SOLD_OUT",
    "message": "쿠폰이 모두 소진되었습니다",
    "details": {
      "couponId": "C001",
      "couponName": "10% 할인 쿠폰",
      "totalQuantity": 100,
      "issuedQuantity": 100
    }
  }
}
```

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ALREADY_ISSUED",
    "message": "이미 발급받은 쿠폰입니다",
    "details": {
      "userId": "U001",
      "couponId": "C001",
      "issuedAt": "2024-01-10T10:00:00Z"
    }
  }
}
```

---

### 사용자 관련 (U0xx)

| 코드 | 메시지 | HTTP Status | 상세 |
|------|--------|-------------|------|
| **U001** | 사용자를 찾을 수 없습니다 | 404 Not Found | 존재하지 않는 userId 요청 시 |
| **U002** | 유효하지 않은 금액입니다 | 400 Bad Request | 충전 금액 <= 0 또는 금액 > 10,000,000 |

#### 응답 예시

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_AMOUNT",
    "message": "유효하지 않은 금액입니다",
    "details": {
      "providedAmount": -10000,
      "minAmount": 1,
      "maxAmount": 10000000
    }
  }
}
```

---

### 장바구니 관련 (CART0xx)

| 코드 | 메시지 | HTTP Status | 상세 |
|------|--------|-------------|------|
| **CART001** | 장바구니 상품을 찾을 수 없습니다 | 404 Not Found | 존재하지 않는 cartItemId 요청 시 |

---

### 시스템 오류 (SYS0xx)

| 코드 | 메시지 | HTTP Status | 상세 |
|------|--------|-------------|------|
| **SYS001** | 서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요 | 500 Internal Server Error | 예상치 못한 서버 오류 |
| **SYS002** | 외부 API 호출에 실패했습니다 | 500 Internal Server Error | 외부 데이터 플랫폼 API 실패 (비동기 전송은 영향 없음) |

#### 응답 예시

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INTERNAL_SERVER_ERROR",
    "message": "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요",
    "details": {
      "timestamp": "2024-01-15T10:30:00Z",
      "path": "/api/orders/ORD-20240115-001/payment"
    }
  }
}
```

---

## 에러 처리 예시

### GlobalExceptionHandler

```java
package io.hhplus.ecommerce.presentation.common;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());

        ErrorResponse error = ErrorResponse.of(
            e.getErrorCode().getCode(),
            e.getErrorCode().getMessage(),
            e.getDetails()
        );

        return ResponseEntity
            .status(e.getErrorCode().getHttpStatus())
            .body(ApiResponse.error(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected exception", e);

        ErrorResponse error = ErrorResponse.of(
            ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
            ErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
            null
        );

        return ResponseEntity
            .status(500)
            .body(ApiResponse.error(error));
    }
}
```

### BusinessException

```java
package io.hhplus.ecommerce.common.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }

    public static BusinessException of(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    public static BusinessException of(ErrorCode errorCode, Map<String, Object> details) {
        return new BusinessException(errorCode, details);
    }
}
```

### 사용 예시

```java
// 1. 단순한 에러
if (product == null) {
    throw BusinessException.of(ErrorCode.PRODUCT_NOT_FOUND);
}

// 2. 상세 정보를 포함한 에러
if (stock.getQuantity() < quantity) {
    throw BusinessException.of(
        ErrorCode.INSUFFICIENT_STOCK,
        Map.of(
            "productId", productId,
            "productName", product.getName(),
            "requestedQuantity", quantity,
            "availableQuantity", stock.getQuantity()
        )
    );
}

// 3. Optimistic Lock 충돌
try {
    stockService.decreaseStock(productId, quantity);
} catch (OptimisticLockException e) {
    throw BusinessException.of(
        ErrorCode.STOCK_DEDUCTION_FAILED,
        Map.of(
            "reason", "동시성 충돌 (Optimistic Lock)",
            "retryable", true
        )
    );
}
```

---

## 에러 코드 규칙

### 네이밍 규칙

1. **접두사**: 도메인별로 구분
   - `P`: Product (상품)
   - `O`: Order (주문)
   - `PAY`: Payment (결제)
   - `C`: Coupon (쿠폰)
   - `U`: User (사용자)
   - `CART`: Cart (장바구니)
   - `SYS`: System (시스템)

2. **숫자**: 3자리 (001~999)

3. **예시**:
   - `P001`: Product Not Found
   - `PAY001`: Insufficient Balance
   - `C002`: Coupon Sold Out

### 메시지 규칙

1. **사용자 친화적**: 기술 용어 지양
   - ✅ "재고가 부족합니다"
   - ❌ "Stock quantity is less than requested quantity"

2. **간결함**: 1~2문장
   - ✅ "쿠폰이 모두 소진되었습니다"
   - ❌ "요청하신 쿠폰의 발급 수량이 총 수량에 도달하여 더 이상 발급할 수 없습니다"

3. **행동 유도** (필요 시):
   - ✅ "재고 차감에 실패했습니다. 다시 시도해주세요"
   - ❌ "재고 차감에 실패했습니다"

### HTTP Status 선택 기준

| Status | 사용 시나리오 |
|--------|--------------|
| **400 Bad Request** | 클라이언트 입력 오류, 비즈니스 규칙 위반 (재고 부족, 잔액 부족) |
| **404 Not Found** | 리소스가 존재하지 않음 (주문 없음, 상품 없음) |
| **409 Conflict** | 동시성 충돌, 중복 요청 (쿠폰 소진, Optimistic Lock 충돌) |
| **500 Internal Server Error** | 예상치 못한 서버 오류 |

---

## 클라이언트 에러 처리 가이드

### 재시도 가능 여부

| 에러 코드 | 재시도 | 설명 |
|-----------|--------|------|
| `PAY003` | ✅ 가능 | 재고 차감 Optimistic Lock 충돌 (동시성 문제) |
| `C006` | ✅ 가능 | 쿠폰 발급 Optimistic Lock 충돌 |
| `C002` | ❌ 불가능 | 쿠폰 소진 (수량 부족) |
| `PAY001` | ❌ 불가능 | 잔액 부족 (포인트 충전 필요) |
| `P002` | ❌ 불가능 | 재고 부족 (수량 감소 또는 다른 상품 선택 필요) |

### 클라이언트 코드 예시 (JavaScript)

```javascript
async function processPayment(orderId, userId) {
  try {
    const response = await fetch(`/api/orders/${orderId}/payment`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId })
    });

    const data = await response.json();

    if (!response.ok) {
      const errorCode = data.error.code;

      // 재시도 가능한 에러
      if (errorCode === 'PAY003' || errorCode === 'STOCK_DEDUCTION_FAILED') {
        console.log('재고 차감 충돌, 재시도 중...');
        await delay(500);
        return processPayment(orderId, userId); // 재시도
      }

      // 사용자 액션 필요
      if (errorCode === 'INSUFFICIENT_BALANCE') {
        alert('잔액이 부족합니다. 포인트를 충전해주세요.');
        window.location.href = '/balance/charge';
        return;
      }

      // 일반 에러
      alert(data.error.message);
      return;
    }

    // 성공 처리
    alert('결제가 완료되었습니다!');
    window.location.href = `/orders/${orderId}`;

  } catch (err) {
    console.error('네트워크 오류', err);
    alert('네트워크 오류가 발생했습니다. 다시 시도해주세요.');
  }
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}
```

---

## 관련 문서

- [API 명세서](./api-specification.md)
- [요구사항 명세서](./requirements.md)
- [시퀀스 다이어그램](../diagrams/sequence-diagrams.md)
