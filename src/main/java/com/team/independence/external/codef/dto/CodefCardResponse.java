package com.team.independence.external.codef.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodefCardResponse {

    private static final String SUCCESS_CODE = "CF-00000";

    private CodefResult result;
    private List<CodefCardItem> data;

    public boolean isSuccess() {
        return result != null && SUCCESS_CODE.equals(result.getCode());
    }

    public String getResultCode() {
        return result != null ? result.getCode() : null;
    }

    public String getResultMessage() {
        return result != null ? result.getMessage() : null;
    }

    public List<CodefCardItem> getCards() {
        return data != null ? data : Collections.emptyList();
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodefResult {
        private String code;
        private String message;
        private String transactionId;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodefCardItem {
        private String resCardNo;
        private String resSleepYN;
        private String resCardName;
        private String resCardType;
        private String resTrafficYN;
        private String resImageLink;
        private String resIssueDate;
        private String resValidPeriod;
        private String resState;
    }
}
