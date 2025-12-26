# Week 10 제출/학습 가이드 (STEP 19-20 루브릭 기반)

이 문서는 제공된 “과제 평가 기준(P/F) + 심화 항목”을 기준으로, **무엇을 학습/실습하고 어떤 산출물로 증빙해야 하는지**를 정리한 제출 가이드입니다.  
이 레포에서는 아래 문서/대시보드를 “증빙 자료”로 사용합니다.

## 0) 이번 주 한 줄 목표
- **부하 테스트를 설계/수행**하고, 관측(모니터링/로깅)으로 **장애를 탐지→분석→완화→회고**하는 과정을 문서화한다.

## 1) 제출 산출물(필수)
### STEP 19
- 부하 테스트 계획서: `docs/week10/step19-load-test-plan.md`
- k6 스크립트: `loadtest/k6/step19-all-apis.js`
- (권장) 실행 기록: k6 출력 요약 + 대시보드 캡처

### STEP 20
- 결과 분석 & (가상) 장애 대응 보고서: `docs/week10/step20-incident-report.md`
- 모니터링 지표/해석 가이드: `docs/week10/monitoring-metrics.md`
- 학습 정리(회고): `docs/week10/LEARNING_SUMMARY.md`

### SRE 심화 학습(권장, 루브릭 보강용)
- SRE 기본 지침: `docs/week10/SRE_GUIDELINES.md`
- Runbook(장애 대응): `docs/week10/RUNBOOK.md`
- SLO/SLI/Alerting: `docs/week10/SLO_SLI_ALERTING.md`

## 2) 루브릭 체크리스트(이 문서만 따라가면 P/F 통과 목표)

## 2.1 STEP 19: 부하 테스트 설계/수행
### ✅ 대상 선정이 적합한가?
- 대상: 인증 없이 호출 가능한 `/api/**` 전체(운영/문서 제외)처럼 **명확한 범위**가 있어야 함
- 선정 이유: “전체 커버리지로 핫스팟 찾기” 또는 “핵심 사용자 여정 위주”처럼 **목적과 연결**되어야 함

### ✅ 시나리오/부하 모델이 구체적인가?
최소 포함 요소
- 시나리오: 어떤 API를 어떤 비율로 호출하는지(읽기/쓰기 분리 포함)
- 부하 모델: VU/Duration, ramp(점진 증가) 등 “숫자”가 있어야 함
- 측정 지표: RED(요청량/에러/지연) + JVM/DB pool 등 원인 신호
- 성공/중단 기준: 언제 “성공”이고 언제 “중단/무효”인지

이 레포의 구현
- 계획서: `docs/week10/step19-load-test-plan.md`
- 스크립트: `loadtest/k6/step19-all-apis.js`
  - sleep 없이 진행(옵션으로만 think time)
  - 상태 변화가 큰 API는 확률로 제어(측정 안정성/재현성)

### ✅ 실행 결과로 TPS/지연/실패율을 “기록”했나?
반드시 2가지 관점을 분리해서 기록하는 것을 권장
- k6: `http_reqs`(요청량), `http_req_duration`(지연), `http_req_failed`(실패율; 4xx 포함 가능)
- 서버: Prometheus/Grafana의 `/api/**` 기준 RPS, 5xx rate, p95

## 2.2 STEP 20: 문제 분석/개선/장애 대응 문서화
### ✅ “문제 정의 → 근거 → 대응 → 재측정” 흐름이 있는가?
보고서에 최소 포함 권장
- 목적/배경: 왜 이 테스트/시나리오를 했는지
- 문제 정의: 어떤 이상 징후(지표/로그)가 있었는지
- 원인 분석: 가설 + 근거(로그/메트릭/재현) + 결론
- 즉시 대응(Short-term): 확산 방지/복구(롤백/차단/제한 등)
- 중기/장기 대응: 구조적 개선, 관측/알람/런북, 회귀 체크리스트
- 재측정: 개선 후 동일 조건으로 결과가 좋아졌는지

이 레포의 구현
- 보고서: `docs/week10/step20-incident-report.md`
- (가상) 장애 대응/포스트모템은 “비난 없는(blameless) 회고” 형식을 유지

### ✅ 지표를 “해석”했나? (단순 캡처 X)
점수(심화)에서 갈리는 포인트는 “왜 이게 문제인지”를 설명하는 능력입니다.
- RED로 증상 파악(어떤 API가 느리고/에러가 나는가)
- 원인 신호로 드릴다운(예: DB pool pending, GC pause, 의존성 실패, 디스크 포화)

관련 정리(바로 복붙 가능한 PromQL 포함)
- `docs/week10/monitoring-metrics.md`

## 3) 대시보드 캡처(제출 품질을 좌우)
권장 캡처 최소 2장(시간 범위 일치)
- `Week10 - API RED (Endpoint View)`
  - 전체 `/api/**` 집계 RED
  - 엔드포인트별(모든 method+uri) RPS/p95 및 테이블(Endpoint RPS/5xx/p95)
- `Week10 - Overview (RED + JVM + DB)`
  - DB pool(Hikari), JVM Heap 등 포화 신호

캡처 팁
- Time range: Last 15 minutes
- Refresh: 10s
- k6 실행 마지막 1분이 포함되도록 캡처

## 4) 실행 가이드(재현 커맨드)
### 4.1 SUT(시스템) 실행
- `docker compose up -d`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` (admin/admin)

### 4.2 k6 실행(권장: 호스트 or docker)
- 호스트: `k6 run -e BASE_URL=http://localhost:8080 loadtest/k6/step19-all-apis.js`
- docker: `docker run --rm --network ecommerce_ecommerce-network -e BASE_URL=http://app:8080 -v "$PWD/loadtest/k6:/scripts" grafana/k6:latest run /scripts/step19-all-apis.js`

## 5) 심화 항목(선택이지만 “통찰”을 만드는 포인트)
### 5.1 MTTD/MTTR 관점(문서화)
- MTTD: “언제 어떤 지표/알람으로 발견했는가?”
- MTTR: “무엇을 했더니 언제 회복됐는가?”
- 이걸 쓰면 “장애 대응 문서”가 현업형으로 보입니다.

### 5.2 장애 레벨/비즈니스 임팩트
- 장애 등급 기준(예: 주문/결제 실패율, 매출 영향, 고객 영향 범위)
- 기술 지표(5xx/p95)와 비즈니스 지표(주문 성공률)를 연결

### 5.3 액션 아이템의 현실성
- short/mid/long-term로 분리하고
- “누가/언제까지/검증 기준”이 있는 형태로 작성

## 6) 흔한 함정(꼭 피하기)
- k6 `http_req_failed`를 “서버 장애”로 단정(4xx 포함 가능)
- sleep로 인위적 대기 넣기(루브릭에서 감점 요인)
- “Top”만 보고 전체 엔드포인트 분포를 놓치기
- 캡처만 있고 해석이 없는 보고서(지표/로그로 주장 뒷받침 필요)

