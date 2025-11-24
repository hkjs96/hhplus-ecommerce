# JMeter Performance Test Plans

제이 코치 피드백 반영 (Priority 4): 성능 측정 및 Before/After 비교

---

## 디렉토리 구조

```
jmeter/
├── README.md                    # 이 파일
├── testplans/                   # JMeter 테스트 플랜 (.jmx 파일)
│   ├── balance-charge.jmx       # 잔액 충전 테스트
│   ├── order-create.jmx         # 주문 생성 테스트
│   └── payment-process.jmx      # 결제 처리 테스트
├── results/                     # 테스트 결과 (.jtl 파일)
│   ├── before/                  # 개선 전 결과
│   └── after/                   # 개선 후 결과
└── reports/                     # HTML 리포트
    ├── before/                  # 개선 전 리포트
    └── after/                   # 개선 후 리포트
```

---

## 빠른 시작

### 1. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 2. JMeter GUI로 테스트 플랜 열기

```bash
jmeter -t testplans/balance-charge.jmx
```

### 3. CLI로 테스트 실행 (권장)

```bash
# 잔액 충전 테스트
jmeter -n -t testplans/balance-charge.jmx \
       -l results/after/balance-charge-results.jtl \
       -e -o reports/after/balance-charge/

# 주문 생성 테스트
jmeter -n -t testplans/order-create.jmx \
       -l results/after/order-create-results.jtl \
       -e -o reports/after/order-create/

# 결제 처리 테스트
jmeter -n -t testplans/payment-process.jmx \
       -l results/after/payment-process-results.jtl \
       -e -o reports/after/payment-process/
```

### 4. HTML 리포트 확인

```bash
open reports/after/balance-charge/index.html
```

---

## 테스트 시나리오

### balance-charge.jmx
- **목적**: Optimistic Lock + 재시도 로직 성능 측정
- **API**: `POST /api/users/{userId}/balance/charge`
- **동시 사용자**: 100명
- **Ramp-Up**: 10초
- **예상 결과**: 에러율 0%, TPS 90

### order-create.jmx
- **목적**: Pessimistic Lock + 타임아웃 성능 측정
- **API**: `POST /api/orders`
- **동시 사용자**: 100명
- **Ramp-Up**: 10초
- **예상 결과**: 평균 응답 시간 400ms, 최대 3.5초

### payment-process.jmx
- **목적**: Idempotency Key 중복 결제 방지 확인
- **API**: `POST /api/orders/{orderId}/payment`
- **동시 요청**: 10회 (동일 멱등성 키)
- **예상 결과**: 1번만 성공, 9번 실패 (정상)

---

## Before/After 측정 방법

### Before (개선 전)

```bash
# 1. 개선 전 커밋으로 체크아웃
git checkout <before-commit-hash>

# 2. 빌드 및 실행
./gradlew clean build
./gradlew bootRun

# 3. 테스트 실행
jmeter -n -t testplans/balance-charge.jmx \
       -l results/before/balance-charge-results.jtl \
       -e -o reports/before/balance-charge/
```

### After (개선 후)

```bash
# 1. 개선 후 커밋으로 체크아웃
git checkout <after-commit-hash>

# 2. 빌드 및 실행
./gradlew clean build
./gradlew bootRun

# 3. 테스트 실행
jmeter -n -t testplans/balance-charge.jmx \
       -l results/after/balance-charge-results.jtl \
       -e -o reports/after/balance-charge/
```

---

## 주요 메트릭

### Application 메트릭
- **TPS (Transactions Per Second)**: 초당 처리량
- **Average Response Time**: 평균 응답 시간
- **Max Response Time**: 최대 응답 시간
- **Error Rate**: 에러율 (%)
- **Throughput**: 처리량 (requests/sec)

### Database 메트릭
```sql
-- Lock Wait 확인
SHOW ENGINE INNODB STATUS;

-- 실행 중인 쿼리 확인
SHOW FULL PROCESSLIST;

-- 락 대기 상황 확인
SELECT * FROM performance_schema.data_locks;
SELECT * FROM performance_schema.data_lock_waits;
```

---

## 트러블슈팅

### Connection Refused
- 애플리케이션이 실행 중인지 확인
- 포트 8080이 사용 가능한지 확인

### Out of Memory
- JMeter 힙 메모리 증가: `export HEAP="-Xms1g -Xmx1g"`

### 테스트 결과가 이상함
- 데이터베이스 초기 상태 확인 (테스트 데이터 리셋)
- 이전 테스트의 잔여 데이터 정리

---

## 참고 자료

- [JMETER_PERFORMANCE_TEST_GUIDE.md](../JMETER_PERFORMANCE_TEST_GUIDE.md) - 상세 가이드
- [Apache JMeter 공식 문서](https://jmeter.apache.org/usermanual/index.html)
