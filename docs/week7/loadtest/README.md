# K6 부하 테스트 가이드

## 개요

이 문서는 E-Commerce API의 성능 측정을 위한 K6 부하 테스트 실행 방법을 설명합니다.

## 사전 준비

### 1. K6 설치

**macOS (Homebrew)**
```bash
brew install k6
```

**Linux (Debian/Ubuntu)**
```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

**Windows (Chocolatey)**
```bash
choco install k6
```

**Docker**
```bash
docker pull grafana/k6
```

### 2. 애플리케이션 시작

부하 테스트 전에 애플리케이션이 실행 중이어야 합니다:

```bash
# MySQL 시작
docker-compose up -d

# 애플리케이션 빌드 & 실행
./gradlew bootRun

# 헬스 체크
curl http://localhost:8080/actuator/health
```

### 3. 테스트 데이터 준비

애플리케이션이 시작되면 `DataInitializer`가 자동으로 초기 데이터를 로딩합니다:
- 사용자 10명 (userId: 1~10)
- 상품 10개 (productId: 1~10)
- 쿠폰 3개 (couponId: 1~3)

## 부하 테스트 실행

### 1. 기본 실행

```bash
k6 run k6/load-test.js
```

**테스트 단계:**
1. Warm-up: 10초 동안 VU 0 → 10
2. Ramp-up: 30초 동안 VU 10 → 50
3. Sustained Load: 1분 동안 VU 50 유지
4. Peak Load: 30초 동안 VU 50 → 100
5. Sustained Peak: 1분 동안 VU 100 유지
6. Ramp-down: 30초 동안 VU 100 → 0

**총 소요 시간:** 약 4분

### 2. 빠른 테스트 (10 VUs, 30초)

```bash
k6 run --vus 10 --duration 30s k6/load-test.js
```

### 3. 고부하 테스트 (200 VUs, 5분)

```bash
k6 run --vus 200 --duration 5m k6/load-test.js
```

### 4. 결과 저장

**JSON 형식으로 저장:**
```bash
k6 run --out json=results/load-test-$(date +%Y%m%d-%H%M%S).json k6/load-test.js
```

**요약만 저장:**
```bash
k6 run --summary-export=results/summary-$(date +%Y%m%d-%H%M%S).json k6/load-test.js
```

### 5. Docker로 실행

```bash
docker run --rm -i --network="host" \
  -v $(pwd):/scripts \
  grafana/k6 run /scripts/docs/week7/loadtest/k6/load-test.js
```

## 테스트 시나리오

부하 테스트는 실제 사용 패턴을 시뮬레이션합니다:

| 시나리오 | 비율 | 설명 |
|---------|------|------|
| 상품 조회 | 70% | `GET /api/products` - 가장 빈번한 작업 |
| 주문+결제 | 20% | `POST /api/orders` → `POST /api/orders/{id}/payment` |
| 쿠폰 발급 | 10% | `POST /api/coupons/{id}/issue` |

## 메트릭 해석

### 1. HTTP 요청 메트릭

```
http_reqs..................: 12000  200/s
http_req_duration..........: avg=150ms min=50ms med=120ms max=2s p(90)=250ms p(95)=400ms
http_req_failed............: 2.5% ✓ 300 ✗ 11700
```

**해석:**
- **http_reqs**: 총 12,000 요청, 초당 200 요청 (TPS = 200)
- **http_req_duration**: 평균 응답 시간 150ms, P95: 400ms
- **http_req_failed**: 실패율 2.5% (300건 실패)

### 2. 커스텀 메트릭

```
errors.....................: 2.5%
order_duration.............: avg=180ms p(95)=350ms
payment_duration...........: avg=250ms p(95)=500ms
coupon_duration............: avg=120ms p(95)=200ms
```

**해석:**
- **errors**: 전체 에러율 2.5%
- **order_duration**: 주문 생성 P95: 350ms
- **payment_duration**: 결제 처리 P95: 500ms

### 3. Threshold 검증

```
✓ http_req_duration........: p(95)<500ms  ✓
✓ http_req_failed..........: rate<0.05    ✓
✓ errors...................: rate<0.05    ✓
```

**해석:**
- ✓: Threshold 통과 (목표 달성)
- ✗: Threshold 실패 (성능 개선 필요)

## 성능 목표 (Threshold)

| 메트릭 | 목표 | 설명 |
|-------|------|------|
| P50 | < 200ms | 중앙값 응답 시간 |
| P95 | < 500ms | 95%의 요청이 500ms 이내 |
| P99 | < 1000ms | 99%의 요청이 1초 이내 |
| 성공률 | > 95% | HTTP 5xx 에러율 5% 미만 |
| 에러율 | < 5% | 비즈니스 에러 포함 |

## 문제 해결

### 1. 연결 실패

```
ERRO[0001] connection refused
```

**해결 방법:**
- 애플리케이션이 실행 중인지 확인: `curl http://localhost:8080/actuator/health`
- 포트가 올바른지 확인: `netstat -an | grep 8080`

### 2. 높은 에러율

```
http_req_failed: 25% ✗
```

**원인 분석:**
1. 데이터베이스 커넥션 풀 고갈
2. 재고 부족 (Product stock)
3. 잔액 부족 (User balance)
4. 쿠폰 소진 (Coupon quantity)

**해결 방법:**
```bash
# 데이터 초기화 후 재실행
./gradlew bootRun

# 또는 VU 수 감소
k6 run --vus 10 --duration 30s load-test.js
```

### 3. 타임아웃

```
http_req_duration: avg=5s max=30s
```

**원인:**
- 데이터베이스 쿼리 성능 문제
- N+1 문제 미해결
- 외부 API 지연 (PGService)

**해결 방법:**
- 쿼리 최적화 (EXPLAIN ANALYZE)
- 커넥션 풀 증가 (HikariCP)
- 인덱스 추가

## Prometheus 메트릭 확인

K6 테스트와 함께 애플리케이션 메트릭도 확인하세요:

```bash
# Prometheus 메트릭 확인
curl http://localhost:8080/actuator/prometheus

# 주문 성공/실패 카운터
curl http://localhost:8080/actuator/metrics/orders_total

# 주문 처리 시간 (P95)
curl http://localhost:8080/actuator/metrics/order_duration_seconds
```

## 최적화 전후 비교

### Before (최적화 전)

```
http_reqs: 100/s
http_req_duration: p(95)=800ms
http_req_failed: 8%
```

### After (최적화 후)

```
http_reqs: 200/s (+100% 🔥)
http_req_duration: p(95)=400ms (-50% 🔥)
http_req_failed: 2% (-75% 🔥)
```

## 참고 자료

- [K6 공식 문서](https://k6.io/docs/)
- [K6 메트릭 설명](https://k6.io/docs/using-k6/metrics/)
- [K6 Threshold](https://k6.io/docs/using-k6/thresholds/)
- [Micrometer 문서](https://micrometer.io/docs)
