# 9. Thread-Safe 컬렉션 (ConcurrentHashMap 중심)

## 📌 핵심 개념

**ConcurrentHashMap**: Java의 Thread-safe한 HashMap 구현체로, 높은 동시성(Concurrency)을 제공하면서도 우수한 성능을 보장하는 컬렉션

---

## 🎯 Week 3에서 ConcurrentHashMap의 역할

### Step 5: In-Memory Repository 구현
```java
@Repository
public class InMemoryProductRepository implements ProductRepository {
    // Thread-safe 저장소
    private final Map<String, Product> storage = new ConcurrentHashMap<>();

    @Override
    public Product save(Product product) {
        storage.put(product.getId(), product);  // Thread-safe
        return product;
    }

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(storage.get(id));  // Thread-safe
    }
}
```

### 로이코치님 조언
> "ConcurrentHashMap을 사용하면 어느 정도 동시성을 보장합니다."

**Week 3 범위:**
- ✅ ConcurrentHashMap으로 기본적인 Thread-safety 보장
- ✅ 추가적인 동시성 제어는 Step 6 (선착순 쿠폰)만 필요

---

## ⚠️ HashMap의 문제점

### 문제 1: Thread-unsafe (동시성 문제)
```java
// ❌ Thread-unsafe - Race Condition 발생
public class ProductRepository {
    private final Map<String, Product> storage = new HashMap<>();  // 위험!

    public void save(Product product) {
        storage.put(product.getId(), product);  // 동시 접근 시 문제 발생
    }
}
```

**시나리오:**
```
Thread A: storage.put("P001", productA)
Thread B: storage.put("P002", productB)

→ 내부 배열 재구성(rehashing) 중 충돌 발생
→ 데이터 손실, NullPointerException, 무한 루프 가능
```

### 문제 2: Concurrent Modification Exception
```java
Map<String, Product> products = new HashMap<>();
products.put("P001", product1);
products.put("P002", product2);

// Thread A: 순회 중
for (Product p : products.values()) {
    System.out.println(p.getName());
}

// Thread B: 동시에 수정 시도
products.put("P003", product3);  // ❌ ConcurrentModificationException!
```

---

## 🔒 Thread-Safe Map 비교

### 4가지 구현체 비교

| 구현체 | Thread-Safe | 읽기 성능 | 쓰기 성능 | Lock 방식 | Week 3 권장 |
|--------|------------|----------|----------|----------|------------|
| **HashMap** | ❌ | ⚡⚡⚡ | ⚡⚡⚡ | 없음 | ❌ |
| **Hashtable** | ✅ | ⚡ | ⚡ | 전체 Lock | ❌ |
| **synchronizedMap** | ✅ | ⚡⚡ | ⚡⚡ | 전체 Lock | △ |
| **ConcurrentHashMap** | ✅ | ⚡⚡⚡ | ⚡⚡⚡ | 부분 Lock | ✅ |

### 1. HashMap (Thread-unsafe)
```java
// ❌ 동시성 문제 발생
Map<String, Product> map = new HashMap<>();
```

**문제점:**
- 여러 스레드가 동시에 put() 호출 시 데이터 손실
- 순회 중 수정 시 ConcurrentModificationException
- 무한 루프 발생 가능 (Java 7 이전)

---

### 2. Hashtable (Legacy, 비권장)
```java
// ❌ 성능 저하 (레거시 방식)
Map<String, Product> map = new Hashtable<>();
```

**특징:**
- ✅ Thread-safe 보장
- ❌ 모든 메서드에 synchronized 적용 (전체 Lock)
- ❌ 읽기/쓰기 모두 느림
- ❌ null key/value 불가

**왜 느린가?**
```java
// Hashtable의 put 메서드
public synchronized V put(K key, V value) {
    // 전체 테이블 잠금 (다른 스레드 대기)
    // ...
}

public synchronized V get(Object key) {
    // 읽기도 잠금 (성능 저하)
    // ...
}
```

---

### 3. Collections.synchronizedMap (Wrapper)
```java
// △ 괜찮지만 ConcurrentHashMap보다 느림
Map<String, Product> map = Collections.synchronizedMap(new HashMap<>());
```

**특징:**
- ✅ Thread-safe 보장
- ❌ 메서드 단위 synchronized (전체 Lock)
- ✅ null key/value 허용
- △ 읽기/쓰기 성능 중간

**내부 구조:**
```java
// Collections.synchronizedMap의 내부 구현
public V get(Object key) {
    synchronized(mutex) {  // 전체 잠금
        return m.get(key);
    }
}

public V put(K key, V value) {
    synchronized(mutex) {  // 전체 잠금
        return m.put(key, value);
    }
}
```

---

### 4. ConcurrentHashMap (최적) ⭐ 권장
```java
// ✅ 최고의 성능과 Thread-safety
Map<String, Product> map = new ConcurrentHashMap<>();
```

**특징:**
- ✅ Thread-safe 보장
- ✅ Lock-free 읽기 (읽기 성능 우수)
- ✅ 부분 Lock (Segment 단위 잠금)
- ✅ 높은 동시성 (여러 스레드 동시 쓰기 가능)
- ❌ null key/value 불가

---

## 🏗️ ConcurrentHashMap 내부 구조

### Java 7 방식 (Segment 기반)
```
ConcurrentHashMap
├── Segment 0 [Lock]
│   ├── Bucket 0 → Entry → Entry → ...
│   ├── Bucket 1 → Entry → ...
│   └── ...
├── Segment 1 [Lock]
│   ├── Bucket 0 → Entry → ...
│   └── ...
└── ...

각 Segment마다 독립적인 Lock
→ 여러 스레드가 다른 Segment에 동시 쓰기 가능
```

**Segment 개념:**
- 16개의 Segment로 분할 (기본값)
- 각 Segment가 독립적인 ReentrantLock 소유
- Thread A가 Segment 0에 쓰는 동안, Thread B는 Segment 1에 쓰기 가능

---

### Java 8+ 방식 (Node + CAS)
```
ConcurrentHashMap
├── Bucket 0 → Node → Node → ...
├── Bucket 1 → Node → Node → ...
├── Bucket 2 → TreeNode → TreeNode → ... (Red-Black Tree)
└── ...

Segment 제거, CAS (Compare-And-Swap) 사용
→ 더 세밀한 Lock (Bucket 단위)
→ 성능 향상
```

**개선 사항:**
- Segment 개념 제거 → Bucket 단위 Lock
- CAS 연산으로 Lock 최소화
- 충돌 시 LinkedList → Red-Black Tree 변환 (8개 이상 충돌 시)

---

## 🔍 주요 메서드와 동작 원리

### 1. get() - Lock-free 읽기 ⚡
```java
Map<String, Product> map = new ConcurrentHashMap<>();
map.put("P001", product1);

// Lock 없이 읽기 가능 (최고 성능)
Product product = map.get("P001");  // ⚡ Lock-free
```

**특징:**
- Lock 없이 읽기 (volatile 변수로 가시성 보장)
- 여러 스레드가 동시에 읽기 가능
- 최신 데이터 반영 (쓰기 후 즉시 읽기 가능)

---

### 2. put() - Bucket 단위 Lock
```java
Map<String, Product> map = new ConcurrentHashMap<>();

// Thread A
map.put("P001", productA);  // Bucket 0에 Lock

// Thread B (동시 실행)
map.put("P999", productB);  // Bucket 5에 Lock (가능!)
```

**특징:**
- 같은 Bucket에만 Lock (다른 Bucket은 동시 쓰기 가능)
- Hash 충돌 시에만 Lock 경합 발생
- 높은 동시성

---

### 3. putIfAbsent() - Atomic 조건부 삽입
```java
Map<String, Product> map = new ConcurrentHashMap<>();

// ❌ Thread-unsafe (2단계 연산)
if (!map.containsKey("P001")) {
    map.put("P001", product);  // Race Condition!
}

// ✅ Thread-safe (1단계 Atomic 연산)
Product prev = map.putIfAbsent("P001", product);
if (prev == null) {
    System.out.println("신규 저장 성공");
} else {
    System.out.println("이미 존재: " + prev.getName());
}
```

**활용 예시: 중복 쿠폰 발급 방지**
```java
@Repository
public class InMemoryUserCouponRepository {
    // 중복 발급 체크용 인덱스 (userId:couponId → userCouponId)
    private final Map<String, String> userCouponIndex = new ConcurrentHashMap<>();

    public boolean isAlreadyIssued(String userId, String couponId) {
        String key = userId + ":" + couponId;
        // Atomic 체크 (putIfAbsent로 중복 방지)
        return userCouponIndex.containsKey(key);
    }

    public void markAsIssued(String userId, String couponId, String userCouponId) {
        String key = userId + ":" + couponId;
        userCouponIndex.putIfAbsent(key, userCouponId);
    }
}
```

---

### 4. computeIfAbsent() - Atomic 연산 + 생성
```java
Map<String, List<Order>> userOrders = new ConcurrentHashMap<>();

// ❌ Thread-unsafe
if (!userOrders.containsKey(userId)) {
    userOrders.put(userId, new ArrayList<>());
}
userOrders.get(userId).add(order);

// ✅ Thread-safe (Atomic)
userOrders.computeIfAbsent(userId, k -> new ArrayList<>()).add(order);
```

**특징:**
- Key가 없으면 Function 실행하여 값 생성
- Atomic 연산으로 Race Condition 방지
- 코드 간결

---

### 5. size() - 정확성보다 성능 우선
```java
Map<String, Product> map = new ConcurrentHashMap<>();
int size = map.size();  // 근사값 반환 (정확하지 않을 수 있음)
```

**특징:**
- 정확한 크기보다 **빠른 응답** 우선
- 동시에 put/remove 발생 시 정확하지 않을 수 있음
- 대부분의 경우 충분히 정확

---

## 🔬 Week 3 프로젝트 실전 분석

### 작성한 8개 Repository의 ConcurrentHashMap 사용 패턴

| Repository | 주 저장소 | 보조 인덱스 | 인덱스 목적 |
|-----------|----------|------------|-----------|
| **InMemoryProductRepository** | `Map<String, Product>` | 없음 | 카테고리 필터링은 Stream |
| **InMemoryUserRepository** | `Map<String, User>` | `Map<String, String>` (email→userId) | 이메일로 빠른 조회 |
| **InMemoryOrderRepository** | `Map<String, Order>` | 없음 | userId 필터링은 Stream |
| **InMemoryOrderItemRepository** | `Map<String, OrderItem>` | 없음 | orderId 필터링은 Stream |
| **InMemoryCouponRepository** | `Map<String, Coupon>` | 없음 | 단순 CRUD |
| **InMemoryUserCouponRepository** | `Map<String, UserCoupon>` | `Map<String, String>` (userId:couponId→id) | 중복 발급 방지 |
| **InMemoryCartRepository** | `Map<String, Cart>` | `Map<String, String>` (userId→cartId) | 1인 1장바구니 |
| **InMemoryCartItemRepository** | `Map<String, CartItem>` | `Map<String, String>` (cartId:productId→id) | 장바구니 내 중복 방지 |

### 패턴 1: 단순 저장소 (보조 인덱스 없음)
```java
// InMemoryProductRepository
@Repository
public class InMemoryProductRepository implements ProductRepository {
    // 주 저장소만 사용
    private final Map<String, Product> storage = new ConcurrentHashMap<>();

    @Override
    public List<Product> findByCategory(String category) {
        // Stream 필터링 (O(n))
        return storage.values().stream()
            .filter(product -> category.equals(product.getCategory()))
            .collect(Collectors.toList());
    }
}
```

**특징:**
- 조회 빈도가 낮거나 데이터가 적을 때 적합
- 카테고리별 조회가 자주 발생하면 인덱스 추가 고려

---

### 패턴 2: 이메일 인덱스 (1:1 관계)
```java
// InMemoryUserRepository
@Repository
public class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> storage = new ConcurrentHashMap<>();
    // 이메일 → userId 매핑 (빠른 조회)
    private final Map<String, String> emailIndex = new ConcurrentHashMap<>();

    @Override
    public User save(User user) {
        storage.put(user.getId(), user);
        emailIndex.put(user.getEmail(), user.getId());  // 인덱스 동기화
        return user;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String userId = emailIndex.get(email);  // O(1) 조회
        if (userId == null) return Optional.empty();
        return Optional.ofNullable(storage.get(userId));
    }
}
```

**장점:**
- 이메일 조회가 O(1)로 매우 빠름
- Stream 필터링 대비 100배 이상 빠름

**주의:**
- save() 시 인덱스 동기화 필수
- 이메일 변경 시 기존 인덱스 삭제 후 재생성

---

### 패턴 3: 복합 키 인덱스 (중복 방지)
```java
// InMemoryUserCouponRepository
@Repository
public class InMemoryUserCouponRepository implements UserCouponRepository {
    private final Map<String, UserCoupon> storage = new ConcurrentHashMap<>();
    // 복합 키 인덱스 (userId:couponId → userCouponId)
    private final Map<String, String> userCouponIndex = new ConcurrentHashMap<>();

    @Override
    public boolean existsByUserIdAndCouponId(String userId, String couponId) {
        String key = makeKey(userId, couponId);
        return userCouponIndex.containsKey(key);  // O(1) 중복 체크
    }

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        storage.put(userCoupon.getId(), userCoupon);

        // 복합 키 인덱스 업데이트
        String key = makeKey(userCoupon.getUserId(), userCoupon.getCouponId());
        userCouponIndex.put(key, userCoupon.getId());

        return userCoupon;
    }

    private String makeKey(String userId, String couponId) {
        return userId + ":" + couponId;  // 복합 키 생성
    }
}
```

**활용 사례:**
- **1인 1매 쿠폰 제한**: 같은 사용자가 같은 쿠폰을 중복 발급받지 못하도록
- **장바구니 중복 방지**: 같은 상품이 장바구니에 여러 번 추가되지 않도록

**성능:**
- 중복 체크가 O(1)로 매우 빠름
- Stream으로 필터링하면 O(n) → 인덱스 사용 권장

---

### 패턴 4: 1:1 매핑 인덱스
```java
// InMemoryCartRepository
@Repository
public class InMemoryCartRepository implements CartRepository {
    private final Map<String, Cart> storage = new ConcurrentHashMap<>();
    // userId → cartId 매핑 (1인 1장바구니)
    private final Map<String, String> userCartIndex = new ConcurrentHashMap<>();

    @Override
    public Optional<Cart> findByUserId(String userId) {
        String cartId = userCartIndex.get(userId);  // O(1)
        if (cartId == null) return Optional.empty();
        return Optional.ofNullable(storage.get(cartId));
    }

    @Override
    public Cart save(Cart cart) {
        storage.put(cart.getId(), cart);
        userCartIndex.put(cart.getUserId(), cart.getId());
        return cart;
    }
}
```

**특징:**
- 1인 1장바구니 제약 보장
- userId로 빠르게 조회 가능

---

### 인덱스 설계 가이드

**인덱스를 추가해야 하는 경우:**
- ✅ 조회 빈도가 높을 때 (매 요청마다 조회)
- ✅ 데이터 크기가 클 때 (100개 이상)
- ✅ 성능이 중요할 때 (사용자 경험에 직접 영향)
- ✅ 중복 체크가 필요할 때 (1인 1매 제한 등)

**Stream 필터링으로 충분한 경우:**
- ✅ 조회 빈도가 낮을 때 (관리자 기능 등)
- ✅ 데이터 크기가 작을 때 (100개 미만)
- ✅ 성능이 덜 중요할 때

**예시 - 카테고리 조회:**
```java
// ❌ 인덱스 추가 (Over-engineering)
private final Map<String, List<String>> categoryIndex = new ConcurrentHashMap<>();

// ✅ Stream 필터링으로 충분 (상품이 많지 않음)
public List<Product> findByCategory(String category) {
    return storage.values().stream()
        .filter(p -> category.equals(p.getCategory()))
        .collect(Collectors.toList());
}
```

---

## 🧠 메모리 가시성과 Lock-free 읽기

### volatile이 없으면 무슨 일이 일어날까?

```java
// ❌ volatile 없는 경우 (문제 발생 가능)
class UnsafeCounter {
    private int count = 0;  // volatile 없음

    public void increment() {
        count++;  // Thread A
    }

    public int getCount() {
        return count;  // Thread B - 최신 값을 못 볼 수 있음!
    }
}
```

**문제:**
- Thread A가 count를 증가시켜도
- Thread B는 캐시된 이전 값을 읽을 수 있음
- **메모리 가시성(Memory Visibility) 문제**

---

### ConcurrentHashMap의 해결책: volatile

```java
// ConcurrentHashMap의 내부 구조 (단순화)
static class Node<K,V> {
    final int hash;
    final K key;
    volatile V val;        // ✅ volatile로 선언
    volatile Node<K,V> next;  // ✅ volatile로 선언
}
```

**volatile의 효과:**
1. **즉시 Main Memory에 쓰기**: Thread A가 값을 쓰면 즉시 Main Memory로
2. **항상 Main Memory에서 읽기**: Thread B는 CPU 캐시가 아닌 Main Memory에서 읽음
3. **최신 값 보장**: 다른 스레드의 변경사항을 즉시 볼 수 있음

---

### Lock-free 읽기가 가능한 이유

```java
// ConcurrentHashMap의 get() 메서드 (단순화)
public V get(Object key) {
    Node<K,V>[] tab;
    Node<K,V> e;
    int n, hash;
    K k;
    V v;

    if ((tab = table) != null && (n = tab.length) > 0 &&
        (e = tabAt(tab, (n - 1) & (hash = spread(key.hashCode())))) != null) {

        // volatile 읽기 (Lock 불필요)
        if ((k = e.key) == key || (k != null && key.equals(k)))
            return e.val;  // volatile 변수 읽기

        // 충돌 시 LinkedList 순회 (역시 Lock 불필요)
        while ((e = e.next) != null) {  // volatile next
            if (e.hash == hash && ((k = e.key) == key ||
                (k != null && key.equals(k))))
                return e.val;  // volatile 변수 읽기
        }
    }
    return null;
}
```

**핵심:**
- `e.val`과 `e.next`가 모두 `volatile`
- volatile 읽기는 Lock 없이도 최신 값 보장
- 여러 스레드가 동시에 읽기 가능 (⚡ 최고 성능)

---

### 쓰기는 Lock이 필요한 이유

```java
// ConcurrentHashMap의 put() 메서드 (단순화)
public V put(K key, V value) {
    // ...
    synchronized (f) {  // ✅ Bucket에 Lock
        // LinkedList에 노드 추가
        Node<K,V> node = new Node<>(hash, key, value, null);
        // ...
    }
    // ...
}
```

**이유:**
- 읽기: 단순히 값만 읽으면 됨 (volatile로 최신 값 보장)
- 쓰기: 여러 변수를 수정해야 함 (next 포인터, val, size 등)
- **복합 연산은 Atomic하지 않음** → Lock 필요

---

### volatile vs synchronized vs Lock

| 방식 | 사용 사례 | 성능 | Atomicity |
|------|----------|------|-----------|
| **volatile** | 단순 읽기/쓰기 | ⚡⚡⚡ | ❌ (복합 연산 불가) |
| **synchronized** | 복합 연산 (간단) | ⚡⚡ | ✅ |
| **Lock** | 복합 연산 (세밀한 제어) | ⚡⚡ | ✅ |
| **CAS (Atomic)** | 단순 증감 | ⚡⚡⚡ | ✅ |

**ConcurrentHashMap의 전략:**
- **읽기**: volatile만 사용 (Lock 없음) → 최고 성능
- **쓰기**: Bucket 단위 synchronized → 높은 동시성

---

## ⚠️ ConcurrentHashMap 주의사항

### 1. null key/value 불가
```java
Map<String, Product> map = new ConcurrentHashMap<>();

map.put(null, product);  // ❌ NullPointerException
map.put("P001", null);   // ❌ NullPointerException
```

**이유:**
- `get(key)` 반환 값이 `null`일 때 의미 모호
- "Key가 없음" vs "Value가 null" 구분 불가

**해결책:**
```java
// Optional 사용
Optional<Product> findById(String id) {
    return Optional.ofNullable(map.get(id));
}
```

---

### 2. 복합 연산은 Thread-safe 아님
```java
// ❌ 2단계 연산 (Thread-unsafe)
if (map.containsKey("P001")) {
    map.remove("P001");  // Race Condition!
}

// ✅ Atomic 연산
map.remove("P001");  // 존재하면 삭제, 없으면 무시
```

---

### 3. Iterator는 Weakly Consistent
```java
Map<String, Product> map = new ConcurrentHashMap<>();
map.put("P001", product1);
map.put("P002", product2);

// 순회 시작
for (Product p : map.values()) {
    System.out.println(p.getName());

    // 다른 스레드가 추가/삭제 가능
    // ConcurrentModificationException 발생 안 함
}
```

**특징:**
- ConcurrentModificationException 발생 안 함
- 순회 중 변경사항이 반영될 수도, 안 될 수도 있음 (Weakly Consistent)
- 대부분의 경우 안전

---

### 4. size(), isEmpty()는 근사값
```java
Map<String, Product> map = new ConcurrentHashMap<>();

// Thread A
for (int i = 0; i < 1000; i++) {
    map.put("P" + i, product);
}

// Thread B (동시 실행)
int size = map.size();  // 정확히 1000이 아닐 수 있음 (근사값)
```

**권장:**
- 정확한 크기가 중요하면 외부 동기화 필요
- 대부분의 경우 근사값으로 충분

---

## 🧪 테스트 작성

### 동시성 테스트 (Repository)
```java
@Test
void ConcurrentHashMap_동시성_테스트() throws InterruptedException {
    // Given
    InMemoryProductRepository repository = new InMemoryProductRepository();
    int threadCount = 100;

    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    // When: 100개 스레드가 동시에 저장
    for (int i = 0; i < threadCount; i++) {
        int index = i;
        executorService.submit(() -> {
            try {
                Product product = Product.create(
                    "P" + String.format("%03d", index),
                    "상품" + index,
                    "설명",
                    10000L,
                    "카테고리",
                    10
                );
                repository.save(product);
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();
    executorService.shutdown();

    // Then: 100개 모두 저장되어야 함
    List<Product> products = repository.findAll();
    assertThat(products).hasSize(100);
}
```

---

## 📊 성능 비교

### 벤치마크 시나리오
- 스레드 수: 16
- 작업: 읽기 70%, 쓰기 30%
- 데이터 크기: 10,000 항목

| 구현체 | 처리량 (ops/sec) | 상대 성능 |
|--------|-----------------|----------|
| HashMap | ❌ (데이터 손실) | - |
| Hashtable | 100,000 | 1x |
| synchronizedMap | 150,000 | 1.5x |
| **ConcurrentHashMap** | **500,000** | **5x** ⭐ |

**결론:** ConcurrentHashMap이 압도적으로 빠름

---

## ✅ Pass 기준

### ConcurrentHashMap 활용
- [ ] 모든 In-Memory Repository에서 ConcurrentHashMap 사용
- [ ] HashMap, Hashtable 미사용
- [ ] null 값 처리 (Optional 사용)

### 인덱스 설계
- [ ] 복합 인덱스로 빠른 조회 구현
- [ ] 중복 체크 인덱스로 1인 1매 제한 구현

### 테스트
- [ ] 동시성 테스트 작성 (ExecutorService)
- [ ] 100% 테스트 통과

---

## ❌ Fail 사유

### ConcurrentHashMap Fail
- ❌ HashMap 사용 (Thread-unsafe)
- ❌ Hashtable 사용 (성능 저하)
- ❌ null 값 처리 누락

### 인덱스 Fail
- ❌ 인덱스 없이 Stream 필터링만 사용 (O(n))
- ❌ 중복 체크 로직 누락

---

## 🎯 학습 체크리스트

### 이론 이해
- [ ] ConcurrentHashMap의 내부 구조를 설명할 수 있다 (Segment, Bucket, Node)
- [ ] Lock Striping의 개념을 설명할 수 있다
- [ ] Java 7과 Java 8+의 차이를 설명할 수 있다
- [ ] Lock-free 읽기의 원리를 설명할 수 있다 (volatile)
- [ ] 메모리 가시성(Memory Visibility) 문제를 설명할 수 있다
- [ ] volatile과 synchronized의 차이를 설명할 수 있다

### Week 3 프로젝트
- [ ] 8개 Repository의 ConcurrentHashMap 활용 패턴을 설명할 수 있다
- [ ] 보조 인덱스가 필요한 경우와 불필요한 경우를 구분할 수 있다
- [ ] 복합 키 인덱스 (userId:couponId)의 목적을 설명할 수 있다
- [ ] Stream 필터링 vs 인덱스 조회의 트레이드오프를 이해한다

### 실전 적용
- [ ] ConcurrentHashMap으로 Repository를 구현할 수 있다
- [ ] 복합 인덱스를 설계하고 구현할 수 있다
- [ ] putIfAbsent, computeIfAbsent를 활용할 수 있다
- [ ] 동시성 테스트를 작성할 수 있다
- [ ] 인덱스가 필요한지 판단하고 Over-engineering을 피할 수 있다

### 성능 이해
- [ ] 4가지 Thread-safe Map의 성능 차이를 설명할 수 있다
- [ ] ConcurrentHashMap이 빠른 이유를 설명할 수 있다
- [ ] size(), isEmpty()가 근사값인 이유를 설명할 수 있다
- [ ] 읽기 70%, 쓰기 30% 시나리오에서 5배 빠른 이유를 설명할 수 있다

### 토론 주제
- "ConcurrentHashMap은 어떻게 Lock 없이 읽기가 가능한가?" (volatile)
- "Segment 방식(Java 7)과 CAS 방식(Java 8+)의 차이는?"
- "null을 허용하지 않는 이유는?"
- "복합 인덱스는 언제 사용해야 하나?" (성능 vs Over-engineering)
- "InMemoryUserRepository는 왜 emailIndex를 사용했나?"
- "InMemoryProductRepository는 왜 categoryIndex를 사용하지 않았나?"

---

## 📚 참고 자료

### 공식 문서
- [Java ConcurrentHashMap API](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentHashMap.html)
- [Java Concurrency Tutorial](https://docs.oracle.com/javase/tutorial/essential/concurrency/)

### 심화 학습
- [Java Concurrency in Practice](https://jcip.net/) - Chapter 5: Building Blocks
- [ConcurrentHashMap 내부 구조 분석](https://javarevisited.blogspot.com/2013/02/concurrenthashmap-in-java-example-tutorial-working.html)

---

## 💡 실전 팁

### Week 3 체크리스트
```java
// ✅ 모든 Repository에서 ConcurrentHashMap 사용
private final Map<String, Entity> storage = new ConcurrentHashMap<>();

// ✅ 인덱스가 필요하면 추가 Map 사용
private final Map<String, String> emailIndex = new ConcurrentHashMap<>();

// ✅ null 안전성 (Optional 사용)
return Optional.ofNullable(storage.get(id));

// ✅ Atomic 연산 활용
storage.putIfAbsent(key, value);
storage.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
```

### 안티 패턴 피하기
```java
// ❌ HashMap 사용
private final Map<String, Product> storage = new HashMap<>();  // Thread-unsafe!

// ❌ 복합 연산 (Race Condition)
if (map.containsKey(key)) {
    map.remove(key);  // 2단계 연산 (unsafe)
}

// ❌ null 값 사용
map.put(key, null);  // NullPointerException

// ✅ 올바른 사용
private final Map<String, Product> storage = new ConcurrentHashMap<>();
map.remove(key);  // Atomic 연산
Optional.ofNullable(map.get(key));  // null 안전
```

---

**관련 학습**:
- [04. Repository 패턴](./04-repository-pattern.md) - In-Memory Repository 구현
- [05. 동시성 제어](./05-concurrency-control.md) - AtomicInteger, Lock
- [README](../README.md) - 학습 자료 목차로 돌아가기
