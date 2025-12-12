package io.hhplus.ecommerce.domain.order;

import io.hhplus.ecommerce.config.TestContainersConfig;
import org.springframework.context.annotation.Import;

import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestContainersConfig.class)

@SpringBootTest
@ActiveProfiles("test")
class OrderConcurrencyTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Order 상태 변경 동시성 테스트 - Optimistic Lock으로 Lost Update 방지")
    void testOrderStatusConcurrency_OptimisticLock() throws InterruptedException {
        // Given: 사용자 및 주문 생성
        String uniqueEmail = "test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = User.create(uniqueEmail, "테스트");
        userRepository.save(user);

        Order order = Order.create("ORDER-TEST-001", user.getId(), 10000L, 0L);
        orderRepository.save(order);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger optimisticLockFailureCount = new AtomicInteger(0);

        // When: 10개 스레드가 동시에 complete() 호출
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 각 스레드에서 새로운 트랜잭션으로 조회 후 상태 변경
                    Order foundOrder = orderRepository.findByIdOrThrow(order.getId());
                    foundOrder.complete();
                    orderRepository.save(foundOrder);
                    successCount.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException e) {
                    // Optimistic Lock 실패 (정상 동작)
                    optimisticLockFailureCount.incrementAndGet();
                } catch (Exception e) {
                    // 상태 검증 실패 등
                    System.out.println("Other exception: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then: 1개만 성공, 나머지는 Optimistic Lock 실패
        System.out.println("성공: " + successCount.get());
        System.out.println("Optimistic Lock 실패: " + optimisticLockFailureCount.get());

        assertThat(successCount.get()).isEqualTo(1); // 1개만 성공
        assertThat(optimisticLockFailureCount.get()).isGreaterThan(0); // 나머지는 충돌

        // 최종 상태 확인
        Order finalOrder = orderRepository.findByIdOrThrow(order.getId());
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Order complete와 cancel 동시 호출 - Optimistic Lock으로 한 가지만 성공")
    @Transactional
    void testOrderCompleteAndCancel_OptimisticLock() throws InterruptedException {
        // Given
        String uniqueEmail = "test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = User.create(uniqueEmail, "테스트2");
        userRepository.save(user);

        Order order = Order.create("ORDER-TEST-002", user.getId(), 10000L, 0L);
        orderRepository.save(order);

        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: complete()와 cancel()을 동시에 호출
        Thread thread1 = new Thread(() -> {
            try {
                Order foundOrder = orderRepository.findByIdOrThrow(order.getId());
                foundOrder.complete();
                orderRepository.save(foundOrder);
                successCount.incrementAndGet();
                System.out.println("Complete 성공");
            } catch (Exception e) {
                System.out.println("Complete 실패: " + e.getClass().getSimpleName());
            } finally {
                latch.countDown();
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                Thread.sleep(10); // 약간의 지연
                Order foundOrder = orderRepository.findByIdOrThrow(order.getId());
                foundOrder.cancel();
                orderRepository.save(foundOrder);
                successCount.incrementAndGet();
                System.out.println("Cancel 성공");
            } catch (Exception e) {
                System.out.println("Cancel 실패: " + e.getClass().getSimpleName());
            } finally {
                latch.countDown();
            }
        });

        thread1.start();
        thread2.start();
        latch.await();

        // Then: 하나만 성공해야 함
        assertThat(successCount.get()).isLessThanOrEqualTo(1);
    }
}
