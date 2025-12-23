# Week 9: Kafka 학습 및 적용 계획 (v2 - 페르소나 검증 반영)

> **작성일**: 2025-12-20
> **프로젝트**: 항해플러스 이커머스 백엔드
> **현재 단계**: Week 9 (Kafka를 활용한 이벤트 기반 아키텍처)
> **브랜치**: step17-18
> **버전**: v2 (5인 시니어 페르소나 검증 반영)

---

## 📊 현재 상태

### 기본 정보
- **기술 스택**: Java 21, Spring Boot 3.5.7, MySQL, Redis
- **빌드 도구**: Gradle
- **테스트**: JUnit 5, Testcontainers (MySQL, Redis)
- **아키텍처**: Layered Architecture (4계층)
- **테스트 커버리지**: 73% (목표 70% 달성 ✅)

### Week 8까지 완료 사항
- ✅ Application Event & TransactionalEventListener 구현
- ✅ Outbox Pattern (외부 API 연동 실패 시 재시도)
- ✅ Redis 기반 랭킹 시스템 (Sorted Set)
- ✅ 선착순 쿠폰 발급 (Redis Lua Script)
- ✅ 전체 테스트 282개 통과

### Todos
- ☒ A6: 주문 완료 이벤트 Kafka 전환 완료
- ☒ A7-1: MySQL Testcontainer 추가
- ☒ A7-2: 테스트 실행 (`./gradlew test ...`, log: `build/test-a7-2.log`)

### Week 9 학습 목표
**Kafka를 활용하여 이벤트 기반 아키텍처의 한계를 극복하고 대용량 트래픽 처리 능력 향상**

---

## 🎯 Week 9 과제 구성

### STEP 17: Kafka 기초 학습 및 활용 (3-5시간)

#### 목표
- Kafka 핵심 개념 이해
- Spring Boot와 Kafka 연동
- 주문 완료 이벤트를 Kafka로 발행
- **운영 관점 추가**: 모니터링, 로깅, 장애 대응 기초

#### 산출물
- [ ] Kafka 개념 학습 문서 (`docs/week9/kafka-learning.md`)
- [ ] Docker Compose에 Kafka 추가 (`docker-compose.yml`)
- [ ] Kafka 설정 (`application.yml`)
- [ ] Producer/Consumer 구현
- [ ] 통합 테스트 (실패 시나리오 포함)
- [ ] Consumer Lag 모니터링 기초

---

### STEP 18: Kafka를 활용한 비즈니스 프로세스 개선 (5-8시간)

#### 목표
- Kafka 파티션 기반 병렬 처리 전략 설계
- 선착순 쿠폰 발급 또는 대기열 처리를 Kafka로 개선
- 성능 개선 효과 측정

#### 산출물
- [ ] 설계 문서 (`docs/week9/{시나리오}-kafka-design.md`)
- [ ] 시퀀스 다이어그램 (Mermaid)
- [ ] Producer/Consumer 구현
- [ ] DLQ 처리 (권장)
- [ ] 성능 측정 결과

---

## 📋 우선순위별 작업 목록

> **진행 원칙**:
> - 한 번에 **1개 항목만** 선택하여 진행
> - **Test-First** 워크플로우 준수
> - **1-3 파일, 200 LoC 이하** 제한
> - **Decision Gate**에서 확인 (태스크 선택, 접근 방식, 긴 커맨드, 범위 증가)

### 🗂️ 작업 의존성 그래프

```
A1 (의존성/환경) → A2 (CLI 테스트) → A3 (설정)
                                         ↓
                  A4 (Producer) ←────────┴──→ A5 (Consumer)
                       │                         │
                       └─────→ A6 (Event 전환) ←┘
                                     ↓
                        A7 (통합 테스트) → A8 (Lag 모니터링)

독립적으로 진행 가능: A4와 A5는 병렬 진행 가능 (단, A3 이후)
```

---

## 🏗️ Kafka 패키지 구조 (아키텍처 관점)

**추가된 패키지 구조** (페르소나 1: 아키텍트 피드백 반영)

```
src/main/java/io/hhplus/ecommerce/
├── infrastructure/
│   ├── kafka/                      # Kafka 관련 Infrastructure
│   │   ├── config/                 # Kafka Configuration
│   │   │   ├── KafkaProducerConfig.java
│   │   │   └── KafkaConsumerConfig.java
│   │   ├── message/                # Kafka 메시지 DTO (직렬화 포함)
│   │   │   ├── OrderCompletedMessage.java
│   │   │   ├── CouponIssuanceMessage.java
│   │   │   └── ...
│   │   ├── producer/               # Kafka Producer 구현
│   │   │   ├── OrderEventProducer.java
│   │   │   └── ...
│   │   └── consumer/               # Kafka Consumer 구현
│   │       ├── OrderEventConsumer.java
│   │       ├── DataPlatformConsumer.java
│   │       └── ...
│   └── ...
│
└── application/
    └── {domain}/listener/          # Application Event Listener (기존)
        └── OrderEventListener.java # Event → Kafka 변환
```

**의존성 방향**:
- `application.listener` → `infrastructure.kafka.producer` (허용)
- `domain` → `infrastructure.kafka` (금지)

---

## 우선순위 A: STEP 17 필수 항목 (Pass 조건)

### A1. Kafka 의존성 및 환경 구성 ⭐

**현재 상태**
- `build.gradle`에 Kafka 의존성 없음
- `docker-compose.yml`에 Kafka 컨테이너 없음

**목표**
1. Spring Kafka 의존성 추가
2. Testcontainers Kafka 의존성 추가
3. Docker Compose에 Zookeeper, Kafka 추가
4. **리소스 제한 및 Volume 설정** (페르소나 5: 데브옵스 피드백)

**변경 예상 파일**
```
build.gradle
docker-compose.yml
```

**리스크**: 낮음 (설정 추가만)
**예상 LoC**: ~50-70

**진행 단계**
1. `build.gradle`에 `spring-kafka` 의존성 추가
2. `testImplementation 'org.testcontainers:kafka'` 추가
3. `docker-compose.yml`에 Zookeeper, Kafka 컨테이너 추가
4. **Kafka 컨테이너 리소스 제한 설정**
   - `mem_limit: 1GB`
   - `cpus: 1.0`
5. **Kafka Volume 설정** (데이터 영속성)
   - `kafka-data:/var/lib/kafka/data`
6. **Topic 자동 생성 설정 여부 결정** (Decision Gate)
   - `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` (개발 편의)
   - 운영 환경은 수동 생성 권장
7. `docker-compose up -d` 실행 확인

**참고 문서**: `docs/week9/kafka-setup.md`

---

### A2. Kafka 환경 실행 및 CLI 테스트 ⭐

**현재 상태**
- Kafka 미실행

**목표**
1. Docker로 Kafka 실행
2. CLI로 Topic 생성 테스트
3. CLI로 Producer/Consumer 테스트
4. **Topic 관리 전략 학습** (페르소나 5 피드백)
5. 실행 로그 문서화

**변경 예상 파일**
```
docs/week9/kafka-setup-log.md (신규)
```

**리스크**: 낮음 (실행 및 문서화)
**예상 LoC**: 문서 작성

**진행 단계**
1. `docker-compose up -d` 실행
2. Kafka 컨테이너 상태 확인 (`docker-compose ps`)
3. **Topic 생성 전략 결정** (Decision Gate)
   - 수동 생성 (권장): `kafka-topics --create`
   - 자동 생성: `auto.create.topics.enable=true`
4. Topic 생성: `kafka-topics --create --topic order-completed --partitions 3 --replication-factor 1`
5. Topic 목록 확인: `kafka-topics --list`
6. Producer 테스트: `kafka-console-producer --topic order-completed`
7. Consumer 테스트: `kafka-console-consumer --topic order-completed --from-beginning`
8. 로그 캡처 및 문서화

**참고 문서**: `docs/week9/kafka-setup.md`

---

### A3. application.yml에 Kafka 설정 추가 ⭐

**현재 상태**
- `application.yml`에 Kafka 설정 없음

**목표**
- Producer/Consumer 설정 추가
- Serializer/Deserializer 설정
- Manual ACK 설정
- **Producer 트랜잭션 설정 여부 결정** (페르소나 2: 백엔드 리드 피드백)

**변경 예상 파일**
```
src/main/resources/application.yml
```

**리스크**: 낮음 (설정 추가만)
**예상 LoC**: ~40-60

**진행 단계**
1. `spring.kafka.bootstrap-servers: localhost:9092` 설정
2. **Producer 설정**
   - `key-serializer: StringSerializer`
   - `value-serializer: JsonSerializer`
   - `acks: all` (모든 replica 확인, 안전성 우선)
3. **Decision Gate: Producer 트랜잭션 사용 여부**
   - **사용 (권장)**: `transactional-id` 설정 → 정확성 우선 (약간의 성능 저하)
   - **미사용**: 단순 설정 → 성능 우선 (드문 메시지 중복 가능)
   - **트레이드오프**: 정확성 vs 성능
4. **Consumer 설정**
   - `key-deserializer: StringDeserializer`
   - `value-deserializer: JsonDeserializer`
   - `group-id: ecommerce-service`
   - `auto-offset-reset: earliest`
   - `enable-auto-commit: false` (Manual ACK)
   - `properties.spring.json.trusted.packages: "*"`
5. **Listener 설정**
   - `ack-mode: manual` (명시적 ACK)

**참고 문서**: `docs/week9/kafka-spring-integration.md`

---

### A4. Kafka Producer 구현 ⭐

**현재 상태**
- Producer 미구현

**목표**
- `OrderEventProducer` 구현
- `KafkaTemplate` 사용
- **구조화된 로깅 추가** (페르소나 5: 데브옵스 피드백)

**변경 예상 파일**
```
infrastructure/kafka/producer/OrderEventProducer.java (신규)
infrastructure/kafka/message/OrderCompletedMessage.java (신규)
infrastructure/kafka/config/KafkaProducerConfig.java (신규)
```

**리스크**: 낮음 (신규 파일)
**예상 LoC**: ~80-120 (3개 파일 합계)

**진행 단계**
1. `infrastructure/kafka/message/OrderCompletedMessage.java` DTO 작성
   - 필드: `orderId`, `userId`, `totalAmount`, `completedAt`
   - `from(Order order)` 정적 팩토리 메서드
2. `infrastructure/kafka/config/KafkaProducerConfig.java` 작성
   - `@Configuration` 클래스
   - `KafkaTemplate<String, Object>` Bean 생성
3. `infrastructure/kafka/producer/OrderEventProducer.java` 작성
   - `@Component` 클래스
   - `KafkaTemplate<String, Object>` 주입
   - `publishOrderCompleted(OrderCompletedMessage)` 메서드 구현
4. **구조화된 로깅 추가** (페르소나 5 피드백)
   - 성공 로그: `orderId`, `topic`, `partition`, `offset`
   - 실패 로그: `orderId`, `에러 메시지`, `스택트레이스`
   - 예: `log.info("Kafka message published: orderId={}, topic={}, partition={}, offset={}", ...)`
5. **에러 처리 전략** (페르소나 4 피드백)
   - `whenComplete()` 콜백에서 성공/실패 로그
   - 실패 시 예외 던지기 (상위에서 재시도 처리)

**참고 문서**: `docs/week9/assignment-step17.md` (Producer 구현 예시)

---

### A5. Kafka Consumer 구현 ⭐

**현재 상태**
- Consumer 미구현

**목표**
- `OrderEventConsumer` 구현
- `@KafkaListener` 사용
- Manual ACK 적용
- **멱등성 처리** (페르소나 2: 백엔드 리드 피드백)

**변경 예상 파일**
```
infrastructure/kafka/consumer/OrderEventConsumer.java (신규)
infrastructure/kafka/config/KafkaConsumerConfig.java (신규)
```

**리스크**: 낮음 (신규 파일)
**예상 LoC**: ~80-120

**진행 단계**
1. `infrastructure/kafka/config/KafkaConsumerConfig.java` 작성
   - `@Configuration` 클래스
   - Consumer Factory 설정
2. `infrastructure/kafka/consumer/OrderEventConsumer.java` 작성
   - `@Component` 클래스
   - `@KafkaListener(topics = "order-completed", groupId = "data-platform")` 어노테이션
3. **메서드 시그니처**
   - 파라미터: `OrderCompletedMessage message`, `Acknowledgment ack`, `@Header(RECEIVED_PARTITION) int partition`
4. **멱등성 처리** (페르소나 2 피드백)
   - **Option A**: 메시지 ID (orderId) 기반 중복 처리 방지
     - Redis: `SETNX processed:order:{orderId} 1 EX 86400` (24시간 TTL)
     - 이미 처리된 경우 즉시 ACK 후 return
   - **Option B**: DB 유니크 제약 조건 활용
     - `INSERT IGNORE` 또는 `ON CONFLICT DO NOTHING`
   - **Decision Gate**: Option A vs B 선택
5. **메시지 처리 로직**
   - 비즈니스 로직 실행
   - 성공 시 `ack.acknowledge()`
   - 실패 시 ACK 안 함 (재처리)
6. **구조화된 로깅**
   - 수신 로그: `orderId`, `partition`, `offset`
   - 처리 완료 로그: `orderId`, `처리 시간`
   - 에러 로그: `orderId`, `에러 메시지`, `재시도 여부`

**참고 문서**: `docs/week9/assignment-step17.md` (Consumer 구현 예시)

---

### A6. 주문 완료 이벤트 Kafka 전환 ⭐

**현재 상태**
- 주문 완료 시 Application Event 발행
- `@TransactionalEventListener(AFTER_COMMIT)` 사용 중

**목표**
- Event Listener에서 Kafka Producer 호출
- 기존 로직 유지, Kafka 발행 추가
- **Kafka 발행 실패 시 재시도 전략** (페르소나 2: 백엔드 리드 피드백)

**변경 예상 파일**
```
application/order/listener/OrderEventListener.java (수정)
infrastructure/kafka/producer/OrderEventProducer.java (A4에서 생성)
```

**리스크**: 중간 (기존 로직 수정)
**예상 LoC**: ~50-80 (수정)

**진행 단계**
1. `OrderEventListener`에서 `OrderEventProducer` 주입
2. `@TransactionalEventListener(phase = AFTER_COMMIT)` 메서드 수정
3. `OrderCompletedMessage message = OrderCompletedMessage.from(order)` 생성
4. `orderEventProducer.publishOrderCompleted(message)` 호출
5. **Kafka 발행 실패 시 재시도 전략** (페르소나 2 피드백)
   - **Spring Retry 사용**: `@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))`
   - 재시도 3회 (1초, 2초, 4초 간격)
   - 최종 실패 시 `@Recover` 메서드로 Outbox 테이블 저장
6. **Outbox 테이블 저장** (최종 실패 시)
   - 기존 Outbox Pattern 활용 (`infrastructure/persistence/outbox`)
   - `OutboxRepository.save(eventType, payload, status=PENDING)`
   - 백그라운드 재전송 스케줄러가 재시도
7. 기존 로직 유지 (호환성)

**참고 문서**: `docs/week9/assignment-step17.md`, `docs/week8/README.md` (Outbox Pattern)

---

### A7. Kafka 통합 테스트 작성 ⭐

**현재 상태**
- Kafka 테스트 없음

**목표**
- Testcontainers Kafka 사용
- 주문 생성 → Kafka 발행 → Consumer 수신 검증
- **실패 시나리오 테스트** (페르소나 3: 시니어 개발자 피드백)

**변경 예상 파일**
```
src/test/java/.../infrastructure/kafka/OrderKafkaIntegrationTest.java (신규)
```

**리스크**: 낮음 (신규 테스트)
**예상 LoC**: ~150-200

**진행 단계**
1. `@Testcontainers` + `@SpringBootTest` 설정
2. `KafkaContainer` 설정
   ```java
   @Container
   static KafkaContainer kafka = new KafkaContainer(
       DockerImageName.parse("confluentinc/cp-kafka:7.5.3")
   );

   @DynamicPropertySource
   static void kafkaProperties(DynamicPropertyRegistry registry) {
       registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
   }
   ```
3. **Happy Path 테스트**: 주문 생성 → Kafka 발행 → Consumer 수신
   - `CountDownLatch`로 메시지 수신 대기 (타임아웃 10초)
   - 수신된 메시지 검증 (`orderId`, `userId`, `totalAmount`)
4. **실패 시나리오 1: Consumer 처리 실패 → ACK 안 함 → 재소비** (페르소나 3 피드백)
   - Consumer에서 강제로 예외 발생
   - ACK 안 함 확인
   - 재소비 확인 (재시도 1회)
5. **실패 시나리오 2: 메시지 역직렬화 실패** (페르소나 3 피드백)
   - 잘못된 JSON 메시지 발행
   - Consumer에서 에러 로그 확인
   - DLQ 전송 확인 (선택)
6. **테스트 격리 전략** (페르소나 3 피드백)
   - 각 테스트마다 다른 Topic 사용 (`order-completed-test-1`, `-test-2` 등)
   - 또는 Consumer Offset 리셋: `auto-offset-reset: earliest`
7. 전체 테스트 실행 (`./gradlew test`)
8. **테스트 커버리지 확인**: 70% 이상 유지

**참고 문서**: `docs/week9/assignment-step17.md`, [Testcontainers Kafka](https://www.testcontainers.org/modules/kafka/)

---

### A8. Consumer Lag 기본 모니터링 ⭐ (신규 - 페르소나 5 피드백)

**현재 상태**
- 모니터링 없음 (기존 plan.md에서 심화 과제 C2로 분류)

**목표**
- Kafka Consumer Lag 확인 명령어 학습
- Lag 의미 이해 (처리하지 못한 메시지 수)
- Lag 발생 시 대응 방법 학습

**변경 예상 파일**
```
docs/week9/consumer-lag-monitoring.md (신규)
```

**리스크**: 낮음 (CLI 명령어 학습 및 문서화)
**예상 LoC**: 문서 작성

**진행 단계**
1. **Consumer Group 상태 확인 명령어** 학습
   ```bash
   docker exec -it kafka kafka-consumer-groups --describe \
     --group ecommerce-service \
     --bootstrap-server localhost:9092
   ```
2. **출력 이해**
   - `CURRENT-OFFSET`: Consumer가 현재까지 읽은 위치
   - `LOG-END-OFFSET`: Kafka에 저장된 최신 메시지 위치
   - `LAG`: `LOG-END-OFFSET - CURRENT-OFFSET` (처리하지 못한 메시지 수)
3. **Lag 발생 원인 분석**
   - Consumer 처리 속도 < Producer 발행 속도
   - Consumer 장애 또는 재시작
   - 파티션 재할당 (Rebalancing)
4. **Lag 대응 방법**
   - Consumer 수 증가 (파티션 수 이하로)
   - Partition 수 증가
   - Consumer 처리 로직 최적화
5. **기본 모니터링 설정**
   - `application.yml`에 Actuator + Prometheus 설정 (이미 존재)
   - Kafka Consumer Metrics 확인: `/actuator/metrics/kafka.consumer.fetch.manager.records.lag.max`
6. 문서화

**참고 문서**: `docs/week9/kafka-basics.md` (Consumer Group, Offset)

---

## 우선순위 B: STEP 18 필수 항목 (Pass 조건)

### B1. 비즈니스 시나리오 선택 및 설계 문서 작성

**목표**
- 선착순 쿠폰 발급 OR 대기열 중 선택
- 기존 방식(Redis)의 한계 분석
- Kafka 기반 개선 설계
- 파티션 전략 및 Consumer 구성 명시

**변경 예상 파일**
```
docs/week9/coupon-kafka-design.md (신규)
또는
docs/week9/queue-kafka-design.md (신규)
```

**리스크**: 낮음 (문서 작성)
**예상 LoC**: 문서 작성

**진행 단계**
1. **시나리오 선택** (Decision Gate)
   - **Option A**: 선착순 쿠폰 발급
     - 장점: 병렬 처리 + 순서 보장 하이브리드
     - 파티션 전략: 메시지 키 = `couponId` (같은 쿠폰은 같은 파티션)
   - **Option B**: 대기열 토큰 활성화
     - 장점: 전체 순서 보장 + 속도 제어
     - 파티션 전략: 파티션 1개 (순서 보장)
2. **기존 Redis 방식의 문제점 분석**
   - 단일 쿠폰 처리 병목
   - 확장성 제한
   - 장애 복구 어려움
   - 모니터링 부족
3. **Kafka 기반 개선 설계**
   - Topic 설계: 이름, 파티션 수, Replication Factor
   - 파티셔닝 전략: 메시지 키 결정
   - Consumer Group 구성: Consumer 수, concurrency 설정
4. **시퀀스 다이어그램 작성** (Mermaid)
   - Producer → Kafka → Consumer 흐름
   - 파티션 분배 시각화
5. **예상 개선 효과**
   - 처리량 향상 (TPS)
   - Lag 모니터링 가능
   - 장애 복구 용이

**참고 문서**: `docs/week9/assignment-step18.md`, `docs/week9/kafka-use-cases.md`

---

### B2. 시퀀스 다이어그램 작성

**목표**
- Producer → Kafka → Consumer 흐름 시각화
- 파티션 분배 및 순서 보장 표현

**변경 예상 파일**
```
docs/week9/diagrams/coupon-kafka-flow.md (신규)
```

**리스크**: 낮음 (문서 작성)
**예상 LoC**: Mermaid 다이어그램

**진행 단계**
1. Mermaid 다이어그램 작성
2. Producer, Kafka (Partition 0, 1, 2), Consumer 1, 2, 3 표현
3. 메시지 키에 따른 파티션 분배 표현
4. ACK 흐름 표현

**참고 문서**: `docs/week9/assignment-step18.md` (시퀀스 다이어그램 예시)

---

### B3. 비즈니스 Producer 구현 (쿠폰/대기열)

**목표**
- 선택한 시나리오에 맞는 Producer 구현
- 메시지 키 전략 적용

**변경 예상 파일**
```
infrastructure/kafka/producer/CouponIssuanceProducer.java (신규)
또는
infrastructure/kafka/producer/WaitingQueueProducer.java (신규)
infrastructure/kafka/message/CouponIssuanceMessage.java (신규)
```

**리스크**: 중간 (비즈니스 로직 변경)
**예상 LoC**: ~80-120

**진행 단계**
1. 메시지 DTO 작성 (`CouponIssuanceMessage` 또는 `WaitingTokenMessage`)
2. Producer 클래스 작성
3. **메시지 키 전략 적용**
   - 쿠폰: `kafkaTemplate.send(topic, couponId, message)` (같은 쿠폰은 같은 파티션)
   - 대기열: `kafkaTemplate.send(topic, null, message)` (순서대로 발행)
4. 로깅 및 에러 처리

**참고 문서**: `docs/week9/assignment-step18.md` (Producer 구현 예시)

---

### B4. 비즈니스 Consumer 구현 (쿠폰/대기열)

**목표**
- 선택한 시나리오에 맞는 Consumer 구현
- 파티션별 병렬 처리 또는 순차 처리
- **Consumer 동시성 설정** (페르소나 2 피드백)

**변경 예상 파일**
```
infrastructure/kafka/consumer/CouponIssuanceConsumer.java (신규)
또는
infrastructure/kafka/consumer/WaitingTokenConsumer.java (신규)
```

**리스크**: 중간 (비즈니스 로직 변경)
**예상 LoC**: ~100-150

**진행 단계**
1. Consumer 클래스 작성
2. **`@KafkaListener` 설정**
   - 쿠폰: `concurrency = "3"` (3개 Consumer, 병렬 처리)
   - 대기열: `concurrency = "1"` (1개 Consumer, 순차 처리)
3. **Consumer 동시성 전략** (페르소나 2 피드백)
   - 같은 파티션 내 메시지는 순차 처리 보장 (Kafka 기본 동작)
   - 다른 파티션은 병렬 처리
4. **재처리 전략**
   - 처리 실패 시 ACK 안 함 → Kafka가 자동 재전송
   - 재시도 횟수 제한: `max.poll.records`, `max.poll.interval.ms` 설정
5. 비즈니스 로직 연동
6. 멱등성 처리 (A5와 동일)

**참고 문서**: `docs/week9/assignment-step18.md` (Consumer 구현 예시)

---

### B5. 성능 개선 효과 측정 및 문서화

**목표**
- TPS, Latency, Consumer Lag 측정
- 기존 방식 대비 장단점 정리
- **성능 목표 설정 및 달성 여부** (페르소나 5 피드백)

**변경 예상 파일**
```
docs/week9/performance-comparison.md (신규)
```

**리스크**: 낮음 (측정 및 문서화)
**예상 LoC**: 문서 작성

**진행 단계**
1. **성능 목표 설정** (페르소나 5 피드백)
   - 예: "기존 Redis 대비 처리량 2배 향상"
   - 예: "Consumer Lag 0 유지"
2. **측정 지표**
   - **TPS (Transactions Per Second)**: 초당 처리 메시지 수
   - **Latency**: P50, P95, P99 (메시지 발행부터 처리까지 시간)
   - **Consumer Lag**: `kafka-consumer-groups --describe`로 확인
   - **에러율**: 처리 실패 메시지 비율
3. **측정 방법**
   - 부하 테스트 도구 (K6, JMeter) 또는 간단한 스크립트
   - 100개, 1000개, 10000개 메시지 발행 후 측정
4. **기존 방식(Redis) vs Kafka 비교**
   - 처리량: Redis 단일 키 vs Kafka 파티션 병렬 처리
   - 안정성: Redis 메모리 vs Kafka 디스크 영속성
   - 확장성: Redis 수직 확장 vs Kafka 수평 확장
5. **엣지 케이스 테스트** (페르소나 3 피드백)
   - 큰 메시지 크기 (max.message.bytes 초과 시?)
   - Consumer Lag 폭증 (처리 속도 < 발행 속도)
6. 결과 문서화

**참고 문서**: `docs/week9/kafka-use-cases.md`

---

## 우선순위 C: 심화 과제 (도전 항목)

### C1. DLQ (Dead Letter Queue) 처리 자동화

**목표**
- 메시지 처리 실패 시 DLQ로 전송
- DB 저장 후 재처리 로직

**변경 예상 파일**
```
infrastructure/kafka/consumer/DLQConsumer.java (신규)
domain/kafka/FailedMessage.java (신규)
infrastructure/persistence/kafka/FailedMessageRepository.java (신규)
```

**리스크**: 중간 (신규 기능)
**예상 LoC**: ~150-200

**진행 단계**
1. DLQ Topic 생성 (`order-completed.DLQ`)
2. Consumer에서 처리 실패 시 DLQ로 전송
3. DLQConsumer 구현 (DLQ 메시지 소비 → DB 저장)
4. Admin API 구현 (실패 메시지 재처리)

**참고 문서**: `docs/week9/kafka-best-practices.md`

---

### C2. Consumer Lag 고급 모니터링 (Grafana)

**목표**
- Grafana 대시보드 구성
- Consumer Lag 시각화

**변경 예상 파일**
```
monitoring/grafana/kafka-dashboard.json (신규)
```

**리스크**: 낮음 (모니터링 설정)
**예상 LoC**: 설정 파일

**진행 단계**
1. Prometheus + Grafana 설정 (이미 존재하는 인프라 활용)
2. Kafka Exporter 추가
3. Grafana 대시보드 생성
   - Consumer Lag 차트
   - Message Rate 차트
   - Error Rate 차트

**참고 문서**: Prometheus Kafka Exporter

---

### C3. 장애 시나리오 테스트

**목표**
- Broker Down 시나리오
- Consumer Rebalancing 시나리오
- 테스트 및 문서화

**변경 예상 파일**
```
src/test/java/.../kafka/KafkaFailoverTest.java (신규)
docs/week9/failure-scenarios.md (신규)
```

**리스크**: 중간 (복잡한 테스트)
**예상 LoC**: ~100-150

**진행 단계**
1. Testcontainers로 Kafka Cluster 구성 (Broker 3개)
2. Broker Down 시나리오 테스트
   - Broker 1개 중단 → 메시지 발행/소비 계속
   - Replication Factor 덕분에 데이터 유실 없음
3. Consumer Rebalancing 시나리오
   - Consumer 추가/제거 → 파티션 재할당
   - 재할당 중 메시지 유실 없음
4. 문서화

---

## 📋 진행 방식

### 1. 항목 선택
- STEP 17 (A1-A8) 먼저 완료
- STEP 18 (B1-B5) 이후 진행
- 심화 과제 (C1-C3) 선택 사항

### 2. Test-First 워크플로우
1. 실패하는 테스트 작성/수정 (테스트 가능한 항목만)
2. 해당 테스트만 실행 → 실패 확인
3. 최소 코드 변경으로 테스트 통과
4. 전체 테스트 실행
5. 최종 검증: `./gradlew clean test jacocoTestReport`

### 3. Decision Gate (반드시 확인)
- **태스크 선택**: A1-A8 중 어느 것부터 할지
- **접근 방식 선택**: 옵션이 2개 이상이면 비교 후 선택 요청
  - A3: Producer 트랜잭션 사용 여부
  - A5: 멱등성 처리 방식 (Redis vs DB)
  - B1: 시나리오 선택 (쿠폰 vs 대기열)
- **긴 커맨드 실행**: `docker-compose up`, `./gradlew clean test` 실행 전 확인
- **범위 증가**: 파일/테스트가 늘어나면 쪼개서 다음 태스크로

### 4. 제약 사항
- 1-3 파일, 200 LoC 이하
- Assertion 삭제/약화 금지
- 대규모 리팩터링/패키지 이동 금지
- Testcontainers 유지 (mock 금지)

---

## ✅ STEP 17 체크리스트

### 환경 구성
- [ ] `build.gradle`에 `spring-kafka` 의존성 추가
- [ ] `docker-compose.yml`에 Kafka 추가 (리소스 제한, Volume 포함)
- [ ] `application.yml`에 Kafka 설정 추가 (Producer TX 여부 결정)
- [ ] Docker로 Kafka 실행 확인
- [ ] CLI로 Topic 생성/메시지 송수신 테스트

### 코드 구현
- [ ] 패키지 구조 생성 (`infrastructure/kafka/config`, `/producer`, `/consumer`, `/message`)
- [ ] `OrderEventProducer` 구현 (구조화된 로깅 포함)
- [ ] `OrderEventConsumer` 구현 (멱등성 처리 포함)
- [ ] `OrderCompletedMessage` DTO 작성
- [ ] `@TransactionalEventListener(AFTER_COMMIT)` 사용
- [ ] Kafka 발행 실패 시 재시도 + Outbox 저장
- [ ] Kafka 통합 테스트 작성 (실패 시나리오 포함)

### 문서
- [ ] Kafka 설치 및 실행 로그 캡처
- [ ] CLI 테스트 결과 문서화
- [ ] 메시지 발행/소비 로그 캡처
- [ ] Consumer Lag 모니터링 기초 문서

### 테스트
- [ ] 주문 생성 → Kafka 발행 확인
- [ ] Consumer에서 메시지 수신 확인
- [ ] Consumer 처리 실패 → 재소비 테스트
- [ ] 통합 테스트 통과
- [ ] 전체 테스트 통과 (`./gradlew test`)
- [ ] 테스트 커버리지 70% 이상 유지

### 모니터링
- [ ] Consumer Lag 확인 명령어 학습
- [ ] Actuator Metrics 확인 (`/actuator/metrics/kafka.*`)

---

## ✅ STEP 18 체크리스트

### 설계
- [ ] 시나리오 선택 (쿠폰 OR 대기열)
- [ ] 기존 방식의 한계 분석
- [ ] Kafka 기반 설계 문서 작성
- [ ] 파티션 전략 및 Consumer 구성 명시
- [ ] 시퀀스 다이어그램 작성 (Mermaid)

### 구현
- [ ] Producer 구현 (메시지 키 지정)
- [ ] Consumer 구현 (파티션별 처리, concurrency 설정)
- [ ] 비즈니스 로직 연동
- [ ] 멱등성 처리
- [ ] 통합 테스트 작성

### 성능 측정
- [ ] 성능 목표 설정 (예: Redis 대비 2배 처리량)
- [ ] Consumer Lag 확인
- [ ] TPS 측정
- [ ] Latency 측정 (P50, P95, P99)
- [ ] 엣지 케이스 테스트 (큰 메시지, Lag 폭증)
- [ ] 기존 방식 대비 개선 효과 정리

### 문서
- [ ] 설계 문서 완성
- [ ] 시퀀스 다이어그램 완성
- [ ] 성능 비교 결과 문서화
- [ ] 장단점 분석 문서화

---

## 🚀 시작하기

### STEP 17 시작 프롬프트 예시
```
A1 항목(Kafka 의존성 및 환경 구성)부터 시작하자.
먼저 변경 후보 리스트를 작성하고,
build.gradle, docker-compose.yml 수정 계획을 알려줘.
리소스 제한과 Volume 설정도 포함해줘.
```

### STEP 18 시작 프롬프트 예시
```
STEP 17 완료했어. 이제 B1(비즈니스 시나리오 선택)을 시작하자.
선착순 쿠폰 발급을 Kafka로 개선하고 싶어.
먼저 설계 문서 작성 계획을 알려줘.
```

---

## 📚 참고 문서

### Week 9 필수 학습 자료
- `docs/week9/README.md` - Week 9 개요
- `docs/week9/kafka-basics.md` - Kafka 핵심 개념
- `docs/week9/kafka-setup.md` - 설치 및 환경 구성
- `docs/week9/kafka-spring-integration.md` - Spring Boot 연동
- `docs/week9/assignment-step17.md` - Step 17 상세 가이드
- `docs/week9/assignment-step18.md` - Step 18 상세 가이드

### 규칙 & 가이드
- `./AGENTS.md` - 모든 코딩 규칙 (단일 소스)
- `./.claude/CLAUDE.md` - Claude Code 사용 가이드
- `./GEMINI.md` - Gemini용 규칙 (AGENTS.md 보충)

### 아키텍처 & 테스트
- `docs/PROJECT_STRUCTURE.md` - 4-layer 아키텍처 상세
- `.claude/commands/testing.md` - 테스트 전략 (`/testing`)
- `.claude/commands/concurrency.md` - 동시성 제어 (`/concurrency`)

---

## 🎓 학습 목표 (Week 9)

### 핵심 개념
1. **Kafka vs Application Event**: 내구성, 처리량, 순서 보장 비교
2. **파티셔닝 전략**: 메시지 키 기반 파티션 분배
3. **Consumer Group**: 독립적 구독 vs 병렬 처리
4. **트랜잭션 연동**: AFTER_COMMIT 후 메시지 발행
5. **멱등성**: At-Least-Once 환경에서 중복 처리 방지
6. **모니터링**: Consumer Lag 확인 및 대응

### 실무 역량
- 이벤트 기반 아키텍처 설계 능력
- 대용량 트래픽 처리 전략 수립
- 메시지 기반 비동기 처리 구현
- 장애 복구 및 재처리 메커니즘 구현
- 운영 관점의 모니터링 및 로깅 전략 수립

---

## 📊 평가 기준

### STEP 17 (Pass 조건)
| 항목 | 배점 | 기준 |
|------|------|------|
| Kafka 개념 이해 | 30% | Producer, Consumer, Partition, Offset 정확히 설명 |
| 환경 구성 | 20% | Docker로 Kafka 실행, CLI 테스트 성공, 리소스 제한 설정 |
| Spring 연동 | 30% | 메시지 발행/소비 성공, 멱등성 처리, 재시도 전략 |
| 트랜잭션 연동 | 20% | AFTER_COMMIT 후 발행 검증, 실패 시나리오 테스트 |

### STEP 18 (Pass 조건)
| 항목 | 배점 | 기준 |
|------|------|------|
| 설계 문서 | 30% | 파티션 전략, Consumer 구성 명확히 설명 |
| 시퀀스 다이어그램 | 20% | Kafka 메시지 흐름 시각화 |
| 코드 구현 | 30% | 설계대로 동작하는 코드, 동시성 설정 적절 |
| 개선 효과 | 20% | 기존 방식 대비 장점 설명, 성능 목표 달성 |

---

## 🔍 페르소나 검증 요약

이 plan.md는 5인 시니어 페르소나 검증을 거쳐 개선되었습니다:

- **아키텍트 (20년)**: Kafka 패키지 구조 명시, 의존성 방향 명확화
- **백엔드 리드 (15년)**: Kafka 발행 실패 재시도, 멱등성, Producer 트랜잭션 추가
- **시니어 개발자 (10년)**: 실패 시나리오 테스트, 엣지 케이스, 테스트 격리 전략 추가
- **미들 개발자 (7년)**: 코드 예시 링크, 에러 처리, 설정값 설명, 의존성 그래프 추가
- **데브옵스 (12년)**: 구조화된 로깅, 리소스 제한, Consumer Lag 모니터링 필수화, 성능 목표 설정

---

**Last Updated**: 2025-12-20 (v2 - 페르소나 검증 반영)
