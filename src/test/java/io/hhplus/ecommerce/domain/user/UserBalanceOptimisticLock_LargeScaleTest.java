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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

@Import(TestContainersConfig.class)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserBalanceOptimisticLock_LargeScaleTest {

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
    @DisplayName("10명이 동시에 1,000원씩 차감 시 정확한 잔액 처리")
    void 동시_차감_테스트() throws InterruptedException {
        // Given
        int threadCount = 10;
        long deductAmount = 1_000L;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When: 여러 스레드가 동시에 1,000원씩 차감
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

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // Then
        System.out.println("\n=== 대규모 동시 차감 결과 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isEqualTo(0);

        // 최종 잔액: 100,000 - (threadCount * 1,000)
        User user = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(user.getBalance()).isEqualTo(90_000L);
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
