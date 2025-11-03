# 플로우차트 (Flowcharts)

이커머스 시스템의 핵심 비즈니스 로직 흐름을 flowchart로 표현합니다.

---

## 목차

1. [주문 생성 플로우](#1-주문-생성-플로우)
2. [결제 처리 플로우](#2-결제-처리-플로우)
3. [쿠폰 발급 플로우 (선착순)](#3-쿠폰-발급-플로우-선착순)
4. [재고 차감 플로우](#4-재고-차감-플로우)
5. [장바구니에서 주문 전환 플로우](#5-장바구니에서-주문-전환-플로우)

---

## 1. 주문 생성 플로우

### 설명
사용자가 상품을 주문할 때의 비즈니스 로직 흐름입니다.

```mermaid
flowchart TD
    Start([주문 생성 시작]) --> ValidateInput{입력 검증}

    ValidateInput -->|유효하지 않음| ReturnBadRequest[400 Bad Request 반환]
    ValidateInput -->|유효함| CheckUser{사용자 존재?}

    CheckUser -->|없음| ReturnUserNotFound[404 사용자 없음]
    CheckUser -->|있음| CheckProducts{모든 상품 존재?}

    CheckProducts -->|없음| ReturnProductNotFound[404 상품 없음]
    CheckProducts -->|있음| CheckStock{재고 충분?}

    CheckStock -->|부족| ReturnInsufficientStock[400 재고 부족]
    CheckStock -->|충분| CheckCoupon{쿠폰 사용?}

    CheckCoupon -->|No| CalculateAmount[금액 계산<br/>subtotal, discount=0, total]
    CheckCoupon -->|Yes| ValidateCoupon{쿠폰 유효?}

    ValidateCoupon -->|만료/사용됨| ReturnInvalidCoupon[400 유효하지 않은 쿠폰]
    ValidateCoupon -->|유효| ApplyDiscount[할인 적용<br/>discount 계산]

    ApplyDiscount --> CalculateAmount
    CalculateAmount --> CreateOrder[주문 생성<br/>status: PENDING]

    CreateOrder --> CreateOrderItems[주문 항목 생성<br/>가격 스냅샷]
    CreateOrderItems --> ReturnSuccess[201 Created<br/>주문 ID 반환]

    ReturnSuccess --> End([종료])
    ReturnBadRequest --> End
    ReturnUserNotFound --> End
    ReturnProductNotFound --> End
    ReturnInsufficientStock --> End
    ReturnInvalidCoupon --> End

    style Start fill:#e1f5e1
    style End fill:#ffe1e1
    style ReturnSuccess fill:#d4edda
    style ReturnBadRequest fill:#f8d7da
    style ReturnUserNotFound fill:#f8d7da
    style ReturnProductNotFound fill:#f8d7da
    style ReturnInsufficientStock fill:#f8d7da
    style ReturnInvalidCoupon fill:#f8d7da
```

---

## 2. 결제 처리 플로우

### 설명
주문에 대한 결제를 처리하는 흐름입니다. 포인트 차감, 재고 차감, 외부 데이터 전송이 포함됩니다.

```mermaid
flowchart TD
    Start([결제 시작]) --> GetOrder{주문 존재?}

    GetOrder -->|없음| ReturnOrderNotFound[404 주문 없음]
    GetOrder -->|있음| CheckOrderStatus{주문 상태가<br/>PENDING?}

    CheckOrderStatus -->|아님| ReturnInvalidStatus[400 이미 처리됨]
    CheckOrderStatus -->|PENDING| StartTransaction[트랜잭션 시작]

    StartTransaction --> LockUser[사용자 포인트<br/>Pessimistic Lock]
    LockUser --> CheckBalance{잔액 충분?}

    CheckBalance -->|부족| Rollback1[트랜잭션 롤백]
    Rollback1 --> ReturnInsufficientBalance[400 잔액 부족]

    CheckBalance -->|충분| DeductBalance[포인트 차감<br/>balance -= total]
    DeductBalance --> DeductStock[재고 차감<br/>Optimistic Lock]

    DeductStock --> StockSuccess{재고 차감 성공?}

    StockSuccess -->|실패<br/>version mismatch| Rollback2[트랜잭션 롤백<br/>포인트 복구]
    Rollback2 --> ReturnStockFailed[409 재고 차감 실패]

    StockSuccess -->|성공| RecordStockHistory[재고 이력 기록<br/>type: OUT]
    RecordStockHistory --> MarkCouponUsed{쿠폰 사용?}

    MarkCouponUsed -->|Yes| UpdateCoupon[쿠폰 상태<br/>USED로 변경]
    MarkCouponUsed -->|No| UpdateOrderStatus
    UpdateCoupon --> UpdateOrderStatus

    UpdateOrderStatus[주문 상태<br/>COMPLETED로 변경]
    UpdateOrderStatus --> CommitTransaction[트랜잭션 커밋]

    CommitTransaction --> SendAsync[외부 데이터 전송<br/>비동기 @Async]
    SendAsync --> ReturnSuccess[200 OK<br/>결제 완료]

    ReturnSuccess --> End([종료])
    ReturnOrderNotFound --> End
    ReturnInvalidStatus --> End
    ReturnInsufficientBalance --> End
    ReturnStockFailed --> End

    SendAsync -.->|백그라운드| TrySend{전송 성공?}
    TrySend -->|성공| LogSuccess[성공 로그]
    TrySend -->|실패| SaveOutbox[Outbox 테이블에<br/>재시도 큐 저장]

    style Start fill:#e1f5e1
    style End fill:#ffe1e1
    style ReturnSuccess fill:#d4edda
    style CommitTransaction fill:#d4edda
    style Rollback1 fill:#fff3cd
    style Rollback2 fill:#fff3cd
    style ReturnOrderNotFound fill:#f8d7da
    style ReturnInvalidStatus fill:#f8d7da
    style ReturnInsufficientBalance fill:#f8d7da
    style ReturnStockFailed fill:#f8d7da
```

---

## 3. 쿠폰 발급 플로우 (선착순)

### 설명
선착순 쿠폰 발급 시 동시성을 제어하고 1인 1매를 보장하는 흐름입니다.

```mermaid
flowchart TD
    Start([쿠폰 발급 시작]) --> GetCoupon{쿠폰 존재?}

    GetCoupon -->|없음| ReturnCouponNotFound[404 쿠폰 없음]
    GetCoupon -->|있음| CheckExpiry{만료되지 않음?}

    CheckExpiry -->|만료됨| ReturnExpired[400 만료된 쿠폰]
    CheckExpiry -->|유효| CheckDuplicate{이미 발급?<br/>DB Unique Check}

    CheckDuplicate -->|Yes| ReturnAlreadyIssued[400 이미 발급받음<br/>1인 1매]
    CheckDuplicate -->|No| StartTransaction[트랜잭션 시작]

    StartTransaction --> ReadCoupon[쿠폰 조회<br/>version 읽기]
    ReadCoupon --> CheckQuantity{남은 수량<br/>issued < total?}

    CheckQuantity -->|소진| Rollback1[트랜잭션 롤백]
    Rollback1 --> ReturnSoldOut[400 수량 소진]

    CheckQuantity -->|있음| IncrementIssued[issued_quantity++<br/>Optimistic Lock]
    IncrementIssued --> OptimisticSuccess{업데이트 성공?<br/>version 일치?}

    OptimisticSuccess -->|실패<br/>동시 발급 충돌| Rollback2[트랜잭션 롤백]
    Rollback2 --> ReturnConflict[409 Conflict<br/>다시 시도]

    OptimisticSuccess -->|성공| CreateUserCoupon[UserCoupon 생성<br/>status: AVAILABLE]
    CreateUserCoupon --> CommitTransaction[트랜잭션 커밋]

    CommitTransaction --> ReturnSuccess[201 Created<br/>userCouponId, remainingQuantity]

    ReturnSuccess --> End([종료])
    ReturnCouponNotFound --> End
    ReturnExpired --> End
    ReturnAlreadyIssued --> End
    ReturnSoldOut --> End
    ReturnConflict --> End

    style Start fill:#e1f5e1
    style End fill:#ffe1e1
    style ReturnSuccess fill:#d4edda
    style CommitTransaction fill:#d4edda
    style Rollback1 fill:#fff3cd
    style Rollback2 fill:#fff3cd
    style ReturnCouponNotFound fill:#f8d7da
    style ReturnExpired fill:#f8d7da
    style ReturnAlreadyIssued fill:#f8d7da
    style ReturnSoldOut fill:#f8d7da
    style ReturnConflict fill:#fff3cd
```

---

## 4. 재고 차감 플로우

### 설명
Optimistic Lock을 사용한 재고 차감 로직입니다.

```mermaid
flowchart TD
    Start([재고 차감 시작]) --> ReadStock[재고 조회<br/>quantity, version 읽기]

    ReadStock --> CheckQuantity{재고 충분?<br/>quantity >= 요청량}

    CheckQuantity -->|부족| ReturnInsufficient[재고 부족 예외]
    CheckQuantity -->|충분| CalculateNew[새 수량 계산<br/>new_qty = qty - 요청량]

    CalculateNew --> TryUpdate[UPDATE stock<br/>SET quantity = new_qty,<br/>version = version + 1<br/>WHERE id = ? AND version = ?]

    TryUpdate --> CheckRowsAffected{영향받은 행 = 1?}

    CheckRowsAffected -->|0<br/>version 불일치| ReturnVersionMismatch[버전 충돌<br/>다른 트랜잭션이<br/>먼저 수정함]
    CheckRowsAffected -->|1<br/>성공| RecordHistory[재고 이력 기록<br/>INSERT stock_history]

    RecordHistory --> ReturnSuccess[재고 차감 성공<br/>new_qty, new_version]

    ReturnSuccess --> End([종료])
    ReturnInsufficient --> End
    ReturnVersionMismatch --> End

    style Start fill:#e1f5e1
    style End fill:#ffe1e1
    style ReturnSuccess fill:#d4edda
    style ReturnInsufficient fill:#f8d7da
    style ReturnVersionMismatch fill:#fff3cd
```

---

## 5. 장바구니에서 주문 전환 플로우

### 설명
장바구니 항목들을 검증하고 주문으로 전환하는 흐름입니다.

```mermaid
flowchart TD
    Start([장바구니 조회]) --> GetCart{장바구니 존재?}

    GetCart -->|없음/비어있음| ReturnEmptyCart[400 빈 장바구니]
    GetCart -->|있음| LoopItems[장바구니 항목 순회]

    LoopItems --> CheckProduct{상품 존재?}
    CheckProduct -->|없음| ReturnProductNotFound[404 상품 없음]
    CheckProduct -->|있음| RevalidateStock{재고 재확인<br/>실시간 조회}

    RevalidateStock -->|부족| MarkOutOfStock[항목에<br/>재고 부족 표시]
    MarkOutOfStock --> HasMoreItems1{더 있음?}
    HasMoreItems1 -->|Yes| LoopItems
    HasMoreItems1 -->|No| ReturnStockIssue[400 재고 부족 항목 있음]

    RevalidateStock -->|충분| CheckPrice{가격 변동?}
    CheckPrice -->|변경됨| UpdatePrice[최신 가격으로<br/>업데이트]
    CheckPrice -->|동일| NextItem
    UpdatePrice --> NextItem

    NextItem[다음 항목] --> HasMoreItems2{더 있음?}
    HasMoreItems2 -->|Yes| LoopItems
    HasMoreItems2 -->|No| AllValid[모든 항목 유효]

    AllValid --> CalculateTotal[총 금액 계산]
    CalculateTotal --> CreateOrder[주문 생성<br/>API 호출]

    CreateOrder --> OrderSuccess{주문 성공?}
    OrderSuccess -->|실패| ReturnOrderFailed[주문 생성 실패]
    OrderSuccess -->|성공| ClearCart[장바구니 비우기]

    ClearCart --> ReturnSuccess[200 OK<br/>주문 ID 반환]

    ReturnSuccess --> End([종료])
    ReturnEmptyCart --> End
    ReturnProductNotFound --> End
    ReturnStockIssue --> End
    ReturnOrderFailed --> End

    style Start fill:#e1f5e1
    style End fill:#ffe1e1
    style ReturnSuccess fill:#d4edda
    style AllValid fill:#d4edda
    style ReturnEmptyCart fill:#f8d7da
    style ReturnProductNotFound fill:#f8d7da
    style ReturnStockIssue fill:#f8d7da
    style ReturnOrderFailed fill:#f8d7da
```

---

## 플로우차트 활용 방법

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

## 핵심 패턴 정리

### 1. 재고 확인
- **주문 생성 시**: 재고 확인만 (차감하지 않음)
- **결제 처리 시**: 실제 재고 차감 (Optimistic Lock)

### 2. 동시성 제어
- **포인트**: Pessimistic Lock (`SELECT FOR UPDATE`)
- **재고/쿠폰**: Optimistic Lock (`version` 필드)

### 3. 트랜잭션 경계
- **주문 생성**: 조회만 (트랜잭션 불필요)
- **결제 처리**: 포인트 + 재고 + 주문 상태 (하나의 트랜잭션)

### 4. 외부 연동
- **비동기 처리**: `@Async`로 Non-blocking
- **Fallback**: 실패 시 Outbox 저장

---

## 관련 문서

- [시퀀스 다이어그램](./sequence-diagrams.md) - API별 상세 흐름
- [ERD](./erd.md) - 데이터베이스 설계
- [API 명세서](../api/api-specification.md) - API 엔드포인트
- [가용성 패턴](../api/availability-patterns.md) - Timeout, Retry, Fallback, Async
