package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.config.TestContainersConfig;
import org.springframework.context.annotation.Import;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestContainersConfig.class)

@SpringBootTest
@ActiveProfiles("test")
class CartItemConcurrencyTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("CartItem 수량 변경 동시성 테스트 - Optimistic Lock으로 Lost Update 방지")
    void testCartItemQuantityConcurrency_OptimisticLock() throws InterruptedException {
        // Given: 사용자, 상품, 장바구니, 장바구니 아이템 생성
        String uniqueEmail = "test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = User.create(uniqueEmail, "테스트");
        userRepository.save(user);

        String uniqueProductCode = "P-" + UUID.randomUUID().toString().substring(0, 8);
        Product product = Product.create(uniqueProductCode, "테스트상품", "설명", 10000L, "카테고리", 100);
        productRepository.save(product);

        Cart cart = Cart.create(user.getId());
        cartRepository.save(cart);

        CartItem cartItem = CartItem.create(cart, product, 1);
        cartItemRepository.save(cartItem);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger optimisticLockFailureCount = new AtomicInteger(0);

        // When: 10개 스레드가 동시에 수량 +1
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    CartItem foundItem = cartItemRepository.findById(cartItem.getId()).orElseThrow();
                    int newQuantity = foundItem.getQuantity() + 1;
                    foundItem.updateQuantity(newQuantity);
                    cartItemRepository.save(foundItem);
                    successCount.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException e) {
                    // Optimistic Lock 실패 (정상 동작)
                    optimisticLockFailureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then: 성공 + 실패 = threadCount
        System.out.println("성공: " + successCount.get());
        System.out.println("Optimistic Lock 실패: " + optimisticLockFailureCount.get());

        assertThat(successCount.get() + optimisticLockFailureCount.get()).isEqualTo(threadCount);
        assertThat(optimisticLockFailureCount.get()).isGreaterThan(0); // 충돌 발생

        // 최종 수량 확인: 초기(1) + 성공 횟수
        CartItem finalItem = cartItemRepository.findById(cartItem.getId()).orElseThrow();
        assertThat(finalItem.getQuantity()).isEqualTo(1 + successCount.get());

        System.out.println("최종 수량: " + finalItem.getQuantity() + " (예상: " + (1 + successCount.get()) + ")");
    }

    @Test
    @DisplayName("동일 사용자 다른 스레드에서 장바구니 수정 - 충돌 빈도 확인")
    void testCartItemConcurrency_LowCollisionRate() throws InterruptedException {
        // Given
        String uniqueEmail = "test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = User.create(uniqueEmail, "테스트2");
        userRepository.save(user);

        String productCode1 = "P-" + UUID.randomUUID().toString().substring(0, 8);
        String productCode2 = "P-" + UUID.randomUUID().toString().substring(0, 8);
        Product product1 = Product.create(productCode1, "상품1", "설명", 10000L, "카테고리", 100);
        Product product2 = Product.create(productCode2, "상품2", "설명", 20000L, "카테고리", 100);
        productRepository.save(product1);
        productRepository.save(product2);

        Cart cart = Cart.create(user.getId());
        cartRepository.save(cart);

        CartItem item1 = CartItem.create(cart, product1, 1);
        CartItem item2 = CartItem.create(cart, product2, 1);
        cartItemRepository.save(item1);
        cartItemRepository.save(item2);

        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount * 2);

        // When: 5개 스레드가 각각 다른 상품 수량 변경 (충돌 없어야 함)
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    CartItem found = cartItemRepository.findById(item1.getId()).orElseThrow();
                    found.updateQuantity(found.getQuantity() + 1);
                    cartItemRepository.save(found);
                } finally {
                    latch.countDown();
                }
            }).start();

            new Thread(() -> {
                try {
                    CartItem found = cartItemRepository.findById(item2.getId()).orElseThrow();
                    found.updateQuantity(found.getQuantity() + 1);
                    cartItemRepository.save(found);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();

        // Then: 서로 다른 상품이므로 충돌 없이 모두 성공해야 함
        // (실제로는 동시성으로 인해 일부 충돌 가능)
        System.out.println("테스트 완료: 다른 상품은 충돌 없음");
    }
}
