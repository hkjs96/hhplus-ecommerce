# CLAUDE.md – hhplus-ecommerce (Week 7: Redis 기반 시스템 설계)

## 1. 프로젝트 개요

항해플러스 이커머스 백엔드 과제 프로젝트입니다.
**현재 단계:** Week 7 - Redis 기반 랭킹 시스템 및 선착순 쿠폰 발급
**핵심 목표:** Redis Sorted Set을 활용한 실시간 랭킹 구현 및 동시성 제어

---

## 2. 기술 스택 & 구조 (WHAT)

### Stack
- Java 21, Spring Boot 3.5.7, Gradle
- Database: MySQL 8 (JPA, Spring Data JPA)
- Cache/Ranking/Lock: **Redis**
- Test: JUnit 5, Testcontainers (MySQL, Redis)

### 패키지 구조 (Layered Architecture)
```
io.hhplus.ecommerce/
├── domain/              # Entity, Domain Service, Repository Interface
├── application/         # UseCase, DTO
├── infrastructure/      # JPA Repository, Redis Repository
│   ├── persistence/     # JPA 구현체
│   └── redis/          # Redis 접근 어댑터
└── api/                # REST Controller
```

---

## 3. 핵심 도메인 (WHY)

### 3.1 주문/결제
- 사용자는 잔액 충전 후 상품 주문/결제
- **결제 완료 시점**에 다음 작업 수행:
   - 상품 판매량 기반 랭킹 업데이트 (비동기)
   - 쿠폰 사용 처리 (필요 시)

### 3.2 실시간 랭킹 (Sorted Set)
**목표:** 가장 많이 주문된 상품을 실시간에 가깝게 제공

**저장 방식:**
- Redis Sorted Set 사용
- 키 패턴: `ranking:product:orders:daily:{yyyyMMdd}`
- member: `productId`, score: 누적 판매 수량

**갱신 시점:**
- **결제 성공 시점**에 각 상품별로 `ZINCRBY`로 score 증가
- 주문 생성이 아닌 **결제 확정** 기준

**동시성/정합성:**
- Redis는 단일 스레드 + `ZINCRBY`는 atomic
- 별도 분산락 불필요
- 많은 동시 요청이 와도 score는 정확히 누적됨

### 3.3 선착순 쿠폰 (Set + Atomic)
**목표:** 동시 다발적 요청에서도 선착순 수량을 정확히 보장

**데이터 배치:**
- 쿠폰 메타(할인율, 유효기간, 총 수량): **DB**
- 선착순 재고/발급 상태: **Redis**
   - `coupon:{id}:remain` → 남은 수량 (정수)
   - `coupon:{id}:issued` → 발급된 userId Set

**발급 규칙 (핵심 정합성):**
- "잔여 수량 차감"과 "userId 발급 기록"은 **하나의 트랜잭션 단위**로 처리
- 둘 중 하나라도 실패하면 원복 필요
- 스케줄러로 나중에 맞추는 방식 사용 금지

**구현 방식:**
1. (권장) 짧은 Lua 스크립트로 `remain 체크 → DECR → SADD`를 원자적으로 처리
2. (대안) 개별 명령 + 방어적 롤백 로직
3. 같은 userId 중복 발급 방지는 Redis 레벨에서 처리

---

## 4. Redis 사용 원칙 (HOW)

### 단일 스레드 이벤트 루프
- Redis는 **단일 스레드 이벤트 루프**로 동작
- 개별 명령은 atomic하지만, **CPU를 오래 쓰는 Lua 스크립트는 전체 처리 지연 유발**

### 이 프로젝트의 Redis 사용 규칙
1. **짧은 명령 조합 또는 짧은 Lua 스크립트만 사용**
2. **랭킹 갱신:** `ZINCRBY` 한 번으로 처리, 별도 분산락 불필요
3. **쿠폰 발급:**
   - (선호) 짧은 Lua로 `잔여 수량 체크 + 차감 + 발급 기록`을 한 번에
   - (대안) 단일 명령 조합 + 실패 시 원복 로직
4. **Redis 역할 명확화:**
   - 랭킹: Redis Sorted Set이 사실상의 진실 소스
   - 쿠폰: Redis 기준으로 동시성 보장, DB는 통계/백오피스용 eventual sync

---

## 5. 작업 방법 (빌드/테스트/실행)

```bash
# Build
./gradlew clean build

# Run
./gradlew bootRun

# Test (단위 + 통합)
./gradlew test

# Redis/MySQL (Docker)
# docker-compose.yml 또는 scripts/ 폴더 참조
```

### 7주차 핵심 테스트
- 다중 스레드 환경에서 쿠폰 발급 수량 초과 방지 검증
- 동시 다발 주문 시 랭킹 score 정확성 검증
- Testcontainers로 Redis 통합 테스트

**자세한 테스트 시나리오:** `agent_docs/testing_redis_features.md` 참조

---

## 6. 추가 문서 (Progressive Disclosure)

세부 지침은 아래 문서를 참조하세요. **필요 시에만** 열어보세요.

- `agent_docs/redis_ranking.md`
  → Sorted Set 키 설계, 만료 정책, 랭킹 조회 API 설계 상세

- `agent_docs/redis_coupon_issue.md`
  → 쿠폰 발급 Lua 스크립트 예시, 실패/원복 전략, 에러 케이스

- `agent_docs/testing_redis_features.md`
  → Redis 동시성/통합 테스트 시나리오와 예제 코드

- `docs/week6/`
  → Week 6 과제 (분산락, 캐시 전략) 참고 문서

- `.claude/commands/`
  → `/architecture`, `/concurrency`, `/testing` 슬래시 커맨드

---

## 7. Claude 사용 가이드

### 이 프로젝트에서 Claude가 지켜야 할 규칙

1. **레이어링 존중:**
   코드 수정/추가 시 항상 기존 4계층 구조(Presentation → Application → Domain ← Infrastructure) 유지

2. **Redis 관련 코드 변경 시:**
   - 먼저 `infrastructure.redis` 패키지의 기존 패턴 확인
   - 위 4번 섹션(Redis 사용 원칙) 준수
   - 필요하면 `agent_docs/redis_*.md` 참고

3. **코드 스타일/포맷팅:**
   IDE/린터에 맡기며, CLAUDE.md에서 따로 규정하지 않음

---

## 📌 Week 7 Pass 기준 (참고)

- [ ] Redis Sorted Set 기반 랭킹 제공 로직 구현
- [ ] 적절한 트랜잭션 + 파이프라인 구성
- [ ] Redis 기반 선착순 쿠폰 발급 (동시성 보장)
- [ ] 기존 RDBMS 로직을 Redis 로직으로 마이그레이션
- [ ] Testcontainers 기반 통합 테스트

**상세 평가 기준:** `docs/week7/REQUIREMENTS.md` 참조
