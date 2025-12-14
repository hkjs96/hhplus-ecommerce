# PaymentEventIntegrationTest 코드 역설계 계획서

## 📅 작성일: 2025-12-14

---

## 🔍 **진짜 근본 원인 발견 과정**

### 1차 가설: @DistributedLock AOP 실패
**검증 방법**: CreateOrderUseCase.java에서 @DistributedLock 주석 처리
**결과**: ❌ **여전히 400 에러 발생** → DistributedLock은 원인이 아님

### 2차 가설: TransactionTemplate 람다 캡처 문제
**검증 방법**: setUp에서 TransactionTemplate.execute() 리턴값 확인
**결과**: ❌ **`result[0]: null`, `result[1]: null`** → 람다 캡처가 아닌 다른 문제

### 3차 가설 (최종): **JPA save() 후 ID가 생성되지 않음**
**원인**:
```java
User savedUser = userRepository.save(user);
// savedUser.getId() → null !!!

// @GeneratedValue(strategy = GenerationType.IDENTITY)를 사용하는 경우
// flush() 없이는 ID가 할당되지 않을 수 있음
```

**검증**:
```
===== TransactionTemplate.execute() 리턴값 =====
result: [Ljava.lang.Object;@2644facd   ← 배열 자체는 정상
result.length: 2                       ← 길이도 2
result[0]: null                        ← ID가 null!!
result[1]: null                        ← ID가 null!!
```

**시도한 해결책**:
1. `entityManager.flush()` 추가 → `TransactionRequiredException` 발생
2. `saveAndFlush()` 사용 → `cannot find symbol` (UserRepository에 없음)

---

## 🚨 **추가로 발견된 문제**

### 문제 1: @DirtiesContext(methodMode = AFTER_METHOD)
- **효과**: 각 테스트 메서드마다 ApplicationContext 재시작
- **부작용**: setUp에서 저장한 데이터가 Context 재시작으로 사라질 가능성
- **대안**: ClassMode.AFTER_CLASS로 변경하거나 완전 제거

### 문제 2: AFTER_COMMIT 이벤트 검증과 데이터 준비의 충돌
- **요구사항**: 테스트 메서드는 `@Transactional 없음` (AFTER_COMMIT 검증)
- **충돌**: setUp에서 데이터 저장하려면 트랜잭션 필요
- **현재 상황**: TransactionTemplate 사용해도 ID 생성 실패

### 문제 3: UserRepository/ProductRepository에 saveAndFlush() 없음
- JpaRepository를 extends하지 않거나,
- Custom Repository 인터페이스만 정의된 상태

---

## 💡 **해결 방안 (3가지 접근)**

### Option 1: @Sql 스크립트 사용 (권장) ✅
**장점**: 명확한 데이터 준비, 트랜잭션 독립
**단점**: SQL 파일 관리 필요

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/test-data-payment-event.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PaymentEventIntegrationTest {
    // setUp() 제거, SQL로 데이터 준비
}
```

**test-data-payment-event.sql**:
```sql
INSERT INTO users (id, email, name, balance, created_at, updated_at)
VALUES (999, 'test@example.com', '테스트유저', 1000000, NOW(), NOW());

INSERT INTO products (id, code, name, description, price, category, stock, created_at, updated_at)
VALUES (888, 'P-TEST-001', '테스트상품', '테스트 상품 설명', 10000, '전자제품', 100, NOW(), NOW());
```

---

### Option 2: @Transactional setUp + DB 직접 접근
**장점**: Java 코드로 데이터 준비
**단점**: 여전히 ID 생성 문제 가능성

```java
@Autowired
private JdbcTemplate jdbcTemplate;

@BeforeEach
@Transactional(propagation = Propagation.REQUIRES_NEW)
void setUp() {
    // JDBC로 직접 INSERT
    jdbcTemplate.update(
        "INSERT INTO users (id, email, name, balance, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
        999L, "test@example.com", "테스트유저", 1000000L
    );

    testUserId = 999L;
    testProductId = 888L;
}
```

---

### Option 3: Repository 인터페이스 수정 (근본 해결)
**장점**: saveAndFlush() 사용 가능
**단점**: 도메인 코드 수정 필요

```java
// UserRepository.java
public interface UserRepository extends JpaRepository<User, Long> {
    User findByIdOrThrow(Long id);
}

// PaymentEventIntegrationTest.java
@BeforeEach
@Transactional
void setUp() {
    User user = User.create("test@example.com", "테스트유저");
    user.charge(1_000_000L);
    User savedUser = userRepository.saveAndFlush(user);  // 이제 가능!
    testUserId = savedUser.getId();
}
```

---

## 🎯 **권장 실행 계획**

### Phase 1: 즉시 적용 (30분)
1. ✅ **Option 1 선택**: @Sql 스크립트로 데이터 준비
2. PaymentEventIntegrationTest에서 다음 수정:
   - setUp() 메서드 제거
   - @Sql 어노테이션 추가
   - testUserId/testProductId를 고정값으로 변경
3. 테스트 실행하여 400 에러 해결 확인

### Phase 2: 다른 통합 테스트도 동일 패턴 적용 (2시간)
- CartControllerIntegrationTest
- CouponControllerIntegrationTest
- OrderControllerIntegrationTest
- UserControllerIntegrationTest
- ProductControllerIntegrationTest

### Phase 3: 전체 빌드 및 성공률 확인 (30분)
```bash
./gradlew clean build
```
**목표**: 81개 실패 → 10개 이하로 감소 (85%+ 성공률)

---

## 📊 **예상 결과**

| 항목 | 현재 | Phase 1 후 | Phase 2 후 | Phase 3 후 |
|------|------|-----------|-----------|-----------|
| **PaymentEventIntegrationTest** | 5/5 실패 | 5/5 성공 | 5/5 성공 | 5/5 성공 |
| **전체 성공률** | 60.5% | 62% | 75% | 85%+ |
| **주요 실패 원인** | setUp 데이터 없음 | - | Context 공유 | - |

---

## 📝 **발견된 근본 문제 Summary**

1. **@DistributedLock은 원인이 아님** (주석 처리해도 400 발생)
2. **진짜 원인**: `userRepository.save(user).getId()` → `null`
   - JPA가 save() 직후 ID를 할당하지 않음 (flush 필요)
   - TransactionTemplate 안에서도 동일 문제 발생
3. **해결책**: @Sql 스크립트로 고정 ID 사용하거나, Repository를 JpaRepository로 변경

---

## ✅ **다음 단계**

1. **즉시 실행**: Option 1 (@Sql) 적용
2. **검증**: `./gradlew test --tests PaymentEventIntegrationTest`
3. **확산**: 다른 Controller Integration Test에도 적용
4. **최종 목표**: 85%+ 테스트 성공률 달성

---

**작성일**: 2025-12-14
**작성자**: Claude Code
**상태**: Ready to Execute
