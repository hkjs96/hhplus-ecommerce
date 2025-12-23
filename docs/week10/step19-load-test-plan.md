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

## 3) 테스트 데이터/전제
- 인증: 없음
- 상태 의존 API(주문/결제/쿠폰/장바구니 등)는 테스트가 동작하도록 최소 데이터가 필요
  - 사전 준비(예): 사용자/상품/쿠폰 기본 데이터 시드
  - 테스트 런마다 데이터 초기화 여부(선택):
    - 정확한 비교가 필요하면 “초기화 후 실행”
    - 추세 관측이 필요하면 “누적 상태에서 실행”

## 4) 목표치(SLO 가정)
- RPS/TPS:
- p95 / p99:
- Error rate(4xx/5xx):

## 5) 부하 테스트 타입 선택
- Load test: “가벼운 기본 부하”를 일정 시간 유지
- Stress test: “점진 증가”로 병목이 드러나는 지점 탐색

## 6) 시나리오 설계
- Baseline 시나리오(전체 엔드포인트 순환)
  - (작성: 엔드포인트 목록/그룹핑 기준)
- Amplify 시나리오(핫스팟 가중치)
  - (작성: 상위 N개 선정 기준 및 N)
  - 선정 기준(예)
    - p95/p99가 높은 엔드포인트
    - 5xx/timeout이 발생하는 엔드포인트
    - DB lock/connection pool 포화 징후가 있는 엔드포인트

## 7) 부하 모델(가벼운 기본 + 점진 증가)
- Warm-up(본 측정 제외)
  - 기간:
  - 목적: JIT/캐시/커넥션 풀 안정화
- Load(기본 부하)
  - VU/Duration:
- Stress(점진 증가)
  - Stage 예시:
    - 1m: 1 VU
    - 3m: 3 VU
    - 5m: 5 VU
    - 3m: 3 VU

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
  - (작성)
- 중단 기준
  - (작성)
