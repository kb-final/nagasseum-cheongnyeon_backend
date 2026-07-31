package com.team.independence.external.codef.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodefBankAccountResponse {

    private static final String SUCCESS_CODE = "CF-00000";

    private CodefResult result;
    private CodefBankData data;
    private String connectedId;

    public boolean isSuccess() {
        return result != null && SUCCESS_CODE.equals(result.getCode());
    }

    public String getResultCode() {
        return result != null ? result.getCode() : null;
    }

    public String getResultMessage() {
        return result != null ? result.getMessage() : null;
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
    public static class CodefBankData {
        private List<CodefDepositItem> resDepositTrust;
        private List<CodefFundItem> resFund;
        private List<CodefLoanItem> resLoan;

        public List<CodefDepositItem> getResDepositTrust() {
            return resDepositTrust != null ? resDepositTrust : Collections.emptyList();
        }

        public List<CodefFundItem> getResFund() {
            return resFund != null ? resFund : Collections.emptyList();
        }

        public List<CodefLoanItem> getResLoan() {
            return resLoan != null ? resLoan : Collections.emptyList();
        }
    }

    /**
     * resAccountDeposit: 11=입출금, 12=적금·청약(혼용), 13=정기예금
     * resOverdraftAcctYN: 1이면 마이너스통장 → 제외
     */
    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodefDepositItem {
        private String resAccount;
        private String resAccountDisplay;
        private String resAccountBalance;
        private String resAccountDeposit;
        private String resAccountName;
        private String resAccountCurrency;
        private String resLastTranDate;
        private String resOverdraftAcctYN;
        private String resAccountStartDate;
        private String resAccountEndDate;
        private String resLoanKind;
        private String resLoanBalance;
        private String resLoanStartDate;
        private String resLoanEndDate;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodefFundItem {
        private String resAccount;
        private String resAccountDisplay;
        private String resAccountBalance;
        private String resAccountName;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodefLoanItem {
        private String resAccount;
        private String resAccountDisplay;
        private String resAccountName;
        private String resLoanBalance;
        private String resLoanStartDate;
        private String resLoanEndDate;
    }
}
