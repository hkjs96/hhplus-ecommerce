# Agents Rules for hhplus-ecommerce

This document provides rules for AI code agents to ensure code quality and consistency. All contributions MUST adhere to these rules.

## 1. Scope & Purpose

Your primary goal is to complete specific, isolated tasks by writing or modifying code in small, verifiable increments.

### Operating Mode (How to Work Efficiently)
- **List first, change one at a time:** If there are many changes, first produce a short "change candidate list" (with risk/priority and the failing/target tests), then pick **exactly one** task and implement it end-to-end before moving on.
- **Prefer the smallest safe step:** Split work by behavior/test, not by file count (e.g., “fix order creation event timing” is a task; “update 5 services” is not).

### Pair-Coding Protocol (How We Collaborate)
Use this protocol unless the user explicitly asks for “just do it”.

- **Decision gates:** Stop and ask for confirmation at (1) task selection, (2) approach choice if multiple options, (3) running long commands (`clean test`, coverage), (4) changes that touch many tests/files.
- **Questions first (when unclear):** Ask for the failing test name/command, expected vs actual behavior, and the acceptance criteria.
- **Show the slice:** Before coding, restate the one task in 1–2 sentences and list the exact files you expect to touch.
- **Keep diffs reviewable:** Prefer ≤1–3 files and ≤200 LoC per task; if you must exceed, explain why and propose an alternative split.

### Prohibited Actions (Strictly Enforced)
- **NO Large-Scale Refactoring:** Do not refactor, rename, or move packages/modules on a large scale.
- **NO Arbitrary Style Changes:** Do not apply sweeping code style or formatting changes. Adhere to the existing style of the file you are editing.
- **NO Architectural Changes:** Do not alter the core layered architecture (`presentation`, `application`, `domain`, `infrastructure`).
- **Respect guides/Q&A:** When changing code, follow the latest docs/week*/README.md guidance and coach feedback; validate choices through the five senior-persona lens (7–20yr) to ensure practicality.

### The "Small Diff" Principle
- **Target:** Aim for changes affecting **1-3 files**.
- **Limit:** Your changes SHOULD NOT exceed **200 Lines of Code (LoC)** (additions + deletions).
- **Reason:** Small diffs are easier to review, test, and rollback, minimizing the risk of introducing bugs.

## 2. Environment & Test Rules

This project uses a specific, containerized testing environment. All tests MUST pass before submitting any change.

- **Stack:** Tests are written with **JUnit 5** and run via **Testcontainers** for **MySQL** and **Redis**. Do not mock repository or database layers; use the provided containers.
- **When full verification is required:** If you changed any code under `src/main` or `src/test`, you MUST validate with `./gradlew test` before submitting.
- **Clean test (heavier):** Run `./gradlew clean test` when finalizing a task/PR, or when you suspect stale build artifacts.
- **Jacoco Coverage (final):** Coverage **MUST NOT** fall below **70%**. Verify with `./gradlew test jacocoTestReport` when finalizing a task/PR.
- **Docs-only changes:** If you only changed documentation files (e.g., `docs/**`, `README.md`, rule docs), you MAY skip Gradle tests.

### Test Execution Ladder (Fast Feedback → Final)
Use the smallest command that gives confidence during iteration, but ALWAYS run the final commands before submitting.

1. **During iteration (focused):** `./gradlew test --tests "fully.qualified.TestClassName"`
2. **After finishing a single task:** `./gradlew test`
3. **Before finalizing a task/PR:** `./gradlew clean test` and `./gradlew test jacocoTestReport`
4. **Capture logs when debugging failures:** `./gradlew test --console=plain --info --stacktrace | tee build/test-full.log` and optionally filter failures with `grep -nE "FAILED|FAILURE|Exception" build/test-full.log`

### Interactive Runs (Ask First)
If the environment is interactive (e.g., approvals required, slow container pulls), ask before running long commands like `./gradlew clean test` or full coverage runs. Prefer a focused test command first.

### Integration Test Rules (Testcontainers)
- **Keep tests deterministic:** Avoid `sleep`, time-dependent assertions, random data without seeding, and non-isolated shared state.
- **Prefer domain-level signals:** Assert DB state / emitted events / observable outputs instead of internal timings.
- **Do not downgrade assertions:** Never delete/comment out asserts or replace exact assertions with weaker ones.

### Test Modification Hierarchy
When a test fails, fix it in this order of preference:

1.  **Fix Business Logic (Highest Priority):** The production code in `src/main` is likely the cause.
2.  **Fix Test Logic:** Modify the test file in `src/test` only if its setup or assertions are incorrect.
3.  **Fix Test Infrastructure:** Only modify test fixtures, base classes, or Testcontainers configuration (`src/test/java/io/hhplus/ecommerce/config/`) as a last resort.

## 3. Workflow

Follow a strict "Test-First" workflow for all tasks.

0.  **Pick ONE task:** If multiple issues exist, select one failing/target test (or add one) and focus only on that until green.
1.  **Write a Failing Test:** Before writing any production code, create a new test (or modify an existing one) that clearly reproduces the bug or demonstrates the new feature. It MUST fail.
2.  **Run the Test:** Execute the specific test first (preferred: `./gradlew test --tests "..."`) to confirm it fails as expected.
3.  **Make Minimal Code Changes:** Write the simplest, most direct code in `src/main` to make the test pass.
4.  **Run All Tests:** Run the full test suite again (`./gradlew test`) to ensure your change didn't break anything else (regression).
5.  **Commit:** Commit the test and the fix together.

### Rules for Modifying Tests
- **DO NOT Delete Assertions:** Never delete or comment out an `assert` statement.
- **DO NOT Weaken Assertions:** Do not change an assertion to be less specific (e.g., changing `assertEquals(10, result)` to `assertTrue(result > 5)`).
- **Justify Changes:** If a test's logic must be changed, you MUST provide a clear reason in your commit message.

## 4. Do & Don't

| Action | ✅ DO | ❌ DON'T |
| :--- | :--- | :--- |
| **Task Focus** | Focus on a single, failing test at a time. | Attempt multiple features or fixes at once. |
| **Testing** | Use the test ladder (focused → full → final). | Submit without `./gradlew clean test` + coverage check. |
| **Code Style** | Mimic the style of surrounding code. | Reformat entire files or change established patterns. |
| **Imports** | Add imports and use short type names; name conflicts should be avoided by better naming, use fully-qualified only if truly unavoidable. | Scatter fully-qualified class names when a normal import suffices. |
| **Architecture**| Respect the existing package and domain structure. | Reorganize packages or change the domain model without explicit instruction. |
| **Assertions** | Keep assertions strong and specific. | Weaken, comment out, or delete assertions to make a test pass. |

## 4.1 Transaction & Event Rules (Critical)
These are critical for correctness and performance (see `docs/week8/README.md`).

- **Keep transactions short:** A transaction should contain the core DB writes only.
- **Decouple with events:** Use `ApplicationEventPublisher` + `@TransactionalEventListener` to separate domains.
- **Side effects AFTER_COMMIT:** Side effects/external calls MUST use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.
- **Weekly guides:** Always check the most recent `docs/week*/README.md` for current coaching/Q&A feedback, but review prior weeks’ Q&A/feedback as needed; prioritize the latest guidance while honoring still-relevant past notes.

## 5. Commit & Pull Request Template

Use this template for your commit messages.

```
feat(domain): Title of your change (e.g., Add 'nickname' to User)

### 1. Purpose
- Why is this change needed? (e.g., To allow users to set a display nickname as per new requirements.)

### 2. Changed Files
- `src/main/java/io/hhplus/ecommerce/domain/user/User.java`
- `src/test/java/io/hhplus/ecommerce/domain/user/UserTest.java`

### 3. Test Results
- **Command:** `./gradlew :test --tests "io.hhplus.ecommerce.domain.user.UserTest"`
- **Output:**
  ```
  > Task :test
  io.hhplus.ecommerce.domain.user.UserTest > canUpdateNickname() PASSED
  
  BUILD SUCCESSFUL in 5s
  ```

### 4. Risks & Rollback
- **Risks:** (e.g., None. Additive change with no impact on existing logic.)
- **Rollback:** (e.g., Revert this commit.)
```

## 6. Examples

### Example 1: Add a "priority" field to the `product` domain

1.  **Create Failing Test:** Add a test case in `src/test/java/io/hhplus/ecommerce/domain/product/ProductTest.java`.
    ```java
    @Test
    void product_should_have_priority() {
        // given
        Product product = new Product(1L, "T-Shirt", new BigDecimal("10.00"), "A nice shirt", 5); // Add new priority field
    
        // then
        assertThat(product.getPriority()).isEqualTo(5);
    }
    ```
2.  **Run & Confirm Failure:**
    - **Command:** `./gradlew test --tests "io.hhplus.ecommerce.domain.product.ProductTest"`
    - The code will not compile because the constructor and field do not exist.
3.  **Apply Minimal Fix:**
    - Add `private final int priority;` to `src/main/java/io/hhplus/ecommerce/domain/product/Product.java`.
    - Update the constructor to accept the new field.
4.  **Verify:**
    - Run `./gradlew test` to ensure all tests pass.

### Example 2: Fix a bug where a coupon is applied to a non-existent user

1.  **Create Failing Test:** In `src/test/java/io/hhplus/ecommerce/application/usecase/coupon/IssueCouponUseCaseTest.java`, add:
    ```java
    @Test
    void issueCoupon_should_throw_exception_for_non_existent_user() {
        // given
        Long nonExistentUserId = 99999L;
        Long couponId = 1L;
        
        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.issueCoupon(nonExistentUserId, couponId))
            .isInstanceOf(UserNotFoundException.class);
    }
    ```
2.  **Run & Confirm Failure:**
    - **Command:** `./gradlew test --tests "io.hhplus.ecommerce.application.usecase.coupon.IssueCouponUseCaseTest"`
    - The test will fail, likely with a `NullPointerException` or by not throwing anything.
3.  **Apply Minimal Fix:**
    - In `src/main/java/io/hhplus/ecommerce/application/usecase/coupon/IssueCouponUseCase.java`, add a user check at the beginning.
    ```java
    // Inside issueCoupon method
    userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    // ... rest of the logic
    ```
4.  **Verify:**
    - Run `./gradlew test` to ensure all tests pass.
