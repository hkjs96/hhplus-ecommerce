# LGTM (Loki + Grafana + Tempo + Prometheus) with OTel Agent (Docker)

이 문서는 **과제(코드/테스트)와 완전히 분리된** 토요 지식회용 가이드입니다.

## 목표
- `docker compose`로 우리 프로젝트(`ecommerce`) 앱까지 포함해 구동
- OTel Java Agent로 **무코드 계측**(traces 중심)
- Grafana에서 **Traces(Tempo) / Logs(Loki) / Metrics(Prometheus)** 를 연결해서 실패 원인까지 빠르게 추적
- (가능하면) Tempo 기반 **Service Graph** 확인
- k6로 “부하”가 아니라 **흐름/실패 시나리오를 가볍게 재현**

## 사전 준비
- Docker Desktop
- 포트 사용: `3000(Grafana)`, `8080(App)`, `3100(Loki)`, `3200(Tempo)`, `9090(Prometheus)`

## 빠른 시작
### 1) 최초 1회(앱 이미지 빌드)
```bash
docker compose -f observability/lgtm/docker-compose.yml build app1
```

### 2) 일반 실행(이미지 재사용)
> `--build`를 붙이지 않으면 기존 `hhplus-ecommerce-app:lgtm` 이미지를 사용합니다.

```bash
docker compose -f observability/lgtm/docker-compose.yml up -d
```

### 영속성(로컬 디렉터리)
- 데이터는 `observability/lgtm/data/` 아래에 저장됩니다.
- 필요하면 전체 초기화: `rm -rf observability/lgtm/data`

### 접속
- App(Nginx → app1~3 로드밸런싱): `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`
- Grafana: `http://localhost:3000` (기본: `admin` / `admin`)

## k6 (흐름 확인용)
```bash
docker compose -f observability/lgtm/docker-compose.yml run --rm k6
```

## Grafana에서 확인할 것(최소)
1) **Tempo**: Explore → Traces에서 `service.name=ecommerce-app` 로 조회 후 에러 trace 확인  
2) **Loki**: Explore → Logs에서 `compose_service="app"` / `compose_service="mysql"` / `compose_service="redis"` 등으로 로그 확인  
3) **Prometheus**: Explore → Metrics에서 아래 지표로 흐름 확인  
   - 앱: `http_server_requests_seconds_count`  
   - k6: `k6_*` (k6 → StatsD → statsd-exporter → Prometheus scrape)

## (옵션) Trace ↔ Logs 링크
- 이 구성은 Loki에 컨테이너 로그를 수집합니다.
- App 로그에 `trace_id`가 포함되면(OTel agent MDC 주입 + 콘솔 패턴), Grafana에서 **Trace → 관련 로그** 이동이 쉬워집니다.

## (옵션) Service Graph
- 이 구성은 **Tempo 자체 metrics-generator가 아니라**, `otel-collector`의 `servicegraph`/`spanmetrics` 커넥터로
  service graph용 메트릭을 생성해서 Prometheus(remote write)에 적재합니다.
- Grafana → Explore → Tempo에서 **Service Graph** / **Node Graph** 탭이 활성화되는지 확인합니다.
- Prometheus에서 `traces_service_graph_request_total` 이 조회되면(service graph 메트릭 생성) 정상입니다.

## DB/Redis 스팬(현재 기본)
- 지금 구성은 **DB/JDBC, Hibernate, Redis** 스팬을 기본으로 포함합니다.
- 대신 Repository 스팬이 너무 많아 **Spring Data만 기본 OFF** 입니다: `otel.instrumentation.spring-data.enabled=false`
- Repository 스팬까지 보고 싶으면 `observability/lgtm/otel-agent.properties`에서 위 값을 지우거나 `true`로 바꾸고 `docker compose -f observability/lgtm/docker-compose.yml restart app`

## 트러블슈팅
- App 로그에 `Failed to connect to otel-collector:4317`가 나오면:
  - `observability/lgtm/otel-collector.yaml`에서 OTLP receiver가 `0.0.0.0:4317/4318`로 바인딩되는지 확인 후 `docker compose -f observability/lgtm/docker-compose.yml restart otel-collector app`
