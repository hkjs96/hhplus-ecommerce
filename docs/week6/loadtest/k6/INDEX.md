# K6 Load Test Suite - Index

## 📁 파일 구조

```
docs/week6/loadtest/k6/
│
├── INDEX.md                                    ← 현재 문서
│
├── QUICKSTART.md                               ← 1분만에 시작하기
├── README.md                                   ← 전체 문서 (8,000자)
│
├── order-creation-idempotency-test.js          ← 멱등성 부하 테스트
├── product-query-cache-test.js                ← 상품 조회 캐시 테스트
├── cart-cache-test.js                          ← 장바구니 캐시 테스트
│
├── run-all-tests.sh                            ← 통합 실행 스크립트
│
└── results/                                    ← 테스트 결과 디렉토리
    ├── order-idempotency-summary.json
    ├── order-idempotency-raw.json
    ├── product-cache-summary.json
    ├── product-cache-raw.json
    ├── cart-cache-summary.json
    ├── cart-cache-raw.json
    └── test-summary.txt
```

---

## 🚀 빠른 시작

처음 시작하시나요? **[QUICKSTART.md](./QUICKSTART.md)** 를 보세요!

```bash
# 1. Redis 실행
docker run -d -p 6379:6379 redis:7-alpine

# 2. 애플리케이션 실행
./gradlew bootRun

# 3. 모든 테스트 실행
./docs/week6/loadtest/k6/run-all-tests.sh
```

---

## 📚 문서 가이드

### 처음 사용하시는 경우
1. **[QUICKSTART.md](./QUICKSTART.md)** - 1분만에 시작하기
   - 사전 준비 (K6 설치, Redis/애플리케이션 실행)
   - 실행 명령어
   - 예상 결과
   - 문제 해결

### 상세 정보가 필요한 경우
2. **[README.md](./README.md)** - 전체 문서
   - 테스트 개요 및 목표
   - 각 테스트 시나리오 상세 설명
   - 실행 방법 (사전 준비, 개별 실행, 전체 실행)
   - 결과 분석 방법
   - 성능 목표표
   - 커스터마이징 방법
   - 검증 체크리스트

---

## 🧪 테스트 스크립트

### 1. Order Creation Idempotency Test
**파일**: [order-creation-idempotency-test.js](./order-creation-idempotency-test.js)

**테스트 내용**:
- 동일 `idempotencyKey`로 중복 요청 시 캐시된 응답 반환
- 첫 요청 대비 캐시된 응답의 성능 향상 측정 (목표: 10배)
- 동시 요청 시 중복 주문 생성 방지

**실행**:
```bash
k6 run docs/week6/loadtest/k6/order-creation-idempotency-test.js
```

**성능 목표**:
- First Request P95: < 1000ms
- Cached Response P95: < 100ms
- Performance Improvement: **10배 이상**

---

### 2. Product Query Cache Test
**파일**: [product-query-cache-test.js](./product-query-cache-test.js)

**테스트 내용**:
- 상품 조회 API의 캐시 적용 효과 검증
- 캐시 히트율 측정 (목표: 90% 이상)
- 캐시 적용 전후 성능 비교 (목표: 50배)

**테스트 대상**:
- `GET /api/products` (상품 목록)
- `GET /api/products/{id}` (상품 상세)
- `GET /api/products/top` (인기 상품)
- `GET /api/products?category={category}` (카테고리 필터)

**실행**:
```bash
k6 run docs/week6/loadtest/k6/product-query-cache-test.js
```

**성능 목표**:
- Cache Hit Rate: > 90%
- Cache Hit P95: < 50ms
- Performance Improvement: **50배 이상**

---

### 3. Cart Cache Test
**파일**: [cart-cache-test.js](./cart-cache-test.js)

**테스트 내용**:
- 장바구니 조회 캐시 적용 효과 검증
- 장바구니 수정 시 캐시 무효화(Cache Eviction) 검증
- 캐시 일관성(Cache Consistency) 검증 (목표: 95% 이상)

**테스트 시나리오**:
- Get Cart → Cache Hit
- Add to Cart → Cache Evict → Consistency Check
- Update Cart Item → Cache Evict → Consistency Check
- Remove Cart Item → Cache Evict → Consistency Check

**실행**:
```bash
k6 run docs/week6/loadtest/k6/cart-cache-test.js
```

**성능 목표**:
- Cache Hit P95: < 100ms
- Cache Evict P95: < 200ms
- Cache Consistency Rate: **> 95%**

---

## 🎯 통합 실행

### 모든 테스트 한 번에 실행
**파일**: [run-all-tests.sh](./run-all-tests.sh)

**기능**:
- 애플리케이션 실행 상태 확인
- Redis 실행 상태 확인
- 3개 테스트 순차 실행
- 결과 JSON 파일 저장
- 통합 Summary Report 생성

**실행**:
```bash
./docs/week6/loadtest/k6/run-all-tests.sh
```

**출력 예시**:
```
========================================
K6 Load Test Suite for Idempotency & Cache
========================================

✓ Application is running
✓ Redis is running

========================================
Test 1: Order Creation Idempotency
========================================

✓ Order Idempotency Test PASSED

========================================
Test 2: Product Query Cache
========================================

✓ Product Cache Test PASSED

========================================
Test 3: Cart Cache
========================================

✓ Cart Cache Test PASSED

========================================
Overall Result: ALL TESTS PASSED ✓
========================================

Summary report saved to: docs/week6/loadtest/k6/results/test-summary.txt
```

---

## 📊 결과 분석

### 결과 파일 위치
테스트 실행 후 다음 디렉토리에 결과 저장:
```
docs/week6/loadtest/k6/results/
```

### 파일 종류

#### 1. Summary JSON Files
- `order-idempotency-summary.json`
- `product-cache-summary.json`
- `cart-cache-summary.json`

**내용**: 주요 메트릭, Threshold 결과, 통계 정보

**보기**:
```bash
cat docs/week6/loadtest/k6/results/order-idempotency-summary.json | jq .
```

#### 2. Raw JSON Files
- `order-idempotency-raw.json`
- `product-cache-raw.json`
- `cart-cache-raw.json`

**내용**: K6 전체 실행 데이터 (상세 분석용)

#### 3. Summary Report
- `test-summary.txt`

**내용**: 전체 테스트 결과 요약 (PASS/FAIL, 실행 시간)

**보기**:
```bash
cat docs/week6/loadtest/k6/results/test-summary.txt
```

---

## 🎓 학습 자료

### K6 기본 개념
- **VU (Virtual User)**: 가상 사용자 단위
- **Stages**: 부하 증가/유지/감소 단계
- **Thresholds**: 성능 기준 (PASS/FAIL 판정)
- **Custom Metrics**: Trend, Rate, Counter
- **Checks**: 응답 검증 (success/failure)
- **Groups**: 테스트 시나리오 그룹화

### 성능 지표 해석
- **P95**: 95%의 요청이 이 값 이내에 완료
- **P99**: 99%의 요청이 이 값 이내에 완료
- **TPS**: Transactions Per Second (초당 처리량)
- **Cache Hit Rate**: 캐시 히트 비율 (높을수록 좋음)
- **Consistency Rate**: 캐시 일관성 비율 (높을수록 좋음)

---

## 🔍 문제 해결

### 자주 발생하는 문제

#### 1. 애플리케이션이 실행되지 않음
```bash
# 확인
curl http://localhost:8080/api/products

# 해결
./gradlew bootRun
```

#### 2. Redis가 실행되지 않음
```bash
# 확인
redis-cli ping

# 해결
docker run -d -p 6379:6379 redis:7-alpine
```

#### 3. K6가 설치되지 않음
```bash
# macOS
brew install k6

# Linux
sudo apt-get install k6

# Windows
choco install k6
```

#### 4. 테스트 실패 (Threshold)
- **원인**: 애플리케이션 성능 저하, Redis 문제, DB 성능
- **해결**: 로그 확인 (`tail -f logs/application.log`)

더 많은 문제 해결 방법은 **[README.md](./README.md)** 참조

---

## 📈 예상 성능

### Order Creation Idempotency
- First Request: ~500ms
- Cached Response: ~40ms
- **Improvement: 12-15x**

### Product Query Cache
- Cache Hit: ~25ms
- Cache Miss: ~190ms
- Cache Hit Rate: 94-96%
- **Improvement: 50-55x**

### Cart Cache
- Cache Hit: ~35ms
- Cache Evict: ~150ms
- Cache Consistency: 98-99%
- **Improvement: 5x**

---

## ✅ 체크리스트

### 테스트 실행 전
- [ ] K6 설치 완료
- [ ] Redis 실행 중 (`redis-cli ping` → PONG)
- [ ] 애플리케이션 실행 중 (`curl localhost:8080/api/products` → 200)
- [ ] 결과 디렉토리 생성 (`mkdir -p docs/week6/loadtest/k6/results`)

### 테스트 실행 후
- [ ] Summary 파일 생성됨 (`test-summary.txt`)
- [ ] JSON 결과 파일 3개 생성됨
- [ ] All Thresholds PASSED
- [ ] 성능 목표 달성 확인

### Production 배포 전
- [ ] Staging 환경에서 테스트 완료
- [ ] Peak Time 부하 테스트 완료
- [ ] Monitoring 설정 완료 (Prometheus + Grafana)
- [ ] Rollback 계획 수립

---

## 📚 관련 문서

### 프로젝트 문서
- [Week 6 README](../../README.md) - Week 6 전체 개요
- [WEEK6_COMPLETE_SUMMARY](../../WEEK6_COMPLETE_SUMMARY.md) - 구현 완료 요약
- [LOAD_TEST_IMPLEMENTATION_COMPLETE](../../LOAD_TEST_IMPLEMENTATION_COMPLETE.md) - 상세 구현 문서

### 외부 자료
- [K6 Official Docs](https://k6.io/docs/)
- [K6 Best Practices](https://k6.io/docs/using-k6/best-practices/)
- [Redis Cache Patterns](https://redis.io/docs/manual/patterns/cache/)

---

## 💡 다음 단계

1. **실행 및 검증**:
   ```bash
   ./docs/week6/loadtest/k6/run-all-tests.sh
   ```

2. **결과 분석**:
   - Summary Report 확인
   - Threshold 통과 여부 확인
   - 성능 목표 달성 여부 확인

3. **Production 준비**:
   - Staging 환경 테스트
   - Monitoring 설정
   - 부하 테스트 반복

4. **최적화**:
   - 성능 병목 지점 파악
   - Cache TTL 조정
   - Query 최적화

---

**작성자**: Claude Code
**작성일**: 2025-11-27
**문의**: K6 테스트 관련 질문은 README.md 참조
