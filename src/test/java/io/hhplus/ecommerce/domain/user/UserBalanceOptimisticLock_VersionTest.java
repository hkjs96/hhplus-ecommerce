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
class UserBalanceOptimisticLock_VersionTest {

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