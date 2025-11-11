# 비즈니스 UseCase 시나리오

## 문서 개요

이 문서는 항해플러스 이커머스 시스템의 비즈니스 UseCase 시나리오를 정의합니다.
각 시나리오는 실제 구현된 Application Layer (Service 클래스)의 메서드와 매핑되어 있습니다.

**프로젝트 단계**: Week 4 - Database Integration (JPA)
**작성일**: 2025-11-11

---

## 📋 목차

1. [상품 조회](#1-상품-조회)
2. [장바구니 관리](#2-장바구니-관리)
3. [포인트 관리](#3-포인트-관리)
4. [쿠폰 시스템](#4-쿠폰-시스템)
5. [주문 생성](#5-주문-생성)
6. [결제 처리](#6-결제-처리)
7. [주문 내역 조회](#7-주문-내역-조회)

---

## 1. 상품 조회

### 1.1 상품 목록 조회 (UC-001)

**구현 위치**: `ProductService.getProducts()`

#### 비즈니스 목표
고객이 구매 가능한 상품 목록을 조회하고, 카테고리 및 정렬 옵션을 통해 원하는 상품을 찾을 수 있도록 한다.

#### 사전 조건
- 없음 (공개 API)

#### 입력 파라미터
- `category` (String, optional): 필터링할 카테고리
- `sort` (String, optional): 정렬 옵션
  - `price`, `price_asc`: 가격 오름차순
  - `price_desc`: 가격 내림차순
  - `newest`: 최신순

#### 정상 흐름
1. 시스템은 모든 상품을 조회한다
2. `category` 파라미터가 있으면 해당 카테고리 상품만 필터링한다
3. `sort` 파라미터에 따라 정렬한다
4. 상품 목록을 `ProductListResponse`로 반환한다

#### 비즈니스 규칙
- 모든 상품은 기본적으로 조회 가능
- 재고가 0인 상품도 목록에 포함 (프론트엔드에서 "품절" 표시)

#### 예외 처리
- 없음 (빈 목록 반환)

#### 응답 예시
```json
{
  "products": [
    {
      "id": "PROD-001",
      "name": "무선 이어폰",
      "price": 89000,
      "stock": 150,
      "category": "전자기기"
    }
  ]
}
```

---

### 1.2 상품 상세 조회 (UC-002)

**구현 위치**: `ProductService.getProduct()`

#### 비즈니스 목표
고객이 특정 상품의 상세 정보를 확인하여 구매 결정을 내릴 수 있도록 한다.

#### 사전 조건
- 상품 ID가 유효해야 함

#### 입력 파라미터
- `productId` (String, required): 조회할 상품 ID

#### 정상 흐름
1. 시스템은 `productId`로 상품을 조회한다
2. 상품 정보를 `ProductResponse`로 반환한다

#### 비즈니스 규칙
- 상품이 존재하지 않으면 예외 발생
- 재고 수량을 포함하여 반환

#### 예외 처리
| 에러 코드 | 발생 조건 | HTTP Status |
|----------|----------|-------------|
| `PRODUCT_NOT_FOUND` | 상품이 존재하지 않음 | 404 |

#### 응답 예시
```json
{
  "id": "PROD-001",
  "name": "무선 이어폰",
  "description": "최신 블루투스 5.0 지원",
  "price": 89000,
  "stock": 150,
  "category": "전자기기",
  "createdAt": "2025-11-01T10:00:00"
}
```

---

### 1.3 인기 상품 Top 5 조회 (UC-003)

**구현 위치**: `ProductService.getTopProducts()`

#### 비즈니스 목표
최근 3일간 판매량이 많은 상품을 노출하여 트렌드를 반영한 구매 의사결정을 돕는다.

#### 사전 조건
- 없음

#### 정상 흐름
1. 시스템은 최근 3일간 완료된 주문을 조회한다 (`paidAt >= now - 3 days`)
2. 해당 주문의 `OrderItem`을 집계하여 상품별 판매량과 매출액을 계산한다
3. 판매량 기준으로 내림차순 정렬하여 상위 5개를 추출한다
4. 각 상품의 순위, 이름, 판매량, 매출액을 `TopProductResponse`로 반환한다

#### 비즈니스 규칙
- 완료된 주문 (`OrderStatus.COMPLETED`)만 집계
- 결제 시각 (`paidAt`) 기준으로 3일 이내 주문만 포함
- 최대 5개 상품 반환
- 판매 내역이 없으면 빈 배열 반환 (Fallback)

#### 예외 처리
- 없음 (빈 배열 반환)

#### 응답 예시
```json
{
  "topProducts": [
    {
      "rank": 1,
      "productId": "PROD-001",
      "name": "무선 이어폰",
      "salesCount": 45,
      "revenue": 4005000
    },
    {
      "rank": 2,
      "productId": "PROD-003",
      "name": "스마트워치",
      "salesCount": 32,
      "revenue": 9600000
    }
  ]
}
```

#### 성능 고려사항
- 현재는 In-Memory Repository로 실시간 집계
- Week 5+: 배치로 집계 후 Redis 캐싱 고려
- Week 6+: 인덱스 최적화 (paidAt, status)

---

## 2. 장바구니 관리

### 2.1 장바구니에 상품 추가 (UC-004)

**구현 위치**: `CartService.addItemToCart()`

#### 비즈니스 목표
고객이 여러 상품을 장바구니에 담아 한 번에 주문할 수 있도록 한다.

#### 사전 조건
- 사용자 ID가 유효해야 함
- 상품 ID가 유효해야 함
- 요청 수량 > 0

#### 입력 파라미터
- `userId` (String, required)
- `productId` (String, required)
- `quantity` (int, required)

#### 정상 흐름
1. 시스템은 사용자와 상품의 존재를 확인한다
2. 상품의 재고가 요청 수량 이상인지 확인한다
3. 사용자의 장바구니가 없으면 생성한다
4. 같은 상품이 이미 장바구니에 있으면 수량을 증가시킨다
5. 없으면 새로운 `CartItem`을 생성한다
6. 장바구니의 `updatedAt`을 갱신한다
7. 업데이트된 장바구니 전체 내용을 반환한다

#### 비즈니스 규칙
- 재고가 부족하면 추가 불가
- 동일 상품 중복 추가 시 수량 증가 (새 항목 생성 X)
- 장바구니는 사용자당 1개

#### 예외 처리
| 에러 코드 | 발생 조건 | HTTP Status |
|----------|----------|-------------|
| `USER_NOT_FOUND` | 사용자가 존재하지 않음 | 404 |
| `PRODUCT_NOT_FOUND` | 상품이 존재하지 않음 | 404 |
| `INSUFFICIENT_STOCK` | 재고 부족 | 400 |

#### 응답 예시
```json
{
  "userId": "USER-001",
  "items": [
    {
      "productId": "PROD-001",
      "productName": "무선 이어폰",
      "quantity": 2,
      "unitPrice": 89000,
      "subtotal": 178000
    }
  ],
  "totalAmount": 178000
}
```

---

### 2.2 장바구니 조회 (UC-005)

**구현 위치**: `CartService.getCart()`

#### 비즈니스 목표
고객이 현재 장바구니에 담긴 상품을 확인하고 주문 전 검토할 수 있도록 한다.

#### 사전 조건
- 사용자 ID가 유효해야 함

#### 입력 파라미터
- `userId` (String, required)

#### 정상 흐름
1. 시스템은 사용자의 존재를 확인한다
2. 사용자의 장바구니를 조회한다
3. 장바구니가 없으면 빈 장바구니 응답을 반환한다
4. 있으면 장바구니의 모든 `CartItem`을 조회한다
5. 각 `CartItem`에 대해 상품 정보를 조회하여 이름, 가격, 소계를 계산한다
6. 장바구니 전체 정보를 `CartResponse`로 반환한다

#### 비즈니스 규칙
- 장바구니가 없으면 빈 배열 반환 (에러 아님)
- 재고 부족 상품도 장바구니에는 유지 (프론트엔드에서 경고 표시)

#### 예외 처리
| 에러 코드 | 발생 조건 | HTTP Status |
|----------|----------|-------------|
| `USER_NOT_FOUND` | 사용자가 존재하지 않음 | 404 |

---

### 2.3 장바구니 상품 수정 (UC-006)

**구현 위치**: `CartService.updateCartItem()`

#### 비즈니스 목표
고객이 장바구니에 담긴 상품의 수량을 변경하거나 삭제할 수 있도록 한다.

#### 사전 조건
- 사용자 ID가 유효해야 함
- 장바구니가 존재해야 함
- 해당 상품이 장바구니에 있어야 함

#### 입력 파라미터
- `userId` (String, required)
- `productId` (String, required)
- `quantity` (int, required): 변경할 수량 (0이면 삭제)

#### 정상 흐름
1. 시스템은 사용자의 장바구니를 조회한다
2. 장바구니에서 해당 상품 항목을 찾는다
3. `quantity`가 0이면 항목을 삭제한다
4. `quantity > 0`이면 상품의 재고를 확인한다
5. 재고가 충분하면 수량을 변경한다
6. 장바구니의 `updatedAt`을 갱신한다
7. 업데이트된 항목 정보를 반환한다

#### 비즈니스 규칙
- `quantity = 0`이면 항목 삭제
- `quantity > 0`이면 재고 확인 필수
- 재고 부족 시 수정 불가

#### 예외 처리
| 에러 코드 | 발생 조건 | HTTP Status |
|----------|----------|-------------|
| `USER_NOT_FOUND` | 사용자가 존재하지 않음 | 404 |
| `CART_NOT_FOUND` | 장바구니가 존재하지 않음 | 404 |
| `CART_ITEM_NOT_FOUND` | 상품이 장바구니에 없음 | 404 |
| `INSUFFICIENT_STOCK` | 재고 부족 | 400 |

---

### 2.4 장바구니 상품 삭제 (UC-007)

**구현 위치**: `CartService.deleteCartItem()`

#### 비즈니스 목표
고객이 장바구니에서 특정 상품을 제거할 수 있도록 한다.

#### 사전 조건
- 사용자 ID가 유효해야 함
- 장바구니가 존재해야 함
- 해당 상품이 장바구니에 있어야 함

#### 정상 흐름
1. 시스템은 사용자의 장바구니를 조회한다
2. 장바구니에서 해당 상품 항목을 찾는다
3. 항목을 삭제한다
4. 장바구니의 `updatedAt`을 갱신한다

#### 예외 처리
| 에러 코드 | 발생 조건 | HTTP Status |
|----------|----------|-------------|
| `USER_NOT_FOUND` | 사용자가 존재하지 않음 | 404 |
| `CART_NOT_FOUND` | 장바구니가 존재하지 않음 | 404 |
| `CART_ITEM_NOT_FOUND` | 상품이 장바구니에 없음 | 404 |

---

### 2.5 장바구니 전체 비우기 (UC-008)

**구현 위치**: `CartService.clearCart()`

#### 비즈니스 목표
고객이 장바구니의 모든 상품을 한 번에 삭제할 수 있도록 한다.

#### 사전 조건
- 사용자 ID가 유효해야 함

#### 정상 흐름
1. 시스템은 사용자의 장바구니를 조회한다
2. 장바구니가 없으면 아무 작업도 하지 않는다
3. 있으면 모든 `CartItem`을 삭제한다
4. 장바구니의 `updatedAt`을 갱신한다

#### 비즈니스 규칙
- 장바구니가 없어도 성공 (Idempotent)

---

## 3. 포인트 관리

### 3.1 포인트 조회 (UC-009)

**구현 위치**: `UserService.getBalance()`

#### 비즈니스 목표
고객이 현재 보유한 포인트를 확인하여 결제 가능 여부를 판단할 수 있도록 한다.

#### 사전 조건
- 사용자 ID가 유효해야 함

#### 입력 파라미터
- `userId` (String, required)

#### 정상 흐름
1. 시스템은 사용자를 조회한다
2. 사용자의 `balance` 필드를 반환한다

#### 예외 처리
| 에러 코드 | 발생 조건 | HTTP Status |
|----------|----------|-------------|
| `USER_NOT_FOUND` | 사용자가 존재하지 않음 | 404 |

#### 응답 예시
```json
{
  "userId": "USER-001",
  "balance": 500000
}
```

---

### 3.2 포인트 충전 (UC-010)

**구현 위치**: `UserService.chargeBalance()`

#### 비즈니스 목표
고객이 포인트를 충전하여 상품을 구매할 수 있도록 한다.

#### 사전 조건
- 사용자 ID가 유효해야 함
- 충전 금액 > 0

#### 입력 파라미터
- `userId` (String, required)
- `amount` (Long, required): 충전할 금액

#### 정상 흐름
1. 시스템은 사용자를 조회한다
2. `User` 도메인의 `charge()` 메서드를 호출한다
   - 음수 금액이면 예외 발생
   - `balance += amount`
3. 변경된 사용자를 저장한다
4. 충전 결과를 `ChargeBalanceResponse`로 반환한다

#### 비즈니스 규칙
- 충전 금액은 양수만 가능
- 최대 충전 제한 없음 (실제로는 추가 가능)
- 충전은 즉시 반영

#### 예외 처리
| 에러 코드 | 발생 조건 | HTTP Status |
|----------|----------|-------------|
| `USER_NOT_FOUND` | 사용자가 존재하지 않음 | 404 |
| `INVALID_INPUT` | 충전 금액이 0 이하 | 400 |

#### 응답 예시
```json
{
  "userId": "USER-001",
  "balance": 700000,
  "chargedAmount": 200000,
  "chargedAt": "2025-11-11T14:30:00"
}
```

#### 동시성 제어
- **Week 3**: In-Memory Repository 사용, 동시성 제어 없음
- **Week 4**: JPA 사용, `@Transactional` 적용
- **Week 5+**: Pessimistic Lock 고려 (포인트는 정확성 최우선)

---

## 4. 쿠폰 시스템

### 4.1 쿠폰 발급 (선착순) (UC-011)

**구현 위치**: `CouponService.issueCoupon()`

#### 비즈니스 목표
한정 수량의 쿠폰을 선착순으로 발급하여 프로모션을 진행한다.

#### 사전 조건
- 사용자 ID가 유효해야 함
- 쿠폰 ID가 유효해야 함

#### 입력 파라미터
- `couponId` (String, required): 발급할 쿠폰 ID
- `userId` (String, required): 사용자 ID

#### 정상 흐름
1. 시스템은 사용자와 쿠폰의 존재를 확인한다
2. 쿠폰의 유효성을 검증한다 (`validateIssuable()`)
   - 만료일이 지났는지 확인
   - 발급 가능한 상태인지 확인
3. 사용자가 이미 쿠폰을 발급받았는지 확인한다
4. 쿠폰의 `tryIssue()` 메서드를 호출하여 수량을 차감한다
   - `remainingQuantity > 0`이면 1 감소
   - `remainingQuantity <= 0`이면 실패
5. `UserCoupon` 엔티티를 생성하여 저장한다
6. 변경된 쿠폰을 저장한다
7. 발급 결과를 `IssueCouponResponse`로 반환한다

#### 비즈니스 규칙
- 1인 1매 제한 (중복 발급 불가)
- 선착순 (수량 소진 시 발급 불가)
- 만료된 쿠폰은 발급 불가

#### 예외 처리
| 에러 코드 | 발생 조건 | HTTP Status |
|----------|----------|-------------|
| `USER_NOT_FOUND` | 사용자가 존재하지 않음 | 404 |
| `COUPON_NOT_FOUND` | 쿠폰이 존재하지 않음 | 404 |
| `ALREADY_ISSUED_COUPON` | 이미 발급받은 쿠폰 | 400 |
| `COUPON_SOLD_OUT` | 쿠폰 수량 소진 | 400 |
| `INVALID_COUPON` | 만료된 쿠폰 | 400 |

#### 응답 예시
```json
{
  "userCouponId": "UC-12345",
  "userId": "USER-001",
  "couponId": "COUPON-001",
  "couponName": "신규 가입 20% 할인",
  "discountRate": 20,
  "expiresAt": "2025-12-31T23:59:59",
  "status": "AVAILABLE",
  "remainingQuantity": 49
}
```

#### 동시성 제어
- **Week 3**: In-Memory + synchronized 또는 CAS
- **Week 4**: JPA + Optimistic Lock (`@Version`)
- **동시 발급 시**: OptimisticLockException 발생 → 재시도 필요

---

### 4.2 보유 쿠폰 조회 (UC-012)

**구현 위치**: `CouponService.getUserCoupons()`

#### 비즈니스 목표
고객이 보유한 쿠폰 목록을 확인하여 주문 시 사용 가능한 쿠폰을 선택할 수 있도록 한다.

#### 사전 조건
- 사용자 ID가 유효해야 함

#### 입력 파라미터
- `userId` (String, required)
- `status` (String, optional): 필터링할 쿠폰 상태
  - `AVAILABLE`: 사용 가능
  - `USED`: 사용 완료
  - `EXPIRED`: 만료됨

#### 정상 흐름
1. 시스템은 사용자의 존재를 확인한다
2. 사용자의 모든 `UserCoupon`을 조회한다
3. `status` 파라미터가 있으면 해당 상태의 쿠폰만 필터링한다
4. 각 `UserCoupon`에 대해 `Coupon` 정보를 조회한다
5. 쿠폰 목록을 `UserCouponListResponse`로 반환한다

#### 비즈니스 규칙
- 쿠폰 상태는 자동으로 계산됨 (만료일 기준)
- 사용 완료된 쿠폰도 목록에 포함

#### 예외 처리
| 에러 코드 | 발생 조건 | HTTP Status |
|----------|----------|-------------|
| `USER_NOT_FOUND` | 사용자가 존재하지 않음 | 404 |

#### 응답 예시
```json
{
  "userId": "USER-001",
  "coupons": [
    {
      "userCouponId": "UC-12345",
      "couponId": "COUPON-001",
      "couponName": "신규 가입 20% 할인",
      "discountRate": 20,
      "status": "AVAILABLE",
      "expiresAt": "2025-12-31T23:59:59"
    }
  ]
}
```

---

## 5. 주문 생성

### 5.1 주문 생성 (UC-013)

**구현 위치**: `OrderService.createOrder()`

#### 비즈니스 목표
고객이 장바구니의 상품들로 주문을 생성하여 결제를 준비한다.

#### 사전 조건
- 사용자 ID가 유효해야 함
- 주문할 상품들이 존재해야 함
- 각 상품의 수량 > 0

#### 입력 파라미터
- `userId` (String, required)
- `items` (List, required): 주문할 상품 목록
  - `productId` (String)
  - `quantity` (int)
- `couponId` (String, optional): 적용할 쿠폰 ID

#### 정상 흐름
1. 시스템은 사용자의 존재를 확인한다
2. 각 주문 상품에 대해:
   - 상품을 조회한다
   - 재고가 충분한지 확인한다 (`stock >= quantity`)
   - 상품 소계를 계산한다 (`price * quantity`)
3. 전체 소계 금액을 계산한다
4. 쿠폰이 지정되었으면:
   - 쿠폰을 조회한다
   - 쿠폰의 유효성을 검증한다 (`validateIssuable()`)
   - 사용자가 해당 쿠폰을 보유하고 있는지 확인한다
   - 할인 금액을 계산한다 (`subtotal * discountRate / 100`)
5. 주문 ID를 생성한다 (`ORDER-{UUID}`)
6. `Order` 엔티티를 생성하여 저장한다
   - `status = PENDING`
7. 각 상품에 대해 `OrderItem` 엔티티를 생성하여 저장한다
8. 주문 정보를 `CreateOrderResponse`로 반환한다

#### 비즈니스 규칙
- 재고가 부족한 상품이 하나라도 있으면 주문 생성 실패
- 쿠폰은 선택 사항
- 쿠폰 적용 시 사용자가 해당 쿠폰을 보유해야 함
- 주문 생성 시점에는 재고 차감하지 않음 (결제 완료 후 차감)
- 초기 주문 상태는 `PENDING`

#### 예외 처리
| 에러 코드 | 발생 조건 | HTTP Status |
|----------|----------|-------------|
| `USER_NOT_FOUND` | 사용자가 존재하지 않음 | 404 |
| `PRODUCT_NOT_FOUND` | 상품이 존재하지 않음 | 404 |
| `INSUFFICIENT_STOCK` | 재고 부족 | 400 |
| `COUPON_NOT_FOUND` | 쿠폰이 존재하지 않음 | 404 |
| `INVALID_COUPON` | 쿠폰 유효하지 않음 | 400 |

#### 응답 예시
```json
{
  "orderId": "ORDER-abc12345",
  "userId": "USER-001",
  "items": [
    {
      "productId": "PROD-001",
      "productName": "무선 이어폰",
      "quantity": 2,
      "unitPrice": 89000,
      "subtotal": 178000
    }
  ],
  "subtotalAmount": 178000,
  "discountAmount": 35600,
  "totalAmount": 142400,
  "status": "PENDING",
  "createdAt": "2025-11-11T15:00:00"
}
```

#### 트랜잭션 경계
- **Week 4**: 메서드 전체가 하나의 트랜잭션 (`@Transactional`)
- Order, OrderItem 저장은 원자적으로 처리

---

## 6. 결제 처리

### 6.1 결제 처리 (UC-014)

**구현 위치**: `OrderService.processPayment()`

#### 비즈니스 목표
고객이 주문에 대한 결제를 완료하고, 재고를 차감하며, 외부 시스템에 데이터를 전송한다.

#### 사전 조건
- 주문 ID가 유효해야 함
- 주문 상태가 `PENDING`이어야 함
- 사용자의 포인트 잔액이 충분해야 함

#### 입력 파라미터
- `orderId` (String, required): 결제할 주문 ID
- `userId` (String, required): 사용자 ID

#### 정상 흐름
1. 시스템은 주문을 조회한다
2. 사용자를 조회한다
3. 주문의 사용자 ID와 요청 사용자 ID가 일치하는지 확인한다
4. 주문 상태가 `PENDING`인지 확인한다
5. 사용자의 포인트 잔액이 주문 금액 이상인지 확인한다
6. **트랜잭션 시작**
   - 사용자의 포인트를 차감한다 (`User.deduct()`)
   - 주문의 각 상품에 대해 재고를 차감한다 (`Product.decreaseStock()`)
   - 주문 상태를 `COMPLETED`로 변경한다 (`Order.complete()`)
   - 변경 사항을 모두 저장한다
7. **트랜잭션 커밋**
8. 외부 데이터 플랫폼으로 주문 정보를 전송한다 (현재는 "SUCCESS" Mock)
9. 결제 결과를 `PaymentResponse`로 반환한다

#### 비즈니스 규칙
- 결제와 재고 차감은 하나의 트랜잭션
- 재고 차감은 결제 완료 후
- 포인트 차감과 재고 차감은 동시 처리
- 결제 실패 시 모든 변경 사항 롤백
- 외부 전송 실패는 결제 완료를 막지 않음 (Week 5+에서 비동기 처리)

#### 예외 처리
| 에러 코드 | 발생 조건 | HTTP Status |
|----------|----------|-------------|
| `ORDER_NOT_FOUND` | 주문이 존재하지 않음 | 404 |
| `USER_NOT_FOUND` | 사용자가 존재하지 않음 | 404 |
| `INVALID_INPUT` | 주문 사용자와 결제 사용자 불일치 | 400 |
| `INVALID_ORDER_STATUS` | 주문 상태가 PENDING이 아님 | 400 |
| `INSUFFICIENT_BALANCE` | 포인트 잔액 부족 | 400 |
| `INSUFFICIENT_STOCK` | 재고 부족 (주문 생성 이후 재고 변동) | 400 |

#### 응답 예시
```json
{
  "orderId": "ORDER-abc12345",
  "paidAmount": 142400,
  "remainingBalance": 357600,
  "paymentStatus": "SUCCESS",
  "dataTransmission": "SUCCESS",
  "paidAt": "2025-11-11T15:05:00"
}
```

#### 트랜잭션 경계
```
@Transactional
processPayment() {
  1. 포인트 차감 (User.deduct)
  2. 재고 차감 (Product.decreaseStock)
  3. 주문 완료 (Order.complete)
  [트랜잭션 커밋]
  4. 외부 전송 (비동기, Week 5+)
}
```

#### 동시성 제어
- **포인트**: Pessimistic Lock (Week 5+)
- **재고**: Optimistic Lock (Week 5+)
- **충돌 시**: 재시도 로직 필요

#### 가용성 패턴 (Week 5+)
- **Async**: 외부 전송은 비동기
- **Fallback**: 외부 전송 실패 시 재시도 큐에 저장
- **Timeout**: 외부 API 호출 3초 제한

---

## 7. 주문 내역 조회

### 7.1 주문 내역 조회 (UC-015)

**구현 위치**: `OrderService.getOrders()`

#### 비즈니스 목표
고객이 자신의 주문 내역을 조회하여 주문 상태와 상세 정보를 확인할 수 있도록 한다.

#### 사전 조건
- 사용자 ID가 유효해야 함

#### 입력 파라미터
- `userId` (String, required)
- `status` (String, optional): 필터링할 주문 상태
  - `PENDING`: 결제 대기
  - `COMPLETED`: 결제 완료

#### 정상 흐름
1. 시스템은 사용자의 존재를 확인한다
2. `userId`로 모든 주문을 조회한다
3. `status` 파라미터가 있으면 해당 상태의 주문만 필터링한다
4. 각 주문에 대해:
   - 주문의 `OrderItem`들을 조회한다
   - 각 `OrderItem`에 대해 상품 정보를 조회한다
   - 주문 응답 객체를 생성한다
5. 주문 목록을 `OrderListResponse`로 반환한다

#### 비즈니스 규칙
- 최신 주문이 먼저 표시 (생성일 기준 내림차순)
- 주문이 없으면 빈 배열 반환
- 삭제된 상품은 "알 수 없음"으로 표시

#### 예외 처리
| 에러 코드 | 발생 조건 | HTTP Status |
|----------|----------|-------------|
| `USER_NOT_FOUND` | 사용자가 존재하지 않음 | 404 |
| `INVALID_INPUT` | 유효하지 않은 상태 값 | 400 |

#### 응답 예시
```json
{
  "orders": [
    {
      "orderId": "ORDER-abc12345",
      "userId": "USER-001",
      "items": [
        {
          "productId": "PROD-001",
          "productName": "무선 이어폰",
          "quantity": 2,
          "unitPrice": 89000,
          "subtotal": 178000
        }
      ],
      "subtotalAmount": 178000,
      "discountAmount": 35600,
      "totalAmount": 142400,
      "status": "COMPLETED",
      "createdAt": "2025-11-11T15:00:00"
    }
  ]
}
```

---

## 📊 UseCase 간 의존성 다이어그램

```
[상품 조회] → [장바구니 추가] → [주문 생성] → [결제 처리]
                                       ↑
                        [포인트 충전] ─┤
                        [쿠폰 발급] ───┘

[주문 내역 조회] ← [결제 처리]
```

---

## 🔄 트랜잭션 경계 요약

| UseCase | Service 메서드 | 트랜잭션 필요 | Week 4 적용 |
|---------|---------------|------------|------------|
| 상품 조회 | `getProducts()`, `getProduct()` | ❌ (읽기 전용) | `@Transactional(readOnly=true)` |
| 인기 상품 | `getTopProducts()` | ❌ (읽기 전용) | `@Transactional(readOnly=true)` |
| 장바구니 추가 | `addItemToCart()` | ✅ | `@Transactional` |
| 장바구니 수정 | `updateCartItem()` | ✅ | `@Transactional` |
| 포인트 충전 | `chargeBalance()` | ✅ | `@Transactional` |
| 쿠폰 발급 | `issueCoupon()` | ✅ | `@Transactional` |
| 주문 생성 | `createOrder()` | ✅ | `@Transactional` |
| **결제 처리** | **`processPayment()`** | ✅ **(Critical)** | `@Transactional` |
| 주문 조회 | `getOrders()` | ❌ (읽기 전용) | `@Transactional(readOnly=true)` |

---

## 🔒 동시성 제어 포인트 (Week 5+)

| 리소스 | 동시성 제어 전략 | 적용 UseCase |
|--------|----------------|-------------|
| **재고** | Optimistic Lock | 주문 생성, 결제 처리 |
| **쿠폰 수량** | Optimistic Lock | 쿠폰 발급 |
| **포인트** | Pessimistic Lock | 포인트 충전, 결제 처리 |

---

## 📝 Week 4 구현 체크리스트

### Service Layer (@Transactional 적용)
- [x] ProductService: 읽기 전용 메서드 `@Transactional(readOnly=true)`
- [x] CartService: 쓰기 메서드 `@Transactional`
- [x] UserService: 쓰기 메서드 `@Transactional`
- [x] CouponService: 쓰기 메서드 `@Transactional`
- [x] OrderService: 쓰기 메서드 `@Transactional`

### Domain Layer (비즈니스 로직 유지)
- [x] Product: `decreaseStock()`, `increaseStock()`
- [x] User: `charge()`, `deduct()`
- [x] Coupon: `tryIssue()`, `validateIssuable()`
- [x] Order: `complete()`

### Repository Layer (JPA 전환)
- [ ] In-Memory Repository → JpaRepository 전환
- [ ] 커스텀 쿼리 메서드 작성

---

## 🚀 향후 개선 사항 (Week 5+)

### 가용성 패턴
1. **Timeout & Retry**: 외부 API 호출 시 3초 Timeout, 3회 재시도
2. **Fallback**: 외부 전송 실패 시 재시도 큐에 저장
3. **Async**: 외부 전송 비동기 처리

### 성능 최적화
1. **인기 상품**: 실시간 쿼리 → 배치 + Redis 캐싱
2. **N+1 문제**: Fetch Join, @EntityGraph 적용
3. **인덱스**: `paidAt`, `status`, `userId` 등

### 동시성 제어
1. **재고**: Optimistic Lock + 재시도
2. **쿠폰**: Optimistic Lock + 재시도
3. **포인트**: Pessimistic Lock

---

## 참고 문서

- [요구사항 명세서](../api/requirements.md)
- [사용자 스토리](../api/user-stories.md)
- [API 명세서](../api/api-specification.md)
- [Layered Architecture 가이드](../../.claude/commands/architecture.md)
- [동시성 제어 패턴](../../.claude/commands/concurrency.md)

---

**작성자**: Claude AI
**최종 수정**: 2025-11-11
**버전**: 1.0 (Week 4 - JPA Integration)
