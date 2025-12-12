package io.hhplus.ecommerce.domain.user;

import io.hhplus.ecommerce.config.TestContainersConfig;
import org.springframework.context.annotation.Import;

import io.hhplus.ecommerce.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사용자 잔액 낙관적 락 동시성 테스트
 * <p>
 * 제이 코치 피드백 반영:
 * "Idempotency Key가 실제로 중복 결제를 막는지, @Version이 Lost Update를 방지하는지 테스트로 검증하면
 * 문서와 코드가 일치하는지 확인할 수 있거든요."
 * <p>
 * 테스트 시나리오:
 * 1. 10명이 동시에 10,000원씩 차감 시 최종 잔액 0원
 * 2. 잔액 50,000원일 때 10명이 10,000원씩 차감 시 5명만 성공
 * 3. 충전과 차감 동시 발생 시 Lost Update 방지
 * 4. 100명이 동시에 차감 시 정확한 잔액 처리
 */
@Import(TestContainersConfig.class)

@SpringBootTest
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class UserBalanceOptimisticLockConcurrencyTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User testUser;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성 (잔액 100,000원)
        testUser = User.create("test@example.com", "테스트유저");
        testUser.charge(100_000L);
        userRepository.save(testUser);
    }

    @Test
    @DisplayName("[제이코치 피드백] 10명이 동시에 10,000원씩 차감 시 최종 잔액 0원 (Optimistic Lock)")
    void 낙관적락_잔액차감_동시성_테스트() throws InterruptedException {
        // Given
        int threadCount = 10;
        long deductAmount = 10_000L;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger optimisticLockFailureCount = new AtomicInteger();
        AtomicInteger retryCount = new AtomicInteger();

        // When: 10명이 동시에 10,000원씩 차감 (재시도 포함)
        for (int i = 0; i < threadCount; i++) {
            final int attemptNumber = i + 1;

            executorService.submit(() -> {
                try {
                    // 낙관적 락 재시도 로직 (최대 10번)
                    int retries = deductBalanceWithRetry(testUser.getId(), deductAmount, 10);

                    if (retries >= 0) {
                        successCount.incrementAndGet();
                        retryCount.addAndGet(retries);
                        System.out.println("✅ 성공 #" + attemptNumber + " (재시도: " + retries + "회)");
                    }

                } catch (Exception e) {
                    optimisticLockFailureCount.incrementAndGet();
                    System.out.println("❌ 최종 실패 #" + attemptNumber + ": " + e.getMessage());

                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 모두 성공 (재시도 포함)
        System.out.println("\n=== 결과 요약 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("재시도 발생 횟수: " + retryCount.get());
        System.out.println("최종 실패: " + optimisticLockFailureCount.get());

        assertThat(successCount.get()).isEqualTo(10);
        assertThat(optimisticLockFailureCount.get()).isEqualTo(0);
        assertThat(retryCount.get()).isGreaterThan(0);  // 낙관적 락 충돌 발생 확인

        // 최종 잔액 0원
        User user = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(user.getBalance()).isEqualTo(0L);
    }

    @Test
    @DisplayName("[제이코치 피드백] 잔액 50,000원일 때 10명이 10,000원씩 차감 시 5명만 성공")
    void 낙관적락_잔액부족_동시성_테스트() throws InterruptedException {
        // Given: 잔액 50,000원으로 설정
        testUser.deduct(50_000L);  // 100,000 - 50,000 = 50,000
        userRepository.save(testUser);

        int threadCount = 10;
        long deductAmount = 10_000L;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When
        for (int i = 0; i < threadCount; i++) {
            final int attemptNumber = i + 1;

            executorService.submit(() -> {
                try {
                    int retries = deductBalanceWithRetry(testUser.getId(), deductAmount, 10);

                    if (retries >= 0) {
                        successCount.incrementAndGet();
                        System.out.println("✅ 성공 #" + attemptNumber + " (재시도: " + retries + "회)");
                    } else {
                        // 잔액 부족 (-1 반환)
                        failCount.incrementAndGet();
                        System.out.println("❌ 잔액 부족 #" + attemptNumber);
                    }

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("❌ 기타 실패 #" + attemptNumber + ": " + e.getMessage());

                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 정확히 5명만 성공
        System.out.println("\n=== 결과 요약 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        // 최종 잔액 0원
        User user = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(user.getBalance()).isEqualTo(0L);
    }

    @Test
    @DisplayName("[제이코치 피드백] 충전과 차감 동시 발생 시 Lost Update 방지")
    void 낙관적락_충전과차감_동시_테스트() throws InterruptedException {
        // Given
        int threadCount = 20;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger chargeCount = new AtomicInteger();
        AtomicInteger deductCount = new AtomicInteger();

        // When: 충전 10번, 차감 10번 동시 실행
        for (int i = 0; i < 10; i++) {
            final int attemptNumber = i + 1;

            // 충전 (10,000원씩)
            executorService.submit(() -> {
                try {
                    int retries = chargeBalanceWithRetry(testUser.getId(), 10_000L, 10);
                    if (retries >= 0) {
                        chargeCount.incrementAndGet();
                        System.out.println("💰 충전 성공 #" + attemptNumber + " (재시도: " + retries + "회)");
                    }
                } catch (Exception e) {
                    System.out.println("💰 충전 실패 #" + attemptNumber);
                } finally {
                    latch.countDown();
                }
            });

            // 차감 (10,000원씩)
            executorService.submit(() -> {
                try {
                    int retries = deductBalanceWithRetry(testUser.getId(), 10_000L, 10);
                    if (retries >= 0) {
                        deductCount.incrementAndGet();
                        System.out.println("💸 차감 성공 #" + attemptNumber + " (재시도: " + retries + "회)");
                    } else {
                        System.out.println("💸 차감 실패 (잔액 부족) #" + attemptNumber);
                    }
                } catch (Exception e) {
                    // 잔액 부족으로 실패 가능
                    System.out.println("💸 차감 실패 #" + attemptNumber + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 최종 잔액 = 초기 잔액 + (충전 횟수 - 차감 횟수) * 10,000
        long expectedBalance = 100_000L + (chargeCount.get() - deductCount.get()) * 10_000L;

        System.out.println("\n=== 결과 요약 ===");
        System.out.println("충전 성공: " + chargeCount.get());
        System.out.println("차감 성공: " + deductCount.get());
        System.out.println("예상 잔액: " + expectedBalance);

        User user = userRepository.findById(testUser.getId()).orElseThrow();
        System.out.println("실제 잔액: " + user.getBalance());

        assertThat(user.getBalance()).isEqualTo(expectedBalance);

        // Lost Update가 발생하지 않았음을 확인
        // (낙관적 락이 없으면 일부 업데이트가 소실될 수 있음)
    }

    @Test
    @DisplayName("100명이 동시에 1,000원씩 차감 시 정확한 잔액 처리")
    void 대규모_동시_차감_테스트() throws InterruptedException {
        // Given
        int threadCount = 100;
        long deductAmount = 1_000L;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When: 100명이 동시에 1,000원씩 차감
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    int retries = deductBalanceWithRetry(testUser.getId(), deductAmount, 20);
                    if (retries >= 0) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then
        System.out.println("\n=== 대규모 동시 차감 결과 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        // 대부분 성공해야 함 (잔액 100,000원 / 1,000원 = 100회 가능)
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(0);

        // 최종 잔액 0원
        User user = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(user.getBalance()).isEqualTo(0L);
    }

    @Test
    @DisplayName("@Version 증가 확인 - 업데이트마다 version이 증가함")
    void 버전_증가_확인_테스트() {
        // Given
        User user = userRepository.findById(testUser.getId()).orElseThrow();
        Long initialVersion = user.getVersion();
        System.out.println("초기 version: " + initialVersion);

        // When: 충전
        executeInTransaction(() -> {
            User foundUser = userRepository.findById(testUser.getId()).orElseThrow();
            foundUser.charge(10_000L);
            userRepository.save(foundUser);
        });

        // Then: version 증가
        user = userRepository.findById(testUser.getId()).orElseThrow();
        Long afterChargeVersion = user.getVersion();
        System.out.println("충전 후 version: " + afterChargeVersion);

        assertThat(afterChargeVersion).isGreaterThan(initialVersion);

        // When: 차감
        executeInTransaction(() -> {
            User foundUser = userRepository.findById(testUser.getId()).orElseThrow();
            foundUser.deduct(5_000L);
            userRepository.save(foundUser);
        });

        // Then: version 다시 증가
        user = userRepository.findById(testUser.getId()).orElseThrow();
        Long afterDeductVersion = user.getVersion();
        System.out.println("차감 후 version: " + afterDeductVersion);

        assertThat(afterDeductVersion).isGreaterThan(afterChargeVersion);
    }

    /**
     * 잔액 차감 (낙관적 락 재시도)
     * @return 재시도 횟수 (성공 시), -1 (잔액 부족)
     */
    private int deductBalanceWithRetry(Long userId, Long amount, int maxRetry) {
        int retryCount = 0;

        while (retryCount < maxRetry) {
            try {
                executeInTransaction(() -> {
                    User user = userRepository.findById(userId).orElseThrow();
                    user.deduct(amount);
                    userRepository.save(user);
                });

                return retryCount;  // 성공 (재시도 횟수 반환)

            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;

                if (retryCount >= maxRetry) {
                    System.out.println("⚠️ 낙관적 락 재시도 " + maxRetry + "회 초과");
                    throw new RuntimeException("최대 재시도 횟수 초과", e);
                }

                // Exponential Backoff (50ms → 100ms → 200ms ...)
                long delayMs = 50 * (long) Math.pow(2, retryCount - 1);
                System.out.println("🔄 낙관적 락 충돌 - " + retryCount + "번째 재시도 (대기: " + delayMs + "ms)");

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("재시도 대기 중 인터럽트", ie);
                }

            } catch (BusinessException e) {
                // 잔액 부족 - 재시도 불필요
                System.out.println("❌ 잔액 부족: " + e.getMessage());
                return -1;

            } catch (RuntimeException e) {
                // BusinessException을 감싼 RuntimeException 처리
                if (e.getCause() instanceof BusinessException) {
                    System.out.println("❌ 잔액 부족 (wrapped): " + e.getCause().getMessage());
                    return -1;
                }
                throw e;
            }
        }

        return -1;
    }

    /**
     * 잔액 충전 (낙관적 락 재시도)
     * @return 재시도 횟수 (성공 시), -1 (실패 시 - 하지만 충전은 실패하지 않음)
     */
    private int chargeBalanceWithRetry(Long userId, Long amount, int maxRetry) {
        int retryCount = 0;

        while (retryCount < maxRetry) {
            try {
                executeInTransaction(() -> {
                    User user = userRepository.findById(userId).orElseThrow();
                    user.charge(amount);
                    userRepository.save(user);
                });

                return retryCount;  // 성공 (재시도 횟수 반환)

            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;

                if (retryCount >= maxRetry) {
                    System.out.println("⚠️ 낙관적 락 재시도 " + maxRetry + "회 초과 (충전)");
                    throw new RuntimeException("최대 재시도 횟수 초과", e);
                }

                // Exponential Backoff
                long delayMs = 50 * (long) Math.pow(2, retryCount - 1);
                System.out.println("🔄 낙관적 락 충돌 (충전) - " + retryCount + "번째 재시도 (대기: " + delayMs + "ms)");

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("재시도 대기 중 인터럽트", ie);
                }
            }
        }

        return -1;
    }

    /**
     * 트랜잭션 내에서 실행
     */
    private void executeInTransaction(Runnable task) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        TransactionStatus status = transactionManager.getTransaction(def);

        try {
            task.run();
            transactionManager.commit(status);
        } catch (Exception e) {
            if (!status.isCompleted()) {
                transactionManager.rollback(status);
            }
            throw e;
        }
    }
}
