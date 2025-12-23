# Gemini Assistant Rules for hhplus-ecommerce

This file contains essential, project-specific instructions for the Gemini assistant.

## 1. Core Principle

The single source of truth for all agent rules is **`./AGENTS.md`**. In case of conflicting instructions, the rules in `AGENTS.md` ALWAYS take precedence.

## 2. Test & Verification (MANDATORY)

- **Primary Test Command:** Before committing or finalizing any task, you MUST run `./gradlew test` and ensure all tests pass.
- **Coverage Check:** To verify code coverage, you MUST use `./gradlew test jacocoTestReport`. The coverage MUST NOT drop below **70%**.
- **When debugging failures:** Capture logs to a file for review: `./gradlew test --console=plain --info --stacktrace | tee build/test-full.log` and filter failures with `grep -nE "FAILED|FAILURE|Exception" build/test-full.log`.

## 3. Architecture Constraints

This project uses a 4-layer architecture. You MUST respect its dependency rule.
- **Layers:** `presentation` -> `application` -> `domain` <- `infrastructure`
- **Dependency Rule:** All dependencies MUST point inwards, towards the `domain` layer. For example, the `application` layer can depend on `domain`, but `domain` CANNOT depend on `application`.
- **Domain Models:** Key domains include `cart`, `coupon`, `order`, `payment`, `product`, `user`.

## 3.1 Import Style

- Prefer normal imports and short type names. If names collide, first consider better naming; only fall back to fully-qualified class names when the conflict is truly unavoidable.

## 3.2 Weekly Guides

- Always read the latest `docs/week*/README.md` for current coaching/Q&A feedback, and consult earlier weeks when relevant; apply the latest guidance first while keeping past feedback in mind.
- For any code change, honor coach feedback/guides and sanity-check decisions via the five senior personas (7–20yr experience) to ensure practicality.

## 4. Critical Domain Rules (MUST FOLLOW)

These rules are derived from `docs/week8/README.md` and are critical for maintaining data integrity and system performance.

- **Rule 1: Use `@TransactionalEventListener` for Decoupling.**
  - To decouple domains (e.g., `order` from `payment`), publish application events.
  - **Reason:** This prevents long-running transactions that lock the database.

- **Rule 2: ALWAYS use `phase = TransactionPhase.AFTER_COMMIT`.**
  - Event listeners that interact with external systems (or perform other side effects) MUST be configured to run only after the main transaction has successfully committed.
  - **Example:**
    ```java
    // DO THIS
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        // ... send notification or call payment API
    }
    
    // DON'T DO THIS
    @EventListener 
    public void handleOrderCompleted(OrderCompletedEvent event) {
        // This runs immediately and can cause inconsistencies if the transaction rolls back.
    }
    ```
- **Rule 3: Keep Transactions Short.**
  - A transaction SHOULD only contain the core database write operations.
  - Any long-running task (e.g., external API calls, notifications, complex calculations) MUST be extracted into an asynchronous, non-transactional event listener.
  - **Reason:** Long transactions reduce database throughput and can cause deadlocks.
