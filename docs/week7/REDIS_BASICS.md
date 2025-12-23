# Redis 기초 개념

## 🎯 Redis란?

**Redis (REmote DIctionary Server)**
- Key-Value 기반 인메모리 데이터 저장소
- 다양한 자료구조 지원 (String, List, Set, Sorted Set, Hash 등)
- 단일 스레드로 동작하지만 매우 빠름 (메모리 기반)
- 캐시, 세션, 랭킹, 메시지 브로커 등 다양한 용도로 활용

---

## 🏗️ Redis 아키텍처

### 단일 스레드 이벤트 루프

```
[클라이언트 요청들]
        ↓
[요청 대기 큐]
        ↓
[이벤트 루프] → 하나씩 꺼내서 처리 요청
        ↓
[명령어 처리] → CPU (단일 스레드)
        ↓
[응답 반환]
```

**특징:**
1. **단일 스레드:** 하나의 명령어만 처리
2. **이벤트 루프:** 처리가 끝나길 기다리지 않고 계속 명령어를 던짐
3. **메모리 기반:** 디스크 I/O 없이 빠른 처리
4. **원자성:** 각 명령어는 원자적(atomic)으로 실행

**장점:**
- Lock 없이도 동시성 제어 가능
- Context Switching 비용 없음
- 매우 빠른 처리 속도 (보통 1ms 이내)

**주의사항:**
- CPU를 오래 쓰는 작업은 전체 지연 유발
- Lua 스크립트는 짧게 작성해야 함

---

## 📦 주요 자료구조

### 1. String

**특징:**
- 가장 기본적인 자료구조
- 최대 512MB 문자열 저장
- 숫자 연산 가능 (INCR, DECR)

**사용 사례:**
- 캐시 데이터 저장
- 카운터 (조회수, 좋아요 수)
- 세션 정보
- 쿠폰 잔여 수량

**주요 명령어:**
```bash
SET key value        # 값 저장
GET key              # 값 조회
INCR key             # 1 증가 (원자적)
DECR key             # 1 감소 (원자적)
INCRBY key amount    # amount만큼 증가
DECRBY key amount    # amount만큼 감소
```

**예시:**
```bash
# 쿠폰 잔여 수량 관리
SET coupon:1:remain 100
DECR coupon:1:remain     # 99
GET coupon:1:remain      # "99"
```

---

### 2. Set

**특징:**
- 중복 없는 문자열 집합
- 순서 보장 안 됨
- O(1) 멤버십 체크
- 집합 연산 지원 (합집합, 교집합, 차집합)

**사용 사례:**
- 중복 방지 (쿠폰 발급자 목록)
- 태그 관리
- 좋아요/팔로우 관계
- 중복 제거가 필요한 모든 경우

**주요 명령어:**
```bash
SADD key member         # 멤버 추가
SISMEMBER key member    # 멤버 존재 여부 (O(1))
SMEMBERS key            # 전체 멤버 조회
SCARD key               # 멤버 수
SREM key member         # 멤버 제거
```

**예시:**
```bash
# 쿠폰 발급자 관리
SADD coupon:1:issued user123
SISMEMBER coupon:1:issued user123   # 1 (존재)
SISMEMBER coupon:1:issued user456   # 0 (없음)
SCARD coupon:1:issued               # 1
```

---

### 3. Sorted Set (핵심 ⭐)

**특징:**
- Set + score (가중치)
- score 기준 **자동 정렬** (오름차순)
- score 같으면 사전순 정렬
- O(log N) 삽입/조회/업데이트
- 랭킹 구현에 최적

**사용 사례:**
- 실시간 랭킹 (게임 점수, 상품 판매량)
- 우선순위 큐
- 시간 기반 데이터 (타임라인)
- Top N 조회

**주요 명령어:**
```bash
ZADD key score member           # 멤버 추가/업데이트
ZINCRBY key increment member    # score 증가 (원자적)
ZSCORE key member               # score 조회
ZRANK key member                # 순위 조회 (낮은 순)
ZREVRANK key member             # 순위 조회 (높은 순)
ZRANGE key start stop           # 범위 조회 (낮은 순)
ZREVRANGE key start stop        # 범위 조회 (높은 순)
ZREVRANGE key 0 9 WITHSCORES    # Top 10 + score
```

**예시:**
```bash
# 상품 판매 랭킹
ZADD ranking:daily:20250102 100 product1
ZADD ranking:daily:20250102 200 product2
ZADD ranking:daily:20250102 150 product3

ZINCRBY ranking:daily:20250102 5 product1    # 100 → 105

ZREVRANGE ranking:daily:20250102 0 2 WITHSCORES
# 1) "product2"  2) "200"
# 3) "product3"  4) "150"
# 5) "product1"  6) "105"

ZREVRANK ranking:daily:20250102 product2     # 0 (1등)
```

---

### 4. List

**특징:**
- 순서가 있는 문자열 리스트
- 양 끝에서 삽입/삭제 O(1)
- 중간 접근 O(N)
- 최대 2^32 - 1 개 원소

**사용 사례:**
- 메시지 큐
- 최근 활동 기록
- 대기열 (FIFO)

**주요 명령어:**
```bash
LPUSH key value    # 왼쪽에 추가
RPUSH key value    # 오른쪽에 추가
LPOP key           # 왼쪽에서 제거
RPOP key           # 오른쪽에서 제거
LRANGE key 0 -1    # 전체 조회
```

---

## ⏰ TTL (Time To Live)

### TTL이란?
- 키의 유효 기간 설정
- 지정된 시간 후 자동 삭제
- 메모리 누수 방지

### 주요 명령어
```bash
EXPIRE key seconds           # 초 단위 TTL 설정
EXPIREAT key timestamp       # Unix timestamp로 만료 시각 설정
TTL key                      # 남은 시간 (초)
PERSIST key                  # TTL 제거 (영구 보존)
```

### 사용 사례
```bash
# 일간 랭킹 (3일 후 자동 삭제)
ZADD ranking:daily:20250102 100 product1
EXPIRE ranking:daily:20250102 259200    # 3일 = 259200초

# 세션 (30분 후 자동 삭제)
SET session:abc123 "user_data"
EXPIRE session:abc123 1800              # 30분 = 1800초

# 쿠폰 (유효기간과 동일하게)
SADD coupon:1:issued user123
EXPIREAT coupon:1:issued 1735689600    # 2025-01-01 00:00:00
```

---

## 🔒 원자성 (Atomicity)

### 원자적 연산이란?
- 명령어가 **중단 없이 완전히 실행**됨
- 동시에 여러 클라이언트가 같은 키에 접근해도 안전
- Redis는 단일 스레드라 모든 명령어가 원자적

### 원자적 명령어 활용

**예시 1: 카운터**
```bash
# 100개 클라이언트가 동시에 INCR
# → 정확히 100 증가 보장
INCR page:views    # 원자적 증가
```

**예시 2: 랭킹**
```bash
# 동시에 10개 요청
# → score는 정확히 10 증가
ZINCRBY ranking:daily:20250102 1 product1
```

**예시 3: 쿠폰 수량 차감**
```bash
# 동시에 여러 사용자가 쿠폰 발급
# → 수량은 정확히 차감됨
DECR coupon:1:remain
```

---

## 🎨 키 네이밍 전략

### 권장 패턴
```
{도메인}:{엔티티}:{속성}:{식별자}
```

### 예시
```bash
# 랭킹
ranking:product:orders:daily:20250102
ranking:product:orders:weekly:2025W01

# 쿠폰
coupon:1:remain         # 쿠폰 1의 잔여 수량
coupon:1:issued         # 쿠폰 1의 발급자 목록
coupon:1:metadata       # 쿠폰 1의 메타 정보

# 사용자
user:123:session        # 사용자 123의 세션
user:123:cart           # 사용자 123의 장바구니

# 캐시
cache:product:123       # 상품 123 캐시
cache:order:456         # 주문 456 캐시
```

### 주의사항
- 일관된 구분자 사용 (`:` 권장)
- 의미 있는 이름 사용
- 너무 길지 않게 (메모리 효율)
- 날짜 포맷 통일 (YYYYMMDD, YYYY-Www 등)

---

## 🚀 성능 특성

### 시간 복잡도

| 명령어 | 복잡도 | 설명 |
|--------|--------|------|
| GET, SET | O(1) | 단일 키 접근 |
| INCR, DECR | O(1) | 원자적 증감 |
| SADD, SISMEMBER | O(1) | Set 추가/조회 |
| ZADD | O(log N) | Sorted Set 추가 |
| ZINCRBY | O(log N) | Sorted Set score 증가 |
| ZREVRANGE | O(log N + M) | M개 조회 |
| ZREVRANK | O(log N) | 순위 조회 |

### 성능 팁
1. **단일 명령어 사용:** 가능하면 원자적 명령어 활용
2. **Pipeline 사용:** 여러 명령어를 한 번에 전송
3. **Lua 스크립트는 짧게:** CPU 연산 최소화
4. **대용량 조회 피하기:** `KEYS *`, `SMEMBERS` (큰 Set) 주의
5. **TTL 설정:** 메모리 누수 방지

---

## 🛠️ Java (Spring Data Redis) 사용 예시

### RedisTemplate 설정
```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(
        RedisConnectionFactory connectionFactory
    ) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
```

### String 사용
```java
// 쿠폰 잔여 수량 차감
String key = "coupon:1:remain";
Long remain = redisTemplate.opsForValue().decrement(key);

if (remain < 0) {
    // 원복
    redisTemplate.opsForValue().increment(key);
    throw new CouponSoldOutException();
}
```

### Set 사용
```java
// 중복 발급 체크
String key = "coupon:1:issued";
String userId = "123";

Boolean alreadyIssued = redisTemplate.opsForSet().isMember(key, userId);

if (Boolean.TRUE.equals(alreadyIssued)) {
    throw new AlreadyIssuedException();
}

// 발급 기록
redisTemplate.opsForSet().add(key, userId);
```

### Sorted Set 사용
```java
// 랭킹 갱신
String key = "ranking:daily:20250102";
String productId = "123";
int quantity = 5;

redisTemplate.opsForZSet().incrementScore(key, productId, quantity);

// Top 10 조회
Set<ZSetOperations.TypedTuple<String>> top10 =
    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 9);

// 순위 조회
Long rank = redisTemplate.opsForZSet().reverseRank(key, productId);
```

---

## ⚠️ 주의사항

### 1. 메모리 관리
- Redis는 인메모리 → 메모리 부족 시 장애
- 반드시 TTL 설정
- 대용량 데이터는 적합하지 않음

### 2. 단일 스레드 특성
- CPU 오래 쓰는 작업 금지 (Lua 스크립트 주의)
- 대량 데이터 조회 피하기 (`KEYS *`, `SMEMBERS` 큰 Set)

### 3. 데이터 영속성
- 기본적으로 메모리만 사용 (재시작 시 데이터 유실)
- RDB, AOF 설정으로 백업 가능
- 중요 데이터는 DB에도 저장

### 4. 네트워크
- Redis는 별도 서버 → 네트워크 비용 발생
- 가능하면 명령어 수 최소화 (Pipeline 활용)

---

## 📚 참고 자료

### 공식 문서
- [Redis 공식 사이트](https://redis.io/)
- [Redis Commands](https://redis.io/commands/)
- [Redis Data Types](https://redis.io/docs/data-types/)
- [Sorted Sets](https://redis.io/docs/data-types/sorted-sets/)

### 학습 자료
- `agent_docs/redis_ranking.md` - 랭킹 구현 상세
- `agent_docs/redis_coupon_issue.md` - 쿠폰 발급 상세
- `docs/week7/COACH_QNA_SUMMARY.md` - QnA 핵심 요약
- `docs/week7/LEARNING_ROADMAP.md` - 학습 로드맵

---

## ✅ 학습 체크리스트

### 기초 개념
- [ ] Redis가 무엇인지 설명할 수 있다
- [ ] 단일 스레드 이벤트 루프를 이해한다
- [ ] 원자성이 무엇인지 이해한다

### 자료구조
- [ ] String, Set, Sorted Set의 차이를 설명할 수 있다
- [ ] 각 자료구조의 사용 사례를 알고 있다
- [ ] 주요 명령어를 사용할 수 있다

### 실전 활용
- [ ] TTL을 설정할 수 있다
- [ ] 키 네이밍 전략을 수립할 수 있다
- [ ] RedisTemplate로 코드를 작성할 수 있다

---

## 🚀 다음 단계

1. **실습:** Redis CLI로 명령어 직접 실행
2. **구현:** `agent_docs/` 문서 참조하여 랭킹/쿠폰 구현
3. **테스트:** Testcontainers로 통합 테스트
4. **최적화:** 성능 측정 및 개선
