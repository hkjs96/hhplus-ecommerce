package io.hhplus.ecommerce.infrastructure.redis;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 분산락 AOP
 *
 * @DistributedLock 어노테이션이 적용된 메서드에 자동으로 분산락을 적용합니다.
 *
 * 동작 흐름:
 * 1. SpEL 표현식을 파싱하여 락 키 생성
 * 2. Redisson RLock을 사용하여 락 획득 시도
 * 3. 락 획득 성공 시 비즈니스 로직 실행
 * 4. finally 블록에서 락 해제 (현재 스레드가 보유한 경우만)
 *
 * Redisson의 Pub/Sub 방식:
 * - tryLock() 호출 시 락을 획득하지 못하면 Redis Subscribe로 대기
 * - 락 해제 시 Redis Publish로 대기 중인 스레드에 알림
 * - Spin Lock 방식보다 CPU 효율적
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * @DistributedLock 어노테이션이 적용된 메서드를 AOP로 가로챕니다.
     */
    @Around("@annotation(io.hhplus.ecommerce.infrastructure.redis.DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        // SpEL 표현식 파싱하여 락 키 생성
        String lockKey = parseLockKey(distributedLock.key(), signature, joinPoint.getArgs());

        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 락 획득 시도
            boolean isLocked = lock.tryLock(
                distributedLock.waitTime(),
                distributedLock.leaseTime(),
                distributedLock.timeUnit()
            );

            if (!isLocked) {
                log.warn("락 획득 실패: key={}, waitTime={}{}, leaseTime={}{}",
                    lockKey,
                    distributedLock.waitTime(),
                    distributedLock.timeUnit(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
                );
                throw new BusinessException(
                    ErrorCode.DUPLICATE_REQUEST,
                    "다른 동일 요청이 처리 중입니다. 잠시 후 다시 시도해주세요. (lockKey: " + lockKey + ")"
                );
            }

            log.info("락 획득 성공: key={}, leaseTime={}{}",
                lockKey,
                distributedLock.leaseTime(),
                distributedLock.timeUnit()
            );

            // 비즈니스 로직 실행
            return joinPoint.proceed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 대기 중 인터럽트 발생: " + lockKey, e);
        } finally {
            // 락 해제 (반드시 현재 스레드가 보유한 경우만)
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("락 해제: key={}", lockKey);
            }
        }
    }

    /**
     * SpEL 표현식 파싱
     *
     * 메서드 파라미터를 SpEL Context에 등록하고 표현식을 평가합니다.
     *
     * 예시:
     * - "'lock:user:' + #userId" → "lock:user:123"
     * - "'lock:product:' + #request.productId" → "lock:product:456"
     * - "'lock:coupon:' + #couponId" → "lock:coupon:789"
     *
     * @param keyExpression SpEL 표현식
     * @param signature 메서드 시그니처
     * @param args 메서드 인자
     * @return 파싱된 락 키
     */
    private String parseLockKey(String keyExpression, MethodSignature signature, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 메서드 파라미터를 SpEL Context에 등록
        String[] parameterNames = signature.getParameterNames();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }
}
