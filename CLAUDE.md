# CLAUDE.md

> **이 프로젝트의 메인 가이드는 `.claude/CLAUDE.md`를 참조하세요.**

---

## 📍 주요 문서 위치

### 메인 가이드
- **`.claude/CLAUDE.md`** ⭐ **여기를 먼저 읽으세요!**
  - Claude Code 사용 방법
  - 작업 방식 (리스트업 후 1개씩, Decision Gate)
  - 테스트 전략, 아키텍처, 핵심 규칙

### 규칙 문서 (우선순위 순)
1. **`AGENTS.md`** - 모든 코딩 규칙의 단일 소스 (Single Source of Truth)
2. **`GEMINI.md`** - Gemini용 규칙 (AGENTS.md 보충)
3. **`docs/CODEX_GUIDE.md`** - Codex Pair-Coding 가이드

### 주차별 상세 가이드
- **`docs/week7/`** - Redis 기반 랭킹 시스템 & 선착순 쿠폰
- **`docs/week8/`** - Event Listener & Outbox Pattern
- **`docs/week*/README.md`** - 주차별 코치 피드백 & Q&A

### 슬래시 커맨드
- **`.claude/commands/`** - 커스텀 슬래시 커맨드
  - `/architecture` - 아키텍처 상세
  - `/concurrency` - 동시성 제어 패턴
  - `/testing` - 테스트 전략
  - `/implementation` - 구현 가이드

---

## 🚀 빠른 시작

1. **`.claude/CLAUDE.md`** 읽기 (필수)
2. **`AGENTS.md`** 규칙 확인
3. 작업 시작 전: 최신 `docs/week*/README.md` 확인
4. Test-First로 작업: 실패 테스트 → 최소 코드 → 통과 → 전체 테스트

---

## 📋 핵심 원칙 (요약)

### 단일 소스
- **AGENTS.md**가 모든 규칙의 기준
- 충돌 시 AGENTS.md 우선

### 작업 방식
- **리스트업 후 1개씩** (1-3 파일, 200 LoC 이하)
- **Decision Gate**: 태스크 선택, 접근 방식, 긴 커맨드, 범위 증가

### 테스트
- Test-First 워크플로우
- 커버리지 70% 이상
- Testcontainers 사용 (mock 금지)

### 금지 사항
- ❌ 대규모 리팩터링/패키지 이동
- ❌ Assertion 삭제/약화
- ❌ 아키텍처 변경

---

## 📞 더 자세한 내용은?

**→ `.claude/CLAUDE.md`를 참조하세요!**
