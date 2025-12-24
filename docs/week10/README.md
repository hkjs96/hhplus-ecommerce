# Week 10 (STEP 19-20) 장애 대응 & 성능 테스트

## 목표
- STEP19: 부하 테스트 대상 선정, 시나리오/목표치/측정 지표를 계획하고 문서화
- STEP20: 테스트 결과 분석, 병목 탐색/개선(또는 개선 가설) 및 장애 대응(가상) 문서화

## 문서 링크
- STEP19 계획서: `docs/week10/step19-load-test-plan.md`
- STEP20 보고서/장애 대응 문서: `docs/week10/step20-incident-report.md`
- 모니터링 항목 정리: `docs/week10/monitoring-metrics.md`

## 환경
- 부하 발생기: `k6` (정확도 우선: 호스트 실행 권장)
- SUT(시스템): `docker compose` 기반 실행(앱/DB/Redis/Kafka)

## 모니터링(로컬)
- 실행:
  - `docker compose up -d prometheus grafana`
- 접속:
  - Prometheus: `http://localhost:9090`
  - Grafana: `http://localhost:3000` (id/pw: `admin` / `admin`)
- 대시보드:
  - Folder: `Week10`
  - Dashboard: `Week10 - Overview (RED + JVM + DB)`
- 주의:
  - Prometheus는 `host.docker.internal:8080/actuator/prometheus`를 스크레이프합니다.
  - 따라서 앱은 호스트에서 `8080`으로 실행되어 있어야 합니다.

## 실행 커맨드(예시)
- k6 실행(호스트):
  - `k6 run -e BASE_URL=http://localhost:8080 <k6-script.js>`

## 결론 요약(작성)
- 테스트 대상:
- 부하 모델(기본/점진 증가):
- 핵심 결과(p95/p99, error rate):
- 발견된 병목/장애 징후:
- 개선 및 재측정 결과(또는 개선 가설):
