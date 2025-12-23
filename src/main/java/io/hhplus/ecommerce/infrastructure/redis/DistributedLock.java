package io.hhplus.ecommerce.infrastructure.redis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 분산락 어노테이션
 *
 * 메서드에 이 어노테이션을 적용하면 Redis 기반 분산락이 자동으로 적용됩니다.
 * SpEL 표현식을 사용하여 동적으로 락 키를 생성할 수 있습니다.
 *
 * 사용 예시:
 * <pre>
 * {@code
 * @DistributedLock(key = "'order:product:' + #productId", waitTime = 10, leaseTime = 30)
 * public void createOrder(Long productId, int quantity) { ... }
 * }
 * </pre>
 *
 * 주의사항:
 * - 락 획득 실패 시 IllegalStateException이 발생합니다.
 * - leaseTime 이후 자동으로 락이 해제됩니다 (데드락 방지).
 * - 락 → 트랜잭션 → 커밋 → 락 해제 순서가 중요합니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 락의 이름 (Redis Key)
     *
     * SpEL 표현식을 사용할 수 있습니다.
     * 예시:
     * - "'lock:user:' + #userId"
     * - "'lock:product:' + #request.productId"
     * - "'lock:coupon:' + #couponId"
     *
     * @return 락 키 (SpEL 표현식)
     */
    String key();

    /**
     * 락 획득을 위한 대기 시간 (기본 10초)
     *
     * 이 시간 동안 락을 획득하지 못하면 예외가 발생합니다.
     * 0으로 설정하면 대기 없이 즉시 실패합니다.
     *
     * @return 대기 시간
     */
    long waitTime() default 10L;

    /**
     * 락 임대 시간 (기본 30초)
     *
     * 이 시간이 지나면 자동으로 락이 해제됩니다.
     * 데드락을 방지하기 위한 안전장치입니다.
     *
     * 비즈니스 로직 실행 시간보다 충분히 길게 설정해야 합니다.
     *
     * @return 임대 시간
     */
    long leaseTime() default 30L;

    /**
     * 시간 단위 (기본 초)
     *
     * waitTime과 leaseTime에 적용되는 시간 단위입니다.
     *
     * @return 시간 단위
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
