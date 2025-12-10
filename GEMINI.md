# GEMINI.md: AI 기반 프로젝트 가이드 (Gemini 최적화 버전)

이 문서는 AI 어시스턴트(Gemini)가 **hhplus-ecommerce** 프로젝트의 맥락을 깊이 이해하고, 정확하며 일관된 지원을 제공할 수 있도록 구성된 핵심 가이드입니다.

---

## 1. 🚀 프로젝트 개요 및 목표

항해플러스 이커머스 백엔드 과제 프로젝트로, 대용량 트래픽을 처리할 수 있는 확장 가능한 백엔드 시스템 구축을 목표로 합니다.

- **현재 단계:** Week 7 - Redis 기반 시스템 설계
- **핵심 구현 목표:**
    1.  **실시간 상품 랭킹:** Redis의 `Sorted Set`을 활용하여 실시간으로 가장 많이 팔린 상품 랭킹을 제공합니다.
    2.  **선착순 쿠폰 발급:** Redis의 `Set`과 원자적 연산(Atomic Operations)을 이용하여 동시성 문제를 해결하고, 한정된 수량의 쿠폰을 빠르고 정확하게 발급합니다.

---

## 2. 🛠️ 기술 스택 및 아키텍처

### 주요 기술 스택
- **언어:** Java 21
- **프레임워크:** Spring Boot 3.5.7
- **빌드 도구:** Gradle
- **데이터베이스:** MySQL 8.0, JPA (Hibernate)
- **캐시/랭킹/락:** Redis 7.x (Spring Data Redis, Redisson)
- **테스트:** JUnit 5, Testcontainers (MySQL, Redis), Jacoco
- **개발 환경:** Docker, Docker Compose

### 계층형 아키텍처 (Layered Architecture)
프로젝트는 의존성 규칙을 엄격히 따르는 4계층 구조로 설계되었습니다.

```
io.hhplus.ecommerce/
├── api/                # 🔴 Presentation Layer (API) - 외부 요청/응답 처리
│   └── dto/
├── application/         # 🟢 Application Layer (UseCases) - 비즈니스 흐름 조정
│   └── dto/
├── domain/              # 🔵 Domain Layer - 핵심 비즈니스 로직 및 규칙
│   ├── model/           # (구: entity) 도메인 객체
│   └── repository/      # Repository 인터페이스
└── infrastructure/      # 🟡 Infrastructure Layer - 외부 시스템 연동
    ├── redis/           # Redis 연동 구현체
    └── persistence/     # RDB 연동 구현체 (JPA)
        └── repository/
```
> **의존성 규칙:** 모든 의존성은 항상 바깥 계층에서 안쪽 계층(Domain)으로만 향합니다. `Infrastructure`는 `Domain`을 알지만, 그 반대는 성립하지 않습니다.

---

## 3. 💡 핵심 도메인 설계 (The "Why")

### 3.1. 실시간 랭킹 (Redis Sorted Set)
- **목표:** 가장 많이 주문된 상품을 실시간에 가깝게 조회합니다.
- **저장 방식:**
    - **자료구조:** Redis `Sorted Set`
    - **키:** `ranking:product:orders:daily:{yyyyMMdd}`
    - **멤버(Member):** `productId`
    - **스코어(Score):** 누적 판매 수량
- **갱신 시점:** **결제 성공 시**, `ZINCRBY` 명령어로 해당 상품의 스코어를 원자적으로 증가시킵니다.
- **동시성:** `ZINCRBY`는 원자적(atomic) 연산이므로 별도의 분산 락(Distributed Lock)이 필요 없습니다.

### 3.2. 선착순 쿠폰 (Redis Set + Atomic Operations)
- **목표:** 동시 다발적인 요청 속에서도 한정된 쿠폰 수량을 정확하게 보장합니다.
- **데이터 배치 전략:**
    - **DB:** 쿠폰의 메타 정보 (할인율, 총량, 유효기간 등 영속성이 중요한 데이터)
    - **Redis:** 실시간 재고 및 발급 상태 (빠른 동시성 처리가 필요한 휘발성 데이터)
        - `coupon:{id}:remain` (String): 남은 쿠폰 수량
        - `coupon:{id}:issued` (Set): 쿠폰을 발급받은 `userId` 집합
- **핵심 정합성 규칙:** "잔여 수량 차감"과 "발급 기록"은 반드시 **하나의 트랜잭션 단위**로 처리되어야 합니다.
- **권장 구현:** 짧은 `Lua 스크립트`를 사용하여 `수량 체크`, `DECR`, `SADD`를 원자적으로 묶어 처리합니다.

---

## 4. ⚙️ Redis 사용 원칙

- **Single-Threaded 동작:** Redis는 단일 스레드로 동작하므로, 오래 실행되는 `Lua 스크립트`는 시스템 전체의 병목이 될 수 있습니다.
- **짧은 명령어 위주 사용:** 개별 명령어는 원자적입니다. 복잡한 로직은 짧은 `Lua 스크립트` 또는 여러 명령어를 파이프라이닝하여 사용합니다.
- **역할 명확화:**
    - **랭킹:** Redis가 데이터의 원천(Source of Truth) 역할을 합니다.
    - **쿠폰:** Redis가 동시성 제어를 담당하며, DB는 통계 및 백오피스를 위해 최종적 일관성(Eventual Consistency)을 가집니다.

---

## 5. 🖥️ 빌드, 실행, 테스트

### 주요 Gradle 명령어
```bash
# 프로젝트 빌드 (의존성 설치 포함)
./gradlew clean build

# 애플리케이션 실행
./gradlew bootRun

# Redis 캐시 초기화 후 실행
./gradlew bootRunRedisReset

# 모든 테스트 실행
./gradlew test

# 테스트 커버리지 리포트 생성
./gradlew test jacocoTestReport

# k6 부하 테스트 실행 (예시)
# k6 run ./docs/week7/loadtest/k6/load-test.js
```

### 로컬 환경 실행
로컬 개발에 필요한 MySQL, Redis는 `docker-compose.yml`을 통해 관리됩니다.
```bash
# 백그라운드에서 서비스 시작
docker-compose up -d
```

---

## 6. 🤖 AI 어시스턴트(Gemini) 활용 가이드라인

효율적인 협업을 위해 다음 가이드라인을 따라주세요.

### 핵심 가이드라인
1.  **계층형 아키텍처 존중:** 코드 수정 및 추가 시, 항상 기존 4계층 구조와 의존성 규칙을 준수해주세요.
2.  **Redis 관련 작업:**
    - 코드 변경 전, `infrastructure/redis/` 패키지의 기존 구현 패턴을 먼저 파악해주세요.
    - 위에 명시된 "Redis 사용 원칙"을 반드시 지켜주세요.
3.  **코드 스타일:** 코드 포맷팅 및 스타일은 프로젝트에 설정된 IDE/린터 규칙을 따릅니다.

### 슬래시 커맨드 (Slash Commands)
프로젝트의 특정 정보가 필요할 때 다음 명령어를 사용하면 관련 컨텍스트를 빠르게 얻을 수 있습니다.

- **/architecture**: 프로젝트 아키텍처, 패키지 구조, API 명세, 에러 코드에 대한 상세 정보를 제공합니다.
- **/concurrency**: 선착순 쿠폰 발급과 관련된 동시성 제어 메커니즘을 설명합니다.
- **/implementation**: 각 계층별 코드 예제를 포함한 구현 가이드를 제공합니다.
- **/testing**: 단위/통합/동시성 테스트 전략과 Jacoco 커버리지 가이드를 제공합니다.

> 이 명령어들은 `.claude/commands/` 디렉토리의 문서를 기반으로 동작합니다.

### 참고 문서
더 깊이 있는 구현 가이드는 `agent_docs/` 디렉토리를 참고하세요.
- `agent_docs/redis_ranking.md`: 랭킹 시스템의 키 설계, 만료 정책 등 상세 가이드
- `agent_docs/redis_coupon_issue.md`: 쿠폰 발급 Lua 스크립트 예시 및 실패/원복 전략

---

## 7. ✅ 주요 개발 체크리스트 (Week 7 기준)

- [ ] Redis `Sorted Set` 기반 실시간 랭킹 로직 구현
- [ ] Redis `Set`과 원자적 연산을 활용한 선착순 쿠폰 발급 기능 (동시성 보장)
- [ ] 기존 RDBMS 기반 로직을 Redis 기반으로 마이그레이션
- [ ] `Testcontainers`를 활용한 Redis 통합 테스트 작성

> 상세한 요구사항 및 평가 기준은 `docs/week7/REQUIREMENTS.md` 파일을 참고하세요.
