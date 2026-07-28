package com.team.independence.external.codef.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CodefApiResponse {

    private CodefResult result;
    private CodefData data;

    public boolean isSuccess() {
        // 결과 코드가 CF-00000인 경우, 계정 등록 성공
        return result != null && "CF-00000".equals(result.getCode());
    }

    @Getter
    @NoArgsConstructor
    public static class CodefResult {
        private String code;
        private String message;
        private String extraMessage;
        private String transactionId;
    }

    @Getter
    @NoArgsConstructor
    public static class CodefData {
        private List<CodefAccountResult> successList;
        private List<CodefAccountResult> errorList;
        private String connectedId;
    }

    @Getter
    @NoArgsConstructor
    public static class CodefAccountResult {
        private String code;
        private String message;
        private String organization;
        private String businessType;
    }
}
