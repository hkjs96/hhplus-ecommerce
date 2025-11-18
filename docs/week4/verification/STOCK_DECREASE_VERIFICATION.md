# 재고 감소 동작 확인 가이드

## 🎯 목적

CreateOrderUseCase의 재고 감소 로직이 정상 동작하는지 직접 확인합니다.

---

## 🚀 Step 1: 애플리케이션 실행

### 1-1. 터미널 1번 (애플리케이션 시작)

```bash
cd /Users/jsb/hanghe-plus/ecommerce

# 애플리케이션 시작 (로그 확인 가능)
./gradlew bootRun
```

**대기**: `Started EcommerceApplication` 메시지가 나올 때까지 (약 10-15초)

---

## 📊 Step 2: 초기 재고 확인

### 2-1. 터미널 2번 열기 (새 터미널)

```bash
# 노트북(P001) 상품 정보 조회
curl -s "http://localhost:8080/api/products/1" | jq
```

**예상 결과**:
```json
{
  "productId": 1,
  "name": "노트북",
  "description": "고성능 게이밍 노트북",
  "price": 1500000,
  "stock": 50,              # ← 초기 재고 확인!
  "category": "전자제품"
}
```

**📝 메모**: 초기 재고 = **50개**

---

## 🛒 Step 3: 주문 생성 (재고 감소 발생)

### 3-1. 주문 생성 요청

```bash
# 노트북 3개 주문
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": [
      {
        "productId": 1,
        "quantity": 3
      }
    ]
  }' | jq
```

**예상 결과**:
```json
{
  "orderId": 19,
  "userId": 1,
  "orderNumber": "ORDER-xxxxxxxx",
  "items": [
    {
      "productId": 1,
      "productName": "노트북",
      "quantity": 3,           # ← 3개 주문
      "unitPrice": 1500000,
      "subtotal": 4500000
    }
  ],
  "subtotalAmount": 4500000,
  "discountAmount": 0,
  "totalAmount": 4500000,
  "status": "PENDING",
  "createdAt": "2025-11-18T..."
}
```

✅ **주문 성공!**

---

## ✅ Step 4: 재고 감소 확인

### 4-1. 재고 다시 조회

```bash
# 노트북 재고 확인
curl -s "http://localhost:8080/api/products/1" | jq '.stock'
```

**예상 결과**:
```
47
```

**계산**:
- 초기 재고: 50개
- 주문 수량: 3개
- **현재 재고: 50 - 3 = 47개** ✅

---

## 🔄 Step 5: 여러 번 주문하여 재고 감소 확인

### 5-1. 2번째 주문 (5개)

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": [
      {
        "productId": 1,
        "quantity": 5
      }
    ]
  }' | jq '.items[0].quantity'
```

**예상**: `5`

### 5-2. 재고 확인

```bash
curl -s "http://localhost:8080/api/products/1" | jq '.stock'
```

**예상 결과**: `42` (47 - 5 = 42)

### 5-3. 3번째 주문 (10개)

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": [
      {
        "productId": 1,
        "quantity": 10
      }
    ]
  }' | jq '.items[0].quantity'
```

### 5-4. 최종 재고 확인

```bash
curl -s "http://localhost:8080/api/products/1" | jq '.stock'
```

**예상 결과**: `32` (42 - 10 = 32)

---

## 🚨 Step 6: 재고 부족 시 에러 확인

### 6-1. 재고보다 많이 주문

```bash
# 현재 재고 32개인데 50개 주문 시도
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": [
      {
        "productId": 1,
        "quantity": 50
      }
    ]
  }'
```

**예상 결과** (에러):
```json
{
  "success": false,
  "error": {
    "code": "P002",
    "message": "재고가 부족합니다. 상품: 노트북, 요청: 50, 재고: 32"
  }
}
```

### 6-2. 재고 변경 없는지 확인

```bash
curl -s "http://localhost:8080/api/products/1" | jq '.stock'
```

**예상 결과**: `32` (변경 없음 - 트랜잭션 롤백됨)

---

## 📊 Step 7: 한눈에 보는 검증 스크립트

### 한번에 실행 (복사해서 사용)

```bash
echo "=== 1. 초기 재고 확인 ==="
STOCK_BEFORE=$(curl -s "http://localhost:8080/api/products/1" | jq -r '.stock')
echo "초기 재고: $STOCK_BEFORE"

echo -e "\n=== 2. 주문 생성 (3개) ==="
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": [{"productId": 1, "quantity": 3}]
  }' | jq -r '.items[0] | "주문: \(.productName) \(.quantity)개"'

echo -e "\n=== 3. 재고 감소 확인 ==="
STOCK_AFTER=$(curl -s "http://localhost:8080/api/products/1" | jq -r '.stock')
echo "현재 재고: $STOCK_AFTER"
echo "감소량: $(($STOCK_BEFORE - $STOCK_AFTER))"

if [ $(($STOCK_BEFORE - $STOCK_AFTER)) -eq 3 ]; then
  echo "✅ 재고 감소 정상 동작!"
else
  echo "❌ 재고 감소 미동작"
fi
```

**예상 출력**:
```
=== 1. 초기 재고 확인 ===
초기 재고: 50

=== 2. 주문 생성 (3개) ===
주문: 노트북 3개

=== 3. 재고 감소 확인 ===
현재 재고: 47
감소량: 3
✅ 재고 감소 정상 동작!
```

---

## 🔍 Step 8: 애플리케이션 로그 확인

### 8-1. 터미널 1번 (애플리케이션 로그)에서 확인

주문 생성 시 다음 로그가 나타나야 합니다:

```
DEBUG i.h.e.a.u.order.CreateOrderUseCase - Creating order for user: 1
DEBUG org.hibernate.SQL -
    update products
    set stock=?, version=?
    where id=? and version=?    # ← 재고 감소 UPDATE 쿼리
INFO  i.h.e.a.u.order.CreateOrderUseCase - Order created successfully. orderId: 19, userId: 1
```

**핵심 확인사항**:
- ✅ `update products set stock=?` 쿼리 실행됨
- ✅ `where ... and version=?` → Optimistic Lock 적용됨

---

## 🎯 검증 체크리스트

- [ ] 초기 재고 조회 성공
- [ ] 주문 생성 성공
- [ ] 재고가 주문 수량만큼 감소
- [ ] 여러 번 주문 시 누적 감소
- [ ] 재고 부족 시 에러 발생
- [ ] 에러 발생 시 재고 변경 없음 (롤백)
- [ ] UPDATE 쿼리 로그 확인
- [ ] @Version 필드 사용 확인

---

## 💡 문제 해결

### 문제 1: 애플리케이션이 안 뜬다

```bash
# 포트 충돌 확인
lsof -i :8080

# 기존 프로세스 종료
pkill -f gradle
pkill -f java

# 다시 시작
./gradlew bootRun
```

### 문제 2: 재고가 감소하지 않는다

1. **로그 확인**: UPDATE 쿼리가 실행되는지 확인
2. **코드 확인**: `product.decreaseStock()` 호출 여부
3. **트랜잭션 확인**: `@Transactional` 어노테이션 존재 여부

### 문제 3: 동시성 테스트

```bash
# 동시에 여러 주문 (병렬 실행)
for i in {1..5}; do
  curl -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -d '{
      "userId": 1,
      "items": [{"productId": 1, "quantity": 2}]
    }' &
done
wait

# 최종 재고 확인 (50 - 10 = 40이어야 함)
curl -s "http://localhost:8080/api/products/1" | jq '.stock'
```

**기대값**: 정확히 10개 감소 (Optimistic Lock 덕분에)

---

## 🎓 추가 학습

### Optimistic Lock vs Pessimistic Lock

**현재 적용: Optimistic Lock (@Version)**
- 충돌이 드문 경우 성능 우수
- 충돌 시 재시도 (OrderPaymentFacade)
- 동시성 제어 보장

**대안: Pessimistic Lock**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Product findByIdWithLock(@Param("id") Long id);
```
- 충돌이 잦은 경우 사용
- 다른 트랜잭션 대기 (성능 저하)

---

## 📚 참고 자료

- CreateOrderUseCase.java:104 - 재고 감소 로직
- Product.java - decreaseStock() 메서드
- OrderPaymentFacade.java - OptimisticLockException 처리

---

## ✅ 최종 확인

재고 감소가 정상 동작하면:
- ✅ 주문 생성 시 재고 자동 감소
- ✅ 재고 부족 시 주문 거부
- ✅ 동시 주문 시 정합성 보장
- ✅ 트랜잭션 롤백 시 재고 복구

**모든 체크리스트 통과 시 → 재고 관리 시스템 완성!** 🎉
