# Week 10: 모니터링 항목 정리 (실무 관점 + 이커머스 도메인)

> 목적: STEP19-20에서 “부하 테스트 결과를 해석하고(원인 분석)”, “장애 대응(탐지/전파/복구/회고)” 문서를 작성할 때 필요한 최소~확장 지표를 정리한다.

---

## 1) 페르소나별 “우선순위” 관점

### (1) SRE/플랫폼 엔지니어(운영 안정성)
- **Golden Signals(RED/USE) + SLO** 기반으로 “고객 영향”을 먼저 본다.
- 알람은 “한 번에 원인까지”보다 **조기 탐지 + 빠른 격리**가 목표.

### (2) 백엔드 리드(서비스 품질/성능)
- API 단위 p95/p99, 5xx 비율, DB pool 포화, 락/트랜잭션 시간을 먼저 본다.
- “병목이 어디서 시작되는지”를 추적할 수 있어야 한다(로그/트레이스/쿼리).

### (3) DBA/데이터 엔지니어(DB 안정성)
- 커넥션/락/슬로우쿼리/QPS 급증을 우선으로 본다.
- 앱 지연의 많은 경우가 DB로 귀결되므로 “DB가 버티는지”가 중요하다.

### (4) 데이터 플랫폼/이벤트(카프카 운영)
- consumer lag, DLQ/DLT, 처리량(consume rate), 재처리/중복(멱등) 지표가 핵심.
- 이벤트는 “재처리 가능”하므로 **중복을 전제로 관측/알람**해야 한다.

### (5) PM/비즈니스(도메인 KPI)
- 매출/주문 성공률/결제 실패율/쿠폰 소진/재고 부족 등 “비즈니스 임팩트” 지표가 핵심.
- 기술 지표는 원인 분석용, 비즈니스 지표는 “장애 등급/전파 기준”이 된다.

---

## 2) 공통 필수: API(RED) + 리소스(USE)

> 실무에서 가장 자주 보는 1순위 묶음. (장애 탐지/성능 저하 탐지에 가장 효과적)

### API (RED: Rate / Errors / Duration)
- **요청량(Rate)**: API별 RPS/TPS (트래픽 변화 감지)
- **에러율(Errors)**: 5xx 비율(서버 장애), 4xx 비율(클라이언트 오류/유효성/정책)
- **지연(Duration)**: p50/p95/p99 (체감 성능, tail latency)

PromQL 예시(가이드):
- 전체 RPS:
  - `sum(rate(http_server_requests_seconds_count[1m]))`
- API별 RPS(라벨은 환경에 따라 `uri`/`method` 등이 다를 수 있음):
  - `sum by (uri, method)(rate(http_server_requests_seconds_count[1m]))`
- 5xx 비율:
  - `sum(rate(http_server_requests_seconds_count{outcome=\"SERVER_ERROR\"}[1m])) / sum(rate(http_server_requests_seconds_count[1m]))`
- p95:
  - `histogram_quantile(0.95, sum by (le)(rate(http_server_requests_seconds_bucket[5m])))`

### 시스템/프로세스 (USE: Utilization / Saturation / Errors)
- **CPU 사용량**: 급증/지속 포화 여부
- **메모리**: RSS/heap 증가 추세(누수/캐시 폭주)
- **스레드**: live threads 증가(블로킹/대기 증가)

PromQL 예시:
- JVM Heap 사용량:
  - `sum by (area)(jvm_memory_used_bytes{area=\"heap\"})`
- GC Pause(추세):
  - `rate(jvm_gc_pause_seconds_sum[5m])`
- Live threads:
  - `jvm_threads_live_threads`

---

## 3) DB(MySQL) 관측: “앱에서 먼저 보는 지표” vs “DB에서 보는 지표”

### (A) 앱 레벨(현재 프로젝트에서 즉시 활용 가능성이 높음)
- **HikariCP Connection Pool**
  - `hikaricp_connections_active`: 사용 중 커넥션
  - `hikaricp_connections_idle`: 유휴 커넥션
  - `hikaricp_connections_pending`: 커넥션 대기(포화의 대표 신호)
  - `hikaricp_connections_timeout_total`: 커넥션 획득 타임아웃 누적(장애 임계 신호)

실무 해석:
- `pending > 0`이 “지속”되면: DB가 느리거나, 트랜잭션이 길거나, 락 대기/데드락 가능성.
- API p95/p99 상승과 함께 보면 원인 추정이 빨라진다.

### (B) DB 레벨(확장/심화: exporter/JMX 등 추가 시)
- QPS/Connections/Threads_running
- Slow query count, lock wait time, deadlock count
- Buffer pool hit ratio, replication lag(해당 시)

---

## 4) Redis 관측(캐시/락/쿠폰/랭킹)

### 실무에서 자주 보는 항목
- **명령 처리량(QPS)**: `GET/SET/ZINCRBY/SADD` 등 사용 패턴 변화 감지
- **Latency(명령 지연)**: 네트워크/서버 포화/키 폭주 탐지
- **메모리 사용량**: maxmemory 도달/eviction 발생 여부
- **Evictions**: eviction이 발생하면 캐시 효율 및 DB 부하 급증으로 이어질 수 있음
- **Connection/timeout**: Redis 연결 불안정은 5xx/지연으로 즉시 번진다

프로젝트 관점(이커머스):
- 랭킹(ZSET): `ZINCRBY` 폭주, key TTL/카디널리티
- 쿠폰/예약(Lua): 스크립트 실행 시간 증가(지연) 또는 timeout
- 분산락(사용 시): 락 획득 실패율/대기 시간

---

## 5) Kafka 관측(이벤트 기반 처리)

### 실무에서 가장 중요한 1순위
- **Consumer Lag**: 처리량 부족/정체의 대표 지표
- **DLQ/DLT 건수**: “복구/보상”이 실제로 얼마나 발생하는지
- **Consume/Produce rate**: 처리량 변화, 스파이크 탐지
- **Retry/재처리 비율**: 멱등 처리 실패/예외 폭주 탐지

프로젝트 관점(예: 쿠폰 발급)
- “중복 발급(UK 충돌)”은 **정상 멱등 케이스**로 분류되어야 함(알람은 낮은 등급)
- “DLT 이동”은 **영구 실패/설계 결함/데이터 품질 문제** 가능성이 높아 더 높은 등급으로 분류

---

## 6) 이커머스 도메인 KPI(장애 등급/전파 기준에 직접 사용)

> 운영에서는 기술지표보다 “비즈니스 지표”로 장애 등급을 먼저 판단한다.

### 주문/결제
- 주문 생성 성공률/실패율
- 결제 성공률/실패율(PG 오류, 잔액 부족, 타임아웃 등)
- 결제 처리 시간(p95/p99)

### 재고/상품
- 재고 부족으로 인한 실패율(판매 기회 손실)
- 재고 차감 실패/재시도 횟수(동시성/락 문제 시그널)
- 인기 상품 조회 트래픽 및 응답 지연(사용자 체감에 직결)

### 쿠폰/프로모션
- 쿠폰 예약 성공률/품절률
- “발급 요청 대비 발급 확정률”(이벤트/비동기 구조의 건강도)
- DLT/보상 발생률(시스템 안정성/데이터 품질)

### 장바구니
- add-to-cart 성공률/지연
- 장바구니 조회 지연(결제 전환에 영향)

---

## 7) “우리 프로젝트”에서 지금 당장 수집 가능한 것 / 추가가 필요한 것

### 바로 가능(Actuator + Micrometer Prometheus)
- `http_server_requests_seconds_*` (API RED)
- `jvm_*` (heap/GC/threads)
- `hikaricp_*` (DB pool)

### 추가 구성이 필요한 것(선택)
- Redis: exporter 또는 lettuce/command latency 메트릭 활성화
- MySQL: mysqld_exporter 등으로 DB 내부 지표 수집
- Kafka: JMX exporter 또는 broker/consumer 메트릭 수집 구성
- 도메인 KPI: 주문/결제/쿠폰 등 **커스텀 Micrometer 메트릭** 정의

---

## 8) 알람 설계(간단 가이드)

> 알람은 “원인”보다 “영향”을 먼저 잡는다.

### P0(즉시 대응)
- 5xx 비율 급증 + p95/p99 급등
- Hikari `pending` 지속 증가 + timeout 발생
- Consumer lag 급증 + 주문/결제/쿠폰 확정 실패율 증가(비즈니스 영향)

### P1(관찰/조사)
- 4xx 급증(클라이언트/정책/배포 영향 가능)
- GC pause 증가(장기적으로 지연 유발)
- Redis eviction 증가(캐시 효율 저하 → DB 부하 증가 가능)

