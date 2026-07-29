package com.team.independence.property.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RentItemDto {

    private String sggCd;
    private String umdNm;
    private String jibun;
    private String excluUseAr;    // APT / 오피스텔 / 연립다세대 전용면적
    private String totalFloorAr;  // 단독다가구 연면적
    private String dealYear;
    private String dealMonth;
    private String dealDay;
    private String deposit;       // "24,000" 형태 - 콤마 포함
    private String monthlyRent;
    private String floor;
    private String buildYear;
    private String contractType;
    private String contractTerm;
    private String aptNm;         // 아파트명
    private String offiNm;        // 오피스텔명
    private String mhouseNm;      // 연립다세대명
    // 단독다가구는 단지명 없음

    /** 4종 중 어느 타입이든 단지명 반환 */
    public String getComplexName() {
        if (aptNm != null && !aptNm.isBlank()) return aptNm.trim();
        if (offiNm != null && !offiNm.isBlank()) return offiNm.trim();
        if (mhouseNm != null && !mhouseNm.isBlank()) return mhouseNm.trim();
        return null;
    }

    /** 4종 중 어느 타입이든 면적 반환 */
    public String getAreaValue() {
        if (excluUseAr != null && !excluUseAr.isBlank()) return excluUseAr.trim();
        if (totalFloorAr != null && !totalFloorAr.isBlank()) return totalFloorAr.trim();
        return null;
    }
}