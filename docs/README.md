# 📚 이커머스 프로젝트 문서

항해플러스 백엔드 Week 2 - API 설계 및 시스템 아키텍처

---

## 📋 문서 개요

이 폴더에는 이커머스 시스템의 **완전한 설계 문서**가 포함되어 있습니다.

**핵심 목표**: 애플리케이션 레벨에서 가용성을 보장하는 이커머스 시스템 설계

---

## 🗂️ 문서 구조

```
docs/
├── api/                          # API 설계 문서
│   ├── requirements.md           # 요구사항 명세서 ⭐
│   ├── user-stories.md           # 사용자 스토리 (16개)
│   ├── api-specification.md      # API 명세서 (엔드포인트, 요청/응답)
│   ├── data-models.md            # 데이터 모델 정의
│   ├── availability-patterns.md  # 가용성 패턴 설계 ⭐
│   ├── error-codes.md            # 에러 코드 표준
│   ├── feature-list.md           # 기능 목록
│   └── scope-clarification.md    # 과제 범위 명확화
│
├── diagrams/                     # 다이어그램
│   ├── erd.md                    # ERD (DBML, Mermaid) ⭐
│   ├── sequence-diagrams.md      # 시퀀스 다이어그램 (8개) ⭐
│   └── flowcharts.md             # 플로우차트 (5개) ⭐
│
└── PROJECT_STRUCTURE.md          # 프로젝트 구조 가이드
```

⭐ = 핵심 문서

---

## 📍 주요 문서 바로가기

### API 설계 문서

| 문서 | 설명 | 링크 |
|------|------|------|
| **요구사항 명세서** | 비즈니스 요구사항 및 제약사항 정의 | [requirements.md](api/requirements.md) |
| **사용자 스토리** | 16개 기능에 대한 사용자 관점 시나리오 | [user-stories.md](api/user-stories.md) |
| **API 명세서** | REST API 엔드포인트 상세 (요청/응답/에러) | [api-specification.md](api/api-specification.md) |
| **데이터 모델** | 엔티티 정의 및 비즈니스 로직 | [data-models.md](api/data-models.md) |
| **가용성 패턴** | Timeout, Retry, Fallback, Async 설계 | [availability-patterns.md](api/availability-patterns.md) |
| **에러 코드** | 표준 에러 코드 체계 | [error-codes.md](api/error-codes.md) |

### 다이어그램

| 문서 | 설명 | 개수 | 링크 |
|------|------|------|------|
| **ERD** | 데이터베이스 설계 (DBML, Mermaid) | 10개 테이블 | [erd.md](diagrams/erd.md) |
| **시퀀스 다이어그램** | API별 상세 플로우 (액션 설명 기반) | 8개 플로우 | [sequence-diagrams.md](diagrams/sequence-diagrams.md) |
| **플로우차트** | 비즈니스 로직 흐름 | 5개 플로우 | [flowcharts.md](diagrams/flowcharts.md) |

---

## 🎯 문서 작성 원칙

### 1. **명확성 (Clarity)**
- 기술 용어 최소화, 비즈니스 용어 사용
- 예시 포함 (요청/응답, SQL, 코드)

### 2. **완전성 (Completeness)**
- 성공 케이스 + 실패 케이스 모두 문서화
- 예외 상황 및 에러 처리 명시

### 3. **일관성 (Consistency)**
- 네이밍 컨벤션 통일
  - 인덱스: `idx_`, unique 인덱스: `uidx_`
  - 에러 코드: `P001`, `O002` 등
- 용어 통일 (stock ✅, inventory ❌)

### 4. **실행 가능성 (Actionability)**
- 개발자가 바로 구현 가능한 수준
- SQL, Java 코드 예시 포함

---

## 📊 다이어그램 사용 가이드

### ERD (erd.md)

**목적**: 데이터베이스 테이블 구조 및 관계 정의

**포함 내용**:
- 10개 테이블 정의 (DBML, Mermaid 형식)
- 인덱스 전략 (복합 인덱스, Unique 제약)
- 동시성 제어 필드 (`version`)
- 주요 쿼리 예시

**활용 도구**:
- [dbdiagram.io](https://dbdiagram.io/d) - DBML 렌더링
- [Mermaid Live Editor](https://mermaid.live) - Mermaid 렌더링

---

### 시퀀스 다이어그램 (sequence-diagrams.md)

**목적**: API별 요청/응답 흐름 및 인프라 상호작용 표현

**포함 내용**:
- 8개 API 플로우 (REST API 7개 + 배치 1개)
- 트랜잭션 경계 명시 (BEGIN, COMMIT, ROLLBACK)
- Lock 획득/해제 시점 표시 (**LOCKED**)
- 성공/실패 분기 (alt fragment)
- **액션 설명 기반** (SQL 쿼리, 메서드명 제거)

**예시**:
```mermaid
API->>Service: 주문 정보 조회 요청
Service->>MySQL: 주문 레코드 조회
MySQL-->>Service: 주문 정보 반환 (상태=PENDING)
```

**피어 리뷰 피드백 반영**:
- ❌ `getOrder(orderId)` → ✅ "주문 정보 조회 요청"
- ❌ `SELECT * FROM orders WHERE id = ?` → ✅ "주문 레코드 조회"

---

### 플로우차트 (flowcharts.md)

**목적**: 비즈니스 로직의 의사결정 흐름 시각화

**포함 내용**:
- 5개 핵심 플로우 (주문 생성, 결제 처리, 쿠폰 발급, 재고 차감, 장바구니 전환)
- 유효성 검증 단계
- 성공/실패 분기
- 예외 처리

**활용 시나리오**:
- 비즈니스 로직 리뷰
- 테스트 케이스 설계
- 코드 구현 가이드

---

## 🔍 핵심 설계 결정

### 1. 시퀀스 다이어그램: SQL 쿼리 제거

**Before (잘못된 방식)**:
```mermaid
Service->>MySQL: SELECT * FROM orders WHERE id = 'ORD-001'
Service->>MySQL: UPDATE users SET balance = 500000 WHERE id = 'U001'
```

**After (올바른 방식)**:
```mermaid
Service->>MySQL: 주문 레코드 조회
Service->>MySQL: 사용자 잔액 업데이트 (잔액 차감, 업데이트 시각 기록)
```

**이유**:
- 시퀀스 다이어그램은 **흐름**을 보여주는 것이 목적
- 구현 세부사항(SQL)은 **의도**를 가림
- 메서드명도 제거하고 **액션 설명**으로 변경

---

### 2. ERD: Unique Index 네이밍 (uidx_)

**적용**:
```sql
-- 일반 인덱스
CREATE INDEX idx_products_category ON products(category);

-- Unique 인덱스
CREATE UNIQUE INDEX uidx_stock_product_warehouse ON stock(product_id, warehouse_id);
CREATE UNIQUE INDEX uidx_user_coupons_user_coupon ON user_coupons(user_id, coupon_id);
```

**이유**:
- 한눈에 Unique 인덱스 구분 가능
- DB 제약조건 명확화 (1인 1매, 창고별 재고)

---

### 3. 가용성 패턴: Circuit Breaker 제거

**결정**: Week 2에서는 Circuit Breaker 패턴 제거

**남은 패턴**:
1. **Timeout** (3초)
2. **Retry** (Exponential Backoff: 1분 → 5분 → 30분)
3. **Fallback** (Outbox 패턴, 빈 배열 반환)
4. **Async** (`@Async` 비동기 처리)

**이유**:
- Week 2 수준에서 Circuit Breaker는 과도한 복잡도
- 핵심 패턴에 집중하여 학습 목표 달성
- 필요 시 추후 추가 가능

---

## ✅ 문서 검증 체크리스트

### API 명세서
- [ ] 모든 엔드포인트 문서화 (URL, Method, 인증)
- [ ] 요청/응답 예시 포함
- [ ] 에러 케이스 문서화 (400, 404, 409)
- [ ] HTTP Status Code 올바르게 매핑

### ERD
- [ ] 모든 테이블 정의 (10개)
- [ ] 인덱스 설계 포함 (복합 인덱스, Unique)
- [ ] 동시성 제어 필드 (`version`)
- [ ] FK 정책 명시 (CASCADE, RESTRICT)
- [ ] Unique 인덱스 `uidx_` 네이밍 사용

### 시퀀스 다이어그램
- [ ] 트랜잭션 경계 명시 (BEGIN, COMMIT, ROLLBACK)
- [ ] Lock 획득/해제 표시 (**LOCKED**)
- [ ] 성공/실패 분기 (alt fragment)
- [ ] **SQL 쿼리 제거** ✅
- [ ] **메서드명 제거**, 액션 설명 사용 ✅

### 플로우차트
- [ ] 비즈니스 로직 의사결정 흐름
- [ ] 유효성 검증 단계
- [ ] 예외 처리 분기

---

## 🚀 다음 단계 (Week 3+)

### 구현 단계
1. **Spring Boot 프로젝트 생성**
2. **Entity 클래스 작성** (ERD 기반)
3. **Repository 인터페이스** (JPA)
4. **Service 계층** (비즈니스 로직)
5. **Controller 계층** (REST API)

### 테스트 단계
1. **단위 테스트** (Service 계층)
2. **통합 테스트** (API 엔드포인트)
3. **동시성 테스트** (재고 차감, 쿠폰 발급)

### 최적화 단계
1. **Redis 캐싱** (인기 상품)
2. **Kafka 메시징** (외부 연동)
3. **성능 테스트** (부하 테스트)

---

## 📚 문서 작성 가이드

### 새 문서 추가 시
1. 목적 명확히 (Why)
2. 예시 포함 (How)
3. 제약사항 명시 (What not to do)
4. 관련 문서 링크

### 기존 문서 수정 시
1. 변경 이력 기록 (변경 날짜, 이유)
2. 영향받는 다른 문서 확인
3. 예시 코드 업데이트

---

## 🔗 관련 링크

- **루트 README**: [../README.md](../README.md)
- **프로젝트 구조**: [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)
- **GitHub Issues**: 피드백 및 개선사항

---

**항해플러스 백엔드 커리큘럼 Week 2** - 완전한 시스템 설계 문서 ✅
