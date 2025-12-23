package io.hhplus.ecommerce.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Cache 설정 (Redis 기반)
 *
 * 캐시 전략: Cache-Aside 패턴
 * - 조회 시: 캐시 확인 → 없으면 DB 조회 → 캐시 저장
 * - 갱신 시: DB 갱신 → 캐시 무효화 (@CacheEvict)
 *
 * 캐시별 TTL:
 * - products: 1시간 (상품 정보는 자주 변경되지 않음)
 * - topProducts: 5분 (인기 상품은 자주 갱신, 배치 주기와 동일)
 * - carts: 1일 (장바구니는 사용자별 격리, 긴 TTL)
 *
 * Thundering Herd 방지:
 * - sync=true: 동일 키에 대한 동시 요청 시 첫 요청만 DB 조회, 나머지는 대기
 * - 확률적 조기 만료(Probabilistic Early Expiration): 만료 직전 무작위 갱신
 *
 * Note: 테스트 환경에서는 비활성화 (@Profile("!test"))
 */
@Configuration
@EnableCaching
@Profile("!test")
public class CacheConfig {

    /**
     * Jackson ObjectMapper 설정
     * - JavaTimeModule: LocalDateTime 등 Java 8 Time API 지원
     * - WRITE_DATES_AS_TIMESTAMPS=false: ISO-8601 형식 사용
     */
    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Explicitly write type information so Redis can deserialize cache entries back to their concrete types
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("io.hhplus.ecommerce")
            // Common JDK collections (mutable/immutable)
            .allowIfSubType("java.util.List")
            .allowIfSubType("java.util.ArrayList")
            .allowIfSubType("java.util.LinkedList")
            .allowIfSubType("java.util.Map")
            .allowIfSubType("java.util.HashMap")
            .allowIfSubType("java.util.ImmutableCollections") // JDK immutable collections (ListN 등)
            // Primitive wrappers and String
            .allowIfSubType("java.lang.Long")
            .allowIfSubType("java.lang.Integer")
            .allowIfSubType("java.lang.String")
            .allowIfSubType("java.lang.Boolean")
            .build();
        // CartResponse 등 record 클래스는 final이므로 NON_FINAL로는 타입 정보가 기록되지 않는다.
        // EVERYTHING으로 설정해 모든 객체에 타입 정보를 남기도록 한다.
        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);
        return mapper;
    }

    /**
     * 기본 Redis 캐시 설정
     */
    private RedisCacheConfiguration defaultCacheConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))  // 기본 TTL: 1시간
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()
                        )
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(objectMapper())
                        )
                )
                .disableCachingNullValues();  // null 값 캐싱 방지
    }

    /**
     * Cart 전용 ObjectMapper: 기본 타이핑을 끄고 DTO 타입 고정 직렬화를 사용한다.
     */
    private ObjectMapper cartObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    private Jackson2JsonRedisSerializer<?> cartValueSerializer() {
        return new Jackson2JsonRedisSerializer<>(
            cartObjectMapper(),
            io.hhplus.ecommerce.application.cart.dto.CartResponse.class
        );
    }

    /**
     * RedisCacheManager 설정
     *
     * 캐시별 TTL 전략:
     * - products: 1시간 (상품 목록 조회)
     * - product: 1시간 (상품 상세 조회)
     * - topProducts: 5분 (인기 상품, 배치 주기와 동일)
     * - carts: 1일 (장바구니, 사용자별 격리)
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 캐시별 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 상품 목록 캐시: 1시간
        cacheConfigurations.put("products",
                defaultCacheConfig()
                        .entryTtl(Duration.ofHours(1))
        );

        // 상품 상세 캐시: 1시간
        cacheConfigurations.put("product",
                defaultCacheConfig()
                        .entryTtl(Duration.ofHours(1))
        );

        // 인기 상품 캐시: 5분 (배치 주기와 동일)
        cacheConfigurations.put("topProducts",
                defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(5))
        );

        // 장바구니 캐시: 1일
        cacheConfigurations.put("carts",
                defaultCacheConfig()
                        .entryTtl(Duration.ofDays(1))
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        cartValueSerializer()
                                )
                        )
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig())
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()  // 트랜잭션 커밋 후 캐시 갱신
                .build();
    }

    /**
     * 캐시 역직렬화 오류 시 해당 키를 제거해 반복 오류를 방지한다.
     * (기존에 타입 정보 없이 저장된 오래된 엔트리가 있을 때 유용)
     */
    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                cache.evict(key);
                super.handleCacheGetError(exception, cache, key);
            }
        };
    }
}
