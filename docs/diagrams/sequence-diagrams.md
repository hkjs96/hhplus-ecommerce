# 시퀀스 다이어그램

이커머스 시스템의 API별 시퀀스 다이어그램입니다. 인프라 구성요소(MySQL, External API)를 명확히 구분하여 표현합니다.

---

## 목차

### REST API
1. [GET /api/products/top - 인기 상품 조회](#1-get-apiproductstop---인기-상품-조회)
2. [POST /api/cart/items - 장바구니 추가](#2-post-apicartitems---장바구니-추가)
3. [GET /api/cart - 장바구니 조회](#3-get-apicart---장바구니-조회)
4. [POST /api/orders - 주문 생성](#4-post-apiorders---주문-생성)
5. [POST /api/orders/{orderId}/payment - 결제 처리](#5-post-apiordersorderidpayment---결제-처리)
6. [POST /api/users/{userId}/balance/charge - 포인트 충전](#6-post-apiusersuseridbalancecharge---포인트-충전)
7. [POST /api/coupons/{couponId}/issue - 쿠폰 발급](#7-post-apicouponscouponidissue---쿠폰-발급)

### 배치 & 백그라운드
8. [외부 API 재시도 워커](#8-외부-api-재시도-워커)

---

## 인프라 구성

| 구성요소 | 역할 | 기술 |
|---------|------|------|
| **MySQL** | 트랜잭션 데이터 저장 | products, stock, stock_history, orders, order_items, carts, cart_items, users, coupons, user_coupons, outbox |
| **External Data Platform** | 외부 데이터 수신 | 주문 데이터 전송 API |

---

## 1. GET /api/products/top - 인기 상품 조회

### 설명
최근 3일간 판매량 기준 Top 5 상품을 **실시간 쿼리**로 조회합니다. 조회 실패 시 빈 배열을 반환합니다.

**인프라:**
- MySQL (products, order_items, orders 테이블 - 읽기 전용)

> **설계 결정**: 초기에는 실시간 쿼리로 단순하게 구현하고, 성능 이슈 발생 시 배치/캐시로 개선 (Week 2 피드백 반영)

```mermaid
sequenceDiagram
    actor Client
    participant API as ProductController
    participant UseCase as ProductUseCase
    participant Service as ProductService
    participant MySQL as MySQL Database

    Client->>API: GET /api/products/top

    API->>UseCase: 인기 상품 Top 5 조회 요청

    Note over UseCase,MySQL: 최근 3일 판매 데이터 집계

    UseCase->>Service: 인기 상품 데이터 조회 (3일, 5개)

    Service->>MySQL: 완료된 주문의 판매량 집계<br/>(products, order_items, orders JOIN)<br/>최근 3일, 판매량 기준 상위 5개

    alt 쿼리 성공
        MySQL-->>Service: 집계 결과 반환<br/>(상품ID, 이름, 판매수량, 매출액)

        Service->>Service: DTO 변환 작업

        Service-->>UseCase: 인기 상품 목록 반환

        UseCase-->>API: TopProductsResponse<br/>{<br/>  period: "3days",<br/>  products: [...]<br/>}

        API-->>Client: 200 OK<br/>{<br/>  "period": "3days",<br/>  "products": [<br/>    {<br/>      "rank": 1,<br/>      "productId": "P001",<br/>      "name": "상품A",<br/>      "salesCount": 150,<br/>      "revenue": 1500000<br/>    },<br/>    ...<br/>  ]<br/>}

    else 쿼리 실패 (DB 오류, Timeout)
        MySQL-->>Service: SQLException /<br/>Timeout Exception

        Service->>Service: Log error<br/>"[ERROR] Failed to query popular products"

        Note over Service,UseCase: Fallback: 빈 배열 반환

        Service-->>UseCase: Empty List

        UseCase-->>API: TopProductsResponse<br/>{<br/>  period: "3days",<br/>  products: []<br/>}

        API-->>Client: 200 OK<br/>{<br/>  "period": "3days",<br/>  "products": []<br/>}
    end
```

---

## 2. POST /api/cart/items - 장바구니 추가

### 설명
장바구니에 상품을 추가합니다. 재고 확인 후 **MySQL**에 저장합니다.

**인프라:**
- MySQL (stock, carts, cart_items 테이블)

```mermaid
sequenceDiagram
    actor Client
    participant API as CartController
    participant Service as CartService
    participant StockService as StockService
    participant MySQL as MySQL Database

    Client->>API: POST /api/cart/items<br/>{<br/>  "userId": "U001",<br/>  "productId": "P001",<br/>  "quantity": 2<br/>}

    API->>Service: 장바구니에 상품 추가 요청

    Note over Service,MySQL: 1️⃣ 재고 확인

    Service->>StockService: 재고 수량 확인 요청
    StockService->>MySQL: 상품 재고 수량 조회
    MySQL-->>StockService: 재고 수량 반환

    alt 재고 부족
        StockService-->>Service: 재고 부족 예외 발생
        Service-->>API: 400 Bad Request
        API-->>Client: {<br/>  "error": "INSUFFICIENT_STOCK",<br/>  "message": "재고가 부족합니다"<br/>}
    end

    StockService-->>Service: ✅ 재고 충분

    Note over Service,MySQL: 2️⃣ 장바구니 조회/생성

    Service->>MySQL: 사용자 장바구니 조회

    alt 장바구니 없음
        MySQL-->>Service: 장바구니 없음
        Service->>MySQL: 새 장바구니 생성
        MySQL-->>Service: 장바구니 생성 완료
    else 장바구니 존재
        MySQL-->>Service: 장바구니 반환
    end

    Note over Service,MySQL: 3️⃣ 상품 추가/수량 증가

    Service->>MySQL: 장바구니 내 해당 상품 조회

    alt 이미 담긴 상품
        MySQL-->>Service: 기존 장바구니 항목 반환
        Service->>MySQL: 기존 수량에 추가 (수량 증가)
        MySQL-->>Service: ✅ 수량 업데이트 완료
    else 새 상품
        MySQL-->>Service: 항목 없음
        Service->>MySQL: 새 장바구니 항목 생성
        MySQL-->>Service: ✅ 항목 생성 완료
    end

    Service-->>API: 장바구니 항목 정보 반환
    API-->>Client: 201 Created<br/>{<br/>  "cartItemId": "CI001",<br/>  "productId": "P001",<br/>  "quantity": 5,<br/>  "addedAt": "2024-01-15T10:00:00Z"<br/>}
```

---

## 3. GET /api/cart - 장바구니 조회

### 설명
사용자의 장바구니 내용을 조회합니다. **MySQL**에서 장바구니, 상품, 재고 정보를 JOIN하여 반환합니다.

**인프라:**
- MySQL (carts, cart_items, products, stock 테이블)

```mermaid
sequenceDiagram
    actor Client
    participant API as CartController
    participant Service as CartService
    participant MySQL as MySQL Database

    Client->>API: GET /api/cart?userId=U001

    API->>Service: 장바구니 조회 요청

    Service->>MySQL: 장바구니 항목과 상품/재고 정보 조회<br/>(carts, cart_items, products, stock JOIN)

    MySQL-->>Service: 장바구니 항목 목록 반환<br/>(상품 정보, 재고 수량 포함)

    Service->>Service: 소계 금액 계산<br/>재고 가용 여부 확인

    Service-->>API: 장바구니 정보 반환
    API-->>Client: 200 OK<br/>{<br/>  "userId": "U001",<br/>  "items": [<br/>    {<br/>      "cartItemId": "CI001",<br/>      "productId": "P001",<br/>      "name": "상품A",<br/>      "unitPrice": 10000,<br/>      "quantity": 5,<br/>      "subtotal": 50000,<br/>      "stockAvailable": true,<br/>      "currentStock": 50<br/>    },<br/>    {<br/>      "cartItemId": "CI002",<br/>      "productId": "P002",<br/>      "name": "상품B",<br/>      "unitPrice": 20000,<br/>      "quantity": 10,<br/>      "subtotal": 200000,<br/>      "stockAvailable": false,<br/>      "currentStock": 5<br/>    }<br/>  ],<br/>  "totalAmount": 250000<br/>}
```

---

## 4. POST /api/orders - 주문 생성

### 설명
장바구니 상품들을 주문으로 변환합니다. **MySQL**에서 재고 확인 및 쿠폰 검증 후 주문을 생성합니다.

**인프라:**
- MySQL (carts, cart_items, stock, user_coupons, orders, order_items 테이블)

```mermaid
sequenceDiagram
    actor Client
    participant API as OrderController
    participant UseCase as OrderUseCase
    participant CartService as CartService
    participant StockService as StockService
    participant CouponService as CouponService
    participant OrderService as OrderService
    participant MySQL as MySQL Database

    Client->>API: POST /api/orders<br/>{<br/>  "userId": "U001",<br/>  "couponId": "C001"<br/>}

    API->>UseCase: 주문 생성 요청

    Note over UseCase,MySQL: 1️⃣ 장바구니 조회

    UseCase->>CartService: 장바구니 항목 조회 요청
    CartService->>MySQL: 사용자 장바구니 항목 조회
    MySQL-->>CartService: 장바구니 항목 목록 반환
    CartService-->>UseCase: 장바구니 항목 목록 반환

    alt 장바구니 비어있음
        UseCase-->>API: 400 Bad Request
        API-->>Client: {<br/>  "error": "EMPTY_CART"<br/>}
    end

    Note over UseCase,MySQL: 2️⃣ 재고 검증

    loop 각 상품별
        UseCase->>StockService: 재고 수량 검증 요청
        StockService->>MySQL: 상품 재고 수량 조회
        MySQL-->>StockService: 재고 수량 반환

        alt 재고 부족
            StockService-->>UseCase: 재고 부족 예외 발생
            UseCase-->>API: 400 Bad Request
            API-->>Client: {<br/>  "error": "INSUFFICIENT_STOCK",<br/>  "productId": "P001"<br/>}
        end

        StockService-->>UseCase: ✅ 재고 충분
    end

    Note over UseCase,MySQL: 3️⃣ 쿠폰 검증 (선택)

    opt 쿠폰 적용
        UseCase->>CouponService: 쿠폰 유효성 검증 요청
        CouponService->>MySQL: 사용 가능한 쿠폰 조회<br/>(사용자ID, 쿠폰ID, 상태로 필터)
        MySQL-->>CouponService: 쿠폰 정보 반환

        alt 쿠폰 사용 불가
            CouponService-->>UseCase: 유효하지 않은 쿠폰 예외 발생
            UseCase-->>API: 400 Bad Request
            API-->>Client: {<br/>  "error": "INVALID_COUPON"<br/>}
        end

        CouponService-->>UseCase: ✅ 쿠폰 유효 (할인율: 10%)
    end

    Note over UseCase,MySQL: 4️⃣ 주문 생성

    UseCase->>OrderService: 주문 생성 요청

    OrderService->>MySQL: 주문 레코드 생성<br/>(상태: PENDING, 금액 정보 포함)
    MySQL-->>OrderService: 주문 생성 완료

    OrderService->>MySQL: 주문 항목 레코드 생성<br/>(상품별 수량, 단가, 소계)
    MySQL-->>OrderService: 주문 항목 생성 완료

    OrderService-->>UseCase: 주문 정보 반환 (상태=PENDING)
    UseCase-->>API: 주문 응답 DTO 반환

    API-->>Client: 201 Created<br/>{<br/>  "orderId": "ORD-20240115-001",<br/>  "items": [<br/>    {<br/>      "productId": "P001",<br/>      "name": "상품A",<br/>      "quantity": 5,<br/>      "unitPrice": 10000,<br/>      "subtotal": 50000<br/>    }<br/>  ],<br/>  "subtotalAmount": 250000,<br/>  "discountAmount": 25000,<br/>  "totalAmount": 225000,<br/>  "status": "PENDING"<br/>}
```

---

## 5. POST /api/orders/{orderId}/payment - 결제 처리

### 설명
주문에 대한 결제를 처리합니다. **MySQL**에서 포인트 차감(Pessimistic Lock), 재고 차감(Optimistic Lock), 재고 이력 기록을 수행하고, **External API**로 비동기 전송합니다.

**인프라:**
- MySQL (users, stock, stock_history, orders, user_coupons, outbox 테이블)
- External Data Platform API (비동기 호출)

```mermaid
sequenceDiagram
    actor Client
    participant API as OrderController
    participant PaymentUC as PaymentUseCase
    participant OrderService as OrderService
    participant UserService as UserService
    participant StockService as StockService
    participant CouponService as CouponService
    participant MySQL as MySQL Database
    participant ExtClient as DataPlatformClient (@Async)
    participant ExtAPI as External Data Platform

    Client->>API: POST /api/orders/ORD-20240115-001/payment<br/>{<br/>  "userId": "U001"<br/>}

    API->>PaymentUC: 결제 처리 요청

    Note over PaymentUC,MySQL: 1️⃣ 주문 조회

    PaymentUC->>OrderService: 주문 정보 조회 요청
    OrderService->>MySQL: 주문 레코드 조회
    MySQL-->>OrderService: 주문 정보 반환 (상태=PENDING, 총액=225000)

    alt 주문 없음 또는 이미 결제됨
        OrderService-->>PaymentUC: 주문 예외 발생
        PaymentUC-->>API: 404/400
        API-->>Client: {<br/>  "error": "ORDER_NOT_FOUND"<br/>}
    end

    OrderService-->>PaymentUC: 주문 정보 반환

    Note over PaymentUC,MySQL: 2️⃣ 포인트 차감 (Pessimistic Lock)

    PaymentUC->>UserService: 포인트 차감 요청
    UserService->>MySQL: BEGIN TRANSACTION
    UserService->>MySQL: 사용자 정보 조회 및 락 획득<br/>(FOR UPDATE)
    MySQL-->>UserService: 사용자 정보 반환 (잔액=500000) **LOCKED**

    alt 잔액 부족
        UserService->>MySQL: ROLLBACK
        UserService-->>PaymentUC: 잔액 부족 예외 발생
        PaymentUC-->>API: 400 Bad Request
        API-->>Client: {<br/>  "error": "INSUFFICIENT_BALANCE",<br/>  "currentBalance": 100000,<br/>  "required": 225000<br/>}
    end

    UserService->>MySQL: 사용자 잔액 업데이트<br/>(잔액 차감, 업데이트 시각 기록)
    MySQL-->>UserService: ✅ 잔액 업데이트 완료
    UserService->>MySQL: COMMIT
    UserService-->>PaymentUC: ✅ 결제 성공

    Note over PaymentUC,MySQL: 3️⃣ 재고 차감 (Optimistic Lock)

    PaymentUC->>StockService: 재고 차감 요청

    loop 각 상품별
        StockService->>MySQL: 재고 정보 조회<br/>(수량, 버전 확인)
        MySQL-->>StockService: 재고 정보 반환 (수량=50, 버전=10)

        StockService->>MySQL: BEGIN TRANSACTION
        StockService->>MySQL: 재고 수량 차감 및 버전 증가<br/>(Optimistic Lock: 버전 일치 조건,<br/>재고 충분 조건 확인)

        alt Version mismatch 또는 재고 부족
            MySQL-->>StockService: 영향받은 행 없음 (0 rows)
            StockService->>MySQL: ROLLBACK

            Note over StockService: ⚠️ 재고 차감 실패 → 포인트 복구 필요

            StockService-->>PaymentUC: 재고 차감 예외 발생
            PaymentUC->>UserService: 포인트 환불 요청
            UserService->>MySQL: 사용자 잔액 복구
            MySQL-->>UserService: ✅ 잔액 복구 완료
            PaymentUC-->>API: 409 Conflict
            API-->>Client: {<br/>  "error": "STOCK_DEDUCTION_FAILED",<br/>  "message": "재고 차감 실패, 다시 시도해주세요"<br/>}
        end

        MySQL-->>StockService: ✅ 재고 업데이트 완료 (1 row)

        Note over StockService,MySQL: 재고 이력 기록

        StockService->>MySQL: 재고 이력 레코드 생성<br/>(유형: OUT, 변경 전/후 수량,<br/>참조 정보: ORDER)
        MySQL-->>StockService: ✅ 이력 기록 완료

        StockService->>MySQL: COMMIT
    end

    StockService-->>PaymentUC: ✅ 재고 차감 완료

    Note over PaymentUC,MySQL: 4️⃣ 쿠폰 사용 처리 (선택)

    opt 쿠폰 사용
        PaymentUC->>CouponService: 쿠폰 사용 처리 요청
        CouponService->>MySQL: 쿠폰 상태 업데이트<br/>(상태: USED, 사용 시각 기록)
        MySQL-->>CouponService: ✅ 쿠폰 사용 처리 완료
    end

    Note over PaymentUC,MySQL: 5️⃣ 주문 상태 업데이트

    PaymentUC->>OrderService: 주문 결제 완료 처리 요청
    OrderService->>MySQL: 주문 상태 업데이트<br/>(상태: COMPLETED, 결제 시각 기록)
    MySQL-->>OrderService: ✅ 주문 완료 처리 완료

    Note over PaymentUC,ExtAPI: 6️⃣ 외부 데이터 전송 (비동기, Non-blocking)

    PaymentUC->>ExtClient: 주문 데이터 전송 요청 [@Async]
    Note over ExtClient: 즉시 반환 (Non-blocking)
    ExtClient-->>PaymentUC: ✅ 큐에 등록됨

    PaymentUC-->>API: 결제 응답 DTO 반환
    API-->>Client: 200 OK<br/>{<br/>  "orderId": "ORD-20240115-001",<br/>  "paidAmount": 225000,<br/>  "remainingBalance": 275000,<br/>  "status": "SUCCESS",<br/>  "paidAt": "2024-01-15T10:05:00Z"<br/>}

    Note over ExtClient,ExtAPI: 백그라운드 처리

    ExtClient->>ExtClient: 주문 데이터 변환 작업<br/>(내부 형식 → 외부 형식)
    ExtClient->>ExtAPI: 외부 API 호출<br/>(Timeout: 3초)

    alt 전송 성공
        ExtAPI-->>ExtClient: 200 OK
        ExtClient->>ExtClient: 성공 로그 기록
    else 전송 실패 (Timeout 또는 Error)
        ExtAPI-->>ExtClient: Timeout / 4xx / 5xx
        ExtClient->>MySQL: Outbox 테이블에 재시도 큐 저장<br/>(메시지, 상태: PENDING,<br/>재시도 횟수: 0, 다음 재시도 시각)
        MySQL-->>ExtClient: ✅ 재시도 큐 저장 완료
        ExtClient->>ExtClient: 실패 로그 기록
    end
```

---

## 6. POST /api/users/{userId}/balance/charge - 포인트 충전

### 설명
사용자 포인트를 충전합니다. **MySQL**에서 Pessimistic Lock으로 정확성을 보장합니다.

**인프라:**
- MySQL (users 테이블)

```mermaid
sequenceDiagram
    actor Client
    participant API as UserController
    participant UseCase as UserUseCase
    participant Service as UserService
    participant MySQL as MySQL Database

    Client->>API: POST /api/users/U001/balance/charge<br/>{<br/>  "amount": 100000<br/>}

    API->>UseCase: 포인트 충전 요청

    alt 유효하지 않은 금액 (음수 또는 0)
        UseCase-->>API: 400 Bad Request
        API-->>Client: {<br/>  "error": "INVALID_AMOUNT",<br/>  "message": "충전 금액은 양수여야 합니다"<br/>}
    end

    UseCase->>Service: 포인트 추가 요청

    Note over Service,MySQL: Pessimistic Lock

    Service->>MySQL: BEGIN TRANSACTION
    Service->>MySQL: 사용자 정보 조회 및 락 획득<br/>(FOR UPDATE)
    MySQL-->>Service: 사용자 정보 반환 (잔액=500000) **LOCKED**

    alt 사용자 없음
        Service->>MySQL: ROLLBACK
        Service-->>UseCase: 사용자 없음 예외 발생
        UseCase-->>API: 404 Not Found
        API-->>Client: {<br/>  "error": "USER_NOT_FOUND"<br/>}
    end

    Service->>MySQL: 사용자 잔액 업데이트<br/>(잔액 증가, 업데이트 시각 기록)
    MySQL-->>Service: ✅ 잔액 업데이트 완료

    Service->>MySQL: COMMIT

    Service-->>UseCase: ✅ 충전 완료 (신규 잔액=600000)
    UseCase-->>API: 충전 응답 DTO 반환

    API-->>Client: 200 OK<br/>{<br/>  "userId": "U001",<br/>  "balance": 600000,<br/>  "chargedAmount": 100000,<br/>  "chargedAt": "2024-01-15T10:00:00Z"<br/>}
```

---

## 7. POST /api/coupons/{couponId}/issue - 쿠폰 발급

### 설명
선착순 쿠폰을 발급합니다. **MySQL**에서 Optimistic Lock으로 동시성을 제어하고, Unique 제약으로 1인 1매를 보장합니다.

**인프라:**
- MySQL (coupons, user_coupons 테이블)

```mermaid
sequenceDiagram
    actor Client
    participant API as CouponController
    participant UseCase as CouponUseCase
    participant Service as CouponService
    participant MySQL as MySQL Database

    Client->>API: POST /api/coupons/C001/issue<br/>{<br/>  "userId": "U001"<br/>}

    API->>UseCase: 쿠폰 발급 요청

    Note over UseCase,MySQL: 1️⃣ 쿠폰 조회

    UseCase->>Service: 쿠폰 정보 조회 요청
    Service->>MySQL: 쿠폰 레코드 조회
    MySQL-->>Service: 쿠폰 정보 반환<br/>(총 수량=100, 발급 수량=95, 버전=50)

    alt 쿠폰 없음
        Service-->>UseCase: 쿠폰 없음 예외 발생
        UseCase-->>API: 404 Not Found
        API-->>Client: {<br/>  "error": "COUPON_NOT_FOUND"<br/>}
    end

    alt 수량 소진 (발급 수량 >= 총 수량)
        Service-->>UseCase: 쿠폰 소진 예외 발생
        UseCase-->>API: 409 Conflict
        API-->>Client: {<br/>  "error": "COUPON_SOLD_OUT",<br/>  "message": "쿠폰이 모두 소진되었습니다"<br/>}
    end

    Service-->>UseCase: 쿠폰 정보 반환

    Note over UseCase,MySQL: 2️⃣ 중복 발급 체크

    UseCase->>Service: 중복 발급 확인 요청
    Service->>MySQL: 사용자 쿠폰 개수 조회<br/>(사용자ID, 쿠폰ID로 필터)
    MySQL-->>Service: 개수 반환 (0)

    alt 이미 발급받음 (개수 > 0)
        Service-->>UseCase: 이미 발급됨 예외 발생
        UseCase-->>API: 409 Conflict
        API-->>Client: {<br/>  "error": "ALREADY_ISSUED",<br/>  "message": "이미 발급받은 쿠폰입니다"<br/>}
    end

    Service-->>UseCase: ✅ 중복 없음

    Note over UseCase,MySQL: 3️⃣ 쿠폰 발급 (Optimistic Lock)

    UseCase->>Service: 사용자에게 발급 요청

    Service->>MySQL: BEGIN TRANSACTION

    Service->>MySQL: 쿠폰 발급 수량 증가 및 버전 증가<br/>(Optimistic Lock: 버전 일치 조건,<br/>수량 소진되지 않음 조건 확인)

    alt Version mismatch (동시 발급 충돌)
        MySQL-->>Service: 영향받은 행 없음 (0 rows)
        Service->>MySQL: ROLLBACK
        Service-->>UseCase: Optimistic Lock 예외 발생
        UseCase-->>API: 409 Conflict
        API-->>Client: {<br/>  "error": "COUPON_ISSUE_FAILED",<br/>  "message": "동시 요청으로 인해 실패했습니다. 다시 시도해주세요"<br/>}
    end

    MySQL-->>Service: ✅ 쿠폰 업데이트 완료 (1 row)

    Note over Service,MySQL: 사용자 쿠폰 생성 (Unique 제약)

    Service->>MySQL: 사용자 쿠폰 레코드 생성<br/>(상태: AVAILABLE, 발급/만료 시각)

    alt Unique 제약 위반 (1인 1매)
        MySQL-->>Service: 중복 키 예외 발생
        Note over Service: ⚠️ 발급 수량 롤백 필요
        Service->>MySQL: 쿠폰 발급 수량 복구 및 버전 재증가
        Service->>MySQL: ROLLBACK
        Service-->>UseCase: 이미 발급됨 예외 발생
        UseCase-->>API: 409 Conflict
        API-->>Client: {<br/>  "error": "ALREADY_ISSUED"<br/>}
    end

    MySQL-->>Service: ✅ 사용자 쿠폰 생성 완료

    Service->>MySQL: COMMIT

    Note over Service,MySQL: 남은 수량 조회

    Service->>MySQL: 쿠폰 발급 통계 조회<br/>(발급 수량, 총 수량)
    MySQL-->>Service: 통계 반환 (발급=96, 총=100)

    Service-->>UseCase: ✅ 쿠폰 발급 완료<br/>(사용자 쿠폰ID=UC001, 남은수량=4)
    UseCase-->>API: 발급 응답 DTO 반환

    API-->>Client: 201 Created<br/>{<br/>  "userCouponId": "UC001",<br/>  "couponName": "10% 할인 쿠폰",<br/>  "discountRate": 10,<br/>  "expiresAt": "2024-02-14T23:59:59Z",<br/>  "remainingQuantity": 4<br/>}
```

---

## 8. 외부 API 재시도 워커

### 설명
1분마다 실행되는 백그라운드 워커가 **MySQL outbox** 테이블에서 실패한 외부 API 전송 건을 조회하여 **External API**로 재전송합니다.

**인프라:**
- MySQL (outbox 테이블)
- External Data Platform API

```mermaid
sequenceDiagram
    participant Worker as @Scheduled<br/>RetryWorker
    participant Service as OutboxService
    participant MySQL as MySQL Database
    participant ExtAPI as External Data Platform

    Note over Worker: 1분마다 실행 (fixedDelay: 60000ms)

    Worker->>Service: 재시도 큐 처리 요청

    Note over Service,MySQL: Pending 메시지 조회

    Service->>MySQL: 재시도 대상 메시지 조회<br/>(상태: PENDING, 재시도 시각 도래,<br/>재시도 횟수 3회 미만,<br/>생성 시각 순, 최대 100건)
    MySQL-->>Service: Outbox 메시지 목록 반환

    alt 메시지 없음
        Service-->>Worker: 처리할 메시지 없음
        Note over Worker: 작업 종료
    end

    loop 각 메시지 처리
        Service->>Service: 메시지 데이터 파싱

        Note over Service,ExtAPI: 재전송 시도

        Service->>ExtAPI: 외부 API 호출<br/>(메시지 내용 전송, Timeout: 3초)

        alt 전송 성공
            ExtAPI-->>Service: 200 OK

            Note over Service,MySQL: 상태를 SENT로 변경

            Service->>MySQL: Outbox 메시지 상태 업데이트<br/>(상태: SENT, 전송 시각 기록)
            MySQL-->>Service: ✅ 업데이트 완료

            Service->>Service: 성공 로그 기록

        else 전송 실패 (Timeout / 4xx / 5xx)
            ExtAPI-->>Service: Timeout / Error

            Service->>Service: 재시도 횟수 증가

            alt 재시도 횟수 < 3
                Note over Service: 다음 재시도 시간 계산<br/>재시도 0회 → 1분 후<br/>재시도 1회 → 5분 후<br/>재시도 2회 → 30분 후

                Service->>MySQL: Outbox 메시지 재시도 정보 업데이트<br/>(재시도 횟수 증가,<br/>다음 재시도 시각 설정,<br/>에러 메시지 저장)
                MySQL-->>Service: ✅ 업데이트 완료

                Service->>Service: 재시도 로그 기록

            else 재시도 횟수 >= 3
                Note over Service: 최대 재시도 횟수 초과

                Service->>MySQL: Outbox 메시지 상태 업데이트<br/>(상태: FAILED,<br/>실패 시각 기록,<br/>에러 메시지 저장)
                MySQL-->>Service: ✅ 업데이트 완료

                Service->>Service: 영구 실패 로그 기록

                Service->>Service: 모니터링 알림 전송<br/>(Slack, PagerDuty 등)
            end
        end
    end

    Service-->>Worker: ✅ 재시도 배치 완료<br/>(처리: 10, 성공: 7, 실패: 3)
```

---

## API 요약

| API 엔드포인트 | HTTP Method | 인프라 사용 | 주요 기술 |
|---------------|-------------|------------|---------|
| `/api/products/top` | GET | MySQL (products, order_items, orders 읽기) | Real-time Query, Fallback |
| `/api/cart/items` | POST | MySQL (stock, carts, cart_items) | Stock Validation |
| `/api/cart` | GET | MySQL (JOIN 쿼리) | Read-only |
| `/api/orders` | POST | MySQL (stock, orders, order_items) | Stock Validation, Coupon Validation |
| `/api/orders/{orderId}/payment` | POST | MySQL (users, stock, stock_history, orders) + External API | Pessimistic Lock (포인트), Optimistic Lock (재고), Async (외부 전송) |
| `/api/users/{userId}/balance/charge` | POST | MySQL (users) | Pessimistic Lock |
| `/api/coupons/{couponId}/issue` | POST | MySQL (coupons, user_coupons) | Optimistic Lock, Unique Constraint |

---

## 배치/백그라운드 프로세스 요약

| 프로세스 | 실행 주기 | 인프라 사용 | 주요 기술 |
|---------|---------|------------|---------|
| 외부 API 재시도 워커 | 1분마다 | MySQL (outbox) → External API | Retry Pattern, Exponential Backoff |

---

## 인프라 연동 패턴

### MySQL
- **트랜잭션**: BEGIN → (쿼리) → COMMIT/ROLLBACK
- **Pessimistic Lock**: `SELECT ... FOR UPDATE` (포인트 충전, 포인트 차감)
- **Optimistic Lock**: `WHERE version = ?` (재고 차감, 쿠폰 발급)
- **Unique Constraint**: 1인 1매 쿠폰 보장


### External Data Platform API
- **동기 호출**: `POST /api/orders` (Timeout: 3초)
- **비동기 호출**: `@Async` (Non-blocking)
- **재시도**: 1분 → 5분 → 30분 (최대 3회)
- **Fallback**: Outbox 패턴 (MySQL outbox 테이블)

---

## 다이어그램 활용 방법

### Mermaid Live Editor
1. https://mermaid.live 접속
2. 위의 mermaid 코드 복사
3. 에디터에 붙여넣기
4. PNG/SVG로 내보내기

### VS Code
- Mermaid Preview 확장 설치
- Markdown 파일에서 미리보기

### GitHub/GitLab
- README.md에 mermaid 코드 블록 포함
- 자동으로 렌더링됨

---

## 관련 문서
- [ERD](./erd.md)
- [데이터 모델](../api/data-models.md)
- [API 명세서](../api/api-specification.md)
- [요구사항 명세서](../api/requirements.md)
