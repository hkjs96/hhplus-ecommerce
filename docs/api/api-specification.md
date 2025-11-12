# API 명세서

## Base URL
```
http://localhost:8080/api
```

---

## 공통 응답 형식

### 성공 응답
```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

### 에러 응답
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ERROR_CODE",
    "message": "에러 메시지 설명"
  }
}
```

---

## 에러 코드 정의

### 상품 관련

| 에러 코드 | 설명 | HTTP 상태 |
|----------|------|-----------|
| `P001` | 상품을 찾을 수 없음 | 404 |
| `P002` | 재고 부족 | 400 |

### 주문 관련

| 에러 코드 | 설명 | HTTP 상태 |
|----------|------|-----------|
| `O001` | 잘못된 수량 (0 이하 또는 음수) | 400 |
| `O002` | 주문을 찾을 수 없음 | 404 |
| `O003` | 주문 상태가 올바르지 않음 | 400 |

### 결제 관련

| 에러 코드 | 설명 | HTTP 상태 |
|----------|------|-----------|
| `PAY001` | 잔액 부족 | 400 |
| `PAY002` | 결제 처리 실패 | 500 |

### 쿠폰 관련

| 에러 코드 | 설명 | HTTP 상태 |
|----------|------|-----------|
| `C001` | 쿠폰 수량 소진 | 400 |
| `C002` | 유효하지 않은 쿠폰 | 400 |
| `C003` | 만료된 쿠폰 | 400 |
| `C004` | 이미 발급받은 쿠폰 (중복 발급) | 400 |

### 사용자 관련

| 에러 코드 | 설명 | HTTP 상태 |
|----------|------|-----------|
| `U001` | 사용자를 찾을 수 없음 | 404 |
| `U002` | 잘못된 충전 금액 | 400 |

---

## 1. 상품 관련 API

### 1.1 상품 목록 조회
**Endpoint:**
```
GET /products
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `category` | string | No | 카테고리 필터 (예: "전자제품", "의류") |
| `sort` | string | No | 정렬 방식: `price` / `popularity` / `newest` |

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "products": [
      {
        "productId": "P001",
        "name": "노트북",
        "price": 890000,
        "stock": 10,
        "category": "전자제품"
      },
      {
        "productId": "P002",
        "name": "키보드",
        "price": 120000,
        "stock": 50,
        "category": "주변기기"
      }
    ],
    "totalCount": 2
  },
  "error": null
}
```

---

### 1.2 상품 상세 조회
**Endpoint:**
```
GET /products/{productId}
```

**Path Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `productId` | string | Yes | 상품 ID |

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "productId": "P001",
    "name": "노트북",
    "description": "고성능 게이밍 노트북",
    "price": 890000,
    "stock": 10,
    "category": "전자제품"
  },
  "error": null
}
```

**Error Responses:**

| HTTP 상태 | 에러 코드 | 설명 |
|----------|----------|------|
| 404 | `P001` | 상품을 찾을 수 없음 |

**Example Error:**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "P001",
    "message": "상품을 찾을 수 없습니다. productId: P999"
  }
}
```

---

### 1.3 인기 상품 조회
**Endpoint:**
```
GET /products/top
```

**설명:**
- 최근 3일간 판매량 기준 Top 5 상품 조회
- **배치 집계**: 매일 자정(00:00)에 스케줄러가 실행되어 통계 데이터 갱신
- 조회 실패 시 빈 배열 반환 (Fallback)
- 캐시된 통계 데이터를 조회하여 빠른 응답 제공

> **설계 결정**: 배치 스케줄러로 통계를 미리 집계하여 조회 성능을 최적화하고, 실시간성이 필요하지 않은 데이터는 캐싱 활용

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "period": "3days",
    "products": [
      {
        "rank": 1,
        "productId": "P001",
        "name": "노트북",
        "salesCount": 150,
        "revenue": 133500000
      },
      {
        "rank": 2,
        "productId": "P005",
        "name": "마우스",
        "salesCount": 320,
        "revenue": 9600000
      },
      {
        "rank": 3,
        "productId": "P002",
        "name": "키보드",
        "salesCount": 180,
        "revenue": 21600000
      }
    ]
  },
  "error": null
}
```

**Response Fields:**

| 필드 | 타입 | 설명 |
|------|------|------|
| `period` | string | 집계 기간 (고정값: "3days") |
| `products` | array | 인기 상품 목록 (최대 5개, 실패 시 빈 배열) |
| `rank` | number | 순위 (1~5) |
| `salesCount` | number | 판매 수량 |
| `revenue` | number | 매출액 (원) |

---

## 2. 장바구니 API

### 2.1 장바구니에 상품 추가
**Endpoint:**
```
POST /cart/items
```

**Request Body:**
```json
{
  "userId": "user123",
  "productId": "P001",
  "quantity": 2
}
```

**Request Fields:**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `userId` | string | Yes | 사용자 ID |
| `productId` | string | Yes | 상품 ID |
| `quantity` | number | Yes | 수량 (최소 1) |

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "cartId": "CART-user123",
    "items": [
      {
        "productId": "P001",
        "name": "노트북",
        "unitPrice": 890000,
        "quantity": 2,
        "subtotal": 1780000,
        "stockAvailable": true
      }
    ],
    "totalAmount": 1780000
  },
  "error": null
}
```

**Error Responses:**

| HTTP 상태 | 에러 코드 | 설명 |
|----------|----------|------|
| 400 | `P002` | 재고 부족 |
| 404 | `P001` | 상품을 찾을 수 없음 |
| 404 | `U001` | 사용자를 찾을 수 없음 |

---

### 2.2 장바구니 조회
**Endpoint:**
```
GET /cart?userId={userId}
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `userId` | string | Yes | 사용자 ID |

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "userId": "user123",
    "items": [
      {
        "productId": "P001",
        "name": "노트북",
        "unitPrice": 890000,
        "quantity": 2,
        "subtotal": 1780000,
        "stockAvailable": true
      },
      {
        "productId": "P005",
        "name": "마우스",
        "unitPrice": 30000,
        "quantity": 1,
        "subtotal": 30000,
        "stockAvailable": false
      }
    ],
    "totalAmount": 1810000
  },
  "error": null
}
```

**Response Fields:**

| 필드 | 타입 | 설명 |
|------|------|------|
| `userId` | string | 사용자 ID |
| `items` | array | 장바구니 상품 목록 |
| `items[].stockAvailable` | boolean | 재고 가능 여부 |
| `totalAmount` | number | 전체 합계 금액 (원) |

---

### 2.3 장바구니 상품 수정
**Endpoint:**
```
PUT /cart/items
```

**Request Body:**
```json
{
  "userId": "user123",
  "productId": "P001",
  "quantity": 3
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "productId": "P001",
    "quantity": 3,
    "subtotal": 2670000
  },
  "error": null
}
```

---

### 2.4 장바구니 상품 삭제
**Endpoint:**
```
DELETE /cart/items
```

**Request Body:**
```json
{
  "userId": "user123",
  "productId": "P001"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "message": "상품이 장바구니에서 삭제되었습니다."
  },
  "error": null
}
```

---

## 3. 주문/결제 관련 API

### 3.1 주문 생성
**Endpoint:**
```
POST /orders
```

**Request Body:**
```json
{
  "userId": "user123",
  "items": [
    {
      "productId": "P001",
      "quantity": 2
    },
    {
      "productId": "P002",
      "quantity": 1
    }
  ],
  "couponId": "COUPON_10"
}
```

**Request Fields:**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `userId` | string | Yes | 사용자 ID |
| `items` | array | Yes | 주문 상품 목록 (최소 1개) |
| `items[].productId` | string | Yes | 상품 ID |
| `items[].quantity` | number | Yes | 주문 수량 (최소 1) |
| `couponId` | string | No | 쿠폰 ID (선택) |

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "orderId": "ORDER-20240115-001",
    "userId": "user123",
    "items": [
      {
        "productId": "P001",
        "name": "노트북",
        "quantity": 2,
        "unitPrice": 890000,
        "subtotal": 1780000
      },
      {
        "productId": "P002",
        "name": "키보드",
        "quantity": 1,
        "unitPrice": 120000,
        "subtotal": 120000
      }
    ],
    "subtotalAmount": 1900000,
    "discountAmount": 190000,
    "totalAmount": 1710000,
    "status": "PENDING",
    "createdAt": "2024-01-15T10:30:00Z"
  },
  "error": null
}
```

**Response Fields:**

| 필드 | 타입 | 설명 |
|------|------|------|
| `orderId` | string | 주문 ID |
| `userId` | string | 사용자 ID |
| `items` | array | 주문 상품 상세 목록 |
| `subtotalAmount` | number | 상품 합계 금액 (원) |
| `discountAmount` | number | 할인 금액 (원) |
| `totalAmount` | number | 최종 결제 금액 (원) |
| `status` | string | 주문 상태 (`PENDING` / `COMPLETED`) |
| `createdAt` | string (ISO 8601) | 주문 생성 시각 |

**Error Responses:**

| HTTP 상태 | 에러 코드 | 설명 |
|----------|----------|------|
| 400 | `P002` | 재고 부족 |
| 400 | `O001` | 잘못된 수량 (0 이하) |
| 400 | `C002` | 유효하지 않은 쿠폰 |
| 400 | `C003` | 만료된 쿠폰 |
| 400 | `C004` | 이미 사용된 쿠폰 |
| 404 | `P001` | 상품을 찾을 수 없음 |
| 404 | `U001` | 사용자를 찾을 수 없음 |

**Example Error (재고 부족):**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "P002",
    "message": "재고가 부족합니다. 상품: 노트북 (요청: 10개, 재고: 5개)"
  }
}
```

---

### 3.2 결제 처리
**Endpoint:**
```
POST /orders/{orderId}/payment
```

**Path Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `orderId` | string | Yes | 주문 ID |

**Request Body:**
```json
{
  "userId": "user123"
}
```

**Request Fields:**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `userId` | string | Yes | 사용자 ID |

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "orderId": "ORDER-20240115-001",
    "paidAmount": 1710000,
    "remainingBalance": 290000,
    "status": "SUCCESS",
    "dataTransmission": "SUCCESS",
    "paidAt": "2024-01-15T10:35:00Z"
  },
  "error": null
}
```

**Response Fields:**

| 필드 | 타입 | 설명 |
|------|------|------|
| `orderId` | string | 주문 ID |
| `paidAmount` | number | 결제된 금액 (원) |
| `remainingBalance` | number | 결제 후 남은 잔액 (원) |
| `status` | string | 결제 상태 (`SUCCESS` / `FAILED`) |
| `dataTransmission` | string | 외부 데이터 전송 상태 (`SUCCESS` / `FAILED` / `PENDING`) |
| `paidAt` | string (ISO 8601) | 결제 완료 시각 |

**Notes:**
- `dataTransmission`: 외부 데이터 플랫폼 전송 결과
  - `SUCCESS`: 즉시 전송 성공
  - `FAILED`: 전송 실패 (재시도 큐에 저장됨)
  - `PENDING`: 전송 대기 중
- **중요**: 외부 전송 실패(`FAILED`)여도 주문은 정상 완료 처리됨

**Error Responses:**

| HTTP 상태 | 에러 코드 | 설명 |
|----------|----------|------|
| 400 | `PAY001` | 잔액 부족 |
| 400 | `O003` | 주문 상태가 올바르지 않음 (이미 결제됨) |
| 404 | `O002` | 주문을 찾을 수 없음 |
| 404 | `U001` | 사용자를 찾을 수 없음 |
| 500 | `PAY002` | 결제 처리 실패 (서버 오류) |

**Example Error (잔액 부족):**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "PAY001",
    "message": "잔액이 부족합니다. (필요: 1710000원, 보유: 1000000원)"
  }
}
```

**Example Response (외부 전송 실패):**
```json
{
  "success": true,
  "data": {
    "orderId": "ORDER-20240115-001",
    "paidAmount": 1710000,
    "remainingBalance": 290000,
    "status": "SUCCESS",
    "dataTransmission": "FAILED",
    "paidAt": "2024-01-15T10:35:00Z"
  },
  "error": null
}
```

---

## 4. 쿠폰 관련 API

### 4.1 쿠폰 발급 (선착순)
**Endpoint:**
```
POST /coupons/{couponId}/issue
```

**Path Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `couponId` | string | Yes | 쿠폰 ID |

**Request Body:**
```json
{
  "userId": "user123"
}
```

**Request Fields:**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `userId` | string | Yes | 사용자 ID |

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "userCouponId": "UC-20240115-001",
    "couponId": "COUPON_10",
    "couponName": "10% 할인쿠폰",
    "discountRate": 10,
    "status": "AVAILABLE",
    "issuedAt": "2024-01-15T10:00:00Z",
    "expiresAt": "2024-12-31T23:59:59Z",
    "remainingQuantity": 95
  },
  "error": null
}
```

**Response Fields:**

| 필드 | 타입 | 설명 |
|------|------|------|
| `userCouponId` | string | 발급된 쿠폰 ID (사용자별 고유) |
| `couponId` | string | 쿠폰 템플릿 ID |
| `couponName` | string | 쿠폰 이름 |
| `discountRate` | number | 할인율 (%) |
| `status` | string | 쿠폰 상태 (`AVAILABLE`) |
| `issuedAt` | string (ISO 8601) | 발급 시각 |
| `expiresAt` | string (ISO 8601) | 만료 시각 |
| `remainingQuantity` | number | 남은 쿠폰 수량 |

**Error Responses:**

| HTTP 상태 | 에러 코드 | 설명 |
|----------|----------|------|
| 400 | `C001` | 쿠폰 수량 소진 |
| 400 | `C004` | 이미 발급받은 쿠폰 (1인 1매) |
| 404 | `C002` | 유효하지 않은 쿠폰 ID |
| 404 | `U001` | 사용자를 찾을 수 없음 |

**Example Error (수량 소진):**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "C001",
    "message": "쿠폰이 모두 소진되었습니다."
  }
}
```

**Example Error (중복 발급):**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "C004",
    "message": "이미 발급받은 쿠폰입니다."
  }
}
```

---

### 4.2 보유 쿠폰 조회
**Endpoint:**
```
GET /users/{userId}/coupons
```

**Path Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `userId` | string | Yes | 사용자 ID |

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `status` | string | No | 쿠폰 상태 필터 (`AVAILABLE` / `USED` / `EXPIRED`) |

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "userId": "user123",
    "coupons": [
      {
        "userCouponId": "UC-20240115-001",
        "couponId": "COUPON_10",
        "couponName": "10% 할인쿠폰",
        "discountRate": 10,
        "status": "AVAILABLE",
        "issuedAt": "2024-01-15T10:00:00Z",
        "expiresAt": "2024-12-31T23:59:59Z"
      },
      {
        "userCouponId": "UC-20240110-005",
        "couponId": "COUPON_20",
        "couponName": "20% 할인쿠폰",
        "discountRate": 20,
        "status": "USED",
        "issuedAt": "2024-01-10T14:00:00Z",
        "usedAt": "2024-01-12T09:30:00Z",
        "expiresAt": "2024-06-30T23:59:59Z"
      }
    ],
    "totalCount": 2
  },
  "error": null
}
```

**Response Fields:**

| 필드 | 타입 | 설명 |
|------|------|------|
| `userId` | string | 사용자 ID |
| `coupons` | array | 보유 쿠폰 목록 |
| `userCouponId` | string | 발급된 쿠폰 ID |
| `status` | string | 쿠폰 상태 (`AVAILABLE` / `USED` / `EXPIRED`) |
| `usedAt` | string (ISO 8601) | 사용 시각 (status가 USED인 경우만) |
| `totalCount` | number | 전체 쿠폰 수 |

**Error Responses:**

| HTTP 상태 | 에러 코드 | 설명 |
|----------|----------|------|
| 404 | `U001` | 사용자를 찾을 수 없음 |

---

## 5. 사용자 관련 API

### 5.1 사용자 조회
**Endpoint:**
```
GET /users/{userId}
```

**Path Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `userId` | string | Yes | 사용자 ID |

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "userId": "user123",
    "username": "홍길동",
    "email": "hong@example.com",
    "balance": 2000000
  },
  "error": null
}
```

---

### 5.2 포인트 조회
**Endpoint:**
```
GET /users/{userId}/balance
```

**설명:**
- PG 연동 없이 내부 포인트 시스템으로만 운영
- 사용자는 미리 포인트를 충전하여 사용

**Path Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `userId` | string | Yes | 사용자 ID |

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "userId": "user123",
    "balance": 2000000
  },
  "error": null
}
```

**Response Fields:**

| 필드 | 타입 | 설명 |
|------|------|------|
| `userId` | string | 사용자 ID |
| `balance` | number | 보유 포인트 (원) |

**Error Responses:**

| HTTP 상태 | 에러 코드 | 설명 |
|----------|----------|------|
| 404 | `U001` | 사용자를 찾을 수 없음 |

---

### 5.3 포인트 충전
**Endpoint:**
```
POST /users/{userId}/balance/charge
```

**설명:**
- PG 연동 없이 내부 포인트 충전만 제공
- 실제 결제는 충전된 포인트로만 가능

**Path Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `userId` | string | Yes | 사용자 ID |

**Request Body:**
```json
{
  "amount": 500000
}
```

**Request Fields:**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `amount` | number | Yes | 충전할 포인트 (원, 양수만 가능) |

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "userId": "user123",
    "balance": 2500000,
    "chargedAmount": 500000,
    "chargedAt": "2024-01-15T09:00:00Z"
  },
  "error": null
}
```

**Response Fields:**

| 필드 | 타입 | 설명 |
|------|------|------|
| `userId` | string | 사용자 ID |
| `balance` | number | 충전 후 포인트 잔액 (원) |
| `chargedAmount` | number | 충전한 포인트 (원) |
| `chargedAt` | string (ISO 8601) | 충전 시각 |

**Error Responses:**

| HTTP 상태 | 에러 코드 | 설명 |
|----------|----------|------|
| 400 | `U002` | 잘못된 충전 금액 (0 이하) |
| 404 | `U001` | 사용자를 찾을 수 없음 |

**Example Error:**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "U002",
    "message": "충전 금액은 0보다 커야 합니다."
  }
}
```

---

## 6. HTTP 상태 코드 정리

| HTTP 상태 | 설명 | 사용 예시 |
|----------|------|----------|
| `200 OK` | 성공 (조회, 수정) | GET, PUT, PATCH 성공 |
| `201 Created` | 생성 성공 | POST 성공 (주문 생성, 쿠폰 발급) |
| `400 Bad Request` | 잘못된 요청 | 재고 부족, 잔액 부족, 잘못된 파라미터 |
| `404 Not Found` | 리소스 없음 | 상품/주문/사용자 찾을 수 없음 |
| `500 Internal Server Error` | 서버 오류 | 결제 처리 실패 등 |

---

## 7. 데이터 타입 및 포맷

### 날짜/시간
- **포맷**: ISO 8601 (`YYYY-MM-DDTHH:mm:ssZ`)
- **예시**: `2024-01-15T10:30:00Z`
- **타임존**: UTC

### 금액
- **단위**: 원 (KRW)
- **타입**: number (정수)
- **예시**: `1500000` (150만원)

### ID 포맷
- **상품 ID**: `P` + 숫자 (예: `P001`, `P002`)
- **주문 ID**: `ORDER-` + 날짜 + 시퀀스 (예: `ORDER-20240115-001`)
- **사용자 쿠폰 ID**: `UC-` + 날짜 + 시퀀스 (예: `UC-20240115-001`)
- **사용자 ID**: 자유 형식 (예: `user123`)

---

## 8. 스케줄러 및 배치 작업

### 재시도 큐 처리
- **실행 간격**: 1분, 5분, 30분 간격
- **대상**: 외부 데이터 플랫폼 전송 실패 건
- **최대 재시도**: 3회
- **실패 시**: 로그 기록 및 알림

---

## 9. 동시성 제어 전략

### 재고 차감 (Optimistic Lock)
- **전략**: Stock 테이블에 `@Version` 필드 사용
- **목표**: 높은 동시성 처리, 성능 최적화
- **실패 시**: 애플리케이션에서 재시도 로직 구현

### 쿠폰 발급 (Optimistic Lock)
```java
@Version
private Long version;
```
- 버전 필드를 이용한 낙관적 잠금
- 동시 발급 시 version 충돌 감지
- 충돌 시 재시도 또는 에러 반환

---

## 부록: ErrorCodes.java 참고

```java
public class ErrorCodes {

    // 상품 관련
    public static final String PRODUCT_NOT_FOUND = "P001";
    public static final String INSUFFICIENT_STOCK = "P002";

    // 주문 관련
    public static final String INVALID_QUANTITY = "O001";
    public static final String ORDER_NOT_FOUND = "O002";
    public static final String INVALID_ORDER_STATUS = "O003";

    // 결제 관련
    public static final String INSUFFICIENT_BALANCE = "PAY001";
    public static final String PAYMENT_FAILED = "PAY002";

    // 쿠폰 관련
    public static final String COUPON_SOLD_OUT = "C001";
    public static final String INVALID_COUPON = "C002";
    public static final String EXPIRED_COUPON = "C003";
    public static final String ALREADY_ISSUED = "C004";

    // 사용자 관련
    public static final String USER_NOT_FOUND = "U001";
    public static final String INVALID_CHARGE_AMOUNT = "U002";
}
```
