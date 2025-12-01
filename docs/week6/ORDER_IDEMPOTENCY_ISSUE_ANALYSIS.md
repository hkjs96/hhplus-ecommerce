# Order Idempotency Issue 분석 및 현황

**작성일**: 2025-12-01
**상태**: 제한사항 인정 및 문서화

---

## 📊 테스트 결과

| 항목 | 결과 | 목표 | 상태 |
|-----|-----|-----|-----|
| 총 요청 | 10,407 | - | ✅ |
| 실패율 | 7.34% | < 1% | ❌ |
| Idempotency 에러 | 2,643 | < 100 | ❌ |
| 평균 응답 시간 | 2,982ms | < 1000ms | ❌ |
| P95 응답 시간 | 30,007ms | < 500ms | ❌ |

**결론**: 목표 달성 실패

---

## 🔍 근본 원인 분석

### 1. 문제의 핵심

**`REQUIRES_NEW` 트랜잭션 + 분산락의 타이밍 문제**

```java
@Transactional
@DistributedLock(leaseTime = 60s)
public OrderResponse execute(Request request) {
    // 1. 분산락 획득

    // 2. IdempotencySaveService.saveProcessing() 호출
    //    → @Transactional(REQUIRES_NEW) → 빠르게 커밋

    // 3. 주문 처리 (시간 소요)

    // 4. 분산락 해제
}
```

**Race Condition 시나리오**:
1. 요청 A: 분산락 획득 → Idempotency 저장 (REQUIRES_NEW로 빠르게 커밋)
2. 요청 B: 분산락 대기 중
3. 요청 A: REQUIRES_NEW 트랜잭션 커밋 완료
4. **데이터베이스에 idempotency 키 저장됨**
5. 요청 C: 같은 시점에 분산락을 획득하려고 시도 (타이밍 이슈)
6. **요청 B와 C가 동시에 같은 idempotency 레코드에 접근 시도**
7. `Lock wait timeout exceeded` 또는 `StaleObjectStateException`

### 2. 시도한 해결책들

| 방법 | 결과 | 문제점 |
|------|------|--------|
| `REQUIRES_NEW` 제거 | ❌ 실패 | `Entry has null identifier` 에러 |
| Pessimistic Lock 추가 | ❌ 실패 | 타임아웃 급증 (60s) |
| Lease Time 증가 (60s) | ⚠️ 개선 미미 | 7.75% → 7.34% |
| Wait Time 증가 | ❌ 실패 | 테스트 시간만 증가 |

### 3. 기술적 제약사항

**분산 환경에서의 한계**:
- Redis 분산락은 **네트워크 레이턴시**가 존재
- AOP 기반 분산락은 **메서드 경계**에서만 작동
- `REQUIRES_NEW`는 **별도 트랜잭션**으로 빠르게 커밋
- 데이터베이스 Unique Constraint는 **INSERT 시점 락** 발생

---

## 💡 근본적 해결 방안 (향후 개선)

### Option 1: 멱등성 키를 메인 트랜잭션에 포함

**변경**:
```java
@Transactional  // REQUIRES_NEW 제거
public OrderIdempotency saveProcessing(OrderIdempotency idempotency) {
    return idempotencyRepository.save(idempotency);
}
```

**장점**:
- 주문 생성과 멱등성 키가 하나의 트랜잭션
- 원자성 보장

**단점**:
- 주문 실패 시 멱등성 키도 롤백
- 재시도 시 멱등성 보장 불가

### Option 2: Database Unique Constraint 활용

**변경**:
```sql
CREATE UNIQUE INDEX idx_idempotency_key ON order_idempotency(idempotency_key);

-- Application에서
INSERT IGNORE INTO order_idempotency ...
```

**장점**:
- 분산락 불필요
- Database 레벨에서 멱등성 보장

**단점**:
- Database 종속적
- `INSERT IGNORE`는 MySQL 전용

### Option 3: 2PC (Two-Phase Commit)

**변경**:
- XA Transaction 사용
- 멱등성 키와 주문을 2단계로 커밋

**장점**:
- 강력한 일관성 보장

**단점**:
- 복잡도 증가
- 성능 저하

### Option 4: Saga 패턴

**변경**:
- 이벤트 기반 멱등성 관리
- 비동기 보상 트랜잭션

**장점**:
- 확장성 우수

**단점**:
- 복잡도 매우 높음
- 6주차 범위 초과

---

## 📝 현재 구현 상태

### 적용된 동시성 제어

1. **Redis 분산락**
   - Key: `order:create:idem:{idempotencyKey}`
   - waitTime: 10s
   - leaseTime: 60s

2. **`REQUIRES_NEW` 트랜잭션**
   - 멱등성 키 빠른 저장
   - 주문 실패 시에도 저장 유지

3. **Database Unique Constraint**
   - `order_idempotency.idempotency_key` UNIQUE

### 제한사항

- ⚠️ **고부하 환경에서 7% 정도의 실패율 발생**
- ⚠️ **평균 응답 시간 3초 (P95: 30초)**
- ⚠️ **동시 요청 100+ 시 Lock Timeout 가능**

---

## ✅ 6주차 과제 수준에서의 결론

### 구현 완료 항목

1. ✅ 멱등성 키 기반 중복 요청 방지 설계
2. ✅ 분산락을 이용한 동시성 제어 시도
3. ✅ `REQUIRES_NEW` 트랜잭션으로 멱등성 키 저장
4. ✅ K6 부하 테스트 실행 및 문제 발견

### 제한사항 인정

1. ⚠️ **높은 동시성 환경에서 7% 실패율** - 허용 가능 범위 초과
2. ⚠️ **평균 응답 시간 3초** - 사용자 경험 저하
3. ⚠️ **근본적 해결은 아키텍처 재설계 필요**

### 학습 성과

1. ✅ 분산 환경에서의 멱등성 보장의 어려움 이해
2. ✅ `REQUIRES_NEW` 트랜잭션의 한계 학습
3. ✅ 분산락과 데이터베이스 락의 차이 이해
4. ✅ K6 부하 테스트를 통한 실제 문제 발견

---

## 🚀 다음 단계 (향후 개선)

1. **Option 2 (Database Unique Constraint) 적용 시도**
   - 가장 간단하고 효과적
   - MySQL `INSERT ... ON DUPLICATE KEY UPDATE` 활용

2. **Optimistic Lock 재시도 로직 강화**
   - 현재 Facade에 있는 재시도를 더 정교하게

3. **모니터링 강화**
   - Idempotency 실패 메트릭 수집
   - 알람 설정

---

**작성자**: Claude Code
**참고**: 이 문서는 6주차 부하 테스트 과제의 일환으로 작성되었습니다.
