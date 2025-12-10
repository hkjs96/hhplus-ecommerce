package io.hhplus.ecommerce.application.payment;

import io.hhplus.ecommerce.application.payment.listener.DataPlatformEventListener;
import io.hhplus.ecommerce.application.payment.listener.PaymentNotificationListener;
import io.hhplus.ecommerce.application.product.listener.RankingEventListener;
import io.hhplus.ecommerce.application.usecase.order.ProcessPaymentUseCase;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest
class PaymentEventIntegrationTest {

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    // 새로 추가된 리스너들을 MockBean으로 등록
    @MockBean
    private DataPlatformEventListener dataPlatformEventListener;

    @MockBean
    private PaymentNotificationListener paymentNotificationListener;
    
    @MockBean
    private RankingEventListener rankingEventListener;

    private User testUser;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(new User(1L, "test-user", "test@email.com", BigDecimal.valueOf(100000)));
        Product product = productRepository.save(new Product(1L, "테스트 상품", "P001", BigDecimal.valueOf(10000), 100));
        testOrder = orderRepository.save(new Order(1L, testUser.getId(), List.of(new io.hhplus.ecommerce.domain.order.OrderItem(1L, product, 1))));
    }

    @Test
    @DisplayName("결제 성공 시 관련 이벤트 리스너들이 비동기적으로 호출되는지 검증")
    void verifyEventListenersOnPaymentSuccess() {
        // Given
        ProcessPaymentUseCase.Command command = new ProcessPaymentUseCase.Command(
                testOrder.getId(),
                testUser.getId(),
                "test-idempotency-key"
        );

        // When
        processPaymentUseCase.execute(command);

        // Then: 비동기적으로 실행되는 리스너들의 메서드가 호출되었는지 검증
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(dataPlatformEventListener).handlePaymentCompleted(any());
            verify(paymentNotificationListener).handlePaymentCompleted(any());
            verify(rankingEventListener).handlePaymentCompleted(any());
        });
    }
}
