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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

@Import(TestContainersConfig.class)
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class UserBalanceOptimisticLock_ConcurrencyTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User testUser;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성 (잔액 100,000원) - UUID 기반 고유 이메일
        String uniqueEmail = "test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        testUser = User.create(uniqueEmail, "테스트유저");
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
    }
    
    private int deductBalanceWithRetry(Long userId, Long amount, int maxRetry) {
        int retryCount = 0;
        while (retryCount < maxRetry) {
            try {
                executeInTransaction(() -> {
                    User user = userRepository.findById(userId).orElseThrow();
                    user.deduct(amount);
                    userRepository.save(user);
                });
                return retryCount;
            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;
                if (retryCount >= maxRetry) throw new RuntimeException("최대 재시도 횟수 초과", e);
                try {
                    long backoffMillis = Math.min(200L, 50L * (long) Math.pow(2, retryCount - 1));
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("재시도 대기 중 인터럽트", ie);
                }
            } catch (BusinessException e) {
                return -1;
            } catch (RuntimeException e) {
                if (e.getCause() instanceof BusinessException) return -1;
                throw e;
            }
        }
        return -1;
    }

    private int chargeBalanceWithRetry(Long userId, Long amount, int maxRetry) {
        int retryCount = 0;
        while (retryCount < maxRetry) {
            try {
                executeInTransaction(() -> {
                    User user = userRepository.findById(userId).orElseThrow();
                    user.charge(amount);
                    userRepository.save(user);
                });
                return retryCount;
            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;
                if (retryCount >= maxRetry) throw new RuntimeException("최대 재시도 횟수 초과", e);
                try {
                    long backoffMillis = Math.min(200L, 50L * (long) Math.pow(2, retryCount - 1));
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("재시도 대기 중 인터럽트", ie);
                }
            }
        }
        return -1;
    }

    private void executeInTransaction(Runnable task) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = transactionManager.getTransaction(def);
        try {
            task.run();
            transactionManager.commit(status);
        } catch (Exception e) {
            if (!status.isCompleted()) transactionManager.rollback(status);
            throw e;
        }
    }
}
