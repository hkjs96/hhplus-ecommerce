# Week 9: Kafka를 활용한 이벤트 기반 아키텍처

> **학습 기간**: 9주차
> **주제**: Kafka 기초 학습 및 비즈니스 프로세스 개선
> **목표**: 대규모 실시간 데이터 스트리밍을 위한 분산 메시징 시스템 이해 및 적용

---

## 📋 목차

1. [주차 개요](#주차-개요)
2. [학습 목표](#학습-목표)
3. [과제 구성](#과제-구성)
4. [문서 구조](#문서-구조)
5. [평가 기준](#평가-기준)
6. [참고 자료](#참고-자료)

---

## 📌 주차 개요

### 왜 Kafka인가?

지난 주차까지 우리는 Application Event와 TransactionalEventListener를 통해 도메인 간 결합도를 낮추고 트랜잭션을 분리하는 방법을 학습했습니다. 하지만 여전히 몇 가지 한계가 있습니다:

1. **데이터 전송 실패 시 책임 문제**
   - 외부 데이터 플랫폼 API 호출 실패 시 재전송 로직 필요
   - 일시적인 장애 상황에서도 데이터 유실 가능

2. **대용량 트래픽 처리의 한계**
   - 동시성 제어를 위한 분산 락 사용의 복잡도
   - 순차 처리 보장과 처리량 향상의 트레이드오프

3. **서비스 간 강결합**
   - 이벤트 발행자가 구독자의 상태를 신경 써야 함
   - 구독자의 장애가 발행자에게 영향

**Kafka는 이러한 문제들을 어떻게 해결할까요?**

```
[Before - Application Event]
OrderService --event--> EventListener --API--> DataPlatform (실패 시 재시도?)
                                            --> NotificationService (실패 시?)

[After - Kafka]
OrderService --publish--> Kafka Topic --subscribe--> DataPlatform (자기 속도로 처리)
                                     --subscribe--> NotificationService (독립적 처리)
```

### Kafka의 핵심 가치

1. **책임 분리**: 메시지 발행 후 구독자의 상태는 구독자의 책임
2. **높은 처리량**: 파티션 기반 병렬 처리로 대용량 트래픽 대응
3. **순서 보장**: 파티션 단위 순차 처리로 동시성 제어 가능
4. **내구성**: 디스크 기반 저장으로 데이터 유실 방지
5. **확장성**: Consumer Group을 통한 수평 확장

---

## 🎯 학습 목표

### STEP 17: Kafka 기초 학습 및 활용

**목표**: Kafka의 구성 요소와 메시지 흐름을 이해하고, 실제 애플리케이션에 적용

#### 학습 내용
- [ ] Kafka 핵심 개념 이해 (Broker, Topic, Partition, Producer, Consumer)
- [ ] 로컬 환경에 Kafka 설치 및 실행
- [ ] Spring Boot와 Kafka 연동
- [ ] 주문/예약 완료 이벤트를 Kafka로 발행
- [ ] Consumer를 통한 메시지 소비 및 처리

#### 산출물
- Kafka 개념 정리 문서 (`kafka-basics.md`)
- Kafka 설치 및 실행 가이드 (`kafka-setup.md`)
- Spring Boot 연동 예제 코드
- 실행 로그 및 테스트 결과

### STEP 18: Kafka를 활용한 비즈니스 프로세스 개선

**목표**: Kafka의 특징(파티셔닝, 병렬 처리)을 활용하여 대용량 트래픽 프로세스 개선

#### 학습 내용
- [ ] 선착순 쿠폰 발급: 파티션 기반 병렬 처리 + 순서 보장
- [ ] 콘서트 대기열: 순차 처리 보장 전략
- [ ] 메시지 키 기반 파티셔닝 전략
- [ ] Consumer 병렬 처리 설계
- [ ] DLQ(Dead Letter Queue) 처리

#### 산출물
- 비즈니스 프로세스 설계 문서 (`kafka-use-cases.md`)
- 시퀀스 다이어그램 (Mermaid)
- Kafka 구성도 (Topic, Partition, Consumer Group)
- 성능 개선 결과 정리

---

## 📚 과제 구성

### 기본 과제 (Pass 필수)

#### STEP 17 체크리스트
- [ ] Kafka 개념 문서 작성 (핵심 구성 요소 설명)
- [ ] Docker Compose로 Kafka 실행
- [ ] Producer: 주문 완료 이벤트 발행
- [ ] Consumer: 메시지 수신 및 로그 출력
- [ ] 트랜잭션 커밋 후 메시지 발행 검증

**Pass 조건**
- Kafka 구성 요소를 정확히 설명
- 애플리케이션에서 메시지 발행/소비 성공
- 트랜잭션 커밋 후 메시지 발행 확인

#### STEP 18 체크리스트
- [ ] 선착순 쿠폰 또는 대기열 중 하나 선택
- [ ] Kafka 기반 설계 문서 작성
- [ ] 파티션 전략 및 Consumer 구성 명시
- [ ] 시퀀스 다이어그램 작성
- [ ] 코드 구현 및 테스트

**Pass 조건**
- Kafka를 활용한 명확한 설계
- 파티션/Consumer 전략이 비즈니스 요구사항과 부합
- 동작하는 코드와 테스트

### 심화 과제 (도전)

#### 고급 주제
- [ ] 여러 이벤트를 Kafka로 전환 (주문, 결제, 재고, 알림 등)
- [ ] 컴팩션 토픽(Compacted Topic) 활용
- [ ] Consumer Rebalancing 시나리오 테스트
- [ ] DLQ 처리 자동화 (DB 저장 + 어드민 재처리)
- [ ] Kafka Streams 활용
- [ ] 성능 비교 (Redis vs Kafka)

#### 도전 체크리스트
- [ ] 병렬 처리와 순차 처리 전략 비교 분석
- [ ] 처리량 개선 지표 측정 (TPS, Latency, Lag)
- [ ] 장애 시나리오 대응 (Broker Down, Consumer Failure)
- [ ] 모니터링 설정 (Grafana + Prometheus)

---

## 📁 문서 구조

```
docs/week9/
├── README.md                          # 이 파일 (주차 개요)
├── kafka-basics.md                    # Kafka 기본 개념
├── kafka-setup.md                     # 설치 및 환경 구성
├── kafka-spring-integration.md        # Spring Boot 연동
├── kafka-use-cases.md                 # 비즈니스 프로세스 개선 사례
├── kafka-best-practices.md            # 실무 베스트 프랙티스
├── assignment-step17.md               # Step 17 과제 가이드
├── assignment-step18.md               # Step 18 과제 가이드
└── diagrams/
    ├── kafka-architecture.md          # Kafka 아키텍처 다이어그램
    ├── coupon-issuance-kafka.md       # 쿠폰 발급 시퀀스 (Kafka)
    └── queue-processing-kafka.md      # 대기열 처리 시퀀스 (Kafka)
```

### 각 문서 설명

| 문서 | 설명 | 대상 |
|------|------|------|
| `kafka-basics.md` | Kafka 핵심 개념, 구성 요소, 메시지 흐름 | STEP 17 필수 |
| `kafka-setup.md` | 로컬/Docker 설치, CLI 사용법 | STEP 17 필수 |
| `kafka-spring-integration.md` | Spring Kafka 설정, Producer/Consumer 구현 | STEP 17 필수 |
| `kafka-use-cases.md` | 쿠폰 발급, 대기열 처리 설계 | STEP 18 필수 |
| `kafka-best-practices.md` | 실무 경험 기반 가이드 | 심화 |
| `assignment-step17.md` | Step 17 상세 가이드 | STEP 17 필수 |
| `assignment-step18.md` | Step 18 상세 가이드 | STEP 18 필수 |

---

## ✅ 평가 기준

### STEP 17 평가

| 항목 | 배점 | 기준 |
|------|------|------|
| Kafka 개념 이해 | 30% | Producer, Consumer, Partition, Offset 등 핵심 개념 정확히 설명 |
| 환경 구성 | 20% | Docker로 Kafka 실행, CLI로 메시지 송수신 |
| Spring 연동 | 30% | 애플리케이션에서 메시지 발행/소비 성공 |
| 트랜잭션 연동 | 20% | AFTER_COMMIT 후 메시지 발행 검증 |

### STEP 18 평가

| 항목 | 배점 | 기준 |
|------|------|------|
| 설계 문서 | 30% | 파티션 전략, Consumer 구성 명확히 설명 |
| 시퀀스 다이어그램 | 20% | Kafka 메시지 흐름 시각화 |
| 코드 구현 | 30% | 설계대로 동작하는 코드 |
| 개선 효과 | 20% | 기존 방식 대비 장점 설명 |

### 도전 과제 평가

| 항목 | 가산점 | 기준 |
|------|--------|------|
| DLQ 처리 자동화 | +10% | DB 저장 + 재처리 로직 |
| 성능 측정 | +10% | TPS, Latency, Lag 측정 및 분석 |
| 모니터링 구성 | +10% | Grafana 대시보드 구성 |
| 장애 시나리오 테스트 | +10% | Rebalancing, Broker Failure 대응 |

---

## 🔍 핵심 학습 포인트

### 1. Kafka vs Application Event

| 비교 | Application Event | Kafka |
|------|-------------------|-------|
| **결합도** | 발행자와 구독자 간 결합 | 완전히 분리 |
| **내구성** | 메모리 기반, 재시작 시 유실 | 디스크 기반, 영구 저장 |
| **처리량** | 제한적 (비동기 스레드 풀) | 매우 높음 (파티션 병렬 처리) |
| **순서 보장** | 보장 어려움 | 파티션 단위 보장 |
| **재처리** | 복잡 (별도 구현 필요) | 쉬움 (Offset 조정) |
| **확장성** | 수직 확장 | 수평 확장 |

### 2. 파티셔닝 전략

**시나리오 1: 선착순 쿠폰 발급**
- **요구사항**: 동일 쿠폰은 순차 처리, 다른 쿠폰은 병렬 처리
- **전략**: 메시지 키 = 쿠폰 ID
- **효과**: 같은 쿠폰은 같은 파티션 → 순서 보장, 다른 쿠폰은 다른 파티션 → 병렬 처리

```
쿠폰 A (Key: "coupon-77") → Partition 0 → Consumer 1 (순차 처리)
쿠폰 B (Key: "coupon-88") → Partition 1 → Consumer 2 (순차 처리)
쿠폰 C (Key: "coupon-99") → Partition 2 → Consumer 3 (순차 처리)
```

**시나리오 2: 콘서트 대기열**
- **요구사항**: 전체 대기열 순서 보장
- **전략**: 파티션 1개, Consumer 1개
- **효과**: 전체 순서 보장, 처리량은 제한적

```
모든 대기 토큰 → Partition 0 → Consumer 1 (N초당 M개 처리)
```

### 3. Consumer Group 전략

**패턴 1: 독립적 구독자**
```
Topic: order-completed
├── Consumer Group: data-platform (독립적으로 모든 메시지 소비)
└── Consumer Group: notification-service (독립적으로 모든 메시지 소비)
```

**패턴 2: 병렬 처리**
```
Topic: coupon-issuance (Partition 3개)
└── Consumer Group: coupon-processor
    ├── Consumer 1 → Partition 0
    ├── Consumer 2 → Partition 1
    └── Consumer 3 → Partition 2
```

### 4. 트랜잭션과 메시지 발행

**잘못된 예 (메시지 유실 가능)**
```java
@Transactional
public void createOrder() {
    orderRepository.save(order);
    kafkaProducer.send("order-completed", order); // 커밋 전 발행
    // 커밋 실패 시 메시지만 발행됨
}
```

**올바른 예 (트랜잭션 커밋 후 발행)**
```java
@Transactional
public void createOrder() {
    orderRepository.save(order);
    eventPublisher.publishEvent(new OrderCompletedEvent(order));
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOrderCompleted(OrderCompletedEvent event) {
    kafkaProducer.send("order-completed", event.getOrder());
}
```

---

## 📖 참고 자료

### 공식 문서
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring for Apache Kafka](https://spring.io/projects/spring-kafka)
- [Confluent Kafka Tutorials](https://kafka-tutorials.confluent.io/)

### 아키텍처 패턴
- [Event-Driven Architecture](https://martinfowler.com/articles/201701-event-driven.html)
- [CQRS Pattern](https://martinfowler.com/bliki/CQRS.html)
- [Saga Pattern](https://microservices.io/patterns/data/saga.html)

### 실무 경험 공유
- 토스 외화 이체 시스템 (비동기 처리 사례)
- 쿠팡 물류 시스템 (CDC + Kafka)
- 대규모 시스템 아키텍처 설계 (가상 면접 사례로 배우는 대규모 시스템 설계)

### 관련 도구
- [AKHQ (Kafka GUI)](https://akhq.io/)
- [Kafka UI](https://github.com/provectus/kafka-ui)
- [Confluent Control Center](https://docs.confluent.io/platform/current/control-center/index.html)

---

## 🚀 시작하기

### 1단계: Kafka 개념 학습
```bash
# 학습 순서
1. kafka-basics.md 읽기 (30분)
2. 주요 개념 정리 (Producer, Consumer, Partition, Offset)
3. 실습 계획 수립
```

### 2단계: 환경 구성
```bash
# Docker로 Kafka 실행
cd docs/week9
docker-compose up -d

# CLI로 메시지 테스트
kafka-console-producer --topic test --bootstrap-server localhost:9092
kafka-console-consumer --topic test --from-beginning --bootstrap-server localhost:9092
```

### 3단계: Spring Boot 연동
```bash
# 의존성 추가
implementation 'org.springframework.kafka:spring-kafka'

# Producer 구현
# Consumer 구현
# 테스트
```

### 4단계: 비즈니스 적용
```bash
# 주문 완료 이벤트 Kafka로 전환
# DLQ 처리 구현
# 성능 측정
```

---

## 💡 학습 팁

### 효과적인 학습 방법
1. **개념 먼저, 코드는 나중에**: Kafka 구조를 이해한 후 코드 작성
2. **작은 단위로 실습**: CLI → Java → Spring Boot 순서로
3. **로그 확인 습관**: Offset, Partition, Consumer Group 상태 확인
4. **장애 시나리오 테스트**: Broker Down, Consumer Failure 대응

### 자주 하는 실수
- ❌ 트랜잭션 커밋 전 메시지 발행
- ❌ Consumer 수 > Partition 수 (Idle Consumer 발생)
- ❌ 메시지 키 없이 발행 (순서 보장 불가)
- ❌ Auto Commit 사용 (메시지 유실 가능)
- ❌ DLQ 처리 없이 에러 무시

### 디버깅 체크리스트
- [ ] Kafka Broker 정상 실행 확인
- [ ] Topic 생성 확인 (`kafka-topics --list`)
- [ ] Producer 메시지 발행 성공 로그 확인
- [ ] Consumer Offset 증가 확인
- [ ] Consumer Group 상태 확인 (`kafka-consumer-groups --describe`)
- [ ] Lag 발생 여부 확인

---

## 📞 도움 받기

### 질문 채널
- Slack: `#week9-kafka` 채널
- 멘토링: 화요일 21:00
- 코치 QnA: Agoora 플랫폼

### 자주 묻는 질문
1. **Q**: 파티션 수를 어떻게 결정하나요?
   - **A**: 기본 3개로 시작, Lag 발생 시 증가 (렉 기반 결정)

2. **Q**: Redis와 Kafka 중 어떤 걸 써야 하나요?
   - **A**: Redis는 즉시 응답 필요한 동시성 제어, Kafka는 비동기 후처리

3. **Q**: 토픽을 도메인별로 나눠야 하나요?
   - **A**: 세분화 권장 (OrderCreated, OrderCancelled 등 별도 토픽)

4. **Q**: DLQ 메시지는 어떻게 재처리하나요?
   - **A**: DB 저장 → 어드민 툴로 수동 재처리 (자동 재시도는 무한 루프 위험)

---

## 📅 학습 일정 (권장)

### 3시간 로드맵 (기초)
- **1시간**: Kafka 개념 + Docker 실행 + CLI 테스트
- **1시간**: Spring Boot 연동 + Producer/Consumer 구현
- **1시간**: 주문 이벤트 Kafka로 전환 + 테스트

### 10시간 로드맵 (심화)
- **2시간**: Kafka 개념 + 환경 구성
- **3시간**: 선착순 쿠폰 발급 Kafka 전환
- **2시간**: 콘서트 대기열 Kafka 전환
- **2시간**: DLQ 처리 + 성능 측정
- **1시간**: 학습 정리 + 문서 작성

---

## ✨ 마무리

이번 주차는 단순히 Kafka 사용법을 배우는 것이 아닙니다. **이벤트 기반 아키텍처의 핵심 개념**을 이해하고, **대용량 트래픽 처리 전략**을 설계하는 능력을 키우는 시간입니다.

Kafka를 학습하면서:
- 도메인 간 결합도를 낮추는 방법
- 대용량 트래픽을 안정적으로 처리하는 방법
- 장애에 강한 시스템을 설계하는 방법

을 자연스럽게 체득하게 될 것입니다.

**Let's Build Event-Driven Systems! 🚀**

---

## 📝 변경 이력

| 날짜 | 버전 | 변경 내용 | 작성자 |
|------|------|-----------|--------|
| 2024-12-18 | 1.0 | 최초 작성 | Claude |
