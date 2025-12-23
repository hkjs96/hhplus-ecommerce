# Step14 쿠폰 예약 동시성 테스트 분석

## 📊 테스트 결과 (2025-12-07 18:00)

### 🚨 실패 원인 분석

**테스트 실패 메트릭:**
```
✗ http_req_failed: 89.70% (threshold: <10%)
✗ reservation_duration p(95): 420.95ms (threshold: <200ms)
✗ reservation responded: 9% (110 success / 1050 failed)
```

**애플리케이션 에러 로그:**
```
사용자를 찾을 수 없습니다. userId: 589
사용자를 찾을 수 없습니다. userId: 756
사용자를 찾을 수 없습니다. userId: 228
...
```

---

## 🔍 근본 원인

### 문제: userId 범위 불일치

**K6 테스트 스크립트 (test-data.js:42)**
```javascript
// Before (잘못된 범위)
export function randomUserId() {
  return Math.floor(Math.random() * 1000) + 10;  // 10 ~ 1009
}
```

**LoadTestDataInitializer (실제 생성된 사용자)**
```
- userId 1: K6 기본 테스트 사용자
- userId 1000-10999: extremeConcurrency (10,000명)
- userId 200000-200099: sequentialIssue (100명)
- userId 300000-309999: rampUpTest (10,000명)
```

**결과:**
- K6가 요청하는 userId: **10 ~ 1009**
- DB에 존재하는 userId: **1, 1000+**
- **범위가 전혀 맞지 않음!** ❌

**통계:**
- 총 1000번의 동시 예약 시도
- 성공: 110번 (우연히 존재하는 userId 호출)
- 실패: 1050번 (존재하지 않는 userId)
- 실패율: 89.70%

---

## ✅ 해결 방법

### 수정 사항: randomUserId() 범위 변경

**파일:** `test-data.js:41-44`

```javascript
// Before
export function randomUserId() {
  return Math.floor(Math.random() * 1000) + 10;  // 10 ~ 1009
}

// After
export function randomUserId() {
  // LoadTestDataInitializer가 생성하는 범위와 일치
  // userId 1000-10999 범위에서 랜덤 선택 (extremeConcurrency 사용자)
  return Math.floor(Math.random() * 10000) + 1000;  // 1000 ~ 10999
}
```

**변경 근거:**
- LoadTestDataInitializer가 userId 1000-10999 범위로 10,000명 생성
- 충분한 동시성 테스트를 위한 사용자 풀 확보
- step14 coupon concurrency 테스트에 적합

---

## 📈 예상 결과 (수정 후)

### Before (수정 전)
```
✗ http_req_failed: 89.70%
✗ reservation responded: 9% (110 / 1050 failed)
✗ reservation_duration p(95): 420.95ms
- 원인: 대부분 userId가 DB에 없음
```

### After (수정 후 예상)
```
✅ http_req_failed: < 10%
✅ reservation responded: > 90%
✅ reservation_duration p(95): < 200ms
✅ duplicate_prevention_rate: 100%
✅ sequence_accuracy_rate: 100%
✅ reservation_success_count: ~100 (쿠폰 수량 기준)
```

---

## 🎯 다음 단계

**1. K6 테스트 재실행**
```bash
k6 run docs/week7/loadtest/k6/step14-reservation-concurrency.js 2>&1 | tee /tmp/hhplus-logs/step14-k6-v3.log
```

**2. 확인 포인트**
- ✅ http_req_failed < 10%
- ✅ reservation responded > 90%
- ✅ duplicate_prevention_rate: 100%
- ✅ sequence_accuracy_rate: 100%
- ✅ "사용자를 찾을 수 없습니다" 에러 없음

**3. 성공 기준**
- 1000명 동시 예약 → 100명 성공 (쿠폰 수량)
- 900명 품절 응답 (정상)
- 중복 방지 100%
- 응답 시간 p95 < 200ms

---

## 🔍 성공한 부분 (수정 전에도 정상 동작)

실제 존재하는 userId로 테스트된 경우에는 완벽하게 동작:

```
✅ duplicate_prevention_rate: 100.00%
   - 중복 발급 방지 완벽

✅ sequence_accuracy_rate: 100.00%
   - 순서 번호 정확도 100%

✅ reservation_success_count: 100
   - 성공한 예약은 정상 처리

✅ initial balance charged
   - Setup phase 정상 동작
```

이는 **쿠폰 예약 비즈니스 로직은 정상**이며, 단지 테스트 데이터 범위 불일치 문제였음을 증명합니다.

---

## 🏆 결론

**문제:**
- K6 테스트 스크립트의 `randomUserId()` 범위가 실제 DB 사용자 범위와 불일치

**해결:**
- `randomUserId()` 범위를 1000-10999로 수정
- LoadTestDataInitializer가 생성하는 사용자와 일치

**예상 효과:**
- http_req_failed: 89.70% → < 10%
- 모든 threshold 통과 예상
- 쿠폰 예약 동시성 제어 완벽하게 검증 가능

**테스트를 재실행하시면 모든 메트릭이 정상으로 나올 것으로 예상됩니다!** 🎉
