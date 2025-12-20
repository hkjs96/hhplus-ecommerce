# Observability (LGTM / ms-demo) 트러블슈팅 모음

이 문서는 `observability/lgtm/`, `observability/ms-demo/` 구성 과정에서 실제로 발생했던 이슈와 해결책을 정리한 “운영/데모 체크리스트”입니다.

> 원칙: **원인(왜)** → **증상(무엇이 보이나)** → **해결(어떻게 고치나)** 순으로 확인합니다.

---

## 1) 공통 (LGTM / ms-demo)

### 1.1 Grafana/Prometheus/Tempo/Loki에 “데이터가 0”
- 원인: 컨테이너가 죽어있거나(Exited), datasource/스크랩 대상이 틀림
- 확인:
  - `docker compose -f <compose.yml> ps -a`
  - Grafana → Connections → Data sources에서 `Save & test`
- 해결:
  - Exited면 `docker logs <container>`로 원인 확인 후 재기동
  - 설정 변경 후 `docker compose ... restart <service>` 또는 `--force-recreate`

### 1.2 Grafana에 대시보드는 뜨는데 패널이 비어있음
- 원인: datasource UID 불일치(대시보드는 `uid`로 참조)
- 해결:
  - provisioning에서 `uid`를 명시(예: Loki `uid: loki`, Prometheus `uid: prometheus`, Tempo `uid: tempo`)
  - 적용: `docker compose ... up -d --force-recreate grafana`

### 1.3 “An unexpected error happened … Illegal value for lineNumber” (Tempo 플러그인)
- 원인: 대시보드의 Explore 링크 URL이 잘못되어 Tempo UI가 깨지는 케이스
- 해결:
  - 링크에서 미지원 매크로/인코딩 사용 금지
  - `${var:percentencode}` 같은 Grafana 변수 포맷을 사용
  - 적용: `docker compose ... up -d --force-recreate grafana`

### 1.4 OTLP 연결 실패: `Failed to connect to otel-collector:4317`
- 원인: collector가 `localhost`에 바인딩되어 컨테이너에서 접근 불가
- 해결:
  - `otel-collector.yaml`의 receiver를 `0.0.0.0:4317/4318`로 바인딩
  - 적용: `docker compose ... restart otel-collector <app>`

### 1.5 Service Graph / Node Graph가 안 보임
- 원인: Service Graph 메트릭이 Prometheus에 없거나, Tempo datasource가 Prometheus UID를 못 참조
- 확인:
  - `curl -s -G --data-urlencode 'query=traces_service_graph_request_total' http://localhost:9090/api/v1/query`
- 해결:
  - collector에서 `servicegraph`/`spanmetrics` 커넥터로 메트릭 생성 → Prometheus remote write
  - Grafana Tempo datasource: `serviceMap.datasourceUid=prometheus`, `nodeGraph.enabled=true`
  - 적용: `docker compose ... restart grafana otel-collector`

### 1.6 Service Graph에 `server="unknown"` 노드가 생김
- 의미: “unknown 서비스가 있다”가 아니라 **상대 서비스(서버) 추론 실패**
- 원인: client span에 `peer.service` 같은 대상 정보가 없어서 servicegraph가 server를 못 만듦
- 해결(구성):
  - collector에서 client span에 `peer.service`를 보강(예: `server.address` → `peer.service`)
  - 적용 후: `docker compose ... restart otel-collector`
- 해결(화면):
  - 임시로 대시보드 쿼리에서 `{server!="unknown"}` 필터링

### 1.7 Loki 라벨이 안 보임 (compose_service 등)
- 원인: 라벨 인덱스는 “새 로그 유입” 후에 생성됨
- 해결:
  - app/gateway 재시작 후 요청 한 번 발생

### 1.8 k6 traces URL 에러: `invalid URL scheme`
- 원인: `--traces-output`는 URL 스킴 포함 필요
- 해결:
  - `--traces-output otel=http://otel-collector:4318`

### 1.9 k6 remote write 400(out-of-order)
- 원인: Prometheus remote write에서 out-of-order 샘플로 400 발생 가능
- 해결:
  - k6 메트릭은 remote write 대신 **StatsD → statsd-exporter → Prometheus scrape**로 전환

---

## 2) `observability/lgtm` 전용

### 2.1 `docker compose up`에서 nginx 8080 포트 충돌
- 증상: `Bind for 0.0.0.0:8080 failed: port is already allocated`
- 원인: 기존 컨테이너(구 `app`/orphan)가 8080을 점유
- 해결:
  - `docker compose -f observability/lgtm/docker-compose.yml up -d --remove-orphans`
  - 또는 `docker ps | rg 8080`로 점유 컨테이너 확인 후 종료

### 2.2 Prometheus에 앱 메트릭이 0 (대시보드가 비어 보임)
- 원인: 스크랩 타겟이 `app:8080`로 남아있는데, 현재는 `app1~3` 구조
- 해결:
  - `observability/lgtm/prometheus.yml`에서 `app1:8080, app2:8080, app3:8080`로 수정
  - 적용: `docker compose ... restart prometheus`

### 2.3 멀티 앱 기동 시 초기 데이터 생성 충돌(duplicate key)
- 원인: 동일 seed 로직이 동시에 실행되며 유니크 키 충돌
- 해결:
  - seed 로직을 idempotent하게 만들거나, 특정 인스턴스만 seed 수행

### 2.4 DB/Redis 컨테이너 포트 충돌(예: 3306)
- 원인: 다른 compose의 MySQL이 이미 3306 점유
- 해결:
  - 스택을 먼저 `up -d`로 띄우고, k6는 `--no-deps`로 실행(의존 서비스 재기동 방지)
  - 예: `docker compose -f observability/lgtm/docker-compose.yml run --rm --no-deps k6`

---

## 3) `observability/ms-demo` 전용

### 3.1 “DB 쿼리 스팬만 보임”
- 원인: 앱 시작 시 seed(초기 데이터/부하테스트 사용자 생성)가 트레이스를 도배해서, UI에서 INSERT 트레이스만 계속 선택하게 됨
- 해결:
  - seed를 끄거나 최소화(예: `demo.loadtest.seed.enabled=false`)
  - Tempo 데이터 초기화 후(선택) k6로 요청을 다시 발생시키고 `service.name=api-gateway`로 검색

### 3.2 order-service가 죽고 Gateway만 살아있음
- 증상: `docker compose ... ps`에서 `order-service`가 `Exited (1)`
- 원인: 초기 데이터/seed가 payment-service와 경쟁하며 유니크 충돌
- 해결:
  - `demo.seed.enabled`를 payment-service에서 끄고(order-service만 seed)
  - 적용: `docker compose ... up -d --force-recreate order-service payment-service`

### 3.3 `02 - Route Drilldown`의 “App logs (errors)”가 비어있음
- 원인1: route=All 이 `|= "$__all"`로 들어가서 매칭이 0
- 해결:
  - 변수 `allValue=".*"` + Loki는 `|~ "${route}"`(regex)로 필터
- 원인2: compose_service가 `gateway|order-service|payment-service`인데 `app.*`로 쿼리함
- 해결:
  - `{compose_service=~"gateway|order-service|payment-service"} ...` 로 변경

### 3.4 `01 - API RED`에서 5xx가 과도하게 높음
- 원인: k6가 지속 주문 생성 → user 잔액 고갈 → 결제 실패가 예외로 전파되어 500으로 집계됨
- 해결:
  - k6에서 주기적으로 잔액 충전(또는 실패 비율 낮추기)

