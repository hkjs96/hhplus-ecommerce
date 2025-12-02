# E-Commerce Backend System

항해플러스 백엔드 커리큘럼 - 이커머스 시스템 (Week 7: Redis 기반 시스템 설계)

---

## 📋 프로젝트 개요

**현재 단계**: Week 7 - Redis 기반 랭킹 시스템 및 선착순 쿠폰 발급

**핵심 목표**: Redis 자료구조를 활용한 실시간 랭킹 및 고성능 동시성 제어

단일 서버 환경에서 Redis를 활용하여 RDBMS의 한계를 극복하고, 대용량 트래픽을 처리할 수 있는 시스템 설계 및 구현

---

## 🎯 7주차 목표

### Step 13: Ranking Design (필수)
- **Redis Sorted Set 활용**: 실시간 상품 판매 랭킹 시스템 구현
- **결제 완료 시점 갱신**: 주문 생성이 아닌 결제 확정 기준
- **동시성 제어**: ZINCRBY 원자성으로 해결 (별도 분산락 불필요)
- **TTL 관리**: 일간/주간 랭킹 분리 및 자동 만료

### Step 14: Asynchronous Design (필수)
- **Redis 기반 쿠폰 발급**: Set + String으로 선착순 수량 제어
- **트랜잭션 단위 처리**: 수량 차감 + 발급 기록은 하나의 단위
- **중복 방지**: Set 자료구조로 동일 사용자 중복 발급 차단
- **Lua 스크립트 활용**: 원자적 처리 (선택적)

---

## 🏗️ 시스템 아키텍처

### Layered Architecture + Redis

```
┌─────────────────────────────────────────┐
│     Presentation Layer (API)            │
│  ┌──────────────────────────────────┐   │
│  │  Controllers (REST Endpoints)    │   │
│  │  - ProductController             │   │
│  │  - OrderController               │   │
│  │  - CartController                │   │
│  │  - CouponController              │   │
│  │  - UserController                │   │
│  │  - RankingController (NEW)       │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
              ⬇
┌─────────────────────────────────────────┐
│     Application Layer (Use Cases)       │
│  ┌──────────────────────────────────┐   │
│  │  UseCases (Business Flows)       │   │
│  │  - OrderUseCase                  │   │
│  │  - PaymentUseCase                │   │
│  │  - CouponUseCase                 │   │
│  │  - RankingUseCase (NEW)          │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
              ⬇
┌─────────────────────────────────────────┐
│     Domain Layer (Business Logic)       │
│  ┌──────────────────────────────────┐   │
│  │  Domain Services & Entities      │   │
│  │  - Product, Stock                │   │
│  │  - Order, OrderItem              │   │
│  │  - Cart, CartItem                │   │
│  │  - Coupon, UserCoupon            │   │
│  │  - User                          │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
              ⬇
┌─────────────────────────────────────────┐
│   Infrastructure Layer (Persistence)    │
│  ┌──────────────────────────────────┐   │
│  │  Repositories & External APIs    │   │
│  │  - JPA Repositories              │   │
│  │  - Redis Repositories (NEW)      │   │
│  │  - External Data Platform Client │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
              ⬇
┌─────────────────────────────────────────┐
│      Database, Cache & Queue            │
│   MySQL  │  Redis  │  External API      │
└─────────────────────────────────────────┘
```

---

## 🗂️ 문서 구조

프로젝트의 모든 설계 문서는 `docs/` 폴더에 체계적으로 정리되어 있습니다.

```
docs/
├── api/                          # API 설계 문서
│   ├── requirements.md           # 요구사항 명세서
│   ├── api-specification.md      # API 명세서 (15개 엔드포인트)
│   └── error-codes.md            # 에러 코드 표준
│
├── diagrams/                     # 다이어그램
│   ├── erd.md                    # ERD (DBML, Mermaid)
│   └── sequence-diagrams.md      # 시퀀스 다이어그램 (API별)
│
├── week7/                        # Week 7 Redis 학습 ⭐ (현재)
│   ├── README.md                 # Week 7 전체 가이드 (시작점)
│   ├── REDIS_BASICS.md           # Redis 기초 개념
│   ├── COACH_QNA_SUMMARY.md      # 코치 QnA 핵심 요약
│   ├── REQUIREMENTS.md           # Step 13-14 요구사항
│   ├── LEARNING_ROADMAP.md       # 10시간/3시간 학습 로드맵
│   ├── STEP_CHECKLIST.md         # 진행 체크리스트
│   └── CLAUDE_MD_MIGRATION_GUIDE.md  # CLAUDE.md 재구성
│
├── week4/                        # Week 4 DB 통합
│   ├── README.md                 # Week 4 가이드
│   ├── verification/             # 검증 문서
│   └── ...
│
├── archive/                      # 아카이브 (과거 구현)
│   └── week3/                    # Week 3 InMemory 구현
│
├── learning-points/              # 개념 학습 문서
└── feedback/                     # 코치 피드백

agent_docs/                       # 구현 상세 가이드
├── redis_ranking.md              # Sorted Set 랭킹 시스템 구현
├── redis_coupon_issue.md         # 선착순 쿠폰 발급 구현 (Lua)
└── testing_redis_features.md     # Testcontainers 테스트 시나리오
```

### 📍 주요 문서 바로가기

#### Week 7 현재 구현 (Redis)
| 문서 | 설명 | 링크 |
|------|------|------|
| **Week 7 README** | Week 7 전체 가이드 (시작점) | [docs/week7/README.md](docs/week7/README.md) |
| **Redis 기초** | Redis 자료구조, TTL, 원자성 | [REDIS_BASICS.md](docs/week7/REDIS_BASICS.md) |
| **코치 QnA** | 김종협 코치 핵심 요약 (트랜잭션 규칙) | [COACH_QNA_SUMMARY.md](docs/week7/COACH_QNA_SUMMARY.md) |
| **과제 요구사항** | Step 13-14 상세 요구사항 | [REQUIREMENTS.md](docs/week7/REQUIREMENTS.md) |
| **학습 로드맵** | 10시간/3시간 학습 계획 | [LEARNING_ROADMAP.md](docs/week7/LEARNING_ROADMAP.md) |
| **진행 체크리스트** | 설계/구현/테스트 체크 | [STEP_CHECKLIST.md](docs/week7/STEP_CHECKLIST.md) |

#### 구현 가이드 (agent_docs)
| 문서 | 설명 | 링크 |
|------|------|------|
| **랭킹 구현** | Sorted Set 키 설계, ZINCRBY 사용법 | [redis_ranking.md](agent_docs/redis_ranking.md) |
| **쿠폰 발급** | Lua 스크립트, 트랜잭션 처리 | [redis_coupon_issue.md](agent_docs/redis_coupon_issue.md) |
| **테스트 시나리오** | Testcontainers 동시성 테스트 | [testing_redis_features.md](agent_docs/testing_redis_features.md) |

#### 설계 문서
| 문서 | 설명 | 링크 |
|------|------|------|
| **API 명세서** | REST API 엔드포인트 상세 | [api-specification.md](docs/api/api-specification.md) |
| **ERD** | 데이터베이스 설계 (10개 테이블) | [erd.md](docs/diagrams/erd.md) |

#### 아카이브
| 문서 | 설명 | 링크 |
|------|------|------|
| **Week 4 아카이브** | JPA, N+1 문제, 쿼리 최적화 | [docs/week4/README.md](docs/week4/README.md) |
| **Week 3 아카이브** | InMemory 구현 학습 자료 | [docs/archive/week3/README.md](docs/archive/week3/README.md) |

---

## 🔑 핵심 기능 (5가지)

### 1. 상품 관리 📦
- **상품 조회**: 목록, 상세
- **인기 상품 랭킹** (NEW): Redis Sorted Set 기반 실시간 랭킹
- **재고 관리**: Stock 테이블 분리, 재고 이력 추적
- **동시성 제어**: Optimistic Lock (@Version)

### 2. 주문/결제 💳
- **장바구니**: 상품 추가, 조회, 수정, 삭제
- **주문 생성**: 재고 검증, 쿠폰 적용
- **포인트 결제**: 내부 포인트 시스템
- **재고 차감**: 결제 완료 **후** 차감
- **랭킹 갱신** (NEW): 결제 완료 시 Redis Sorted Set 업데이트

### 3. 쿠폰 시스템 🎟️
- **선착순 발급** (NEW): Redis 기반 고성능 동시성 제어
  - **데이터 배치**: DB (메타 정보) + Redis (수량/발급자)
  - **트랜잭션**: 수량 차감 + 발급 기록은 하나의 단위
  - **중복 방지**: Set 자료구조 활용
- **1인 1매 제한**: Redis Set + DB Unique Constraint
- **쿠폰 사용**: 결제 시점에 적용

### 4. 실시간 랭킹 🏆 (NEW - Week 7)
- **자료구조**: Redis Sorted Set
- **갱신 시점**: 결제 완료 시 (비동기)
- **키 전략**: `ranking:product:orders:daily:{date}`
- **TTL 관리**: 일간 랭킹 3일 후 자동 만료
- **동시성**: ZINCRBY 원자성 (별도 락 불필요)
- **API**: Top N 조회, 특정 상품 순위 조회

### 5. 외부 연동 🔗
- **비동기 전송**: 주문 완료 후 외부 데이터 플랫폼으로 전송
- **Timeout & Retry**: 3초 타임아웃, 최대 3회 재시도
- **Fallback**: Outbox 패턴 (재시도 큐)

---

## 🛠️ 기술 스택

### Backend
- **Language**: Java 17
- **Framework**: Spring Boot 3.5.7
- **Build**: Gradle

### Database & ORM
- **RDBMS**: MySQL 8.0
- **ORM**: JPA (Hibernate)
- **Direct Query**: JDBC Template (복잡한 쿼리용)
- **Migration**: SQL Scripts (DDL)

### Cache & Queue (Week 7 NEW)
- **Cache/Ranking**: Redis 7.x
- **자료구조**: String, Set, Sorted Set
- **Client**: Spring Data Redis (RedisTemplate)
- **사용처**:
  - 실시간 랭킹 (Sorted Set)
  - 선착순 쿠폰 수량 관리 (String)
  - 쿠폰 발급자 기록 (Set)

### Testing
- **Unit Test**: JUnit 5, Mockito
- **Integration Test**: Testcontainers (MySQL 8.0, Redis 7.x)
- **Coverage**: Jacoco (94% line coverage)
- **Concurrency Test**: ExecutorService + CountDownLatch

### Monitoring & Debugging
- **Query Logging**: p6spy (바인딩 파라미터 확인)
- **Slow Query**: MySQL Slow Query Log
- **Performance Analysis**: EXPLAIN, EXPLAIN ANALYZE
- **Redis Monitoring**: Redis CLI, redis-cli MONITOR

### 동시성 제어
- **Pessimistic Lock**: `SELECT ... FOR UPDATE` (포인트 차감)
- **Optimistic Lock**: `@Version` (재고 차감)
- **Redis Atomic**: ZINCRBY, DECR, SADD (랭킹, 쿠폰)

### 가용성 패턴
- **Timeout**: 3초 (외부 API)
- **Retry**: Exponential Backoff + Outbox 패턴
- **Fallback**: 빈 배열 반환 (서비스 중단 방지)
- **Async**: `@Async` (비동기 외부 전송, 랭킹 갱신)

### Development Tools
- **Docker**: MySQL 8.0, Redis 7.x 컨테이너
- **Docker Compose**: 개발 환경 구성

---

## 🔄 핵심 플로우

### 1. 주문 생성 및 결제 + 랭킹 갱신 플로우 (Week 7 업데이트)

```
1. 장바구니 조회 (MySQL)
   ↓
2. 재고 검증 (MySQL stock 테이블)
   ↓
3. 쿠폰 검증 (선택, Redis + MySQL)
   ↓
4. 주문 생성 (status=PENDING)
   ↓
5. 결제 처리
   - 포인트 차감 (Pessimistic Lock)
   - 재고 차감 (Optimistic Lock) ← 결제 성공 후
   - 재고 이력 기록 (stock_history)
   - 쿠폰 사용 처리
   ↓
6. 주문 상태 업데이트 (status=COMPLETED)
   ↓
7. 랭킹 갱신 (@Async, Non-blocking) ← NEW (Week 7)
   - Redis Sorted Set: ZINCRBY ranking:daily:{date} {quantity} {productId}
   - TTL 설정 (3일)
   ↓
8. 외부 데이터 전송 (@Async, Non-blocking)
   - 성공: 완료
   - 실패: Outbox 테이블에 저장 → 재시도 워커가 처리
```

### 2. 선착순 쿠폰 발급 플로우 (Week 7 업데이트)

**방식 1: Lua 스크립트 (권장)**
```
1. Redis Lua 스크립트 실행 (원자적 처리)
   - 중복 발급 체크: SISMEMBER coupon:{id}:issued {userId}
   - 잔여 수량 체크: GET coupon:{id}:remain
   - 수량 차감: DECR coupon:{id}:remain
   - 발급 기록: SADD coupon:{id}:issued {userId}
   ↓
2. 결과 처리
   - 성공 (1): 발급 완료
   - 중복 (-1): 이미 발급됨
   - 수량 부족 (-2): 선착순 마감
```

**방식 2: 개별 명령 + 롤백 (대안)**
```
1. 중복 발급 체크 (Redis Set)
   - SISMEMBER coupon:{id}:issued {userId}
   ↓
2. 수량 차감 (Redis String)
   - DECR coupon:{id}:remain
   ↓
3. 수량 부족 체크
   - remain < 0 → 원복 (INCR) + 에러 반환
   ↓
4. 발급 기록 (Redis Set)
   - SADD coupon:{id}:issued {userId}
   - 실패 시 → 수량 원복 (INCR) + 에러 반환
   ↓
5. DB 기록 (비동기, 선택적)
   - UserCoupon 테이블에 발급 기록
```

---

## 📝 API 엔드포인트

### 상품

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | `/api/products` | 상품 목록 조회 | - |
| GET | `/api/products/{productId}` | 상품 상세 조회 | - |
| GET | `/api/products/ranking/top` | 인기 상품 랭킹 Top N (NEW) | - |

### 장바구니

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/cart/items` | 장바구니 추가 | ✅ |
| GET | `/api/cart` | 장바구니 조회 | ✅ |
| PUT | `/api/cart/items` | 장바구니 수정 | ✅ |
| DELETE | `/api/cart/items` | 장바구니 삭제 | ✅ |

### 주문/결제

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/orders` | 주문 생성 | ✅ |
| POST | `/api/orders/{orderId}/payment` | 결제 처리 | ✅ |
| GET | `/api/orders/{orderId}` | 주문 조회 | ✅ |

### 쿠폰

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/coupons/{couponId}/issue` | 쿠폰 발급 (Redis 기반) | ✅ |
| GET | `/api/users/{userId}/coupons` | 보유 쿠폰 조회 | ✅ |

### 사용자

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | `/api/users/{userId}/balance` | 포인트 조회 | ✅ |
| POST | `/api/users/{userId}/balance/charge` | 포인트 충전 | ✅ |

**상세 API 명세**: [docs/api/api-specification.md](docs/api/api-specification.md)

---

## 🚀 실행 방법

### 사전 요구사항
- Java 17 이상
- Docker & Docker Compose
- Gradle 8.0 이상

### 1. MySQL + Redis 환경 구성 (Docker)

```bash
# Docker Compose로 MySQL 8.0 + Redis 7.x 실행
docker-compose up -d

# MySQL 데이터베이스 생성
docker exec -it hhplus-mysql mysql -uroot -ppassword -e "CREATE DATABASE IF NOT EXISTS ecommerce;"

# DDL 실행 (스키마 생성)
docker exec -i hhplus-mysql mysql -uroot -ppassword ecommerce < docs/sql/schema.sql

# Redis 연결 확인
docker exec -it hhplus-redis redis-cli ping
# 응답: PONG
```

### 2. 애플리케이션 빌드 및 실행

```bash
# 의존성 설치 및 빌드
./gradlew clean build

# 애플리케이션 실행
./gradlew bootRun

# 또는 JAR 실행
java -jar build/libs/ecommerce-0.0.1-SNAPSHOT.jar
```

### 3. Redis 확인

```bash
# Redis CLI 접속
docker exec -it hhplus-redis redis-cli

# 랭킹 확인
127.0.0.1:6379> ZREVRANGE ranking:product:orders:daily:20251202 0 9 WITHSCORES

# 쿠폰 수량 확인
127.0.0.1:6379> GET coupon:1:remain

# 쿠폰 발급자 확인
127.0.0.1:6379> SMEMBERS coupon:1:issued
```

### 4. API 문서 확인

```
Swagger UI: http://localhost:8080/swagger-ui/index.html
```

### 5. 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# 테스트 커버리지 리포트 생성
./gradlew test jacocoTestReport

# 리포트 확인
open build/reports/jacoco/test/html/index.html
```

---

## 📚 학습 포인트

### Week 7에서 중점적으로 학습한 내용

#### 1. **Redis 기초 개념** ⭐
- **단일 스레드 이벤트 루프**: CPU 오래 쓰는 작업 금지
- **원자적 연산**: ZINCRBY, DECR, SADD 활용
- **TTL 관리**: 모든 키에 TTL 설정 (메모리 누수 방지)
- **키 네이밍 전략**: `domain:entity:attribute:id` 패턴

#### 2. **Sorted Set 기반 랭킹 시스템** ⭐
- **자료구조 선택 이유**: score 기반 자동 정렬, O(log N) 성능
- **키 설계**: `ranking:product:orders:daily:{date}`
- **갱신 시점**: 결제 완료 시 (주문 생성 ❌)
- **동시성**: ZINCRBY 원자성 (별도 분산락 불필요)
- **TTL 전략**: 일간 랭킹 3일 후 만료

#### 3. **선착순 쿠폰 발급 시스템** ⭐
- **트랜잭션 규칙**: 수량 차감 + 발급 기록은 **하나의 단위**
- **Lua 스크립트**: 원자적 처리 (짧게 작성)
- **중복 방지**: Set 자료구조 활용 (SISMEMBER)
- **실패 시 원복**: 즉시 롤백 (스케줄러 방식 ❌)
- **데이터 배치**: DB (메타) + Redis (실시간)

#### 4. **Testcontainers 통합 테스트**
- **Redis 독립 환경**: GenericContainer 활용
- **동시성 검증**: ExecutorService + CountDownLatch
- **테스트 격리**: @BeforeEach에서 Redis 초기화

#### 5. **코치 QnA 핵심** ⭐
- **Redis 이벤트 루프**: Lua 스크립트는 짧게
- **랭킹 갱신**: 결제 완료 시점 (주문 생성 ❌)
- **쿠폰 발급**: 트랜잭션 단위, 실시간 처리
- **스케줄러 금지**: 나중에 맞추는 방식 ❌
- **손실 방지**: 원복 로직 필수

---

## 🔍 주요 설계 결정 (Design Decisions)

### 1. Redis Sorted Set for Ranking

**결정**: 실시간 랭킹에 Redis Sorted Set 사용

**이유**:
- score 기반 자동 정렬 (O(log N))
- ZINCRBY로 원자적 score 증가
- 별도 분산락 불필요
- Top N 조회 빠름 (ZREVRANGE)

### 2. 결제 완료 시점 랭킹 갱신

**결정**: 주문 생성이 아닌 결제 완료 시점에 랭킹 갱신

**이유**:
- 결제 실패 시 랭킹 오염 방지
- 정확한 판매량 집계
- 비즈니스 로직 명확성

### 3. Lua 스크립트 vs 개별 명령

**결정**: Lua 스크립트 권장, 개별 명령 + 롤백도 허용

**이유**:
- Lua: 원자적 처리, 네트워크 왕복 1회
- 개별 명령: 디버깅 용이, Lua 학습 불필요
- **주의**: Lua는 짧게 작성 (Redis 단일 스레드)

### 4. 쿠폰 데이터 배치 (DB vs Redis)

**결정**: DB (메타 정보) + Redis (실시간 수량/발급자)

**이유**:
- DB: 안정성, 백오피스 조회, 통계
- Redis: 고속 처리, 동시성 제어, 원자성
- 역할 명확히 분리

### 5. TTL 전략

**결정**: 모든 Redis 키에 TTL 설정

**이유**:
- 메모리 누수 방지
- 일간 랭킹: 3일 후 자동 만료
- 쿠폰: 유효기간과 동일하게 설정

---

## 📋 체크리스트

### Week 3: Layered Architecture ✅
- [x] 4계층 분리 (Presentation, Application, Domain, Infrastructure)
- [x] Domain Entity 구현 (비즈니스 로직 캡슐화)
- [x] Repository 패턴 (인터페이스 Domain, 구현체 Infrastructure)
- [x] UseCase 구현 (Application Layer)
- [x] In-Memory Repository (ConcurrentHashMap)
- [x] 동시성 제어 (synchronized, ReentrantLock)
- [x] 단위 테스트 (커버리지 94%)

### Week 4: Database Integration ✅
- [x] JPA Entity 변환
- [x] Repository 구현 (JPA + JDBC Template)
- [x] Transaction 관리 (@Transactional)
- [x] N+1 문제 해결 (Fetch Join)
- [x] 쿼리 최적화 (EXPLAIN, Index)
- [x] Testcontainers 통합 테스트

### Week 7 Step 13: Ranking Design ⏳ (진행 중)
- [ ] Redis Sorted Set 랭킹 시스템 설계
- [ ] 키 네이밍 전략 수립 (`ranking:product:orders:daily:{date}`)
- [ ] 결제 완료 시 랭킹 갱신 (비동기)
- [ ] ZINCRBY로 score 증가
- [ ] TTL 설정 (3일)
- [ ] Top N 조회 API
- [ ] 특정 상품 순위 조회 API
- [ ] 동시성 테스트 (score 정확성 검증)

### Week 7 Step 14: Asynchronous Design ⏳ (진행 중)
- [ ] Redis 기반 쿠폰 발급 시스템 설계
- [ ] 데이터 배치 전략 (DB vs Redis)
- [ ] Lua 스크립트 구현 (원자적 처리)
- [ ] 개별 명령 + 롤백 로직 (대안)
- [ ] 중복 발급 방지 (Set)
- [ ] 수량 마이너스 방지
- [ ] 초기 데이터 로딩 (ApplicationReadyEvent)
- [ ] 동시성 테스트 (1000 요청 → 100 발급)
- [ ] Testcontainers Redis 통합 테스트

---

## 🙏 참고 자료

### Redis
- [Redis 공식 문서](https://redis.io/docs/)
- [Redis Data Types](https://redis.io/docs/data-types/)
- [Sorted Sets](https://redis.io/docs/data-types/sorted-sets/)
- [Redis Testcontainers](https://java.testcontainers.org/modules/databases/redis/)

### JPA & Hibernate
- [Hibernate User Guide](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html)
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)

### Database Optimization
- [Use The Index, Luke](https://use-the-index-luke.com/)
- [MySQL Performance Tuning](https://dev.mysql.com/doc/refman/8.0/en/optimization.html)

### Testing
- [Testcontainers Documentation](https://testcontainers.com/)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)

---

## 📞 Contact

프로젝트 관련 문의: [GitHub Issues](https://github.com/hkjs96/hhplus-ecommerce/issues)

---

## 📄 License

This project is licensed under the MIT License.

---

**항해플러스 백엔드 커리큘럼 Week 7** - Redis 기반 랭킹 시스템 및 선착순 쿠폰 발급

**🚀 시작하기**: [docs/week7/README.md](docs/week7/README.md)
