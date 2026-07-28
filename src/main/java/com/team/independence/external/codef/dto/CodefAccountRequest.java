package com.team.independence.external.codef.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CodefAccountRequest {

    private String connectedId;
    private List<CodefAccountItem> accountList;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CodefAccountItem {
        private String countryCode;
        private String businessType;
        private String clientType;
        private String organization;
        private String loginType;
        private String id;
        private String password;
        private String birthDate;
        private String loginTypeLevel;
        private String clientTypeLevel;
        private String cardNo;
        private String cardPassword;
    }
}
