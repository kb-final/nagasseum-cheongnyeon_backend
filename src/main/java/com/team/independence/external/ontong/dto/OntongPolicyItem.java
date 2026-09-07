package com.team.independence.external.ontong.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 온통청년 API youthPolicyList 단건 항목
 * 필드명은 API 원본 그대로 유지
 * extras는 매핑되지 않은 나머지 필드를 전부 흡수해 extra 컬럼에 직렬화
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class OntongPolicyItem {

    private String plcyNo;
    private String plcyNm;
    private String plcyExplnCn;
    private String lclsfNm;
    private String mclsfNm;
    private String plcyPvsnMthdCd;
    private String plcySprtCn;
    private String sprvsnInstCdNm;
    private String addAplyQlfcCndCn;
    private String aplyPrdSeCd;
    private String bizPrdBgngYmd;
    private String bizPrdEndYmd;
    private String aplyYmd;
    private String aplyUrlAddr;
    private String refUrlAddr1;
    private String sprtTrgtMinAge;
    private String sprtTrgtMaxAge;
    private String sprtTrgtAgeLmtYn;

    private final Map<String, Object> extras = new LinkedHashMap<>();

    @JsonAnySetter
    public void setExtra(String key, Object value) {
        extras.put(key, value);
    }
}
