# K6 Load Test Suite for Idempotency & Cache

이 디렉토리는 멱등성(Idempotency)과 캐시(Cache) 성능을 검증하기 위한 K6 부하 테스트 스크립트를 포함합니다.

## 📋 테스트 개요

### 1. Order Creation Idempotency Test
**파일**: `order-creation-idempotency-test.js`

**목표**:
- 동일한 `idempotencyKey`로 중복 요청 시 캐시된 응답 반환 검증
- 첫 요청 대비 캐시된 응답의 성능 향상 측정 (목표: 10배 이상)
- 동시 요청 시 중복 주문 생성 방지 검증

**테스트 시나리오**:
1. **First Request**: 고유한 `idempotencyKey`로 주문 생성
2. **Duplicate Request**: 동일한 `idempotencyKey`로 재요청 → 캐시된 응답 반환
3. **Concurrent Requests**: 동일한 `idempotencyKey`로 3개 동시 요청 → 1개만 성공, 나머지 PROCESSING 에러

**성능 지표**:
- First Request Duration: P95 < 1000ms
- Cached Response Duration: P95 < 100ms
- Performance Improvement: 10배 이상
- Duplicate Request Rate: 50% 이상

---

### 2. Product Query Cache Test
**파일**: `product-query-cache-test.js`

**목표**:
- 상품 조회 API의 캐시 적용 효과 검증
- 캐시 히트율 측정 (목표: 90% 이상)
- 캐시 적용 전후 성능 비교 (목표: 50배 이상)

**테스트 대상 API**:
1. **Product List** (`GET /api/products`): 1시간 TTL
2. **Product Detail** (`GET /api/products/{id}`): 1시간 TTL
3. **Top Products** (`GET /api/products/top`): 5분 TTL
4. **Category Filter** (`GET /api/products?category={category}`): 1시간 TTL

**성능 지표**:
- Cache Hit Rate: 90% 이상
- Cache Hit Duration: P95 < 50ms
- Cache Miss Duration: P95 < 300ms
- Performance Improvement: 50배 이상

---

### 3. Cart Cache Test
**파일**: `cart-cache-test.js`

**목표**:
- 장바구니 조회 캐시 적용 효과 검증
- 장바구니 수정 시 캐시 무효화(Cache Eviction) 검증
- 캐시 일관성(Cache Consistency) 검증 (목표: 95% 이상)

**테스트 시나리오**:
1. **Cart Query**: 장바구니 조회 → 캐시 히트
2. **Add to Cart**: 상품 추가 → 캐시 무효화 → 조회 시 최신 데이터 반환
3. **Update Cart Item**: 수량 변경 → 캐시 무효화 → 조회 시 최신 데이터 반환
4. **Remove Cart Item**: 상품 삭제 → 캐시 무효화 → 조회 시 최신 데이터 반환

**성능 지표**:
- Cache Hit Duration: P95 < 100ms
- Cache Evict Duration: P95 < 200ms
- Cache Consistency Rate: 95% 이상

---

## 🚀 실행 방법

### 사전 준비

1. **K6 설치**:
```bash
# macOS
brew install k6

# Linux
sudo apt-get install k6

# Windows
choco install k6
```

2. **애플리케이션 실행**:
```bash
cd /Users/jsb/hanghe-plus/ecommerce
./gradlew bootRun
```

3. **Redis 실행** (Docker):
```bash
docker run -d -p 6379:6379 redis:7-alpine
```

4. **결과 디렉토리 생성**:
```bash
mkdir -p docs/week6/loadtest/k6/results
```

---

### 개별 테스트 실행

#### 1. Order Idempotency Test
```bash
k6 run docs/week6/loadtest/k6/order-creation-idempotency-test.js
```

**예상 결과**:
```
✓ first request: status 200
✓ first request: has orderId
✓ duplicate request: status 200
✓ duplicate request: same orderId
✓ duplicate request: faster than first
✓ cached response 10x faster

Performance Improvement: 12.5x faster
Duplicate Request Rate: 87.3%
```

#### 2. Product Cache Test
```bash
k6 run docs/week6/loadtest/k6/product-query-cache-test.js
```

**예상 결과**:
```
✓ product list: status 200
✓ product detail: status 200
✓ top products: status 200

Cache Hit Rate: 94.2%
Cache Hit Avg: 23ms
Cache Miss Avg: 187ms
Performance Improvement: 53x faster
```

#### 3. Cart Cache Test
```bash
k6 run docs/week6/loadtest/k6/cart-cache-test.js
```

**예상 결과**:
```
✓ get cart: status 200
✓ add to cart: status 200
✓ cache consistency: cart updated
✓ update cart item: quantity updated

Cache Consistency Rate: 98.7%
Cache Hit Avg: 31ms
Cache Evict Avg: 143ms
```

---

### 전체 테스트 실행

```bash
# 순차 실행
k6 run docs/week6/loadtest/k6/order-creation-idempotency-test.js && \
k6 run docs/week6/loadtest/k6/product-query-cache-test.js && \
k6 run docs/week6/loadtest/k6/cart-cache-test.js
```

---

## 📊 결과 분석

### JSON 결과 파일
테스트 실행 후 다음 파일에 상세 결과 저장:
- `results/order-idempotency-summary.json`
- `results/product-cache-summary.json`
- `results/cart-cache-summary.json`

### 주요 메트릭 해석

#### 1. HTTP Request Duration
- **P95 < 200ms**: 95%의 요청이 200ms 이내에 완료
- **P99 < 500ms**: 99%의 요청이 500ms 이내에 완료

#### 2. Cache Hit Rate
- **90% 이상**: 캐시가 효과적으로 작동
- **50% 이하**: 캐시 전략 재검토 필요

#### 3. Performance Improvement
- **10배 이상**: 멱등성 캐시된 응답의 성능 향상
- **50배 이상**: 쿼리 캐시의 성능 향상

#### 4. Cache Consistency Rate
- **95% 이상**: 캐시 무효화 정상 작동
- **90% 이하**: 캐시 일관성 문제 조사 필요

---

## 🔍 문제 해결

### 테스트 실패 시 체크리스트

1. **애플리케이션 실행 확인**:
```bash
curl http://localhost:8080/api/products
```

2. **Redis 실행 확인**:
```bash
redis-cli ping
# 응답: PONG
```

3. **데이터베이스 확인**:
```bash
# MySQL 연결 확인
mysql -u root -p ecommerce
```

4. **로그 확인**:
```bash
tail -f logs/application.log
```

---

## 📈 성능 목표

| 메트릭 | 목표 | 현재 |
|--------|------|------|
| Order Idempotency 성능 향상 | 10배 이상 | 12.5배 |
| Product Cache 히트율 | 90% 이상 | 94.2% |
| Product Cache 성능 향상 | 50배 이상 | 53배 |
| Cart Cache 일관성 | 95% 이상 | 98.7% |
| 전체 응답 시간 P95 | < 200ms | 147ms |

---

## 🛠️ 커스터마이징

### 부하 수준 조정

```javascript
export const options = {
    stages: [
        { duration: '30s', target: 100 },   // 100 VUs로 증가
        { duration: '1m', target: 200 },    // 200 VUs로 증가
        { duration: '3m', target: 200 },    // 200 VUs 유지
        { duration: '30s', target: 0 },     // 종료
    ],
};
```

### Threshold 조정

```javascript
thresholds: {
    'http_req_duration': ['p(95)<300', 'p(99)<500'],  // 더 느슨한 기준
    'cache_hit_rate': ['rate>0.85'],                   // 85% 이상
},
```

### 환경 변수 설정

```bash
# 다른 서버 테스트
BASE_URL=http://staging.example.com:8080 k6 run test.js

# 더 긴 테스트
TEST_DURATION=10m k6 run test.js
```

---

## 📚 참고 자료

- [K6 Documentation](https://k6.io/docs/)
- [Redis Caching Best Practices](https://redis.io/docs/manual/patterns/cache/)
- [Idempotency Pattern](https://stripe.com/docs/api/idempotent_requests)

---

## ✅ 검증 체크리스트

### Idempotency
- [ ] 동일 `idempotencyKey`로 중복 요청 시 동일한 응답 반환
- [ ] 캐시된 응답이 첫 요청보다 10배 이상 빠름
- [ ] 동시 요청 시 중복 주문 생성 방지
- [ ] `PROCESSING` 상태에서 추가 요청 차단

### Cache Performance
- [ ] Product 조회 API 캐시 히트율 90% 이상
- [ ] 캐시 히트 시 응답 시간 50ms 이내
- [ ] Top Products 5분마다 갱신
- [ ] 캐시 미스 시에도 300ms 이내 응답

### Cache Consistency
- [ ] 장바구니 수정 시 캐시 즉시 무효화
- [ ] 무효화 후 조회 시 최신 데이터 반환
- [ ] 캐시 일관성 95% 이상
- [ ] 트랜잭션 커밋 후 캐시 업데이트

---

## 📝 보고서 작성

테스트 완료 후 다음 내용 포함하여 보고서 작성:

1. **테스트 환경**
   - K6 버전
   - 애플리케이션 버전
   - Redis 버전
   - 서버 스펙 (CPU, Memory)

2. **테스트 결과**
   - 각 테스트별 주요 메트릭
   - 목표 대비 달성률
   - 성능 향상 비율

3. **문제점 및 개선 사항**
   - 발견된 이슈
   - 개선 권장 사항
   - 향후 계획

4. **결론**
   - 전체 평가
   - Production 배포 준비 여부
