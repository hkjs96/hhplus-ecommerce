# Kafka Use Cases (STEP 18)

> **목표**: STEP 18에서 요구하는 “비즈니스 프로세스 개선”을 Kafka 기반 비동기로 설계할 때, 최소한의 설계 축(토픽/키/파티션/컨슈머그룹/멱등성/DLQ)을 빠르게 정리한다.

---

## 📋 목차

1. [Use Case 1: 쿠폰 발급 비동기화](#use-case-1-쿠폰-발급-비동기화)
2. [Use Case 2: 대기열(Queue) 처리](#use-case-2-대기열queue-처리)
3. [설계 체크리스트](#설계-체크리스트)
4. [참고 다이어그램](#참고-다이어그램)

---

## Use Case 1: 쿠폰 발급 비동기화

### 목표
- “요청을 받는 API”와 “쿠폰 발급 처리”를 분리해 응답 지연을 줄이고, 재시도/지연/폭주 상황을 안전하게 처리한다.

### 권장 이벤트(예시)
- `CouponIssueRequested` (요청 수신 이벤트)
- `CouponIssued` / `CouponIssueFailed` (처리 결과 이벤트)

### 토픽/키/파티션(예시)
- Topic: `coupon.issue.requested`
- Key: `couponId` 또는 `userId` (정렬이 필요한 단위를 기준으로 선택)
- Partition: 처리량(병렬성)과 정렬(순서 보장) 트레이드오프를 명시

### 컨슈머 그룹(예시)
- Group: `coupon-processor`
- 동시성: 파티션 수 범위 내에서만 scale-out 가능

### 멱등성(필수)
- “중복 메시지”를 허용하고도 결과가 1번만 반영되도록 설계한다.
  - 예: `idempotencyKey = (userId, couponId)` 또는 이벤트의 `eventId`
  - DB Unique Key 또는 멱등성 테이블로 중복 처리 방지

### 실패/재시도/DLQ(권장)
- 일시 장애(네트워크/Redis/MySQL 등): 제한 재시도(백오프)
- 비즈니스 실패(재고 없음/정책 위반): 재시도하지 않고 실패 이벤트 발행
- 반복 실패/역직렬화 실패: DLQ로 분리

---

## Use Case 2: 대기열(Queue) 처리

### 목표
- “선착순” 또는 “처리 순서”가 중요한 작업을 큐로 직렬화하거나, 파티션 단위로 순서를 보장한다.

### 토픽/키(예시)
- Topic: `queue.task.created`
- Key: `queueId` 또는 `userId`

### 순서 보장 전략
- 같은 Key → 같은 Partition → 같은 Consumer Instance에서 순서가 보장된다.
- “전체 순서”가 필요하면 파티션 1개(처리량 제한) 또는 별도 시퀀싱 전략이 필요하다.

### 소비 모델(예시)
- `at-least-once` 기반으로 설계하고, 컨슈머는 멱등하게 만든다.

---

## 설계 체크리스트

- [ ] 이 이벤트가 “도메인 이벤트/애플리케이션 이벤트/통합 이벤트” 중 무엇인지 정의했는가?
- [ ] Topic 이름/스키마/Key/Partition 기준을 문서에 명시했는가?
- [ ] 정렬이 필요한 단위(사용자/쿠폰/주문 등)를 정하고 Key에 반영했는가?
- [ ] 멱등성 전략(DB Unique/멱등 테이블/Outbox 등)을 정했는가?
- [ ] 재시도/백오프/최대 시도 횟수/DLQ 기준이 있는가?
- [ ] 모니터링 지표(consumer lag, 처리량, 실패율)가 있는가?

---

## 참고 다이어그램

- 쿠폰 발급 시퀀스: `docs/week9/diagrams/coupon-issuance-kafka.md`
- 대기열 처리 시퀀스: `docs/week9/diagrams/queue-processing-kafka.md`

