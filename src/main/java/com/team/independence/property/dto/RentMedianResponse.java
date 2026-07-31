package com.team.independence.property.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import lombok.Builder;
import lombok.Getter;

/**
 * 조건에 부합하는 최근 6개월 실거래의 보증금·월세 4분위값.
 *
 * <p>표본이 적은 조건(단독다가구·비수도권 등)에서는 sampleCount가 한 자릿수까지 떨어질 수 있어,
 * 프론트가 신뢰도를 함께 노출하도록 건수를 그대로 내려준다.
 */
@Getter
@Builder
public class RentMedianResponse {

    /** 지역 코드 */
    private final String regionCode;

    /** 지역 전체 지명 */
    private final String regionName;

    /** 주거 형태 */
    private final HousingType housingType;

    /** 거래 유형 */
    private final DealType dealType;

    /** 집계 시작 연월 (YYYYMM) */
    private final String baseStartYm;

    /** 집계 종료 연월 (YYYYMM) */
    private final String baseEndYm;

    /** 집계에 사용된 실거래 건수 */
    private final int sampleCount;

    /** 보증금 분위값. 표본이 없으면(sampleCount=0) 세 값 모두 null */
    private final Quartile deposit;

    /** 월세 분위값. 전세 조회이거나 표본이 없으면 세 값 모두 null */
    private final Quartile monthlyRent;

    /**
     * 4분위값 묶음. 객체 자체는 항상 존재하고 값만 null이 될 수 있다.
     * 프론트가 deposit/monthlyRent를 null 체크 없이 타고 들어갈 수 있도록 한 계약이다.
     */
    @Getter
    public static class Quartile {

        /** 25% 분위값 (원) */
        private final Long q1;

        /** 중앙값 (원) */
        private final Long median;

        /** 75% 분위값 (원) */
        private final Long q3;

        private Quartile(Long q1, Long median, Long q3) {
            this.q1 = q1;
            this.median = median;
            this.q3 = q3;
        }

        public static Quartile of(long q1, long median, long q3) {
            return new Quartile(q1, median, q3);
        }

        /** 집계 대상이 없거나(표본 0건) 해당 지표를 쓰지 않는 경우(전세의 월세) */
        public static Quartile empty() {
            return new Quartile(null, null, null);
        }
    }
}
