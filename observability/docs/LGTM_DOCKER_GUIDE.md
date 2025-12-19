# LGTM + OpenTelemetry (Docker) — hhplus-ecommerce 데모 가이드

이 문서는 **과제 코드/테스트와 무관한** 관측성(Observability) 데모용 구성 가이드입니다.

## 1) 구성 목표
- Docker로 `ecommerce` 앱 + MySQL + Redis + LGTM를 한 번에 구동
- OpenTelemetry Java Agent로 **무코드 트레이싱**
- Grafana에서
  - Traces(Tempo)
  - Logs(Loki, Docker 로그 수집)
  - Metrics(Prometheus: 앱/인프라 + k6)
  - Service Graph / Node Graph
  를 함께 확인

## 2) 구성 요소(요약)
- App: `observability/lgtm/app.Dockerfile` (Gradle `bootJar` 빌드 → 컨테이너 실행)
- OTel Java Agent: `observability/lgtm/app.Dockerfile`에서 다운로드, `JAVA_TOOL_OPTIONS`로 적용
- OTel Collector: `observability/lgtm/otel-collector.yaml`
  - OTLP 수신(4317/4318) → Tempo export
  - service graph/span metrics 생성(servicegraph/spanmetrics) → Prometheus(remote write)
- Tempo: traces 저장(로컬 스토리지)
- Loki: Docker 로그 수집(Alloy) + 저장(로컬)
- Alloy: Docker logs → Loki (라벨 포함)
- Prometheus: scrape + remote write receiver
- k6: 흐름/실패 재현(트레이스는 OTLP로, 메트릭은 StatsD→statsd-exporter→Prometheus)

## 3) 파일/디렉터리
- 실행/설정: `observability/lgtm/`
  - `docker-compose.yml`
  - `README.md` (짧은 실행 메모)
  - `app.env` (Spring 환경 변수)
  - `otel-agent.properties` (OTel agent 설정)
  - `otel-collector.yaml` (collector 파이프라인)
  - `tempo.yaml`, `loki.yaml`, `prometheus.yml`, `alloy.river`
  - `grafana/provisioning/datasources/datasources.yml`
  - `k6/script.js`
- 영속 데이터: `observability/lgtm/data/` (자동 생성)

## 4) 실행
### 4.1 최초 1회(이미지 빌드)
> 앱 코드를 바꿔도 “이미지”를 다시 만들기 전까지는 기존 이미지를 그대로 사용합니다.

```bash
docker compose -f observability/lgtm/docker-compose.yml build app1
```

### 4.2 일반 실행(이미지 재사용)
> `--build`를 붙이지 않으면 기존 `hhplus-ecommerce-app:lgtm` 이미지를 사용합니다.

```bash
docker compose -f observability/lgtm/docker-compose.yml up -d
```

### 접속
- App(Nginx → app1~3 로드밸런싱): `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`
- Grafana: `http://localhost:3000` (기본: `admin` / `admin`)
- Prometheus: `http://localhost:9090`

## 5) k6 실행(흐름 + 실패)
> 부하 테스트가 아니라 “흐름/실패” 가시화가 목적입니다.

```bash
docker compose -f observability/lgtm/docker-compose.yml run --rm k6
```

### k6 메트릭이 Prometheus에 들어오는지 빠른 확인
```bash
curl "http://localhost:9090/api/v1/query?query=k6_http_reqs_total"
```

## 6) Grafana에서 보는 순서(추천)
### 6.1 Tempo Traces
1) Grafana → Explore → Tempo
2) 최근 trace에서 `POST /api/orders` / `POST /api/orders/{orderId}/payment` 를 열기
3) Span tree에서 내부 스팬(UseCase/Facade/Listener/Handler)이 함께 보이는지 확인

### 6.2 Node Graph
- Explore(Tempo)에서 trace 상세 화면의 **Node graph** 탭

### 6.3 Service Graph
- Explore(Tempo) 또는 대시보드에서 **Service Graph** 패널/탭
- Prometheus에 아래 메트릭이 있으면 정상입니다:
  - `traces_service_graph_request_total`

```bash
curl "http://localhost:9090/api/v1/query?query=traces_service_graph_request_total"
```

### 6.4 Loki Logs
- Explore → Loki에서 아래 라벨로 조회
  - `{compose_service="app"}`
  - `{compose_service="mysql"}`
  - `{compose_service="redis"}`

## 7) “내부 스팬은 많이, Repository 스팬은 적당히” 설정
- 내부 비즈니스 흐름 스팬(무코드)은 `observability/lgtm/otel-agent.properties`의
  - `otel.instrumentation.methods.enabled=true`
  - `otel.instrumentation.methods.include=...`
  로 제어합니다.

- Repository 스팬이 너무 많으면 기본적으로 Spring Data instrumentation을 끕니다:
  - `otel.instrumentation.spring-data.enabled=false`

변경 후에는 앱만 재시작하면 됩니다.
```bash
docker compose -f observability/lgtm/docker-compose.yml restart app
```

## 8) 영속성/초기화
### 8.1 전체 초기화(트레이스/로그/메트릭/그라파나 + DB/Redis까지)
```bash
docker compose -f observability/lgtm/docker-compose.yml down
rm -rf observability/lgtm/data
docker compose -f observability/lgtm/docker-compose.yml up -d
```

### 8.2 LGTM만 초기화(예: Grafana/Loki/Tempo/Prometheus만)
> DB(MySQL)/Redis는 남기고 관측 데이터만 지우고 싶을 때
```bash
docker compose -f observability/lgtm/docker-compose.yml down
rm -rf observability/lgtm/data/{grafana,loki,tempo,prometheus}
docker compose -f observability/lgtm/docker-compose.yml up -d
```

## 8.3 앱 코드 변경을 반영하고 싶다면(이미지 재빌드)
> 이 문서에서 말하는 “재빌드”는 Gradle 빌드가 아니라 **Docker 이미지 빌드**입니다.

```bash
docker compose -f observability/lgtm/docker-compose.yml build app1
docker compose -f observability/lgtm/docker-compose.yml up -d --force-recreate app1 app2 app3 nginx
```

## 9) 자주 겪는 이슈(체크리스트)
> 아래는 실제 구성 과정에서 “한 번씩 다 터졌던” 트러블슈팅을 모아둔 목록입니다.

### 9.1 trace가 안 들어온다: `Failed to connect to otel-collector:4317`
- collector OTLP receiver가 `0.0.0.0:4317/4318`로 바인딩돼야 합니다.
- 재시작:
```bash
docker compose -f observability/lgtm/docker-compose.yml restart otel-collector app
```

### 9.2 Loki에 로그가 없다
- Alloy가 죽어있지 않은지 확인:
```bash
docker compose -f observability/lgtm/docker-compose.yml ps -a | rg alloy
docker logs --tail=200 lgtm-alloy-1
```
- Alloy가 죽는 대표 원인(과거 사례)
  - River 문법 오류(필드 리스트에 `,` 누락)
  - `loki.source.docker`에 `targets`/`host` 누락
  - Docker discovery 없이 `loki.source.docker`만 사용

### 9.3 Grafana는 뜨는데 Loki/Tempo/Prometheus에 데이터가 “0”
- 먼저 컨테이너가 실제로 떠있는지 확인(Exited면 데이터가 당연히 0)
```bash
docker compose -f observability/lgtm/docker-compose.yml ps -a
```
- 과거 사례
  - `tempo.yaml`에 Tempo 버전이 지원하지 않는 필드를 넣어서 Tempo가 `Exited (1)` → trace 0
  - Alloy가 `Exited (1)` → Loki 로그 0
  - Prometheus가 잘못된 flag로 `Exited (1)` → k6/서비스 그래프 메트릭 0

### 9.4 k6 trace 출력 에러: `couldn't parse the otel URL: invalid URL scheme`
- `--traces-output`는 URL을 요구합니다.
- 예시(HTTP OTLP):
  - `--traces-output otel=http://otel-collector:4318`

### 9.5 Prometheus가 기동 실패: `unknown long flag ...`
- Prometheus는 버전별로 플래그가 다릅니다.
- 증상:
  - `docker compose ... ps -a`에서 `prometheus`가 `Exited (1)`
  - 다른 컨테이너에서 `nslookup prometheus`가 `NXDOMAIN`
- 해결:
  - `docker logs lgtm-prometheus-1`로 원인 플래그 확인 후 `docker-compose.yml`에서 제거/수정
  - 수정 후 재기동:
```bash
docker compose -f observability/lgtm/docker-compose.yml up -d --force-recreate prometheus
```

### 9.6 k6 remote write 400(out-of-order) 이슈
- 증상:
  - k6: `Failed to send the time series data ... status code: 400`
  - Prometheus: `Out of order sample from remote write`
- 해결(현재 구성):
  - k6는 remote write 대신 **StatsD → statsd-exporter → Prometheus scrape** 사용

### 9.7 Tempo Service Graph/Node Graph가 안 보임
- 원인:
  - “service graph용 메트릭”이 Prometheus에 없거나, Grafana Tempo datasource에서 Prometheus 연결이 안 됨
- 확인:
```bash
curl "http://localhost:9090/api/v1/query?query=traces_service_graph_request_total"
```
- 해결(현재 구성):
  - `otel-collector`에서 `servicegraph`/`spanmetrics` 커넥터로 메트릭 생성 후 Prometheus(remote write)
  - Grafana datasource provisioning에서 Tempo `serviceMap.datasourceUid=prometheus` 설정
  - datasource 변경 후 Grafana 재시작:
```bash
docker compose -f observability/lgtm/docker-compose.yml restart grafana
```

### 9.8 “내부 스팬(methods)이 안 보인다”
- 가장 흔한 원인: **앱을 재시작하지 않음** (에이전트 설정은 부팅 시에만 반영)
```bash
docker compose -f observability/lgtm/docker-compose.yml restart app
```
- 그리고 “GET만 보면 밋밋”할 수 있으니 `POST /api/orders` / `POST /api/orders/{orderId}/payment` trace를 확인

### 9.9 특정 요청이 계속 실패(예: balance charge 400/500)
- 예: `POST /api/users/{userId}/balance/charge`는 `idempotencyKey`가 필요합니다.
- k6 스크립트는 이를 반영한 상태입니다(`observability/lgtm/k6/script.js`)

### 9.10 mysqld-exporter가 죽는다(계정 정보 없음)
- 증상:
  - `no user specified in section or parent`
  - `.my.cnf err="no configuration found"`
- 해결:
  - `observability/lgtm/mysqld-exporter.my.cnf`를 마운트하도록 구성
  - 재기동:
```bash
docker compose -f observability/lgtm/docker-compose.yml up -d --force-recreate mysqld-exporter
```

### 9.11 “Loki 라벨이 안 보인다”(compose_service 등)
- Loki 라벨 인덱스는 “새 로그 유입”이 있어야 생깁니다.
- app 재시작 후 로그 생성:
```bash
docker compose -f observability/lgtm/docker-compose.yml restart app
curl -s http://localhost:8080/actuator/health >/dev/null
```

### 9.12 영속 데이터 초기화는 하고 싶은데 DB/Redis는 유지하고 싶다
- `observability/lgtm/data/{grafana,loki,tempo,prometheus}`만 지우고 재기동:
```bash
docker compose -f observability/lgtm/docker-compose.yml down
rm -rf observability/lgtm/data/{grafana,loki,tempo,prometheus}
docker compose -f observability/lgtm/docker-compose.yml up -d --build
```

### 9.3 mysqld-exporter가 죽는다(계정 정보 없음)
- `observability/lgtm/mysqld-exporter.my.cnf`가 마운트되어야 합니다.

### 9.13 (참고) Docker compose 상태가 갑자기 “텅 비어 보임”
- `docker compose ... ps`가 빈 목록을 보여주면, 보통
  - `down`으로 내렸거나,
  - 다른 디렉터리/파일로 compose를 보고 있거나,
  - 프로젝트 이름이 달라진 경우입니다.
- 항상 `-f observability/lgtm/docker-compose.yml`로 동일 파일을 지정하세요.
