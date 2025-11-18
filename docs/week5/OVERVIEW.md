# Week 5: 동시성 제어와 최적화 - 전체 개요

> **목표**: E-Commerce 시스템의 동시성 문제를 식별하고, DB 기반 동시성 제어 방식을 적용하여 데이터 정합성과 성능을 보장한다.

---

## 📌 학습 목표

1. **동시성 문제 식별**: Race Condition, Deadlock 등 실제 발생 가능한 동시성 이슈 파악
2. **DB 동시성 제어**: Transaction Isolation, Pessimistic/Optimistic Lock 이해 및 적용
3. **통합 테스트**: 동시성 문제를 재현하고 해결을 검증하는 테스트 작성
4. **성능 최적화**: 트랜잭션 경계 설정, 락 전략, 인덱스 최적화

---

## 🎭 전문가 페르소나 소개

본 문서는 5명의 시니어 개발자 관점에서 동시성 문제를 분석하고 해결책을 제시합니다.

### 1. **김데이터 (20년차, DBA 출신 백엔드 개발자)**
- **전문 분야**: Database Performance Tuning, Transaction Management
- **강점**: ACID 속성, 격리 수준, 인덱스 최적화에 대한 깊은 이해
- **관점**: "동시성 문제는 데이터베이스 레벨에서 원천적으로 해결해야 한다"
- **선호 방식**: Pessimistic Lock, Transaction Isolation Level 조정
- **핵심 가치**: 데이터 정합성 > 성능

### 2. **박트래픽 (15년차, 대용량 트래픽 처리 전문가)**
- **전문 분야**: High Traffic System, Distributed System Architecture
- **강점**: 대규모 동시 접속 환경에서의 성능 최적화 경험
- **관점**: "Lock은 최소화하고, 분산 환경에서도 확장 가능한 구조를 설계해야 한다"
- **선호 방식**: Optimistic Lock, Redis Distributed Lock, Queue 기반 처리
- **핵심 가치**: 성능 및 확장성 > 완벽한 일관성

### 3. **이금융 (12년차, 금융권 시스템 개발자)**
- **전문 분야**: Financial Transaction System, Mission-Critical Application
- **강점**: 결제, 정산 시스템에서의 Zero-Tolerance 에러 처리
- **관점**: "돈과 관련된 로직은 절대 실패하면 안 된다. 멱등성과 보상 트랜잭션이 필수다"
- **선호 방식**: Two-Phase Commit, Saga Pattern, Idempotency Key
- **핵심 가치**: 정확성 및 추적 가능성

### 4. **최아키텍트 (10년차, MSA 아키텍트)**
- **전문 분야**: Microservices Architecture, Event-Driven Design
- **강점**: 분산 시스템에서의 최종 일관성(Eventual Consistency) 설계
- **관점**: "강한 일관성은 비용이 크다. 비즈니스 특성에 맞는 일관성 수준을 선택해야 한다"
- **선호 방식**: Event Sourcing, Outbox Pattern, CQRS
- **핵심 가치**: 시스템 분리 및 느슨한 결합

### 5. **정스타트업 (7년차, 스타트업 CTO)**
- **전문 분야**: Rapid Development, Cost-Effective Solutions
- **강점**: 제한된 리소스에서 빠르게 MVP를 만들고 점진적 개선
- **관점**: "완벽한 설계보다 빠른 검증이 중요하다. 단순하게 시작해서 필요할 때 고도화한다"
- **선호 방식**: Application-Level Lock (synchronized), Simple Retry Logic
- **핵심 가치**: 개발 속도 및 유지보수성

---

## 🔍 E-Commerce 시스템의 주요 동시성 이슈

### 1. **재고 차감 (Stock Deduction)**
```
문제: 동시에 여러 사용자가 마지막 남은 재고 1개를 구매 시도
결과: 음수 재고 발생 (Over-selling)
영향: 실제 재고 없이 주문 발생 → 고객 불만, 환불 처리 비용
```

### 2. **선착순 쿠폰 발급 (First-Come Coupon Issuance)**
```
문제: 100개 한정 쿠폰에 200명이 동시 신청
결과: 100개를 초과하여 발급 (예: 105개)
영향: 비용 증가, 마케팅 예산 초과, 공정성 문제
```

### 3. **결제 처리 (Payment Processing)**
```
문제: 동일 주문에 대한 중복 결제 요청 (사용자 실수 또는 네트워크 재시도)
결과: 잔액이 2번 차감됨
영향: 고객 불만, 환불 처리, PG사 수수료 손실
```

### 4. **잔액 충전/차감 (Balance Update)**
```
문제: A가 잔액 충전하는 동시에 B가 자동 결제로 차감
결과: Lost Update (한쪽 업데이트가 사라짐)
영향: 잔액 불일치, 정산 오류
```

### 5. **주문 상태 변경 (Order Status Update)**
```
문제: 결제 완료와 배송 시작이 동시에 발생
결과: 상태 전이 순서가 어긋남 (PAID → SHIPPING 대신 SHIPPING → PAID)
영향: 물류 시스템 오류, 배송 지연
```

---

## 🛠️ 동시성 제어 방식 개요

### 비관적 락 (Pessimistic Lock)
- **방식**: 데이터 읽기 시점에 락을 획득 (`SELECT FOR UPDATE`)
- **장점**: 충돌 발생 시 대기만 하면 되므로 구현 단순
- **단점**: Lock Contention으로 성능 저하, Deadlock 위험
- **적합한 케이스**: 충돌이 자주 발생하는 경우 (재고 차감, 결제)

### 낙관적 락 (Optimistic Lock)
- **방식**: 데이터 읽기는 자유, 업데이트 시점에 버전 체크 (`@Version`)
- **장점**: Lock을 잡지 않아 성능 우수
- **단점**: 충돌 발생 시 재시도 필요, 충돌이 많으면 비효율
- **적합한 케이스**: 충돌이 드문 경우 (상품 정보 수정, 리뷰 작성)

### 분산 락 (Distributed Lock)
- **방식**: Redis, Zookeeper 등 외부 저장소를 활용한 락
- **장점**: 다중 인스턴스 환경에서도 동작
- **단점**: 추가 인프라 필요, 네트워크 지연
- **적합한 케이스**: 분산 환경에서 단일 작업 보장 (선착순 쿠폰)

### 혼합 전략 (Hybrid Approach)
- **방식**: 시나리오별로 다른 락 전략 조합
- **장점**: 각 상황에 최적화된 방식 적용
- **단점**: 복잡도 증가, 팀 학습 비용
- **적합한 케이스**: 대부분의 실무 시스템

---

## 📊 전문가별 추천 방식

| 시나리오 | 김데이터 (DBA) | 박트래픽 (성능) | 이금융 (정확성) | 최아키텍트 (MSA) | 정스타트업 (MVP) |
|---------|--------------|--------------|--------------|----------------|----------------|
| **재고 차감** | Pessimistic Lock | Optimistic Lock + Retry | Pessimistic Lock | Event Sourcing | Pessimistic Lock |
| **쿠폰 발급** | Pessimistic Lock | Redis Distributed Lock | Queue + Batch | Outbox Pattern | Synchronized |
| **결제 처리** | Serializable Isolation | Idempotency Key | 2PC / Saga | Saga Pattern | Idempotency Key |
| **잔액 업데이트** | Pessimistic Lock | Optimistic Lock | Pessimistic Lock | Event Sourcing | Pessimistic Lock |
| **주문 상태** | DB Constraint | Optimistic Lock | State Machine | Event Store | Enum Validation |

---

## 🎯 베스트 프랙티스 (합의된 방식)

### ✅ 재고 차감: **Pessimistic Lock (비관적 락)**
**근거:**
- 재고는 충돌이 자주 발생하는 Hot Spot
- 음수 재고는 절대 발생하면 안 됨 (비즈니스 크리티컬)
- 대부분의 전문가가 동의 (5명 중 4명)

**구현:**
```java
@Transactional
public void decreaseStock(Long productId, int quantity) {
    Product product = productRepository.findByIdWithLock(productId); // SELECT FOR UPDATE
    product.decreaseStock(quantity);
}
```

### ✅ 쿠폰 발급: **Redis Distributed Lock + DB 동기화**
**근거:**
- 선착순은 극도로 높은 동시성 발생
- 정확히 N개만 발급되어야 함
- Redis로 빠르게 처리 후 DB 비동기 동기화

**구현:**
```java
public void issueCoupon(Long couponId, Long userId) {
    RLock lock = redissonClient.getLock("coupon:" + couponId);
    if (lock.tryLock(1, 3, TimeUnit.SECONDS)) {
        try {
            // Redis에서 수량 체크 및 차감
            Long remaining = redisTemplate.opsForValue().decrement("coupon:" + couponId + ":stock");
            if (remaining >= 0) {
                // DB 비동기 저장
                saveCouponAsync(couponId, userId);
            }
        } finally {
            lock.unlock();
        }
    }
}
```

### ✅ 결제 처리: **Idempotency Key + Pessimistic Lock**
**근거:**
- 중복 결제는 절대 발생하면 안 됨
- 멱등성 키로 1차 방어, Lock으로 2차 방어
- 금융권 전문가의 경험 반영

**구현:**
```java
@Transactional
public PaymentResult processPayment(String idempotencyKey, PaymentRequest request) {
    // 1차: 멱등성 체크
    if (paymentRepository.existsByIdempotencyKey(idempotencyKey)) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey);
    }

    // 2차: 잔액 락 획득 후 차감
    User user = userRepository.findByIdWithLock(request.getUserId());
    user.deductBalance(request.getAmount());

    // 결제 기록
    Payment payment = Payment.create(idempotencyKey, request);
    return paymentRepository.save(payment);
}
```

### ✅ 잔액 업데이트: **Pessimistic Lock (단순 케이스) / Event Sourcing (복잡한 케이스)**
**근거:**
- 단일 잔액 업데이트: Pessimistic Lock으로 충분
- 복잡한 거래 (충전, 사용, 환불 동시): Event Sourcing으로 이력 관리

### ✅ 주문 상태: **Optimistic Lock + State Machine Validation**
**근거:**
- 상태 변경 충돌은 드물게 발생
- 상태 전이 규칙 검증이 더 중요
- 낙관적 락으로 성능 유지

---

## 📁 문서 구조

```
docs/week5/
├── OVERVIEW.md                    # ✅ 전체 개요 (현재 문서)
├── CONCURRENCY_ANALYSIS.md        # 동시성 문제 상세 분석
├── SOLUTION_COMPARISON.md         # 해결 방안 비교 (전문가 리뷰)
├── IMPLEMENTATION_GUIDE.md        # 구현 가이드 (코드 포함)
├── TEST_STRATEGY.md               # 테스트 전략 및 시나리오
├── PERFORMANCE_OPTIMIZATION.md    # 성능 측정 및 최적화
└── EXPERT_REVIEWS.md              # 전문가별 상세 리뷰
```

---

## 🚀 학습 로드맵

### Phase 1: 문제 이해 (STEP09 Day 1-2)
1. `CONCURRENCY_ANALYSIS.md` 읽고 동시성 문제 5가지 이해
2. 각 문제가 발생하는 시나리오 시각화
3. 본인 프로젝트에서 발생 가능한 케이스 식별

### Phase 2: 해결 방안 학습 (STEP09 Day 3-4)
1. `SOLUTION_COMPARISON.md` 읽고 5가지 동시성 제어 방식 비교
2. `EXPERT_REVIEWS.md`에서 전문가 의견 확인
3. 각 시나리오에 적합한 방식 선택 및 근거 작성

### Phase 3: 구현 (STEP09 Day 5-6)
1. `IMPLEMENTATION_GUIDE.md` 참고하여 코드 작성
2. Transaction 경계 설정
3. Lock 전략 적용
4. 예외 처리 및 롤백 로직 추가

### Phase 4: 테스트 (STEP10 Day 1-3)
1. `TEST_STRATEGY.md` 참고하여 동시성 테스트 작성
2. ExecutorService로 멀티스레드 시뮬레이션
3. 100회 반복 실행으로 안정성 검증

### Phase 5: 최적화 (STEP10 Day 4-5)
1. `PERFORMANCE_OPTIMIZATION.md` 참고하여 병목 지점 식별
2. 인덱스 추가, 트랜잭션 경계 조정
3. 부하 테스트로 성능 측정

---

## 💡 핵심 메시지

### 완벽한 해결책은 없다
- 모든 전문가가 다른 의견을 가질 수 있음
- 비즈니스 요구사항, 트래픽 규모, 팀 역량에 따라 선택이 달라짐
- **중요한 것은 선택의 근거를 명확히 설명할 수 있는 것**

### 트레이드오프 이해하기
- 성능 vs 정합성
- 단순함 vs 확장성
- 개발 속도 vs 유지보수성

### 점진적 개선
- 처음부터 완벽한 설계를 하지 않아도 됨
- 단순한 방식(Pessimistic Lock)으로 시작 → 병목 발생 시 최적화
- 모니터링과 측정을 통해 지속적으로 개선

---

## 📚 다음 문서

- **동시성 문제 분석**: [CONCURRENCY_ANALYSIS.md](./CONCURRENCY_ANALYSIS.md)
- **해결 방안 비교**: [SOLUTION_COMPARISON.md](./SOLUTION_COMPARISON.md)
- **구현 가이드**: [IMPLEMENTATION_GUIDE.md](./IMPLEMENTATION_GUIDE.md)
- **테스트 전략**: [TEST_STRATEGY.md](./TEST_STRATEGY.md)
- **성능 최적화**: [PERFORMANCE_OPTIMIZATION.md](./PERFORMANCE_OPTIMIZATION.md)
- **전문가 리뷰**: [EXPERT_REVIEWS.md](./EXPERT_REVIEWS.md)

---

**작성일**: 2025-11-18
**버전**: 1.0
**작성자**: HH+ E-Commerce Team
