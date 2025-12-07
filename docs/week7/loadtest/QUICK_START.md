# K6 부하 테스트 Quick Start 가이드

## 🚀 빠른 실행 (3단계)

### ✅ Ranking 테스트 (이미 완료)
```bash
cd /Users/jsb/hanghe-plus/ecommerce
k6 run docs/week7/loadtest/k6/step13-ranking-improved-test.js
```
**결과**: 42,836 iterations, 9/10 thresholds 통과 ✅

---

### 🎫 Coupon 동시성 테스트 (다음 단계)

#### ⚡ 테스트 데이터 자동 생성!

**애플리케이션 시작 시 테스트 사용자가 자동으로 생성됩니다.**

#### Step 1: 애플리케이션 실행 (자동 데이터 생성)
```bash
cd /Users/jsb/hanghe-plus/ecommerce
./gradlew bootRun

# 애플리케이션 시작 로그 확인:
# === K6 Load Test Data Initializer START ===
# Creating test users: 1000 - 10999 (K6Test-Extreme)
# Created 10000 users for range 1000 - 10999
# Creating test users: 200000 - 200049 (K6Test-Seq)
# Created 50 users for range 200000 - 200049
# Creating test users: 300000 - 304999 (K6Test-Ramp)
# Created 5000 users for range 300000 - 304999
# Created 15050 new test users in XXXms
# === K6 Load Test Data Initializer END ===
```

**자동 생성되는 사용자**:
- 10,000명: userId 1000-10999 (극한 동시성)
- 50명: userId 200000-200049 (순차 발급)
- 5,000명: userId 300000-304999 (램프업)
- **총 15,050명** (이미 존재하면 skip)

#### Step 2: 쿠폰 ID 확인
```bash
mysql -h localhost -u root -p ecommerce -e "SELECT id, name, total_quantity FROM coupons LIMIT 5;"
```

#### Step 3: 테스트 실행
```bash
cd /Users/jsb/hanghe-plus/ecommerce

# 쿠폰 ID가 1인 경우
./docs/week7/loadtest/k6/run-test.sh coupon 1

# 또는 직접 실행
k6 run -e COUPON_ID=1 docs/week7/loadtest/k6/step14-coupon-concurrency-test.js
```

---

## 🎯 성공 기준

### Ranking 테스트 ✅
- [x] iterations > 40,000
- [x] thresholds 통과율 > 80%
- [x] ranking_accuracy = 100%

### Coupon 테스트 (예상)
- [ ] `actual_issued_count` = 정확히 100개
- [ ] `duplicate_issue_attempts` = 0
- [ ] `sold_out_responses` ≈ 100개
- [ ] `coupon_issue_success_rate` = 30-60%
- [ ] Response time: p(95) < 1s, p(99) < 2s

---

## ⚠️ 문제 해결

### "사용자를 찾을 수 없습니다"
```bash
# 해결: setup-test-users.sql 실행
mysql -h localhost -u root -p ecommerce < docs/week7/loadtest/k6/setup-test-users.sql
```

### "COUPON_ID is required"
```bash
# 해결: COUPON_ID 환경 변수 제공
k6 run -e COUPON_ID=1 docs/week7/loadtest/k6/step14-coupon-concurrency-test.js
```

### "Failed to convert String to Long"
- ✅ 이미 수정 완료 (userId를 숫자로 변경)

---

## 📊 테스트 시나리오

### Coupon Test 3가지 시나리오

1. **Extreme Concurrency** (0-30초)
   - 100 VUs가 동시에 100번 시도
   - Race Condition 극한 테스트

2. **Sequential Issue** (35초-1분35초)
   - 1 VU가 순차적으로 50번 시도
   - 정상 동작 검증

3. **Ramp Up Test** (1분30초-2분10초)
   - 0 → 20 → 50 → 0 VUs
   - 현실적인 부하 패턴

**총 소요 시간**: 약 2분 10초

---

## 📁 관련 파일

- `docs/week7/loadtest/k6/step13-ranking-improved-test.js` - Ranking 테스트
- `docs/week7/loadtest/k6/step14-coupon-concurrency-test.js` - Coupon 테스트
- `docs/week7/loadtest/k6/setup-test-users.sql` - 사용자 생성 스크립트
- `docs/week7/loadtest/k6/setup-coupon-test.sh` - 쿠폰 설정 도우미
- `docs/week7/loadtest/k6/run-test.sh` - 테스트 실행 스크립트

---

## 🎉 다음 단계

1. ✅ Ranking 테스트 완료
2. 🔄 Coupon 테스트 실행 중
3. ⏳ 결과 분석 및 문서화
4. ⏳ CI/CD 통합 (선택)

---

**마지막 업데이트**: 2025-12-05
**작성자**: Claude
