package io.hhplus.ecommerce.application.cart;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 사용자별 로컬 락 매니저.
 * 동일 userId에 대한 장바구니 읽기/쓰기 작업을 직렬화해
 * 다중 VU 테스트에서도 불필요한 경합 실패를 줄인다.
 */
@Component
public class CartLockManager {

    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(Long userId, Supplier<T> supplier) {
        ReentrantLock lock = locks.computeIfAbsent(userId, id -> new ReentrantLock(true));
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
            // 큐가 비면 락을 맵에서 제거해 메모리 누수를 방지
            if (!lock.hasQueuedThreads()) {
                locks.remove(userId, lock);
            }
        }
    }

    public void withLock(Long userId, Runnable runnable) {
        withLock(userId, () -> {
            runnable.run();
            return null;
        });
    }
}
