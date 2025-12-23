# Week 7 Step 13-14 체크리스트

## 📋 과제 진행 체크리스트

---

## 🎯 STEP 13: Ranking Design

### ✅ 설계 단계
- [ ] Redis Sorted Set을 랭킹 시스템으로 선택한 이유 문서화
- [ ] 키 네이밍 전략 수립 (`ranking:product:orders:daily:{date}`)
- [ ] TTL 전략 수립 (일간/주간/월간 랭킹 분리)
- [ ] 랭킹 갱신 시점 정의 (결제 완료 시점 ✅)
- [ ] 동시성 이슈 분석 (ZINCRBY 원자성 확인)

### ✅ 구현 단계
- [ ] **도메인 이벤트 발행** (결제 완료 시)
  ```java
  eventPublisher.publishEvent(new PaymentCompletedEvent(orderId));
  ```
- [ ] **이벤트 리스너 구현** (비동기 처리)
  ```java
  @EventListener
  @Async
  public void updateRanking(PaymentCompletedEvent event) { ... }
  ```
- [ ] **Redis Sorted Set 갱신**
  ```java
  redisTemplate.opsForZSet().incrementScore(key, productId, quantity);
  ```
- [ ] **TTL 설정**
  ```java
  redisTemplate.expire(key, 3, TimeUnit.DAYS);
  ```
- [ ] **랭킹 조회 API** (Top N)
  ```java
  @GetMapping("/api/products/ranking/top")
  public RankingResponse getTopRanking(...) { ... }
  ```
- [ ] **특정 상품 순위 조회 API**
  ```java
  Long rank = redisTemplate.opsForZSet().reverseRank(key, productId);
  ```

### ✅ 테스트 단계
- [ ] **단위 테스트:** ZINCRBY 동작 검증
- [ ] **통합 테스트:** 결제 완료 → 랭킹 갱신 플로우
- [ ] **동시성 테스트:** 100개 동시 요청 시 score 정확성
  ```java
  @Test
  void 랭킹_동시_갱신_정합성_테스트() throws InterruptedException {
      // 100개 스레드가 각각 10씩 증가
      // 최종 score는 정확히 1000이어야 함
  }
  ```
- [ ] **TTL 검증 테스트:** 키 만료 확인
- [ ] **Top N 조회 테스트:** 정렬 순서 검증
- [ ] **Testcontainers 사용:** Redis 독립 환경

### ✅ 문서화 단계
- [ ] 설계 보고서 작성 (배경, 문제 해결, 테스트)
- [ ] 키 네이밍 전략 문서화
- [ ] 동시성 이슈 분석 및 해결 방법 정리
- [ ] TTL 전략 정리
- [ ] API 명세 업데이트

---

## 🎯 STEP 14: Asynchronous Design (선착순 쿠폰)

### ✅ 설계 단계
- [ ] **데이터 배치 전략 수립**
  - DB: 쿠폰 메타 정보 (ID, 이름, 할인율, 총 수량, 유효기간)
  - Redis: 선착순 재고 (`coupon:{id}:remain`)
  - Redis: 발급자 기록 (`coupon:{id}:issued`)
- [ ] **트랜잭션 규칙 정의**
  - 수량 차감 + 발급 기록은 **하나의 트랜잭션**
  - 실패 시 **즉시 원복**
  - 스케줄러 방식 금지 ❌
- [ ] **구현 방식 선택**
  - [ ] 옵션 A: Lua 스크립트 (원자적 처리)
  - [ ] 옵션 B: 개별 명령 + 롤백 로직
- [ ] **에러 케이스 정의**
  - 수량 부족 (SOLD_OUT)
  - 중복 발급 (ALREADY_ISSUED)
  - 수량 마이너스 방지

### ✅ 구현 단계 (Lua 스크립트 방식)
- [ ] **Lua 스크립트 작성** (`issue_coupon.lua`)
  ```lua
  -- 중복 체크 → 수량 체크 → DECR → SADD
  ```
- [ ] **RedisScript Bean 등록**
  ```java
  @Bean
  public RedisScript<Long> issueCouponScript() {
      return RedisScript.of(new ClassPathResource("lua/issue_coupon.lua"), Long.class);
  }
  ```
- [ ] **쿠폰 발급 서비스 구현**
  ```java
  public CouponIssueResult issueCoupon(Long couponId, Long userId) {
      Long result = redisTemplate.execute(issueCouponScript, keys, args);
      // result: 1(성공), -1(중복), -2(수량 부족)
  }
  ```
- [ ] **초기 데이터 로딩**
  ```java
  @EventListener(ApplicationReadyEvent.class)
  public void initializeCouponData() {
      // DB → Redis 데이터 세팅
  }
  ```
- [ ] **TTL 설정** (쿠폰 유효기간과 동일)

### ✅ 구현 단계 (개별 명령 + 롤백 방식)
- [ ] **중복 발급 체크**
  ```java
  Boolean alreadyIssued = redisTemplate.opsForSet().isMember(issuedKey, userId);
  ```
- [ ] **수량 차감**
  ```java
  Long remain = redisTemplate.opsForValue().decrement(remainKey);
  ```
- [ ] **수량 부족 체크 및 롤백**
  ```java
  if (remain < 0) {
      redisTemplate.opsForValue().increment(remainKey);  // 원복
      return CouponIssueResult.soldOut();
  }
  ```
- [ ] **발급 기록**
  ```java
  Long addResult = redisTemplate.opsForSet().add(issuedKey, userId);
  ```
- [ ] **발급 기록 실패 시 롤백**
  ```java
  if (addResult == 0) {
      redisTemplate.opsForValue().increment(remainKey);  // 원복
      throw new CouponIssueException();
  }
  ```
- [ ] **예외 처리 및 롤백**
  ```java
  catch (Exception e) {
      rollbackCouponIssue(couponId, userId);
  }
  ```

### ✅ 테스트 단계
- [ ] **단위 테스트:** 정상 발급
  ```java
  @Test
  void 쿠폰_발급_성공_테스트() { ... }
  ```
- [ ] **단위 테스트:** 수량 부족
  ```java
  @Test
  void 쿠폰_수량_부족_테스트() { ... }
  ```
- [ ] **단위 테스트:** 중복 발급 방지
  ```java
  @Test
  void 쿠폰_중복_발급_방지_테스트() { ... }
  ```
- [ ] **동시성 테스트:** 1000개 요청 → 100개만 성공 (핵심 ⭐)
  ```java
  @Test
  void 선착순_쿠폰_발급_동시성_테스트() throws InterruptedException {
      // Given: 총 수량 100
      // When: 1000개 스레드 동시 요청
      // Then: 정확히 100개만 성공, 900개 실패
      assertThat(successCount.get()).isEqualTo(100);
      assertThat(remain).isEqualTo("0");
      assertThat(issuedCount).isEqualTo(100);
  }
  ```
- [ ] **수량 마이너스 방지 검증**
  ```java
  assertThat(Integer.parseInt(remain)).isGreaterThanOrEqualTo(0);
  ```
- [ ] **롤백 테스트** (개별 명령 방식)
  ```java
  @Test
  void 쿠폰_발급_중_오류_발생_시_롤백_테스트() { ... }
  ```
- [ ] **Testcontainers 사용:** Redis 독립 환경

### ✅ 문서화 단계
- [ ] 설계 보고서 작성 (배경, 문제 해결, 테스트)
- [ ] 데이터 배치 전략 문서화 (DB vs Redis 역할)
- [ ] 트랜잭션 규칙 정리
- [ ] 구현 방식 선택 이유 (Lua vs 개별 명령)
- [ ] 에러 케이스 정리
- [ ] 동시성 테스트 결과 정리

---

## 📊 통합 테스트 체크리스트

### ✅ Testcontainers 설정
- [ ] **의존성 추가** (build.gradle)
  ```gradle
  testImplementation 'com.redis:testcontainers-redis:2.0.1'
  ```
- [ ] **베이스 테스트 클래스 작성**
  ```java
  @SpringBootTest
  @Testcontainers
  @ActiveProfiles("test")
  public abstract class RedisIntegrationTest { ... }
  ```
- [ ] **Redis Container 설정**
  ```java
  @Container
  static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
      .withExposedPorts(6379);
  ```
- [ ] **Dynamic Properties 설정**
  ```java
  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) { ... }
  ```

### ✅ 핵심 통합 테스트
- [ ] **주문 완료 → 랭킹 갱신 플로우**
  ```java
  @Test
  void 주문_완료_후_랭킹_자동_갱신_테스트() { ... }
  ```
- [ ] **쿠폰 발급 → 주문 적용 플로우**
  ```java
  @Test
  void 쿠폰_발급_후_주문_적용_테스트() { ... }
  ```
- [ ] **Redis 데이터 초기화** (각 테스트 전)
  ```java
  @BeforeEach
  void clearRedis() {
      redisTemplate.getConnectionFactory()
          .getConnection()
          .serverCommands()
          .flushAll();
  }
  ```

---

## 🎨 코드 품질 체크리스트

### ✅ 아키텍처
- [ ] **레이어 분리:** Presentation → Application → Domain ← Infrastructure
- [ ] **Redis Repository 분리:** `infrastructure.redis` 패키지
- [ ] **도메인 이벤트 사용:** 결제 완료 → 랭킹 갱신
- [ ] **비동기 처리:** `@Async` 적용

### ✅ Redis 사용
- [ ] **TTL 설정:** 모든 키에 TTL 필수
- [ ] **키 네이밍 일관성:** `domain:entity:attribute:id` 패턴
- [ ] **원자적 연산 활용:** ZINCRBY, DECR, SADD
- [ ] **Lua 스크립트 짧게:** CPU 연산 최소화

### ✅ 에러 처리
- [ ] **에러 코드 정의:** SOLD_OUT, ALREADY_ISSUED 등
- [ ] **적절한 예외 처리:** CouponIssueException 등
- [ ] **롤백 로직:** 실패 시 원복 필수

### ✅ 테스트
- [ ] **테스트 격리:** @BeforeEach에서 Redis 초기화
- [ ] **비동기 검증:** Awaitility 활용
- [ ] **동시성 검증:** ExecutorService + CountDownLatch
- [ ] **커버리지:** 핵심 로직 100% 달성

---

## 📝 문서 작성 체크리스트

### ✅ 설계 보고서 구조
- [ ] **배경**
  - 왜 Redis를 사용하는가?
  - RDBMS의 한계는 무엇인가?
  - 어떤 문제를 해결하려고 하는가?

- [ ] **문제 해결**
  - 어떤 자료구조를 선택했는가?
  - 왜 이 자료구조를 선택했는가?
  - 트랜잭션은 어떻게 보장하는가?
  - 동시성 이슈는 어떻게 해결하는가?

- [ ] **테스트**
  - 어떤 테스트를 작성했는가?
  - 동시성 테스트 결과는?
  - 성능 측정 결과는?

- [ ] **한계점**
  - 어떤 부분이 부족한가?
  - 개선할 수 있는 부분은?
  - Redis 장애 시 대응 방안은?

- [ ] **결론**
  - 무엇을 배웠는가?
  - 다음에 시도할 것은?

### ✅ README.md 업데이트
- [ ] Week 7 진행 상황 추가
- [ ] Redis 사용 방법 추가
- [ ] 테스트 실행 방법 추가
- [ ] 트러블슈팅 추가

---

## 🔍 코치 피드백 체크리스트

### ✅ Redis 구조 이해
- [ ] 단일 스레드 이벤트 루프 특성 이해
- [ ] Lua 스크립트는 짧게 작성
- [ ] 원자적 연산 활용

### ✅ 랭킹 시스템
- [ ] 결제 완료 시점에 갱신 (주문 생성 시점 ❌)
- [ ] Sorted Set 사용
- [ ] ZINCRBY로 원자적 증가
- [ ] 별도 분산락 불필요

### ✅ 쿠폰 발급
- [ ] **수량 차감 + 발급 기록은 트랜잭션 단위** (핵심 ⭐)
- [ ] 실패 시 즉시 원복
- [ ] 스케줄러로 나중에 맞추는 방식 절대 금지
- [ ] 실시간 처리 필수
- [ ] 수량 마이너스 방지

### ✅ 데이터 배치
- [ ] 메타 정보: DB
- [ ] 실시간 데이터: Redis
- [ ] 역할 명확히 분리

### ✅ 복잡도 관리
- [ ] 복잡도 증가는 당연함
- [ ] 역할과 책임 명확히 분리
- [ ] 손실은 절대 발생하면 안 됨

---

## 📊 PR 제출 전 최종 체크리스트

### ✅ 코드
- [x] Redis 기반 랭킹 시스템 구현 완료
- [x] Redis 기반 쿠폰 발급 시스템 구현 완료
- [x] Testcontainers 통합 테스트 작성
- [x] 동시성 검증 테스트 작성
- [x] 테스트 커버리지 70% 이상

### ✅ 문서
- [x] 설계 보고서 작성 완료
- [x] README.md 업데이트
- [x] 코드 주석 작성
- [x] API 명세 업데이트

### ✅ 부하 테스트 (2025-12-10 완료)
- [x] **K6 부하 테스트 실행** (3분 40초, 100 VUs, 10,494 iterations)
- [x] **Step 13 검증**: p(95) 13ms (목표 500ms) - ✅ PASS
- [x] **Step 14 검증**: 100개 정확히 발급, 중복 0건 - ✅ PASS
- [x] **응답 시간 Threshold**: 6/6 모두 통과 - ✅ PASS
- [x] **부하 테스트 결과 문서화**: `loadtest/LOAD_TEST_RESULTS.md`

### ✅ PR
- [ ] PR 제목 규칙 준수 (`[STEP13-14] 이름`)
- [ ] PR 템플릿 체크리스트 완료
- [ ] 커밋 메시지 명확하게 작성
- [ ] 불필요한 파일 제외 (.gitignore 확인)

### ✅ 회고
- [ ] KPT 형식 회고 작성
- [ ] 잘한 점 정리
- [ ] 어려웠던 점 정리
- [ ] 다음에 시도할 것 정리

---

## 🎯 평가 기준 재확인

### ✅ Pass 조건 (필수)
- [x] Redis Sorted Set 기반 랭킹 제공 ✅
- [x] 적절한 트랜잭션 + 파이프라인 구성 ✅
- [x] Redis 기반 선착순 쿠폰 발급 ✅
- [x] 동시성 제어 및 정합성 보장 ✅
- [x] 기존 RDBMS 로직 → Redis 로직 마이그레이션 ✅
- [x] Testcontainers 기반 통합 테스트 ✅

### ✅ 부하 테스트 검증 결과 (2025-12-10)
- [x] **Step 13 랭킹**: p(95) 13ms < 500ms (목표 대비 97% 개선) ✅
- [x] **Step 14 쿠폰**: 100/100 발급 정확성, 중복 0건 ✅
- [x] **전체 응답 시간**: 6/6 Threshold 통과 ✅
- [x] **처리량**: 60 req/s 안정적 처리 ✅
- [x] **동시성**: 100 VUs 정상 처리 ✅

**상세 결과:** `loadtest/LOAD_TEST_RESULTS.md` 참조

### ✅ 도전 항목 (심화)
- [x] DIP 적용으로 비즈니스 로직 보호 ✅
- [x] RedisTemplate 활용한 의존성 최소화 ✅
- [x] Redis 자료구조의 적절한 시스템 디자인 ✅
- [x] 보고서의 명확한 구성 (배경, 문제해결, 테스트, 한계점, 결론) ✅

---

## ✅ 최종 점검

모든 체크리스트를 완료했다면, 다음을 확인하세요:

1. **테스트 실행:** `./gradlew test` → 모두 통과
2. **커버리지 확인:** `./gradlew jacocoTestReport` → 70% 이상
3. **Redis 연결 확인:** Docker Compose로 Redis 실행
4. **동시성 테스트 확인:** 쿠폰 발급 수량 정확성
5. **문서 확인:** 보고서 완성도

**축하합니다! Week 7 과제를 완료했습니다! 🎉**
