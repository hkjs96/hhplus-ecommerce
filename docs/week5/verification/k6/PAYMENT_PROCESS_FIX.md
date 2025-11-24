# Payment Process 테스트 실패 분석 및 해결

**테스트 일시**: 2025-11-24
**문제**: 100% 주문 생성 실패 (409 Conflict)

---

## 🚨 문제 상황

### 테스트 결과

```
총 Iteration: 20,827
주문 생성 실패: 100% (20,827건)
멱등성 검증: 0건 (불가능)
HTTP 실패율: 80.61%
에러: 409 Conflict
```

### 로그 분석

```
INFO[0214] [VU 4, Iter 161] Order creation failed: 409
INFO[0214] [VU 4, Iter 161] Order creation failed: 409
INFO[0214] [VU 4, Iter 161] Failed to create order after 3 retries
```

**패턴**:
- 모든 VU에서 409 에러 발생
- 재시도 3회 모두 실패
- 주문 생성 단계에서 차단

---

## 🔍 근본 원인 분석

### 원인 1: 잔액 부족 ⭐⭐⭐ (가장 유력)

**계산**:
```
총 요청: 20,827
결제 금액: 50,000원/건
필요 총액: 1,041,350,000원 (약 10억)

사용자 수: 100명
사용자당 평균: 208회 결제
사용자당 필요 금액: 10,413,500원 (약 1천만원)
```

**현재 잔액 추정**: 1,000,000원 (100만원)
**부족 금액**: 9,413,500원

**결론**: 잔액 부족으로 주문 생성 실패 (409)

---

### 원인 2: 재고 소진 (가능성 낮음)

**반박 근거**:
- order-create.js는 89.91% 성공률
- 재고가 충분했다면 payment-process.js도 성공해야 함
- 하지만 100% 실패 → 재고 문제 아님

---

### 원인 3: API 검증 로직 문제

**가능성**:
- 결제 API의 잔액 검증이 주문 생성 시점에 실행
- 잔액 < 주문 금액 → 409 반환
- 정상적인 비즈니스 로직

---

## ✅ 해결 방안

### 방안 1: 사용자 잔액 대폭 증가 ⭐ **권장**

**필요 금액 계산**:
```
테스트 규모: 200 VU × 3.5분 × 평균 1 req/s = 약 40,000 요청
사용자 수: 100명
사용자당 요청: 400회
사용자당 필요 금액: 400 × 50,000 = 20,000,000원 (2천만원)

안전 마진 2배: 40,000,000원 (4천만원)
```

**SQL**:
```sql
-- 사용자 잔액을 4천만원으로 증가
UPDATE user_balance
SET balance = 40000000
WHERE user_id BETWEEN 1 AND 100;
```

**예상 효과**:
- 주문 생성 성공률 100%
- 멱등성 검증 가능
- 테스트 목적 달성

---

### 방안 2: 테스트 규모 축소

**현재 설정**:
```javascript
stages: [
  { duration: '30s', target: 100 },
  { duration: '1m', target: 100 },
  { duration: '30s', target: 200 },
  { duration: '1m', target: 200 },
  { duration: '30s', target: 0 },
]
```

**축소안**:
```javascript
stages: [
  { duration: '30s', target: 50 },
  { duration: '1m', target: 50 },
  { duration: '30s', target: 100 },
  { duration: '1m', target: 100 },
  { duration: '30s', target: 0 },
]
```

**예상 효과**:
- 요청 수 50% 감소
- 필요 잔액 50% 감소
- 사용자당 1천만원으로 가능

---

### 방안 3: 결제 금액 감소

**현재 금액**:
```javascript
const payload = JSON.stringify({
  amount: 50000,  // 5만원
});
```

**감소안**:
```javascript
const payload = JSON.stringify({
  amount: 10000,  // 1만원 (5배 감소)
});
```

**예상 효과**:
- 필요 잔액 5배 감소
- 사용자당 200만원으로 가능
- 테스트 목적은 동일

---

### 방안 4: 결제 전 잔액 충전 (추천하지 않음)

**이유**:
- 테스트 복잡도 증가
- 충전 API 부하 추가
- 멱등성 테스트 목적과 무관

---

## 🎯 권장 솔루션

### 최종 권장: 방안 1 + 방안 3 조합

**1. 잔액 증가 (적당히)**:
```sql
UPDATE user_balance
SET balance = 10000000  -- 1천만원
WHERE user_id BETWEEN 1 AND 100;
```

**2. 결제 금액 감소**:
```javascript
const payload = JSON.stringify({
  amount: 10000,  // 5만원 → 1만원
});
```

**예상 결과**:
```
사용자당 잔액: 10,000,000원
사용자당 평균 요청: 200회
사용자당 필요 금액: 200 × 10,000 = 2,000,000원
안전 마진: 5배 (충분)
```

---

## 📝 실행 가이드

### Step 1: 잔액 증가

```sql
-- MySQL 접속
mysql -u root -p ecommerce

-- 잔액 증가
UPDATE user_balance
SET balance = 10000000
WHERE user_id BETWEEN 1 AND 100;

-- 확인
SELECT user_id, balance
FROM user_balance
WHERE user_id BETWEEN 1 AND 10;
```

---

### Step 2: 스크립트 수정 (선택)

```bash
# payment-process.js 수정
# Line 201-205
const payload = JSON.stringify({
  userId: getRandomUserId(),
  amount: 10000,  // 50000 → 10000
  idempotencyKey: idempotencyKey,
});
```

---

### Step 3: 테스트 재실행

```bash
# 기본 실행
k6 run docs/week5/verification/k6/scripts/payment-process.js

# 규모 축소 (선택)
k6 run --vus 100 --duration 2m \
  docs/week5/verification/k6/scripts/payment-process.js
```

---

## 🎯 예상 결과

### After Fix

```
총 Iteration: ~20,000
주문 생성 성공: >80% (16,000+)
멱등성 검증: >5,000건
Idempotency Conflict: >10,000건
중복 결제 방지: >10,000건
```

### Threshold 예상

```
✅ http_req_duration p(95): < 1s
✅ idempotency_verification_success: count > 0
✅ duplicate_payments_prevented: count > 0
```

---

## 💡 추가 개선 사항

### 1. 동적 잔액 모니터링

테스트 중 잔액을 실시간으로 모니터링:

```javascript
export function setup() {
  // 테스트 시작 전 잔액 확인
  const balanceCheckUrl = `${BASE_URL}/api/users/1/balance`;
  const response = http.get(balanceCheckUrl);

  const balance = JSON.parse(response.body).balance;
  console.log(`User 1 balance: ${balance}`);

  if (balance < 10000000) {
    console.warn(`Insufficient balance: ${balance}. Recommend 10,000,000+`);
  }
}
```

---

### 2. 잔액 부족 에러 구분

```javascript
if (response.status === 409) {
  const body = response.body || '';

  if (body.includes('잔액') || body.includes('balance')) {
    // 잔액 부족
    console.log(`[VU ${__VU}] Insufficient balance`);
  } else if (body.includes('재고') || body.includes('stock')) {
    // 재고 소진
    console.log(`[VU ${__VU}] Stock depleted`);
  } else {
    // 기타
    console.log(`[VU ${__VU}] Other 409: ${body}`);
  }
}
```

---

### 3. 결제 금액 랜덤화 (선택)

```javascript
function getRandomAmount() {
  const amounts = [10000, 20000, 30000, 50000];
  return amounts[Math.floor(Math.random() * amounts.length)];
}

const payload = JSON.stringify({
  userId: getRandomUserId(),
  amount: getRandomAmount(),  // 랜덤 금액
  idempotencyKey: idempotencyKey,
});
```

---

## 🎓 학습 포인트

### 1. 테스트 데이터 준비의 중요성

**교훈**:
- 부하 테스트는 충분한 테스트 데이터 필요
- 잔액, 재고 등을 테스트 규모에 맞게 준비
- 사전 계산으로 필요량 예측

---

### 2. 에러 메시지 분석

**409 Conflict의 다양한 원인**:
- 잔액 부족
- 재고 소진
- 멱등성 키 중복
- 주문 상태 불일치

**해결 방법**:
- 에러 메시지 로그 추가
- API 응답 바디 분석
- 비즈니스 로직 이해

---

### 3. 테스트 설계 시 고려사항

**체크리스트**:
- [ ] 테스트 규모 (VU, Duration)
- [ ] 필요한 리소스 (잔액, 재고)
- [ ] API 제약 조건 (잔액 검증)
- [ ] 테스트 목적 (멱등성 검증)
- [ ] 데이터 준비 (충분한 잔액)

---

## 📊 다음 단계

### 1. 잔액 증가 ✅ (필수)

```sql
UPDATE user_balance
SET balance = 10000000
WHERE user_id BETWEEN 1 AND 100;
```

### 2. 테스트 재실행 ⏳

```bash
k6 run docs/week5/verification/k6/scripts/payment-process.js
```

### 3. 결과 검증 ⏳

- [ ] 주문 생성 성공률 >80%
- [ ] 멱등성 검증 >1000건
- [ ] 중복 결제 방지 확인

### 4. 문서 업데이트 ⏳

- [ ] 실제 테스트 결과 기록
- [ ] Before/After 비교
- [ ] README.md 업데이트

---

**작성자**: Claude Code
**버전**: 1.0
**다음 작업**: 잔액 증가 후 테스트 재실행
