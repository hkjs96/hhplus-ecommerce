# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot e-commerce reference project for the Hanghe Plus backend curriculum (항해플러스 백엔드 커리큘럼). It's a Java-based application using Spring Boot 3.5.7 with Gradle as the build tool.

**Current Phase:** Week 2 - API Design and System Architecture (설계 단계)

**핵심 목표**: 애플리케이션 레벨에서 가용성을 보장하는 이커머스 시스템 설계

---

## Technology Stack

### 현재 설계 단계 (Week 2)
- **Language**: Java 17
- **Framework**: Spring Boot 3.5.7
- **Build Tool**: Gradle
- **Architecture**: Layered Architecture (Domain, Application, Infrastructure, Presentation)

### 설계에 포함되는 기술
- **Database**: H2 (Dev) / MySQL (Prod)
- **Cache**: Redis (설계 포함, 향후 구현)
- **Message Queue**: Kafka (설계 포함, 향후 구현)
- **Distributed Lock**: Redis Lock (설계 포함, 향후 구현)

### Key Dependencies
- Spring Boot Starter (Web, JPA, Validation, Cache)
- Lombok
- H2 Database (Dev) / MySQL (Prod)
- JUnit 5 (Testing)

---

## 📋 Week 2 Assignment: API Design & System Architecture

### Assignment Objectives
1. **API Design**: Design RESTful APIs following best practices
2. **ERD**: Create Entity Relationship Diagrams for database design
3. **Sequence Diagrams**: Document process flows and interactions
4. **Application-Level Availability**: Design resilience patterns
5. **Mock API**: Implement working API with Spring Boot (No db.json)

> ⚠️ **중요**: Week 2는 **설계 단계**입니다. 완전한 시스템 설계를 목표로 하며, Mock 서버는 In-Memory로 구현합니다.

### Core Requirements (Basic Assignment)

#### 1. Product Management 📦
- Product list/detail inquiry (price, stock)
- Real-time stock verification
- **Popular products statistics** (last 3 days, Top 5)
  - ⚠️ NOT real-time - **Batch aggregation** (every 5 minutes)
  - Fallback to cached data if batch fails

#### 2. Order/Payment System 💳
- Create order
- Stock verification and deduction
- **Balance-based payment**
- Coupon discount application

#### 3. Coupon System (First-Come-First-Served) 🎟️
- Limited quantity issuance
- Coupon validation
- Usage history management
- **Critical**: One coupon per user limit

#### 4. External Data Platform Integration 🔗
- Send order data (after order completion)
- Data transformation (internal → external format)
- Failure handling (retry queue)

---

### Extended Requirements (Advanced) ✨

#### 5. Cart System 🛒 NEW
- Add/update/delete cart items
- View cart (per user)
- Convert selected items to order
- Display out-of-stock items
- Validate cart items before order

#### 6. Shipping Management 📮 NEW
- Input shipping address (at order)
- Manage shipping status
  - PENDING → PREPARING → SHIPPED → DELIVERED
- Query shipping info per order
- Update shipping status (admin)

#### 7. Order History 📋 NEW
- List orders per user
- View order details
- Filter by order status

#### 8. Payment Extension 💰 NEW (Optional)
- **TossPay test API integration**

#### 9. Additional External Integrations 🔗 NEW (Optional)
- Shipping tracker API (CJ, Korea Post)
- Notification service (Email/SMS)
- Warehouse management system (WMS)
- Settlement system (sales data)

---

## 🎯 Application-Level Availability

### Definition
> External/internal component failures or slowdowns should NOT interrupt **core use cases**. Design, implement, and operate resilience at the application level.

---

### Basic Patterns (Required)

#### 1. Timeout & Retry 🔄
- Set timeout for ALL external calls (3 seconds)
- Retry on failure (max 3 times, Exponential Backoff)
- Immediately fail on non-retryable errors

#### 2. Fallback 🛡️
- Alternative behavior when primary fails
- **Examples**:
  - Data platform failure → Save to retry queue
  - Popular products batch failure → Return cached data

#### 3. Async Processing ⚡
- Non-critical tasks run asynchronously
- **Apply to**:
  - Data platform transmission ✅
  - Statistics aggregation ✅

---

### Advanced Patterns (Extended) ✨

#### 4. Additional Fallback & Async 🛡️⚡
- Shipping tracker failure → Return last known status
- Notification failure → Log only (non-critical)
- Notification sending ✅
- Email sending ✅

---

## 📁 Project Structure (Layered Architecture)

```
src/main/java/io/hhplus/ecommerce/
├── domain/                      # Domain Layer (Core business logic)
│   ├── product/
│   │   ├── Product.java
│   │   ├── ProductRepository.java  (interface)
│   │   └── ProductService.java
│   ├── order/
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   ├── OrderRepository.java
│   │   └── OrderService.java
│   ├── cart/
│   │   ├── Cart.java
│   │   ├── CartItem.java
│   │   └── CartService.java
│   ├── coupon/
│   │   ├── Coupon.java
│   │   ├── UserCoupon.java
│   │   └── CouponService.java
│   ├── user/
│   │   ├── User.java
│   │   └── UserService.java
│   └── shipping/
│       ├── Shipping.java
│       └── ShippingService.java
│
├── application/                 # Application Layer (Use cases)
│   ├── product/
│   │   ├── ProductUseCase.java
│   │   └── dto/
│   ├── order/
│   │   ├── OrderUseCase.java
│   │   ├── PaymentUseCase.java
│   │   └── dto/
│   ├── cart/
│   │   ├── CartUseCase.java
│   │   └── dto/
│   └── coupon/
│       ├── CouponUseCase.java
│       └── dto/
│
├── infrastructure/              # Infrastructure Layer
│   ├── persistence/             # DB implementations
│   │   ├── product/
│   │   │   ├── ProductRepositoryImpl.java
│   │   │   └── ProductJpaRepository.java
│   │   └── ...
│   ├── external/                # External API clients
│   │   ├── dataplatform/
│   │   │   ├── DataPlatformClient.java
│   │   │   └── DataPlatformConfig.java
│   │   ├── payment/
│   │   │   └── TossPaymentClient.java
│   │   ├── notification/
│   │   │   └── NotificationClient.java
│   │   └── shipping/
│   │       └── ShippingTrackerClient.java
│   └── batch/
│       └── ProductStatisticsScheduler.java
│
├── presentation/                # Presentation Layer
│   ├── api/
│   │   ├── product/ProductController.java
│   │   ├── order/OrderController.java
│   │   ├── cart/CartController.java
│   │   └── coupon/CouponController.java
│   └── common/
│       ├── ApiResponse.java
│       ├── ErrorResponse.java
│       └── GlobalExceptionHandler.java
│
├── config/
│   ├── JpaConfig.java
│   ├── AsyncConfig.java
│   ├── CacheConfig.java
│   └── RestTemplateConfig.java
│
└── common/
    ├── exception/
    └── util/
```

**See**: `docs/PROJECT_STRUCTURE.md` for detailed structure and examples

---

## 📚 Key Documentation Files

### API Design Documents (`docs/api/`)
- **requirements.md** - Business requirements and availability patterns
- **user-stories.md** - User stories with acceptance criteria
- **api-specification.md** - Complete API specifications (endpoints, errors)
- **data-models.md** - Entity definitions and relationships
- **availability-patterns.md** ✨ - Detailed resilience patterns guide

### System Design Diagrams (`docs/diagrams/`)
- **erd.md** - Entity Relationship Diagram
- **sequence-diagrams.md** - Key process flows (5 diagrams)

### Project Structure
- **PROJECT_STRUCTURE.md** - Layered architecture guide

---

## 🎯 RESTful API Design Principles

### Resource-Oriented URLs
- ✅ `GET /api/products/123`
- ❌ `GET /api/getProduct?id=123`

### HTTP Methods
| Method | Operation | Example |
|--------|-----------|---------|
| GET | Read | `GET /api/products` |
| POST | Create | `POST /api/orders` |
| PUT | Full Update | `PUT /api/products/123` |
| PATCH | Partial Update | `PATCH /api/users/123` |
| DELETE | Delete | `DELETE /api/products/123` |

### Status Codes
- `200 OK` - Successful GET/PUT/PATCH
- `201 Created` - Successful POST
- `400 Bad Request` - Invalid input
- `404 Not Found` - Resource not found
- `409 Conflict` - Business rule violation
- `500 Internal Server Error` - Server error

### Response Format
```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

---

## 🗄️ Database Design Guidelines

### Concurrency Control

**Stock Management (Pessimistic Lock)**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Product findByIdWithLock(@Param("id") String id);
```

**Coupon Issuance (Optimistic Lock)**
```java
@Entity
public class Coupon {
    @Version
    private Long version;
}
```

### Required Indexes
```sql
-- Product queries
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_created_at ON products(created_at);

-- Order queries
CREATE INDEX idx_orders_user_status ON orders(user_id, status);
CREATE INDEX idx_orders_paid_at ON orders(paid_at);

-- Popular products statistics
CREATE INDEX idx_order_items_product ON order_items(product_id);

-- Coupon queries
CREATE INDEX idx_user_coupons_user_status ON user_coupons(user_id, status);
```

---

## 📊 Critical Constraints

### Stock Management
- **Accuracy**: Real-time stock reflection
- **Concurrency**: Guarantee stock during concurrent purchases (no negative stock)
- **Recovery**: Restore stock on payment failure

### Coupon System
- **First-Come-First-Served**: Exact quantity control
- **Duplicate Prevention**: One coupon per user (DB constraint)
- **Validation**: Check expiration and usage status

### Order Process
- **Stock Deduction Timing**: AFTER successful payment
- **Atomicity**: Payment and stock deduction in one transaction
- **External Integration**: Async, order completes even if external fails

### Cart
- **Validation**: Recheck stock before order conversion
- **Expiration**: Auto-delete unused carts after N days (optional)

---

## 🚀 Feature Priority (Week 2)

### Core Features (Basic Assignment)
1. ✅ Product inquiry (list, detail, stock)
2. ✅ Cart (add, view, update, delete)
3. ✅ Order creation (stock check, coupon apply)
4. ✅ Payment processing (balance-based)
5. ✅ Coupon issuance/usage (first-come-first-served)
6. ✅ External data transmission (async, fallback)

### Availability Patterns (Design)
1. ✅ Timeout & Retry (design)
2. ✅ Fallback implementation (design)
3. ✅ Async processing (design: notifications, stats)

### Additional Features (Optional)
1. Shipping management (status tracking)
2. Order history query
3. TossPay test API integration
4. Additional external integrations (shipping tracker, notifications)
5. Admin functions (simple CRUD)

### Out of Scope ❌
- Complex search (Elasticsearch)
- Recommendation system (ML-based)
- Real-time notifications (Push, SMS) - Log/Email only
- Event/Promotion management
- Review/Rating system
- Wishlist

---

## 🤖 AI-Assisted Development Workflow

### Step 1: Requirements Analysis
```
Prompt: "Analyze docs/api/requirements.md and identify all entities,
relationships, and availability constraints for the e-commerce system."
```

### Step 2: API Design
```
Prompt: "Based on docs/api/user-stories.md, design RESTful API endpoints
with cart and shipping features. Include request/response formats, status codes,
and error handling. Update docs/api/api-specification.md"
```

### Step 3: Data Model Design
```
Prompt: "Create ERD for e-commerce system including Cart, Shipping, and Outbox
entities. Include concurrency control for stock and coupon.
Write to docs/diagrams/erd.md"
```

### Step 4: Sequence Diagrams
```
Prompt: "Create sequence diagram for order creation with cart conversion,
including stock check, coupon validation, payment, and async external transmission.
Use Mermaid format in docs/diagrams/sequence-diagrams.md"
```

### Step 5: Availability Patterns
```
Prompt: "Implement Timeout & Retry for DataPlatformClient based on
docs/api/availability-patterns.md. Include fallback to outbox queue."
```

### Step 6: Implementation
```
Prompt: "Implement CartService based on docs/api/data-models.md.
Include validation logic and cart-to-order conversion.
Follow the layered architecture in docs/PROJECT_STRUCTURE.md"
```

---

## 🛠️ Development Commands

### Building the Project
```bash
./gradlew build
```

### Running the Application
```bash
./gradlew bootRun
```

### Running Tests
```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests io.hhplus.ecommerce.domain.order.OrderServiceTest

# Continuous testing (TDD)
./gradlew test --continuous
```

### Cleaning Build Artifacts
```bash
./gradlew clean
```

---

## ✅ Assignment Checklist (Week 2)

### Design Documents (Must Complete)
- [ ] Requirements with availability patterns in `docs/api/requirements.md`
- [ ] User stories including cart and shipping in `docs/api/user-stories.md`
- [ ] Complete API specification in `docs/api/api-specification.md`
- [ ] Data models in `docs/api/data-models.md`
- [ ] ERD diagram in `docs/diagrams/erd.md`
- [ ] Sequence diagrams (5 flows) in `docs/diagrams/sequence-diagrams.md`
- [ ] Availability patterns documented in `docs/api/availability-patterns.md`
- [ ] Stock deduction timing clearly defined
- [ ] Coupon issuance policy documented
- [ ] External integration failure handling documented
- [ ] Redis/Kafka architecture design included

### Mock Server Implementation (Required)
- [ ] Spring Boot Mock API (No db.json, use in-memory collections)
- [ ] Timeout & Retry design
- [ ] Async processing design for external APIs
- [ ] Batch aggregation for popular products
- [ ] API testing with Postman/curl

### Optional Features
- [ ] TossPay test API integration
- [ ] Additional external integrations (shipping tracker, notifications)

### Design Validation Checklist
- [ ] Cart validation before order conversion designed?
- [ ] Stock deduction happens after payment?
- [ ] External transmission doesn't block order?
- [ ] Fallback methods designed?
- [ ] Timeout & Retry strategies defined?
- [ ] Concurrency control for stock deduction (Pessimistic Lock)?
- [ ] Optimistic lock for coupon issuance?
- [ ] Batch aggregation has fallback to cache?
- [ ] Indexes for performance queries designed?

---

## 🔍 Common Pitfalls to Avoid

### Stock Management
- ❌ Don't deduct stock before payment
- ❌ Don't forget to restore stock on payment failure
- ✅ Use database locks for concurrent updates

### Coupon System
- ❌ Don't allow multiple issuance per user
- ❌ Don't skip expiration validation
- ✅ Use optimistic lock (version field)

### External Integration
- ❌ Don't make external calls synchronous in order flow
- ❌ Don't rollback order on external failure
- ✅ Use async + Timeout + Retry + Fallback

### Cart System
- ❌ Don't skip stock revalidation before order
- ❌ Don't allow invalid quantities
- ✅ Validate all items before order conversion

### API Design
- ❌ Don't use verbs in URLs
- ❌ Don't return 200 for all responses
- ✅ Use proper HTTP methods and status codes

---

## 📚 Reference Materials

### REST API Design
- [REST API Best Practices](https://restfulapi.net/rest-api-best-practices/)
- [Microsoft API Design Guide](https://learn.microsoft.com/en-us/azure/architecture/best-practices/api-design)

### Resilience Patterns
- [Microservices Patterns](https://microservices.io/patterns/index.html)

### Database Design
- [Entity-Relationship Model](https://en.wikipedia.org/wiki/Entity%E2%80%93relationship_model)

### System Design
- [Sequence Diagrams](https://en.wikipedia.org/wiki/Sequence_diagram)
- [Mermaid Documentation](https://mermaid.js.org/)

---

## 🎓 Success Criteria (Week 2)

### Design Deliverables
- [ ] **API Specification**: All endpoints documented
- [ ] **ERD**: Entity Relationship Diagram complete
- [ ] **Sequence Diagrams**: 5+ core flows documented
- [ ] **Redis/Kafka Design**: Included in architecture diagrams
- [ ] **Availability Patterns**: Timeout, Retry, Fallback, Async designed

### Mock Server Implementation
- [ ] **Spring Boot**: Mock API server running
- [ ] **Business Logic**: Basic operations verified
- [ ] **In-Memory**: Cache/Queue working
- [ ] **API Testing**: Testable with Postman/curl

### Design Validation
- [ ] Cart validation before order conversion designed
- [ ] Stock deduction timing clearly defined (after payment)
- [ ] External transmission doesn't block order (async)
- [ ] Fallback methods designed
- [ ] Timeout & Retry strategies designed
- [ ] Concurrency control strategies defined (Pessimistic/Optimistic Lock)

---

## Configuration

Application configuration is in `src/main/resources/application.yml`.

### Key Configurations
- **Database**: H2 (in-memory) for development
- **Cache**: Spring Cache or Redis
- **Async**: Thread pool for external APIs
