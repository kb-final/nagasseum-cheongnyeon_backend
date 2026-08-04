package com.team.independence.goal.dto;

import com.team.independence.goal.domain.SavingBasis;
import java.time.YearMonth;
import lombok.Builder;
import lombok.Getter;

/**
 * 특정 월 저축액을 유지했을 때의 예상 달성 시점.
 *
 * <p>목표 달성 상세 조회의 forecasts 항목과 월 저축액 시뮬레이션 응답이 같은 구조여야 해서
 * 한 클래스를 공유한다. 화면이 추천 금액과 직접 입력 금액을 같은 방식으로 처리한다.
 *
 * <p>YearMonth는 "2027-12" 형태로 직렬화된다
 * (RootConfig의 JavaTimeModule + WRITE_DATES_AS_TIMESTAMPS=false).
 */
@Getter
@Builder
public class GoalForecastResponse {

    private SavingBasis basis;
    /** 해당 기준의 월 저축액 */
    private Long monthlySaving;
    /** 예상 달성 시점. 이미 달성했거나 탐색 상한 내에 도달하지 못하면 null */
    private YearMonth expectedDate;
    /** 고정 기준 대비 앞당겨진 개월 수(양수=단축, 0=동일, 음수=지연). 비교 불가면 null */
    private Integer monthsDiff;

    /**
     * 도달 개월수를 화면용 응답으로 옮긴다.
     * 상세 조회와 시뮬레이션이 같은 금액에 같은 값을 내도록 조립은 여기 한곳에서만 한다.
     *
     * @param months      목표 도달까지 남은 개월수. 이미 달성했으면 0, 계산 불가면 null
     * @param fixedMonths 고정 저축액 기준 도달 개월수(비교 기준). 없으면 monthsDiff는 null
     */
    public static GoalForecastResponse of(SavingBasis basis, long monthlySaving,
                                          Long months, Long fixedMonths) {
        // 계산 불가(저축액 0 이하 또는 탐색 상한 초과) — 시점도 비교도 낼 수 없다.
        if (months == null) {
            return build(basis, monthlySaving, null, null);
        }
        // 이미 달성했으면 예상 시점을 따질 게 없다.
        if (months == 0) {
            return build(basis, monthlySaving, null, 0);
        }

        Integer monthsDiff = fixedMonths == null ? null : (int) (fixedMonths - months);
        return build(basis, monthlySaving, YearMonth.now().plusMonths(months), monthsDiff);
    }

    private static GoalForecastResponse build(SavingBasis basis, long monthlySaving,
                                              YearMonth expectedDate, Integer monthsDiff) {
        return GoalForecastResponse.builder()
                .basis(basis)
                .monthlySaving(monthlySaving)
                .expectedDate(expectedDate)
                .monthsDiff(monthsDiff)
                .build();
    }
}
