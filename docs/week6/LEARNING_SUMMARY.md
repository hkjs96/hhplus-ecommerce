# Week 6 학습 정리 - STEP11-12 분산락 & 캐싱

> **작성일**: 2025-11-26
> **학습 주제**: Redis 분산락, 캐싱 전략, Cache Stampede 방지
> **참고 문서**: STEP11-12_QUICK_START.md, STEP11-12_LEARNING_GUIDE.md, STEP11-12_CODE_EXAMPLES.md

---

## 📚 학습 목표

### STEP 11: Distributed Lock
- ✅ Redis 기반 분산락의 동작 원리 이해
- ✅ Redisson을 활용한 분산락 구현
- ✅ 락과 트랜잭션 순서 보장의 중요성 이해
- ✅ Simple Lock, Spin Lock, Pub/Sub 방식 차이 학습

### STEP 12: Caching
- ✅ Cache-Aside 패턴 구현
- ✅ Cache Stampede 이슈 이해 및 대응 방안
- ✅ TTL/Eviction 전략 설계
- ✅ 성능 개선 측정 및 보고서 작성

---

## 🎯 구현 과제 목록

### Phase 1: 분산락 구현 (STEP 11)

#### 1. Redis 환경 설정
```yaml
# docker-compose.yml
services:
  redis:
    image: redis:7-alpine
    container_name: ecommerce-redis
    ports:
      - "6379:6379"
    command: redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru
```

#### 2. Gradle 의존성
```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.redisson:redisson-spring-boot-starter:3.23.5'
}
```

#### 3. RedisConfig 작성
- Redisson 클라이언트 Bean 등록
- JSON 직렬화 설정 (Jackson)
- 커넥션 풀 설정 (50개, 최소 유휴 10개)

#### 4. DistributedLock 어노테이션 + AOP
- SpEL 표현식으로 동적 락 키 생성
- waitTime, leaseTime 설정
- finally 블록에서 락 해제 보장

#### 5. 비즈니스 로직 적용
- 주문 생성 (`OrderUseCase`)
- 결제 처리 (`PaymentUseCase`)
- 쿠폰 발급 (`CouponUseCase`)

### Phase 2: 캐싱 전략 (STEP 12)

#### 1. Cache-Aside 패턴 구현
```java
public List<ProductResponse> getPopularProducts() {
    // 1. 캐시 조회 (Cache Hit 시 즉시 반환)
    // 2. Cache Miss 시 분산락 획득
    // 3. Double-Check (락 대기 중 다른 스레드가 캐싱했을 수 있음)
    // 4. DB 조회 및 캐시 저장 (TTL: 5분)
}
```

#### 2. Cache Stampede 방지
- 분산락으로 동시 DB 조회 방지
- Double-Check 패턴으로 불필요한 조회 제거

#### 3. 캐시 갱신 전략
- TTL 기반 자동 만료 (5분)
- @Scheduled 주기적 갱신 (10분마다)
- 이벤트 기반 캐시 무효화 (@CacheEvict)

### Phase 3: 테스트 및 검증

#### 1. TestContainers 설정
- MySQL Container
- Redis Container

#### 2. 동시성 테스트
- 100명 동시 주문 시 재고 정확성 검증
- 재고 부족 케이스 테스트
- 락 타임아웃 테스트

#### 3. 캐시 테스트
- Cache Hit/Miss 동작 확인
- TTL 검증
- Cache Stampede 방지 확인 (50명 동시 요청)

#### 4. 성능 측정
- Before/After 비교 (응답 시간, TPS, DB 부하)
- Cache Hit Rate 분석

---

## 💡 평일 QnA 정리 (김종협 코치님, 2025-11-24)

### 1. DB 락 vs Redis 분산락 사용 기준

**질문**
> "언제 분산락으로 전환해야 하나요? 단일 서버/단일 DB에서는 DB 락으로도 충분한 것 같은데요?"

**핵심 답변**
- **단일 DB + 낮은 TPS (10~100 언더)** → DB 락(비관/낙관 락)으로 충분
- **분산락이 의미 있는 지점**:
  - 애플리케이션 서버 여러 대 + DB 1대 → DB가 병목
  - 향후 MS 분리, DB 샤딩 계획이 확실할 때
  - **DB가 버틸 수 있는 TPS를 넘기거나 곧 넘을 때**
- **미리 분산락 깔아두는 건 오버엔지니어링**
  - 실제로는 장애/지연을 겪고 → 이후에 도입하는 패턴이 일반적

**실무 적용**
```
순서:
1. 유저/트래픽 증가
2. DB 락 기반 동시성 제어로 시작
3. 장애 또는 심각한 지연 발생
4. 부하 분산/분산락/캐시 도입
```

---

### 2. 비관락/낙관락 vs 분산락

**질문**
> "단일 DB 환경에서 비관락/낙관락이면 동시성 제어가 되는데, 이때도 분산락이 필요한가요?"

**핵심 답변**
- 단일 DB라면 비관락/낙관락으로 **동시성 제어는 가능**
- 다만 전제 조건:
  - 서비스가 **평생 단일 DB 구조에서 끝날 것인가?**
- 실제 서비스는:
  - 유저가 늘면 단일 DB로 못 버팀 → 구조 변경 필요
  - 그때부터 DB 락만으로 해결 불가능한 상황 발생
- **결론**: "지금 단일 DB 상태에 갇혀서만 생각하면 안 된다"

---

### 3. 락 TTL, waitTime(대기 시간) 설계 기준

**질문**
> "락 대기 시간, TTL을 어느 기준으로 잡나요? TTL 안에 처리 못하면 어떻게 되나요?"

**핵심 답변**

1. **락 대기 시간(waitTime)**
   - 프론트/외부 호출 **전체 타임아웃**을 기준
   - 예: 클라이언트 전체 요청 왕복이 15초라면
     - 락 대기만 15초 줄 수 없음
     - **보통 3~5초 정도**가 현실적인 상한선
   - 그 이상은 유저 입장에서 "응답 없음"으로 느껴짐

2. **락 TTL/leaseTime(자동 해제 시간)**
   - "**이 로직이 아무리 느려도 끝나는 시간 + 여유 몇 초**"
   - 예: 최악 1초 처리 → 3~5초 이상으로 잡기
   - 로직마다 다를 수 있으니 **파라미터/설정으로 분리**

3. **TTL 동안 처리 못했을 때**
   - 락은 풀렸는데 트랜잭션이 아직 돌고 있을 수 있음
   - TTL만 믿지 말고 **DB 쿼리에 방어 조건 추가**
   - 예: `WHERE quantity >= ?` (동시성 경쟁 상황 방어)

**실무 예시**
```java
@DistributedLock(
    key = "'order:product:' + #productId",
    waitTime = 5,      // 5초 대기 (프론트 타임아웃 고려)
    leaseTime = 10     // 10초 후 자동 해제 (처리 시간 + 여유)
)
@Transactional
public OrderResponse createOrder(Long productId, int quantity) {
    // 비즈니스 로직
}
```

---

### 4. 캐시 갱신 전략 (Lazy vs Refresh-ahead vs 스케줄)

**질문**
> "캐시 갱신을 Lazy로 할지, TTL 끝나기 전에 미리 할지, 스케줄로 할지 어떻게 선택하나요?"

**핵심 답변**

1. **기본은 Lazy Loading**
   - `캐시 미스 → DB 조회 → 캐시 저장` 패턴으로 충분
   - 캐시 없던 시절 속도에 유저가 익숙함

2. **Refresh-ahead / 스케줄 갱신**
   - **부하 패턴**을 볼 때 고민
   - 특정 시간대 트래픽 집중 구간이 명확하면:
     - 그 전에 미리 캐싱(스케줄/배치)
   - 예: 출근 시간, 점심 시간, 이벤트 시간

3. **스탬피드(동시에 캐시 미스) 상황**
   - Lazy만 쓰면 만료 시점에 모든 요청이 DB로 몰림
   - 조합 전략:
     - 일부는 "몇 초 포기"
     - 일부는 캐시 락 + Lazy
     - 일부는 백그라운드 리프레시

**실무 적용**
```java
// Lazy Loading (기본)
public List<ProductResponse> getPopularProducts() {
    return cache.get("popular:products", () -> {
        return productRepository.findTop5ByOrderBySalesCountDesc();
    });
}

// 스케줄 갱신 (트래픽 집중 시간대 대비)
@Scheduled(cron = "0 */10 * * * *")  // 10분마다
public void refreshPopularProductsCache() {
    // 미리 캐시 갱신
}
```

---

## 🎓 멘토링 정리 (제이 코치님, 2025-11-25)

### 1. Redis 캐시 무효화 & 키 관리

**핵심 내용**

1. **`KEYS` 명령어는 프로덕션 금지**
   - O(N) 연산 + 실행 중 블로킹
   - 트래픽 많은 환경에서 장애 포인트

2. **대신 Set 자료구조로 캐시 키 관리**
   ```redis
   # 상품 캐시 키 그룹 관리
   SADD product:cache:keys product:1 product:2 product:3

   # 상품 수정 시 관련 캐시 일괄 삭제
   SMEMBERS product:cache:keys  # 키 목록 조회
   DEL product:1 product:2 product:3
   ```

3. **캐시 무효화는 완벽하지 않다**
   - Redis 명령 실패, 앱 버그, 다중 인스턴스 환경
   - 결론: **TTL + 최대한의 무효화 전략 조합**

**실무 패턴**
```java
// 캐시 키 그룹 관리
public void addProductCache(String productId, Product product) {
    // 1. 캐시 저장
    redisTemplate.opsForValue().set("product:" + productId, product);

    // 2. 키 그룹에 추가
    redisTemplate.opsForSet().add("product:cache:keys", "product:" + productId);
}

// 상품 수정 시 캐시 무효화
public void invalidateProductCache(String productId) {
    // Set에서 관련 키 조회 후 삭제
    Set<String> keys = redisTemplate.opsForSet().members("product:cache:keys");
    keys.stream()
        .filter(key -> key.contains(productId))
        .forEach(redisTemplate::delete);
}
```

---

### 2. 분산락 AOP 구현 vs 명시적 락 매니저

**질문**
> "AOP로 분산락을 구현할 때, 락 획득 후 DB 트랜잭션 시작 순서를 보장하려면 AOP 순서만 믿어도 되나요?"

**핵심 내용**

1. **AOP + @Order로 우선순위 조정은 가능**
   ```java
   @Order(1)  // 분산락 Aspect
   public class DistributedLockAspect { ... }

   @Order(2)  // 트랜잭션 Aspect
   public class TransactionAspect { ... }
   ```

2. **하지만 AOP 순서에만 의존하는 설계는 위험**
   - 스프링 AOP는 프록시 기반
   - 클래스 내부 메서드 호출 시 프록시를 안 탈 수 있음
   - 여러 Aspect 섞이면 실행 순서 추적이 복잡
   - 새 Aspect 추가될 때마다 전체 `@Order` 조정 필요

3. **실무 추천: LockManager 컴포넌트**
   ```java
   // LockManager로 순서 명시
   lockManager.executeWithLock(key, () -> {
       // @Transactional 메서드 호출
   });
   ```

**실무 패턴**
```java
@Component
@RequiredArgsConstructor
public class LockManager {

    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    public <T> T executeWithLock(String lockKey, Supplier<T> task) {
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);

            if (!isLocked) {
                throw new IllegalStateException("락 획득 실패");
            }

            // 락 획득 후 트랜잭션 시작
            return transactionTemplate.execute(status -> task.get());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 대기 중 인터럽트", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

// 사용
@Service
@RequiredArgsConstructor
public class OrderService {

    private final LockManager lockManager;

    public OrderResponse createOrder(Long productId, int quantity) {
        return lockManager.executeWithLock(
            "order:product:" + productId,
            () -> createOrderInternal(productId, quantity)
        );
    }

    @Transactional
    private OrderResponse createOrderInternal(Long productId, int quantity) {
        // 비즈니스 로직
    }
}
```

---

### 3. 낙관락 vs Redis 락 선택 기준

**질문**
> "유저 잔액/포인트 같은 경우, 낙관락 + 재시도 대신 처음부터 Redis 분산락을 쓰는 게 나을까요?"

**핵심 답변**
- 기준은 **충돌 빈도(동시성 경쟁률)**

**일반적인 시나리오**
- 한 사용자가 동시에 여러 번 잔액 충전/사용하는 건 **드문 편**
- 대부분 하나의 기기에서 한 번씩만 발생
- **낙관락 + 재시도로 충분**

**예외적인 시나리오**
- 정기 결제/자동 결제로 **동일 유저 잔액을 동시에 여러 프로세스가 건드리는 경우**
- 충돌률이 높게 측정되면 Redis 락 고려

**단계적 접근**
```
1. 낙관락으로 시작
2. 충돌률 모니터링 (재시도 횟수, 실패율)
3. 일정 수준 이상이면 Redis 락으로 이동
```

**실무 예시**
```java
// 1단계: 낙관락 + 재시도
@Version
private Long version;

@Transactional
public void chargeBalance(Long userId, BigDecimal amount) {
    User user = userRepository.findById(userId).orElseThrow();
    user.chargeBalance(amount);  // version 자동 증가
}

// 2단계: 충돌률 모니터링
@Retryable(
    value = OptimisticLockingFailureException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 100)
)
public void chargeBalanceWithRetry(Long userId, BigDecimal amount) {
    chargeBalance(userId, amount);
}

// 3단계: 충돌률 높으면 Redis 락으로 전환
@DistributedLock(key = "'balance:user:' + #userId")
@Transactional
public void chargeBalanceWithLock(Long userId, BigDecimal amount) {
    User user = userRepository.findById(userId).orElseThrow();
    user.chargeBalance(amount);
}
```

---

### 4. Cache Stampede & TTL 랜덤화

**질문**
> "모든 캐시에 동일 TTL을 주면 한 시점에 동시에 만료돼서 스탬피드 생길 것 같은데, 어떻게 막나요?"

**핵심 내용**

1. **TTL 랜덤화**
   ```java
   // 기본 TTL ± 10~20% 범위에서 랜덤 값 추가
   Duration baseTTL = Duration.ofMinutes(5);
   Duration randomTTL = baseTTL.plus(
       Duration.ofSeconds(ThreadLocalRandom.current().nextInt(60))
   );

   bucket.set(data, randomTTL);
   ```

2. **캐시 미스 시 분산락**
   ```java
   public List<ProductResponse> getPopularProducts() {
       // 1. 캐시 조회
       List<ProductResponse> cached = cache.get("popular:products");
       if (cached != null) return cached;

       // 2. 캐시 미스 시 분산락 획득
       return lockManager.executeWithLock("lock:popular:products", () -> {
           // 3. Double-Check
           cached = cache.get("popular:products");
           if (cached != null) return cached;

           // 4. DB 조회 및 캐시 저장
           List<Product> products = productRepository.findTop5();
           cache.set("popular:products", products, randomTTL);
           return products;
       });
   }
   ```

3. **백그라운드 리프레시(Refresh-ahead)**
   ```java
   @Scheduled(cron = "0 */9 * * * *")  // TTL(10분) 전에 갱신
   public void refreshPopularProductsCache() {
       // TTL 만료 전에 미리 갱신
       List<Product> products = productRepository.findTop5();
       cache.set("popular:products", products, Duration.ofMinutes(10));
   }
   ```

---

### 5. Redis 메모리 관리 전략

**질문**
> "Redis 메모리가 꽉 차면 일부 키가 삭제돼 서비스에 영향이 있을 텐데, 어떻게 관리해야 하나요?"

**핵심 내용**

1. **maxmemory & maxmemory-policy**
   ```redis
   # redis.conf
   maxmemory 256mb
   maxmemory-policy allkeys-lru  # 가장 오래 안 쓰인 키부터 삭제
   ```

   - `noeviction` (기본): 추가 쓰기 불가 + 에러 반환 → 실무 지양
   - `allkeys-lru` (실무 권장): 전체 키 중 LRU 삭제

2. **TTL 설정은 기본**
   ```java
   // 용도별 TTL 차등 적용
   cache.set("product:detail:" + id, product, Duration.ofHours(1));
   cache.set("product:list", products, Duration.ofMinutes(10));
   ```
   - TTL 없는 키는 메모리에 계속 남아 **사실상 메모리 누수**

3. **모니터링 & 확장**
   - 메모리 사용률 70~80%를 경계로 봄
   - 인스턴스 스펙 업/샤딩 등으로 대응

4. **용도별 Redis 분리**
   ```
   Redis Instance 1: 캐시용 (allkeys-lru)
   Redis Instance 2: 세션/락용 (noeviction)
   ```
   - 캐시/세션/락을 한 인스턴스에 몰지 말고 분리
   - 장애 전파 최소화

---

### 6. Spin Lock vs Pub/Sub 기반 락 (Redisson)

**Spin Lock**
```java
// ❌ CPU를 갈아 먹는 구조
while (!lock.tryLock()) {
    Thread.sleep(100);  // 계속 반복
}
```
- 락이 풀릴 때까지 루프를 돌며 확인
- 동시 대기 쓰레드 많으면 CPU 100%

**Redisson & Pub/Sub**
```java
// ✅ Pub/Sub 기반 락 알림
RLock lock = redissonClient.getLock("myLock");
lock.lock();  // 내부적으로 Pub/Sub 사용
```
- 락이 풀릴 때만 클라이언트에게 알림
- 불필요한 반복/폴링 줄임

**비유**
- Spin Lock = 카운터 앞에 서서 "제 커피 나왔나요?" 계속 물어보기
- Pub/Sub = 진동벨 받고 자리에 있다가 울리면 나가기

**결론**
- Redis 환경이면 **직접 Spin Lock 구현하지 말고 Redisson 기본 락 사용**

---

## 📋 구현 체크리스트

### STEP 11: Distributed Lock

#### 필수 구현
- [ ] Docker Compose에 Redis 추가 (redis:7-alpine)
- [ ] Gradle 의존성 추가 (spring-boot-starter-data-redis, redisson-spring-boot-starter)
- [ ] RedisConfig 작성 (RedissonClient Bean)
- [ ] DistributedLock 어노테이션 구현 (key, waitTime, leaseTime)
- [ ] DistributedLockAspect 구현 (AOP)
- [ ] 주문 생성에 분산락 적용 (OrderUseCase)
- [ ] 결제 처리에 분산락 적용 (PaymentUseCase)
- [ ] 쿠폰 발급에 분산락 적용 (CouponUseCase)

#### 테스트
- [ ] TestContainers 설정 (MySQL + Redis)
- [ ] 분산락 동시성 테스트 (100명 동시 요청)
- [ ] 재고 부족 케이스 테스트
- [ ] 락 타임아웃 테스트
- [ ] 락과 트랜잭션 순서 검증

#### 문서화
- [ ] 분산락이 필요한 이유 설명
- [ ] Simple/Spin/Pub-Sub Lock 비교
- [ ] 락과 트랜잭션 순서 중요성 문서화

### STEP 12: Caching

#### 필수 구현
- [ ] 인기 상품 조회 캐싱 적용 (ProductUseCase)
- [ ] Cache-Aside 패턴 구현
- [ ] 분산락으로 Cache Stampede 방지
- [ ] Double-Check 패턴 적용
- [ ] TTL 설정 (5분)
- [ ] @Scheduled 주기적 캐시 갱신 (10분마다)
- [ ] @CacheEvict로 캐시 무효화

#### 테스트
- [ ] 캐시 Hit/Miss 테스트
- [ ] TTL 동작 테스트
- [ ] Cache Stampede 방지 테스트 (50명 동시 요청)
- [ ] 성능 측정 (Before/After)

#### 성능 보고서
- [ ] 문제 배경 및 원인 분석
- [ ] 적용한 캐싱 전략 설명
- [ ] 성능 측정 결과 (응답 시간, TPS, DB 부하)
- [ ] Cache Hit Rate 분석
- [ ] 한계점 및 개선 방안
- [ ] 결론 및 학습 내용 정리

---

## 🎓 학습 포인트

### 1. 분산락의 필요성
- 단일 DB 환경에서는 DB 락(비관/낙관)으로 충분
- **분산 환경 또는 DB 부하가 높을 때** 분산락 고려
- 미리 도입하지 말고 **필요성 확인 후 단계적 도입**

### 2. 락 전략 선택 기준
```
충돌 빈도 낮음 (< 1%)  → 낙관락 + 재시도
충돌 빈도 중간 (1~10%) → 비관락 (DB 락)
충돌 빈도 높음 (> 10%) → Redis 분산락
```

### 3. 캐시 갱신 전략
```
기본: Lazy Loading (Cache-Aside)
트래픽 집중: Refresh-ahead (스케줄 갱신)
스탬피드 방지: 분산락 + Double-Check
```

### 4. Redis 메모리 관리
```
TTL 설정 (용도별 차등)
maxmemory-policy: allkeys-lru
모니터링: 70~80% 경계
용도별 분리: 캐시 / 세션 / 락
```

### 5. AOP vs 명시적 락 매니저
```
AOP 장점: 선언적, 간결
AOP 단점: 순서 보장 어려움, 디버깅 복잡

실무 권장: LockManager 컴포넌트
→ 순서 명시, 트랜잭션 범위 제어
```

---

## 🚀 다음 단계

### Week 7: 외부 API 연동 & 인기 상품 배치
- 외부 데이터 플랫폼 전송 (Async)
- 인기 상품 집계 배치 (Scheduled)
- Circuit Breaker, Retry, Fallback

### Week 8: 캐싱 고도화 & 인덱스 최적화
- 캐시 워밍 (Cache Warming)
- 이벤트 기반 캐시 무효화
- DB 인덱스 최적화 (EXPLAIN ANALYZE)

### Week 9: 부하 테스트 & 모니터링
- K6/JMeter 부하 테스트
- 성능 병목 지점 분석
- APM 도구 연동 (Prometheus, Grafana)

---

## 📚 참고 자료

### 공식 문서
- [Redis 공식 문서](https://redis.io/docs/)
- [Redisson GitHub](https://github.com/redisson/redisson)
- [Spring Cache 가이드](https://spring.io/guides/gs/caching/)

### 추천 아티클
- [분산 락을 구현하는 여러 가지 방법](https://www.youtube.com/watch?v=UOWy6zdsD-c) (우아한테크)
- [Redis를 이용한 분산 락 구현](https://hyperconnect.github.io/2019/11/15/redis-distributed-lock-1.html)
- [Cache Stampede 문제와 해결](https://www.sobyte.net/post/2022-01/cache-stampede/)

### 학습 가이드
- `STEP11-12_QUICK_START.md`: 3시간 압축 학습
- `STEP11-12_LEARNING_GUIDE.md`: Day 1~4 상세 가이드
- `STEP11-12_CODE_EXAMPLES.md`: 바로 사용 가능한 코드

---

**작성자**: 항해플러스 백엔드 6기
**최종 수정일**: 2025-11-26
