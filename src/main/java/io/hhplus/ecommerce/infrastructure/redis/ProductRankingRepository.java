package io.hhplus.ecommerce.infrastructure.redis;

import io.hhplus.ecommerce.domain.product.ProductRanking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis Sorted Set 기반 상품 랭킹 저장소
 *
 * 키 패턴: ranking:product:orders:daily:{yyyyMMdd}
 * - member: productId (String)
 * - score: 누적 판매 수량 (Double)
 *
 * TTL: 26시간 (다음날까지 조회 가능하도록 여유 시간)
 *
 * 핵심 특징:
 * - ZINCRBY: 원자적 score 증가 (동시성 안전)
 * - ZREVRANGE: score 높은 순 조회
 * - 별도 분산락 불필요 (Redis 단일 스레드 + ZINCRBY 원자성)
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ProductRankingRepository {

    private static final String KEY_PREFIX = "ranking:product:orders:daily:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration TTL = Duration.ofHours(26); // 26시간 (여유 시간)

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 상품 판매량을 랭킹에 반영 (ZINCRBY - 원자적)
     *
     * @param productId 상품 ID
     * @param quantity  판매 수량
     */
    public void incrementScore(String productId, int quantity) {
        String key = generateDailyKey(LocalDate.now());

        try {
            // 1. Score 증가 (원자적)
            redisTemplate.opsForZSet().incrementScore(key, productId, quantity);

            // 2. TTL 설정 (이미 있어도 갱신)
            redisTemplate.expire(key, TTL);

            log.debug("랭킹 갱신 성공: key={}, productId={}, quantity={}", key, productId, quantity);

        } catch (Exception e) {
            log.error("랭킹 갱신 실패: key={}, productId={}, quantity={}", key, productId, quantity, e);
            throw e;
        }
    }

    /**
     * 상위 N개 상품 조회 (ZREVRANGE - score 높은 순)
     *
     * @param date  조회 날짜
     * @param limit 조회 개수
     * @return 랭킹 목록 (순위순)
     */
    public List<ProductRanking> getTopN(LocalDate date, int limit) {
        String key = generateDailyKey(date);

        try {
            // score 높은 순으로 조회 (0-based index)
            Set<ZSetOperations.TypedTuple<String>> result =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);

            if (result == null || result.isEmpty()) {
                log.debug("랭킹 데이터 없음: key={}", key);
                return List.of();
            }

            // ProductRanking으로 변환
            return result.stream()
                .map(tuple -> ProductRanking.of(
                    Long.parseLong(tuple.getValue()),
                    tuple.getScore().intValue()
                ))
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("랭킹 조회 실패: key={}, limit={}", key, limit, e);
            return List.of();  // 실패 시 빈 리스트 반환 (Fallback)
        }
    }

    /**
     * 특정 상품의 순위 조회 (ZREVRANK)
     *
     * @param date      조회 날짜
     * @param productId 상품 ID
     * @return 순위 (1부터 시작, 없으면 -1)
     */
    public int getRank(LocalDate date, String productId) {
        String key = generateDailyKey(date);

        try {
            Long rank = redisTemplate.opsForZSet().reverseRank(key, productId);

            // rank는 0-based이므로 1을 더해야 실제 순위
            return rank != null ? rank.intValue() + 1 : -1;

        } catch (Exception e) {
            log.error("순위 조회 실패: key={}, productId={}", key, productId, e);
            return -1;
        }
    }

    /**
     * 특정 상품의 판매량 조회 (ZSCORE)
     *
     * @param date      조회 날짜
     * @param productId 상품 ID
     * @return 판매 수량 (없으면 0)
     */
    public int getScore(LocalDate date, String productId) {
        String key = generateDailyKey(date);

        try {
            Double score = redisTemplate.opsForZSet().score(key, productId);
            return score != null ? score.intValue() : 0;

        } catch (Exception e) {
            log.error("판매량 조회 실패: key={}, productId={}", key, productId, e);
            return 0;
        }
    }

    /**
     * 일간 랭킹 키 생성
     *
     * @param date 날짜
     * @return ranking:product:orders:daily:20251203
     */
    private String generateDailyKey(LocalDate date) {
        return KEY_PREFIX + date.format(DATE_FORMATTER);
    }
}
