# STEP19 부하 테스트 계획서

## 1) 테스트 대상 선정
- 대상: 인증 없는 `HTTP API` 전체
  - 포함: `/api/**`
  - 제외: 운영/문서 엔드포인트(예: `/actuator/**`, `/swagger/**`, 정적 리소스 등)

## 2) 접근 방식(전체 API + 핫스팟 증폭)
- Baseline(전체 커버리지)
  - 목적: 전체 엔드포인트에서 에러율/지연/병목 후보를 빠르게 탐지
  - 방식: 모든 `/api/**` 엔드포인트를 “가벼운 빈도”로 순환 호출
- Amplify(핫스팟 증폭)
  - 목적: Baseline에서 관측된 상위 N개(지연/에러)를 비율을 올려 점진 증가로 경계 확인
  - 방식: Baseline + Hot endpoints(상위 N개) 가중치 증가(예: 전체 70% + 핫스팟 30%)

## 2-1) k6 스크립트(초안)
- 스크립트: `loadtest/k6/step19-all-apis.js`
- 기본 특징
  - `/api/**` 전체를 그룹별로 호출 (product/user/cart/coupon/order)
  - 상태 변형이 큰 API(쿠폰 예약/발급, 주문 생성/결제, 장바구니 쓰기)는 기본 확률을 낮게 둠 (ENV로 조절)
  - DB 데이터가 누적된 환경에서도 실패를 줄이기 위해, `/api/products` 응답에서 `stock`이 충분한 상품을 선택해 정상 플로우를 우선 측정

## 3) 테스트 데이터/전제
- 인증: 없음
- 상태 의존 API(주문/결제/쿠폰/장바구니 등)는 테스트가 동작하도록 최소 데이터가 필요
  - 사전 준비(예): 사용자/상품/쿠폰 기본 데이터 시드
  - 테스트 런마다 데이터 초기화 여부(선택):
    - 정확한 비교가 필요하면 “초기화 후 실행”
    - 추세 관측이 필요하면 “누적 상태에서 실행”

## 4) 목표치(SLO 가정)
- 기준(실측 기반 보정)
  - STEP20 재측정(동일 스크립트/스테이지, 5 VUs / 3분)에서 `http_req_failed=0.82%`, `http_req_duration p95=7.94ms`(k6)이 관측됨.
  - 추가 재측정(동일 스크립트/스테이지, 5 VUs / 3분)에서 `http_req_failed=0.61%`, `http_req_duration p95=8.22ms`(k6)이 관측됨.
  - 참고: k6 `http_req_failed`는 4xx까지 포함될 수 있으므로, “서버 5xx 비율”과 분리해서 본다.
- RPS/TPS(관측 기반)
  - 목표는 “특정 RPS 고정”이 아니라, 아래 부하 모델(최대 5 VUs)에서 **지연/에러가 안정적으로 유지**되는지 확인하는 것.
  - RPS는 Grafana 패널(`API RPS (all /api/**)`)에서 실행별로 기록한다.
  - 참고(실측 예시, 동일 스테이지 1회)
    - k6 전체 요청: `http_reqs=795.88 req/s` (actuator 포함)
    - 서버 `/api/**` 기준(프로메테우스): 평균 `~517 rps`, 피크 `~1019 rps` (query_range, step=15s)
- 지연시간(p95 / p99)
  - 목표(서버 측, `/api/**`): p95 < 300ms, p99 < 800ms
  - 목표(k6 측, `http_req_duration`): p95 < 200ms
- Error rate(4xx/5xx)
  - 목표(서버 측 5xx): 5xx rate < 1% (5m window)
  - 목표(k6 `http_req_failed`): < 2%
  - 의도된 4xx(재고 부족/중복/정책)는 “기능 실패”로 분류하되, 5xx/timeout과 분리 집계한다.

## 5) 부하 테스트 타입 선택
- Load test: “가벼운 기본 부하”를 일정 시간 유지
- Stress test: “점진 증가”로 병목이 드러나는 지점 탐색

## 6) 시나리오 설계
- Baseline 시나리오(전체 엔드포인트 순환)
  - 엔드포인트 그룹(스크립트 기준): product/user/cart/coupon/order
  - 기본 원칙: 상태 변화가 큰 API는 확률을 낮춰 “측정 안정성”을 우선 확보
    - `CART_WRITE_PROB=0.3`
    - `ORDER_CREATE_PROB=0.05`, `ORDER_COMPLETE_PROB=0.02`
    - `COUPON_RESERVE_PROB=0.05`, `COUPON_ISSUE_PROB=0.02`
- Amplify 시나리오(핫스팟 가중치)
  - 상위 N개 선정 기준 및 N: Top 3 (RPS 상위 또는 p95 상위 또는 5xx 발생)
  - 전환 기준(예): Baseline에서 아래 중 하나라도 발생하면 Amplify로 전환해 경계 탐색
    - 특정 endpoint p95가 전체 p95의 2배 이상
    - 5xx가 특정 endpoint에 편중
    - DB pool(`hikaricp_connections_pending`)이 0보다 커지는 구간이 반복됨
  - 선정 기준(예)
    - p95/p99가 높은 엔드포인트
    - 5xx/timeout이 발생하는 엔드포인트
    - DB lock/connection pool 포화 징후가 있는 엔드포인트

## 7) 부하 모델(가벼운 기본 + 점진 증가)
- Warm-up(본 측정 제외)
  - 기간: 30s
  - 목적: JIT/캐시/커넥션 풀 안정화
- Load(기본 부하)
  - VU/Duration: 3 VUs / 60s
- Stress(점진 증가)
  - Stage(스크립트 기본값, 제출 기준):
    - 30s: 1 VU
    - 60s: 3 VU
    - 60s: 5 VU
    - 30s: 1 VU

## 8) 측정 지표(필수)
- API
  - RPS/TPS, latency(p50/p95/p99), error rate
- 시스템
  - CPU, Memory
- JVM
  - Heap(young/old), GC pause/횟수, thread 상태
- DB
  - QPS, slow query, lock, connection pool(active/idle)
- Redis(사용 시)
  - QPS(command), hit rate, CPU/Memory

## 9) 측정 신뢰도(Accuracy) 확보 계획
- 부하 발생기 분리
  - k6: 호스트(로컬) 실행
  - SUT: docker compose 실행
- 환경 고정
  - 동일 commit / 동일 설정 / 동일 초기 데이터
- 반복 실행
  - 동일 시나리오 최소 3회, 편차 기록
- 무효/중단 기준
  - k6 호스트 CPU 과포화(예: 90% 이상 지속) 시 “측정 무효”로 처리
  - 컨테이너 throttling 등 환경 요인 기록 후 재측정

## 10) 성공 기준 / 중단 기준
- 성공 기준
  - (서버 측) `/api/**` 5xx rate < 1%가 유지되고, p95 < 300ms 유지
  - (k6 측) `http_req_duration p95 < 200ms`, `http_req_failed < 2%`
  - 대시보드 캡처(제출): API RED + Overview 2장 이상(마지막 1분 구간 포함)
- 중단 기준
  - (즉시 중단) 앱/DB 컨테이너가 재시작하거나, `OOMKilled`/CrashLoop가 발생
  - (즉시 중단) `5xx rate > 5%`가 1분 이상 지속
  - (즉시 중단) p95가 2s 이상으로 1분 이상 지속(환경 문제 포함)
  - (측정 무효) k6 실행 호스트가 CPU 90% 이상으로 지속 포화되면 해당 런은 무효로 처리하고 재측정
