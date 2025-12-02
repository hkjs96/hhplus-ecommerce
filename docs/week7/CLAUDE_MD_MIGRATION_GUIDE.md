# CLAUDE.md 재구성 가이드 (Week 7 최적화)

## 📊 Before/After 비교

| 항목 | 기존 CLAUDE.md | 새 CLAUDE_WEEK7.md |
|------|---------------|-------------------|
| **줄 수** | 286줄 | 133줄 (-53%) |
| **초점** | Week 4 (JPA/DB) | Week 7 (Redis/랭킹/쿠폰) |
| **코드 예시** | 다수 포함 | 0개 (별도 문서로 분리) |
| **구조** | 과거 Week 정보 혼재 | 현재 Week만 집중 |
| **Redis 정보** | 없음 | 핵심 원칙 명시 |
| **세부 지침** | 본문에 포함 | agent_docs로 분리 |

---

## ✅ 개선 사항

### 1. 정보 밀도 최적화
**Before (기존):**
```markdown
### Phase 1: Documentation & Design ✅ (Week 2)
- ✅ step1-2: ERD, Sequence Diagrams, API Specification
- ✅ step3: Infrastructure + Core Controllers
...
### Phase 2: Layered Architecture Implementation ✅ (Week 3)
...
### Phase 3: Database Integration ✅ (Week 4)
...
```
→ 과거 Week 정보가 과도하게 포함됨

**After (Week 7 최적화):**
```markdown
## 3. 핵심 도메인 (WHY)
### 3.2 실시간 랭킹 (Sorted Set)
- 저장 방식: Redis Sorted Set
- 갱신 시점: 결제 성공 시 ZINCRBY
- 동시성: Redis atomic 보장, 별도 락 불필요
```
→ 현재 Week 핵심만 간결하게

---

### 2. Redis 사용 원칙 명시

**Before:** Redis 관련 정보 없음

**After:**
```markdown
## 4. Redis 사용 원칙
- 단일 스레드 이벤트 루프 특성 이해
- Lua 스크립트는 짧게 (CPU 오래 쓰면 전체 지연)
- 랭킹: ZINCRBY 사용, 분산락 불필요
- 쿠폰: 수량 차감 + 발급 기록은 트랜잭션 단위
```
→ QnA 내용 반영, 핵심 규칙 명시

---

### 3. Progressive Disclosure 적용

**Before:** 모든 정보를 CLAUDE.md에 포함

**After:** 세부 지침은 별도 문서로 분리
```markdown
## 6. 추가 문서 (Progressive Disclosure)
- agent_docs/redis_ranking.md (상세 설계)
- agent_docs/redis_coupon_issue.md (Lua 스크립트 예시)
- agent_docs/testing_redis_features.md (테스트 시나리오)
```
→ Claude가 필요할 때만 읽도록 유도

---

### 4. 코드 예시 제거

**Before:**
```java
// Week 3: 순수 Java 클래스
public class Product {
    private String id;
    ...
}

// Week 4: JPA Entity
@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue
    ...
}
```
→ 60줄 이상의 코드 예시 포함

**After:** 코드 예시 0개
→ 실제 코드 파일 참조 또는 별도 문서로 분리

---

## 📋 마이그레이션 체크리스트

### Step 1: 현재 CLAUDE.md 백업
```bash
cp CLAUDE.md CLAUDE_BACKUP_WEEK4.md
```

### Step 2: Week 7용 CLAUDE.md로 교체
```bash
cp CLAUDE_WEEK7.md CLAUDE.md
```

### Step 3: 세부 문서 작성 (agent_docs/)
아래 3개 파일을 작성하여 `agent_docs/` 폴더에 배치:

- [ ] `redis_ranking.md` (랭킹 시스템 상세 설계)
- [ ] `redis_coupon_issue.md` (쿠폰 발급 로직 상세)
- [ ] `testing_redis_features.md` (테스트 시나리오)

### Step 4: 검증
- [ ] CLAUDE.md가 150줄 이내인가?
- [ ] Week 7 핵심(랭킹/쿠폰)에 집중하는가?
- [ ] Redis 사용 원칙이 명시되어 있는가?
- [ ] 코드 예시가 제거되었는가?
- [ ] 세부 지침이 별도 문서로 분리되었는가?

---

## 🎯 왜 이렇게 바꾸는가?

### 1. Claude의 무상태성
- LLM은 세션 간 기억을 유지하지 않음
- 매 세션마다 CLAUDE.md를 다시 읽음
- 불필요한 정보가 많으면 **관련성 낮다고 판단하여 무시**

### 2. 지시사항 과잉 방지
- LLM은 약 150~200개의 지시사항을 안정적으로 수행
- Claude Code 시스템 프롬프트에 이미 ~50개 지시 포함
- CLAUDE.md에 과도한 지시를 넣으면 **전체 품질 균등 저하**

### 3. Progressive Disclosure
- 세부 지침은 필요할 때만 읽도록
- Claude가 `agent_docs/*.md`를 선택적으로 참조
- 예: "Redis 쿠폰 발급 구현 시 `agent_docs/redis_coupon_issue.md` 참조"

### 4. 정보 밀도 최적화
- **WHAT (구조)**, **WHY (목적)**, **HOW (방법)** 만 포함
- 코드 예시, 과거 Week 정보 제거
- 현재 과제에 집중

---

## 📝 다음 단계

### agent_docs 폴더 구조 제안
```
agent_docs/
├── redis_ranking.md          # Sorted Set 키 설계, 만료 정책
├── redis_coupon_issue.md     # Lua 스크립트 예시, 에러 케이스
├── testing_redis_features.md # 동시성 테스트 시나리오
└── week4_archive.md          # Week 4 JPA 정보 보관용
```

### 각 문서 작성 가이드

**redis_ranking.md:**
- Sorted Set 키 네이밍 전략
- TTL 설정 (일간/주간 랭킹 분리)
- ZINCRBY vs ZADD 선택 기준
- 랭킹 조회 API 예시 (ZREVRANGE, ZREVRANK)

**redis_coupon_issue.md:**
- Lua 스크립트 예시 (짧게 작성)
- 개별 명령 조합 + 롤백 로직
- 에러 케이스 (수량 부족, 중복 발급)
- DB-Redis 싱크 전략

**testing_redis_features.md:**
- Testcontainers 설정
- 다중 스레드 동시성 테스트 예시
- 쿠폰 발급 수량 검증
- 랭킹 score 정확성 검증

---

## 🚫 피해야 할 실수

### ❌ CLAUDE.md에 넣지 말 것
1. **과거 Week 정보** (Week 2, 3, 4 상세 내역)
2. **코드 예시** (JPA Entity, Repository 구현 등)
3. **린터 규칙** (코드 스타일, 포맷팅)
4. **과제 요구사항 전문** (복붙 금지)
5. **개인 취향** ("나를 지수라고 불러" 등)

### ✅ CLAUDE.md에 넣을 것
1. **프로젝트 개요** (한 줄 요약)
2. **기술 스택 & 구조** (WHAT)
3. **핵심 도메인** (WHY) - 랭킹, 쿠폰
4. **Redis 사용 원칙** (HOW)
5. **작업 방법** (빌드/테스트 명령어)
6. **추가 문서 목록** (Progressive Disclosure)
7. **Claude 사용 규칙** (최소 3~5줄)

---

## 📚 참고: GPT 가이드 핵심 요약

1. **Less is more**: 지시사항은 최소한으로
2. **300줄 미만**: 가능하면 100~150줄 유지
3. **Progressive Disclosure**: 세부 지침은 별도 문서
4. **Claude는 린터가 아님**: 스타일 규칙은 도구 활용
5. **자동 생성 금지**: 신중한 수작업 작성

---

## ✨ 기대 효과

1. **Claude 무시율 감소**: 관련성 높은 정보만 포함하여 지시 준수율 향상
2. **세션 효율 향상**: 불필요한 context 제거로 집중도 향상
3. **유지보수 용이**: Week별 문서 분리로 관리 포인트 명확화
4. **확장성 확보**: 새 Week 추가 시 기존 문서 영향 최소화

---

## 🎓 참고 자료

- HumanLayer CLAUDE.md 예시 (60줄 미만)
- Anthropic Claude Code 공식 문서
- GPT 작성 가이드 (이 프로젝트용 커스터마이즈)
