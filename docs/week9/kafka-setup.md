# Kafka 설치 및 환경 구성

> **목표**: 로컬 환경과 Docker 환경에서 Kafka를 설치하고 실행하는 방법을 익힌다.

---

## 📋 목차

1. [환경 요구사항](#환경-요구사항)
2. [Docker Compose로 Kafka 실행](#docker-compose로-kafka-실행) ⭐ 권장
3. [로컬에 직접 설치](#로컬에-직접-설치)
4. [Kafka CLI 사용법](#kafka-cli-사용법)
5. [GUI 도구 설정](#gui-도구-설정)
6. [트러블슈팅](#트러블슈팅)

---

## 환경 요구사항

### 필수 사항
- **Java**: JDK 11 이상 (프로젝트는 JDK 21 사용 중)
- **메모리**: 최소 4GB RAM (권장 8GB)
- **디스크**: 최소 10GB 여유 공간

### 권장 사항
- **Docker Desktop**: 최신 버전 (가장 쉬운 방법)
- **OS**: macOS, Linux, Windows (WSL2)

### 버전 정보
```
Kafka: 3.6.1
Zookeeper: 3.8.3 (Kafka 3.x에서는 선택사항)
```

---

## Docker Compose로 Kafka 실행

> ⭐ **권장 방법**: 가장 빠르고 간편하게 Kafka를 실행할 수 있습니다.

### 1단계: Docker Compose 파일 생성

프로젝트 루트 디렉토리에 `docker-compose.yml` 파일을 생성합니다.

```yaml
# docker-compose.yml
version: '3.8'

services:
  # Zookeeper (Kafka 메타데이터 관리)
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.3
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"
    networks:
      - kafka-network

  # Kafka Broker
  kafka:
    image: confluentinc/cp-kafka:7.5.3
    container_name: kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
      - "9093:9093"
    environment:
      # Kafka Broker 설정
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181

      # Listener 설정 (내부/외부 통신)
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,PLAINTEXT_HOST://0.0.0.0:9092
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT

      # Topic 설정
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1

      # Auto Create Topic (개발 편의)
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'

      # Log 설정
      KAFKA_LOG_RETENTION_HOURS: 168  # 7일
      KAFKA_LOG_SEGMENT_BYTES: 1073741824  # 1GB
    volumes:
      - kafka-data:/var/lib/kafka/data
    networks:
      - kafka-network
    healthcheck:
      test: ["CMD-SHELL", "kafka-broker-api-versions --bootstrap-server localhost:9092 || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Kafka UI (GUI 도구) - 선택사항
  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: kafka-ui
    depends_on:
      - kafka
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
      KAFKA_CLUSTERS_0_ZOOKEEPER: zookeeper:2181
    networks:
      - kafka-network

volumes:
  kafka-data:
    driver: local

networks:
  kafka-network:
    driver: bridge
```

### 2단계: Kafka 실행

```bash
# 프로젝트 루트 디렉토리에서
docker-compose up -d

# 로그 확인
docker-compose logs -f kafka

# 상태 확인
docker-compose ps
```

**예상 출력**
```
NAME         IMAGE                                PORTS
kafka        confluentinc/cp-kafka:7.5.3         0.0.0.0:9092->9092/tcp, 0.0.0.0:9093->9093/tcp
kafka-ui     provectuslabs/kafka-ui:latest       0.0.0.0:8090->8080/tcp
zookeeper    confluentinc/cp-zookeeper:7.5.3     0.0.0.0:2181->2181/tcp
```

### 3단계: 동작 확인

```bash
# 1. Kafka 컨테이너 접속
docker exec -it kafka bash

# 2. Topic 생성
kafka-topics --create \
  --topic test-topic \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1

# 3. Topic 목록 확인
kafka-topics --list --bootstrap-server localhost:9092

# 4. Topic 상세 정보
kafka-topics --describe \
  --topic test-topic \
  --bootstrap-server localhost:9092
```

**예상 출력**
```
Topic: test-topic       TopicId: xyz123       PartitionCount: 3       ReplicationFactor: 1
        Topic: test-topic       Partition: 0    Leader: 1       Replicas: 1     Isr: 1
        Topic: test-topic       Partition: 1    Leader: 1       Replicas: 1     Isr: 1
        Topic: test-topic       Partition: 2    Leader: 1       Replicas: 1     Isr: 1
```

### 4단계: 메시지 테스트

**터미널 1: Producer**
```bash
docker exec -it kafka bash

kafka-console-producer \
  --topic test-topic \
  --bootstrap-server localhost:9092

# 메시지 입력 (엔터로 전송)
> Hello Kafka!
> This is a test message
> 안녕하세요 카프카!
```

**터미널 2: Consumer**
```bash
docker exec -it kafka bash

kafka-console-consumer \
  --topic test-topic \
  --from-beginning \
  --bootstrap-server localhost:9092

# 메시지 수신 확인
Hello Kafka!
This is a test message
안녕하세요 카프카!
```

### 5단계: Kafka UI 접속

브라우저에서 `http://localhost:8090` 접속

- Topic 목록 확인
- 메시지 조회
- Consumer Group 상태 확인

### 6단계: 종료 및 정리

```bash
# Kafka 중지 (데이터 유지)
docker-compose stop

# Kafka 재시작
docker-compose start

# Kafka 완전 삭제 (데이터 포함)
docker-compose down -v
```

---

## 로컬에 직접 설치

> **주의**: Docker 사용을 권장합니다. 로컬 설치는 학습 목적이나 특수한 경우에만 사용하세요.

### macOS (Homebrew)

```bash
# 1. Kafka 설치 (Zookeeper 포함)
brew install kafka

# 2. Zookeeper 실행
zookeeper-server-start /opt/homebrew/etc/kafka/zookeeper.properties

# 3. Kafka 실행 (새 터미널)
kafka-server-start /opt/homebrew/etc/kafka/server.properties
```

### Linux

```bash
# 1. Kafka 다운로드
wget https://downloads.apache.org/kafka/3.6.1/kafka_2.13-3.6.1.tgz
tar -xzf kafka_2.13-3.6.1.tgz
cd kafka_2.13-3.6.1

# 2. Zookeeper 실행
bin/zookeeper-server-start.sh config/zookeeper.properties

# 3. Kafka 실행 (새 터미널)
bin/kafka-server-start.sh config/server.properties
```

### Windows (WSL2 권장)

```bash
# WSL2에서 Linux 설치 방법 동일
# 또는 Docker Desktop 사용 권장
```

### 로컬 설치 확인

```bash
# Topic 생성
kafka-topics --create \
  --topic test \
  --bootstrap-server localhost:9092 \
  --partitions 1 \
  --replication-factor 1

# Topic 목록
kafka-topics --list --bootstrap-server localhost:9092
```

---

## Kafka CLI 사용법

### Topic 관리

#### Topic 생성
```bash
# 기본 생성
kafka-topics --create \
  --topic order-completed \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1

# 옵션 포함
kafka-topics --create \
  --topic order-completed \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000 \  # 7일 보관
  --config compression.type=gzip     # 압축
```

#### Topic 목록 조회
```bash
kafka-topics --list \
  --bootstrap-server localhost:9092
```

#### Topic 상세 정보
```bash
kafka-topics --describe \
  --topic order-completed \
  --bootstrap-server localhost:9092

# 모든 Topic 상세 정보
kafka-topics --describe \
  --bootstrap-server localhost:9092
```

**출력 예시**
```
Topic: order-completed  TopicId: abc123       PartitionCount: 3       ReplicationFactor: 1
        Topic: order-completed  Partition: 0    Leader: 1       Replicas: 1     Isr: 1
        Topic: order-completed  Partition: 1    Leader: 1       Replicas: 1     Isr: 1
        Topic: order-completed  Partition: 2    Leader: 1       Replicas: 1     Isr: 1
```

#### Topic 설정 변경
```bash
# Retention 변경 (14일)
kafka-configs --alter \
  --entity-type topics \
  --entity-name order-completed \
  --add-config retention.ms=1209600000 \
  --bootstrap-server localhost:9092

# 설정 확인
kafka-configs --describe \
  --entity-type topics \
  --entity-name order-completed \
  --bootstrap-server localhost:9092
```

#### Topic 삭제
```bash
kafka-topics --delete \
  --topic order-completed \
  --bootstrap-server localhost:9092
```

### Producer

#### 메시지 발행 (콘솔)
```bash
# 기본 발행
kafka-console-producer \
  --topic order-completed \
  --bootstrap-server localhost:9092

# Key-Value 발행
kafka-console-producer \
  --topic order-completed \
  --bootstrap-server localhost:9092 \
  --property "parse.key=true" \
  --property "key.separator=:"

# 입력 예시
> user-123:{"orderId":"order-789","amount":50000}
> user-456:{"orderId":"order-790","amount":30000}
```

#### 파일에서 발행
```bash
# messages.txt 파일 생성
cat > messages.txt << EOF
{"orderId":"order-123","userId":"user-1","amount":10000}
{"orderId":"order-124","userId":"user-2","amount":20000}
{"orderId":"order-125","userId":"user-3","amount":30000}
EOF

# 파일 내용 발행
kafka-console-producer \
  --topic order-completed \
  --bootstrap-server localhost:9092 \
  < messages.txt
```

### Consumer

#### 메시지 소비 (콘솔)
```bash
# 최신 메시지부터
kafka-console-consumer \
  --topic order-completed \
  --bootstrap-server localhost:9092

# 처음부터
kafka-console-consumer \
  --topic order-completed \
  --from-beginning \
  --bootstrap-server localhost:9092

# Key-Value 함께 출력
kafka-console-consumer \
  --topic order-completed \
  --from-beginning \
  --bootstrap-server localhost:9092 \
  --property print.key=true \
  --property key.separator=" : "

# Partition과 Offset 포함
kafka-console-consumer \
  --topic order-completed \
  --from-beginning \
  --bootstrap-server localhost:9092 \
  --property print.partition=true \
  --property print.offset=true \
  --property print.key=true
```

**출력 예시**
```
Partition:0, Offset:0, Key:user-123 : {"orderId":"order-789","amount":50000}
Partition:1, Offset:0, Key:user-456 : {"orderId":"order-790","amount":30000}
Partition:2, Offset:0, Key:user-789 : {"orderId":"order-791","amount":40000}
```

#### Consumer Group 지정
```bash
kafka-console-consumer \
  --topic order-completed \
  --from-beginning \
  --bootstrap-server localhost:9092 \
  --group my-consumer-group
```

### Consumer Group 관리

#### Consumer Group 목록
```bash
kafka-consumer-groups --list \
  --bootstrap-server localhost:9092
```

#### Consumer Group 상세 정보
```bash
kafka-consumer-groups --describe \
  --group my-consumer-group \
  --bootstrap-server localhost:9092
```

**출력 예시**
```
GROUP            TOPIC            PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG  CONSUMER-ID     HOST            CLIENT-ID
my-consumer-group order-completed  0         100             105             5    consumer-1-...  /172.17.0.1     consumer-1
my-consumer-group order-completed  1         200             200             0    consumer-2-...  /172.17.0.2     consumer-2
my-consumer-group order-completed  2         150             160             10   consumer-3-...  /172.17.0.3     consumer-3
```

**LAG**: 아직 처리하지 못한 메시지 수

#### Offset 초기화 (재처리)
```bash
# 가장 처음부터
kafka-consumer-groups --reset-offsets \
  --group my-consumer-group \
  --topic order-completed \
  --to-earliest \
  --bootstrap-server localhost:9092 \
  --execute

# 특정 Offset으로
kafka-consumer-groups --reset-offsets \
  --group my-consumer-group \
  --topic order-completed:0 \
  --to-offset 50 \
  --bootstrap-server localhost:9092 \
  --execute

# 특정 시간으로 (2024-12-18 00:00:00)
kafka-consumer-groups --reset-offsets \
  --group my-consumer-group \
  --topic order-completed \
  --to-datetime 2024-12-18T00:00:00.000 \
  --bootstrap-server localhost:9092 \
  --execute
```

**주의**: Consumer를 먼저 중지해야 합니다.

---

## GUI 도구 설정

### 1. Kafka UI (Docker Compose 포함)

이미 Docker Compose에 포함되어 있습니다.

```bash
# 접속
http://localhost:8090

# 기능
- Topic 생성/삭제/조회
- 메시지 조회/발행
- Consumer Group 모니터링
- Broker 상태 확인
```

### 2. AKHQ (Alternative)

```yaml
# docker-compose.yml에 추가
  akhq:
    image: tchiotludo/akhq:latest
    container_name: akhq
    depends_on:
      - kafka
    ports:
      - "8091:8080"
    environment:
      AKHQ_CONFIGURATION: |
        akhq:
          connections:
            local:
              properties:
                bootstrap.servers: "kafka:29092"
    networks:
      - kafka-network
```

```bash
# 접속
http://localhost:8091
```

### 3. Conduktor (상용, 무료 체험)

```bash
# 다운로드
https://www.conduktor.io/

# 설치 후 Kafka 연결
Bootstrap Server: localhost:9092
```

---

## 트러블슈팅

### 문제 1: Kafka 컨테이너가 시작되지 않음

**증상**
```bash
docker-compose ps
# kafka 컨테이너 상태: Restarting
```

**원인 및 해결**

1. **포트 충돌**
```bash
# 9092 포트 사용 중인 프로세스 확인
lsof -i :9092

# 프로세스 종료
kill -9 <PID>
```

2. **메모리 부족**
```bash
# Docker Desktop > Settings > Resources
# Memory: 4GB 이상 할당
```

3. **Zookeeper 미실행**
```bash
# Zookeeper 상태 확인
docker-compose logs zookeeper

# Zookeeper 재시작
docker-compose restart zookeeper
docker-compose restart kafka
```

### 문제 2: Producer/Consumer 연결 실패

**증상**
```
Error: Connection to node -1 (localhost/127.0.0.1:9092) could not be established
```

**해결**

1. **Bootstrap Server 확인**
```bash
# 컨테이너 내부에서는 kafka:29092
# 호스트에서는 localhost:9092
```

```java
// 애플리케이션 설정
spring:
  kafka:
    bootstrap-servers: localhost:9092  # 호스트에서 실행 시
```

2. **네트워크 확인**
```bash
# Kafka 네트워크 상태
docker network inspect ecommerce_kafka-network
```

### 문제 3: Topic이 자동 생성되지 않음

**증상**
```
Topic 'order-completed' does not exist
```

**해결**

1. **수동 생성**
```bash
docker exec -it kafka kafka-topics --create \
  --topic order-completed \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1
```

2. **Auto Create 활성화 (개발 환경)**
```yaml
# docker-compose.yml
KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
```

### 문제 4: Consumer Lag 계속 증가

**증상**
```bash
kafka-consumer-groups --describe \
  --group my-group \
  --bootstrap-server localhost:9092

# LAG이 계속 증가
```

**원인 및 해결**

1. **처리 속도 < 발행 속도**
```bash
# Consumer 수 증가 (Partition 수만큼)
# Spring Boot: 인스턴스 추가 or concurrency 설정
```

```yaml
spring:
  kafka:
    listener:
      concurrency: 3  # 동시 처리 스레드 수
```

2. **처리 로직 최적화**
```java
// 배치 처리
@KafkaListener(topics = "order-completed")
public void handleBatch(List<OrderMessage> messages) {
    // 한 번에 여러 메시지 처리
    orderService.processBatch(messages);
}
```

3. **Partition 증가**
```bash
# Partition 3 → 6으로 증가
kafka-topics --alter \
  --topic order-completed \
  --partitions 6 \
  --bootstrap-server localhost:9092
```

### 문제 5: 메시지 유실

**증상**
- Producer는 성공했지만 Consumer가 메시지를 못 받음

**원인 및 해결**

1. **Offset Reset 정책**
```yaml
spring:
  kafka:
    consumer:
      auto-offset-reset: earliest  # 처음부터 읽기
```

2. **Replication Factor 확인**
```bash
# RF가 1이면 Broker 장애 시 유실 가능
# RF를 3으로 설정 권장 (프로덕션)
```

3. **ACK 설정**
```yaml
spring:
  kafka:
    producer:
      acks: all  # 모든 Replica가 받을 때까지 대기
```

---

## 개발 환경 체크리스트

### ✅ 초기 설정
- [ ] Docker Compose로 Kafka 실행
- [ ] Kafka UI 접속 확인 (http://localhost:8090)
- [ ] Topic 생성 및 조회
- [ ] Producer/Consumer 테스트
- [ ] 로그 확인 (`docker-compose logs -f kafka`)

### ✅ Spring Boot 연동 준비
- [ ] `spring-kafka` 의존성 추가
- [ ] `application.yml`에 Kafka 설정 추가
- [ ] Topic 생성 (개발용은 Auto Create 사용 가능)
- [ ] Producer 테스트
- [ ] Consumer 테스트

### ✅ 테스트 환경
- [ ] Testcontainers 설정 (통합 테스트)
- [ ] 테스트용 Topic 별도 관리
- [ ] 테스트 후 데이터 정리

---

## 다음 단계

- [ ] [Spring Boot와 Kafka 연동](./kafka-spring-integration.md)
- [ ] [비즈니스 프로세스 개선](./kafka-use-cases.md)

---

## 참고 자료

### 공식 문서
- [Kafka Quickstart](https://kafka.apache.org/quickstart)
- [Kafka Docker Images](https://hub.docker.com/r/confluentinc/cp-kafka/)
- [Kafka CLI Reference](https://kafka.apache.org/documentation/#cli)

### 유용한 도구
- [Kafka UI](https://github.com/provectus/kafka-ui)
- [AKHQ](https://akhq.io/)
- [Conduktor](https://www.conduktor.io/)

---

**Last Updated**: 2024-12-18
