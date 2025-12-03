package io.hhplus.ecommerce.application.product.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 랭킹 조회 응답 DTO
 *
 * @param date     조회 날짜
 * @param rankings 랭킹 목록 (순위순)
 */
public record RankingResponse(
    LocalDate date,
    List<RankingItem> rankings
) {
    public static RankingResponse of(LocalDate date, List<RankingItem> rankings) {
        return new RankingResponse(date, rankings);
    }
}
