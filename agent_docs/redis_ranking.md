# Redis 기반 실시간 랭킹 시스템 설계

## 개요

Redis Sorted Set을 활용하여 가장 많이 주문된 상품의 실시간 랭킹을 제공합니다.

---

## 핵심 설계 원칙

### 1. 자료구조 선택: Sorted Set

**왜 Sorted Set인가?**
- score 기반 자동 정렬 (오름차순)
- O(log N) 복잡도로 삽입/조회/업데이트
- ZINCRBY로 원자적 score 증가 가능

**대안과의 비교:**
- List: 정렬 수동 처리 필요 ❌
- Hash: 순위 계산 불가 ❌
- Set: score 없음 ❌

---

## 키 설계 전략

### 키 네이밍 패턴
```
ranking:product:orders:{scope}:{date}
```

**예시:**
```
ranking:product:orders:daily:20250102   # 2025년 1월 2일 일간 랭킹
ranking:product:orders:weekly:2025W01   # 2025년 1주차 주간 랭킹
ranking:product:orders:monthly:202501   # 2025년 1월 월간 랭킹
```

### TTL 설정
```java
// 일간 랭킹: 3일 후 만료
redisTemplate.expire(dailyKey, 3, TimeUnit.DAYS);

// 주간 랭킹: 2주 후 만료
redisTemplate.expire(weeklyKey, 14, TimeUnit.DAYS);

// 월간 랭킹: 3개월 후 만료
redisTemplate.expire(monthlyKey, 90, TimeUnit.DAYS);
```

**주의:** TTL 없이 키를 생성하면 메모리 누수 발생

---

## 랭킹 갱신 시점

### ✅ 올바른 시점: 결제 완료
```java
@Transactional
public void completePayment(Long orderId) {
    // 1. 결제 처리
    Payment payment = paymentService.processPayment(orderId);

    // 2. 결제 성공 시 랭킹 갱신 (비동기)
    if (payment.isSuccess()) {
        eventPublisher.publishEvent(new PaymentCompletedEvent(orderId));
    }
}

@EventListener
@Async
public void updateRanking(PaymentCompletedEvent event) {
    Order order = orderRepository.findById(event.getOrderId());

    for (OrderItem item : order.getItems()) {
        String key = generateDailyRankingKey(LocalDate.now());

        // 원자적으로 score 증가
        redisTemplate.opsForZSet()
            .incrementScore(key, item.getProductId().toString(), item.getQuantity());
    }
}
```

### ❌ 잘못된 시점
- 주문 생성 시점 (결제 실패 가능)
- 장바구니 담기 시점 (구매 미확정)

---

## Redis 명령어 사용

### 1. 랭킹 갱신 (score 증가)
```bash
# 상품 ID 123의 score를 5 증가
ZINCRBY ranking:product:orders:daily:20250102 5 "123"
```

**Java 코드:**
```java
String key = "ranking:product:orders:daily:20250102";
String productId = "123";
int quantity = 5;

redisTemplate.opsForZSet().incrementScore(key, productId, quantity);
```

### 2. Top N 조회 (높은 score 순)
```bash
# Top 10 조회 (score 포함)
ZREVRANGE ranking:product:orders:daily:20250102 0 9 WITHSCORES
```

**Java 코드:**
```java
String key = "ranking:product:orders:daily:20250102";
int topN = 10;

Set<ZSetOperations.TypedTuple<String>> ranking =
    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, topN - 1);

List<ProductRankingDto> result = ranking.stream()
    .map(tuple -> new ProductRankingDto(
        Long.parseLong(tuple.getValue()),
        tuple.getScore().intValue()
    ))
    .collect(Collectors.toList());
```

### 3. 특정 상품 순위 조회
```bash
# 상품 ID 123의 순위 조회 (0-based index)
ZREVRANK ranking:product:orders:daily:20250102 "123"
```

**Java 코드:**
```java
String key = "ranking:product:orders:daily:20250102";
String productId = "123";

Long rank = redisTemplate.opsForZSet().reverseRank(key, productId);
// rank는 0-based이므로 1을 더해야 실제 순위
int actualRank = rank != null ? rank.intValue() + 1 : -1;
```

---

## 동시성 처리

### ✅ 별도 분산락 불필요
- Redis는 단일 스레드로 동작
- `ZINCRBY`는 원자적(atomic) 명령어
- 100개의 동시 요청이 와도 score는 정확히 100 증가

### ❌ 이렇게 하면 안 됨
```java
// 잘못된 예시: 조회 후 업데이트 (Race Condition 발생)
Double currentScore = redisTemplate.opsForZSet().score(key, productId);
Double newScore = currentScore + quantity;
redisTemplate.opsForZSet().add(key, productId, newScore);  // ❌
```

**올바른 방법:**
```java
// ZINCRBY 사용 (원자적)
redisTemplate.opsForZSet().incrementScore(key, productId, quantity);  // ✅
```

---

## API 설계 예시

### GET /api/products/ranking/top

**Request:**
```
GET /api/products/ranking/top?scope=daily&date=2025-01-02&limit=10
```

**Response:**
```json
{
  "scope": "daily",
  "date": "2025-01-02",
  "rankings": [
    {
      "rank": 1,
      "productId": 123,
      "productName": "상품A",
      "salesCount": 500
    },
    {
      "rank": 2,
      "productId": 456,
      "productName": "상품B",
      "salesCount": 350
    }
  ]
}
```

**구현:**
```java
@GetMapping("/api/products/ranking/top")
public RankingResponse getTopRanking(
    @RequestParam(defaultValue = "daily") String scope,
    @RequestParam(required = false) LocalDate date,
    @RequestParam(defaultValue = "10") int limit
) {
    if (date == null) date = LocalDate.now();

    String key = generateRankingKey(scope, date);

    Set<ZSetOperations.TypedTuple<String>> ranking =
        redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);

    List<ProductRankingDto> products = ranking.stream()
        .map(tuple -> {
            Long productId = Long.parseLong(tuple.getValue());
            Product product = productRepository.findById(productId);

            return new ProductRankingDto(
                productId,
                product.getName(),
                tuple.getScore().intValue()
            );
        })
        .collect(Collectors.toList());

    return new RankingResponse(scope, date, products);
}
```

---

## 주의사항

### 1. TTL 관리
- 키 생성 시 반드시 TTL 설정
- 매일 자정 배치로 새 키 생성 (이전 키는 TTL로 자동 만료)

### 2. score 동률 처리
- Sorted Set은 score가 같으면 사전순 정렬
- 동률 시 추가 정렬 기준 필요하면 score에 타임스탬프 병합
  ```java
  // 예: score = (판매량 * 1000000) + timestamp
  double score = (salesCount * 1000000) + System.currentTimeMillis();
  ```

### 3. 대용량 랭킹
- 상위 N개만 필요하면 ZREVRANGE 사용
- 전체 조회는 `ZRANGE key 0 -1` (비권장, 메모리 부담)

### 4. 캐시 레이어
- Redis 랭킹 자체가 이미 캐시 역할
- 추가 Application 메모리 캐시는 불필요

---

## 참고: Redis 공식 문서

- [ZADD](https://redis.io/commands/zadd/)
- [ZINCRBY](https://redis.io/commands/zincrby/)
- [ZREVRANGE](https://redis.io/commands/zrevrange/)
- [ZREVRANK](https://redis.io/commands/zrevrank/)
- [Sorted Sets](https://redis.io/docs/data-types/sorted-sets/)
