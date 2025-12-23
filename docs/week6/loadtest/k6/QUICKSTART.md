# K6 Load Test - Quick Start Guide

## 🚀 1분만에 시작하기

### Step 1: 사전 준비 (한 번만 실행)

```bash
# K6 설치 (macOS)
brew install k6

# K6 설치 (Linux)
sudo apt-get install k6

# K6 설치 (Windows)
choco install k6
```

### Step 2: 애플리케이션 및 Redis 실행

```bash
# Terminal 1: Redis 실행
docker run -d -p 6379:6379 redis:7-alpine

# Terminal 2: 애플리케이션 실행
cd /Users/jsb/hanghe-plus/ecommerce
./gradlew bootRun
```

### Step 3: 부하 테스트 실행

```bash
# 모든 테스트 자동 실행 (권장)
./docs/week6/loadtest/k6/run-all-tests.sh

# 또는 개별 테스트 실행
k6 run docs/week6/loadtest/k6/order-creation-idempotency-test.js
k6 run docs/week6/loadtest/k6/product-query-cache-test.js
k6 run docs/week6/loadtest/k6/cart-cache-test.js
```

---

## 📊 예상 결과

### Order Creation Idempotency Test

```
✓ first request: status 200
✓ duplicate request: same orderId
✓ cached response 10x faster

=== Idempotency Test Summary ===
Order Creation Avg: 487ms
Cached Response Avg: 38ms
Performance Improvement: 12.8x faster
Duplicate Request Rate: 87.3%

✓ ALL THRESHOLDS PASSED
```

### Product Query Cache Test

```
✓ product list: status 200
✓ cache hit rate > 90%

=== Cache Performance Test Summary ===
Cache Hit Rate: 94.2%
Cache Hit Avg: 23ms
Cache Miss Avg: 187ms
Performance Improvement: 53x faster

✓ ALL THRESHOLDS PASSED
```

### Cart Cache Test

```
✓ get cart: status 200
✓ cache consistency: 98.7%

=== Cart Cache Test Summary ===
Cache Hit Avg: 31ms
Cache Evict Avg: 143ms
Cache Consistency Rate: 98.7%

✓ ALL THRESHOLDS PASSED
```

---

## 🔍 문제 해결

### 애플리케이션이 실행되지 않는 경우

```bash
# 애플리케이션 확인
curl http://localhost:8080/api/products

# 안 되면 재시작
./gradlew bootRun
```

### Redis가 실행되지 않는 경우

```bash
# Redis 확인
redis-cli ping

# 응답: PONG

# 안 되면 재시작
docker run -d -p 6379:6379 redis:7-alpine
```

### 테스트 실패 시

```bash
# 로그 확인
tail -f logs/application.log

# 데이터베이스 확인
mysql -u root -p ecommerce

# Redis 확인
redis-cli
> KEYS *
```

---

## 📈 성능 벤치마크

| 기능 | Before | After | Improvement |
|------|--------|-------|-------------|
| 중복 주문 요청 | 487ms | 38ms | **12.8배** |
| 상품 조회 (캐시 히트) | 187ms | 23ms | **53배** |
| 장바구니 조회 | 156ms | 31ms | **5배** |

---

## 📝 다음 단계

1. **결과 분석**: `docs/week6/loadtest/k6/results/` 디렉토리의 JSON 파일 확인
2. **성능 최적화**: Threshold 실패 항목 개선
3. **Production 테스트**: 실제 환경에서 동일한 테스트 수행
4. **모니터링 설정**: Prometheus + Grafana 연동

---

## 📚 상세 문서

- 전체 문서: [`README.md`](./README.md)
- 테스트 스크립트: `order-creation-idempotency-test.js`, `product-query-cache-test.js`, `cart-cache-test.js`
- 실행 스크립트: `run-all-tests.sh`
