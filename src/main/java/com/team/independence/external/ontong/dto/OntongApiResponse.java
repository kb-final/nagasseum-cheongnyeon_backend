package com.team.independence.external.ontong.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OntongApiResponse {

    private int resultCode;
    private String resultMessage;
    private OntongResult result;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OntongResult {
        private OntongPaging pagging;
        private List<OntongPolicyItem> youthPolicyList;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OntongPaging {
        private int totCount;
        private int pageNum;
        private int pageSize;
    }
}
