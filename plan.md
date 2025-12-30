# 항해플러스 백엔드 학습 체크리스트 점검 계획

이 문서는 “주차별 학습 체크리스트”를 **레포 문서/코드 증빙과 연결**하기 위한 인덱스입니다.  
체크 상태는 아래 의미로 사용합니다.

- ✅ 충분: 문서가 있고, 설명/예시/실행(또는 검증)까지 연결됨
- ⚠️ 부분: 언급/예시는 있으나, 정리 문서가 얇거나 실행/검증까지 연결이 약함
- ❌ 없음: 레포에서 해당 주제를 직접 다룬 정리/증빙을 찾기 어려움

---

## 1주차: 테스트 + TDD

| 항목 | 상태 | 근거(문서) |
|------|------|------------|
| 테스트 대역(Mock/Stub/Fake/Spy) 이해 | ✅ | `docs/archive/week3/learning-points/06-testing-strategy.md` |
| 단위 테스트 vs 통합 테스트(차이/장단점/언제 작성) | ✅ | `docs/INTEGRATION_TEST_STRATEGY.md`, `docs/archive/week3/learning-points/06-testing-strategy.md` |
| 좋은 테스트 코드/강결합 테스트 취약점 | ✅ | `docs/archive/week3/learning-points/06-testing-strategy.md` |
| TDD 이해/적용 가능 여부 | ⚠️ | `docs/archive/week3/commands/week3-faq.md` (Q&A 형태) |
| 런던파 vs 고전파 이해 + 본인 견해 | ✅ | `docs/learning-points/12-testing-schools-london-vs-classical.md` |

---

## 2주차: 설계

| 항목 | 상태 | 근거(문서/증빙) |
|------|------|------------------|
| 설계 문서(시퀀스/ERD/API 스펙 등) 이해 | ✅ | `docs/api/`, `docs/diagrams/` |
| Swagger 등 도구 기반 문서화 | ⚠️ | `README.md` (링크/언급 수준) |
| Mock API 제공 필요성 | ✅ | `docs/api/scope-clarification.md`, `docs/archive/week3/commands/week3-faq.md` |
| RESTful API 설계 | ✅ | `docs/api/api-specification.md`, `docs/api/requirements.md` |

---

## 3주차: 아키텍처 패턴 및 구현

| 항목 | 상태 | 근거(문서) |
|------|------|------------|
| 아키텍처 패턴(장단점/이상적 구조) | ✅ | `docs/learning-points/01-layered-architecture.md`, `docs/learning-points/02-usecase-pattern.md` |
| DIP/의존성 방향/역전 | ✅ | `docs/learning-points/01-layered-architecture.md` |
| 입력 유효성 vs 비즈니스 유효성 | ✅ | `docs/learning-points/02-usecase-pattern.md` |
| OCP/역할과 책임/객체 생성 패턴 | ✅ | `docs/learning-points/03-domain-modeling.md` |
| Optional API 올바른 사용/안티패턴 | ⚠️ | `docs/learning-points/02-usecase-pattern.md` (안티패턴), 관련 언급 산발적 |
| 스레드/ThreadLocal/MDC, 자바 동시성 도구 | ⚠️ | `docs/learning-points/08-discussion-topics.md`, `docs/archive/week3/learning-points/09-concurrent-collections.md` |
| GC 알고리즘 이해 | ⚠️ | Week10 모니터링 관점으로 언급: `docs/week10/monitoring-metrics.md` |
| Spring Triangle(PSA 포함), DispatcherServlet, Filter/Interceptor | ✅ | `docs/learning-points/14-spring-web-core-dispatcher-filter-interceptor-aop-psa.md` |
| Spring AOP/내부참조 이슈, Transaction 동작/전파 | ⚠️ | AOP 이슈/사례는 Week6에 강함: `docs/week6/README.md`, 트랜잭션 동작 정리는 일부: `docs/learning-points/11-jpa-transaction-management.md` |
| LazyConnectionDataSourceProxy, ConfigurationProperties | ❌ | (레포에서 “학습 정리” 형태로는 부족) |

---

## 4~5주차: 데이터베이스

| 항목 | 상태 | 근거(문서) |
|------|------|------------|
| 트랜잭션/ACID/격리 수준/MVCC | ✅ | `docs/week5/TRANSACTION_FUNDAMENTALS.md` |
| 실행 계획/EXPLAIN(ANALYZE) | ✅ | `docs/week4/verification/EXPLAIN_ANALYZE_GUIDE.md`, `docs/week4/verification/QUERY_OPTIMIZATION_SUMMARY.md` |
| 낙관적 락 vs 비관적 락 | ✅ | `docs/week5/OVERVIEW.md`, `docs/week5/CONCURRENCY_ANALYSIS.md` |
| 자연키 vs 대체키 | ✅ | `docs/learning-points/13-mysql-keys-and-indexes.md` |
| MySQL PK/인덱스 차이, 클러스터링 인덱스, 인덱스/락 동작 방식 | ✅ | `docs/learning-points/13-mysql-keys-and-indexes.md` |

---

## 6주차: 분산락과 캐싱

| 항목 | 상태 | 근거(문서) |
|------|------|------------|
| 분산락 개념/구현 방식/한계 | ✅ | `docs/week6/CREATE_ORDER_DISTRIBUTED_LOCK.md`, `docs/week6/DB_LOCK_TO_REDIS_LOCK_ANALYSIS.md` |
| 캐시 필요성/전략/로컬 vs 분산 캐시 | ✅ | `docs/week6/WEEK6_COMPLETE_SUMMARY.md` |
| Spring Cache API(@Cacheable/@CacheEvict 등) | ✅ | `docs/week6/WEEK6_COMPLETE_SUMMARY.md` |
| 캐시 직렬화 도구(Serializer) 정리 | ⚠️ | 설정/예시 중심(비교/선택 가이드로는 얇음) |

---

## 7주차: Redis

| 항목 | 상태 | 근거(문서) |
|------|------|------------|
| Redis 내부 구조(싱글 스레드), 빠른 이유 | ✅ | `docs/week7/REDIS_BASICS.md`, `docs/week7/COACH_QNA_SUMMARY.md` |
| 자료구조(Sorted Set 등), TTL | ✅ | `docs/week7/REDIS_BASICS.md` |
| RedisTemplate 활용 | ✅ | `docs/week7/REDIS_BASICS.md` |

---

## 8주차: 이벤트 기반 트랜잭션 분리

| 항목 | 상태 | 근거(문서) |
|------|------|------------|
| Application Event/Publisher | ✅ | `docs/week8/README.md`, `docs/week8/QUICK_START.md` |
| @TransactionalEventListener/AFTER_COMMIT | ✅ | `docs/week8/TRANSACTION_SEPARATION_DESIGN.md`, `docs/week8/COMMON_PITFALLS.md` |
| @Async 동작/스레드풀 설정 | ✅ | `docs/week8/COMMON_PITFALLS.md` |
| Async MDC 설정 | ✅ | `docs/learning-points/15-async-mdc-propagation.md` |

---

## 9주차: Kafka

| 항목 | 상태 | 근거(문서) |
|------|------|------------|
| 구조/토픽·파티션·오프셋·컨슈머그룹 | ✅ | `docs/week9/kafka-basics.md` |
| 리밸런싱, auto commit, heartbeat/timeouts, fetch 설정 | ✅ | `docs/week9/kafka-basics.md`, `docs/week9/kafka-spring-integration.md` |
| auto.offset.reset=earliest 주의점 | ✅ | `docs/week9/kafka-basics.md` |

---

## 10주차: 장애 대응(SRE)

| 항목 | 상태 | 근거(문서/산출물) |
|------|------|------------------|
| 모니터링(메트릭/로그/APM), 핵심 지표(RED/Golden Signals) | ✅ | `docs/week10/monitoring-metrics.md`, `docs/week10/SRE_GUIDELINES.md` |
| 부하 시스템 이해/진행(k6) | ✅ | `docs/week10/step19-load-test-plan.md`, `loadtest/k6/step19-all-apis.js` |
| 장애 대응 프로세스/문화/런북 | ✅ | `docs/week10/step20-incident-report.md`, `docs/week10/RUNBOOK.md` |
| 로그 레벨 | ✅ | `docs/week10/RUNBOOK.md` (대응 루틴 포함) |
| 부하로 인한 Tomcat 스레드 상태 변화 | ⚠️ | 스레드/풀/리소스 신호 관점 언급은 있으나 “Tomcat thread state 변화”를 별도 정리한 섹션은 약함 |

---

## 개선 후보(TODO)

우선순위는 “체크리스트에 명시되어 있고, 리뷰어가 바로 확인하는 항목” 위주로 잡습니다.

1) 3주차: LazyConnectionDataSourceProxy, ConfigurationProperties 정리 (❌)
