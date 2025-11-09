# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot e-commerce reference project for the Hanghe Plus backend curriculum (항해플러스 백엔드 커리큘럼). It's a Java-based application using Spring Boot 3.5.7 with Gradle as the build tool.

**Current Phase:** Week 4 - Database Integration (Step 7-8)

**핵심 목표**: JPA 기반 데이터베이스 통합 및 트랜잭션 관리

---

## 📊 Implementation Progress

### Phase 1: Documentation & Design ✅ (Week 2)
- ✅ step1-2: ERD, Sequence Diagrams, API Specification, Requirements
- ✅ step3: Infrastructure + Core Controllers (Product, Cart, Order)
- ✅ step4: Additional Controllers (Coupon, User)
- **Status**: 15 API endpoints with Mock data

### Phase 2: Layered Architecture Implementation ✅ (Week 3)
- ✅ **step5**: Domain & Application Layer (Entity, UseCase, In-Memory Repository)
- ✅ **step6**: Concurrency Control & Integration Testing
- **Status**: 94% test coverage, layered architecture complete

### Phase 3: Database Integration 🚧 (Week 4 - 현재)
- 🚧 **step7-8**: JPA Entity, Spring Data JPA, Transaction Management
- **Status**: In Progress

---

## Technology Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.5.7
- **Build Tool**: Gradle
- **Architecture**: Layered Architecture (4-Layer)
- **Data Storage**: In-Memory (Week 3) → **Database (Week 4+)**
- **ORM**: Spring Data JPA, Hibernate (Week 4+)
- **Testing**: JUnit 5, Mockito, AssertJ

---

## 📚 Documentation Structure

When you receive a task, **first check the relevant documentation** before starting implementation.

### Available Commands (Slash Commands)

| Command | Description | When to Use |
|---------|-------------|-------------|
| `/architecture` | Layered Architecture, Best Practices | 레이어 구조, Repository 패턴 질문 시 |
| `/concurrency` | Concurrency Control (synchronized, ReentrantLock, CAS) | 동시성 제어 구현 시 |
| `/testing` | Test Strategy, Coverage, Isolation | 테스트 작성 및 품질 개선 시 |

### Available Documentation Files

| File Path | Content | When to Reference |
|-----------|---------|-------------------|
| `@.claude/commands/architecture.md` | Layered Architecture 상세 설명 | Repository 패턴, 검증 레이어 분리 |
| `@.claude/commands/concurrency.md` | 동시성 제어 패턴 비교 | synchronized vs ReentrantLock vs CAS |
| `@.claude/commands/testing.md` | 테스트 전략 및 품질 | F.I.R.S.T 원칙, Test Isolation |
| `@docs/week2/` | Week 2 문서 (ERD, Sequence, API) | API 명세, 요구사항 확인 |
| `@docs/week3/` | Week 3 분석 문서 | Layered Architecture 구현 분석 |

---

## 🚩 Current Task: Week 4 - Database Integration

### 과제 목표
1. **JPA Entity 구현**: Week 3 도메인 모델을 JPA Entity로 전환
2. **Spring Data JPA Repository**: JpaRepository 활용, In-Memory 제거
3. **Transaction Management**: @Transactional 적용
4. **Database 연동**: H2 (Development), MySQL (Production - optional)

### Pass 조건
- [ ] JPA Entity 변환 (비즈니스 로직 유지)
- [ ] Spring Data JPA Repository 활용
- [ ] @Transactional 적절히 적용
- [ ] In-Memory Repository 제거
- [ ] 테스트 커버리지 70% 이상 유지

### Fail 사유
- ❌ In-Memory 유지 (JPA 미사용)
- ❌ Entity에서 비즈니스 로직 제거 (Anemic Domain Model)
- ❌ @Transactional 부재 또는 잘못된 위치 적용

---

## 🎯 Implementation Quick Guide

### 1. JPA Entity 전환

```java
// Week 3: 순수 Java 클래스
public class Product {
    private String id;
    private String name;
    private Integer stock;

    public void decreaseStock(int quantity) { /* 비즈니스 로직 */ }
}

// Week 4: JPA Entity (비즈니스 로직 유지!)
@Entity
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer stock;

    public void decreaseStock(int quantity) { /* 비즈니스 로직 유지 */ }
}
```

### 2. Spring Data JPA Repository

```java
// Week 3: InMemoryRepository 구현체
@Repository
public class InMemoryProductRepository implements ProductRepository {
    private final Map<String, Product> storage = new ConcurrentHashMap<>();
    // 직접 구현...
}

// Week 4: JpaRepository 상속 (구현체 불필요)
@Repository
public interface JpaProductRepository extends JpaRepository<Product, Long>, ProductRepository {
    List<Product> findByCategory(String category);  // 메서드 네이밍 쿼리
}
```

### 3. Transaction Management

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 기본 readOnly
public class OrderUseCase {

    @Transactional  // 쓰기 작업은 readOnly=false
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 트랜잭션 내에서 Entity 변경 시 자동 UPDATE (Dirty Checking)
    }

    // 조회 전용 메서드는 기본값(readOnly=true) 사용
    public OrderResponse getOrder(Long orderId) { /* ... */ }
}
```

---

## 📖 How to Use This Guide

### When starting a new task:

1. **Read the user request carefully**
2. **Check if relevant documentation exists**:
   - Use `/architecture` for layered architecture questions
   - Use `/concurrency` for concurrency control implementation
   - Use `/testing` for test writing
   - Read `@.claude/commands/*.md` files for detailed guidance
   - Read `@docs/week*/` for requirements and specifications

3. **Ask for clarification if needed**:
   - "어떤 문서를 참조해야 할까요?"
   - "Week 2 API 명세를 확인해야 하나요?"
   - "동시성 제어 방식을 선택해야 하나요?"

4. **Execute the task** using the guidance from documentation

### When receiving unclear instructions:

**Always ask the user**:
- "어떤 작업을 수행해야 하나요?"
- "Week 몇 차 작업인가요?"
- "참조할 문서가 있나요? (@.claude/, @docs/, 또는 slash command)"

---

## ✅ Week 4 Implementation Checklist

### JPA Entity
- [ ] Product, User, Order, OrderItem Entity 변환
- [ ] Cart, CartItem Entity 변환
- [ ] Coupon, UserCoupon Entity 변환
- [ ] 비즈니스 로직 메서드 유지

### Spring Data JPA Repository
- [ ] JpaRepository 상속
- [ ] 커스텀 쿼리 메서드 작성
- [ ] InMemory Repository 제거

### Database Configuration
- [ ] application.yml 설정 (H2)
- [ ] 초기 데이터 로딩 (ApplicationRunner)

### Transaction Management
- [ ] UseCase에 @Transactional 적용
- [ ] 읽기 전용 메서드 readOnly=true

### Testing
- [ ] Repository 테스트 (@DataJpaTest)
- [ ] 통합 테스트 (@SpringBootTest)
- [ ] 테스트 커버리지 70% 이상 유지

---

## 🔍 Common Pitfalls to Avoid

### JPA
- ❌ Entity를 단순 데이터 객체로 사용 (비즈니스 로직 제거)
- ✅ Week 3의 비즈니스 로직 메서드를 그대로 유지

### Transaction
- ❌ Controller나 Entity에 @Transactional 적용
- ✅ UseCase(Application Layer)에만 @Transactional 적용

### N+1 Problem
- ❌ 지연 로딩으로 인한 N+1 문제
- ✅ Fetch Join, @EntityGraph, Batch Size 설정

---

## 🛠️ Development Commands

```bash
# Build
./gradlew build

# Run
./gradlew bootRun

# Test with coverage
./gradlew test jacocoTestReport

# H2 Console (Development)
http://localhost:8080/h2-console
```

---

## 📝 Next Steps

1. **Week 5**: 외부 API 연동, Async/Fallback, 인기 상품 배치
2. **Week 6**: 캐싱, 인덱스 최적화, 부하 테스트
3. **Week 7**: Docker, CI/CD, 모니터링

---

## Configuration

Application configuration is in `src/main/resources/application.yml`.

### Key Configurations
- **Database**: H2 (Development), MySQL (Production)
- **JPA**: ddl-auto, show-sql, format_sql
- **Logging**: SQL, Parameter binding
