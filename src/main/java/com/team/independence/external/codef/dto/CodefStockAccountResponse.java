package com.team.independence.external.codef.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodefStockAccountResponse {

    private static final String SUCCESS_CODE = "CF-00000";

    private CodefResult result;
    private List<CodefStockAccountItem> data;
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

    public List<CodefStockAccountItem> getData() {
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
    public static class CodefStockAccountItem {
        private String resAccount;  // 계좌번호 (숫자만)
        private String resAccountDisplay;  // 표시용 계좌번호
        private String resAccountName;  // 계좌명(유형)
        private String resAccountNickName; // 계좌별칭
        private String resDepositReceived;  // 예수금
        private String resDepositReceivedD1;
        private String resDepositReceivedD2;
        private String resDepositReceivedF;  // 외화예수금 (비어있지 않으면 외화 계좌)
        private String resValuationAmt;  // 평가금액
        private String resValuationPL;  // 평가손익
        private String resPurchaseAmount;  // 매입금액
        private String resEarningsRate;  // 수익률 [%]
        private String resLoanAmt;  // 대출금액
        private String resPrincipal;  // 원금
        private String resWithdrawalAmt;  // 출금가능금액
    }
}
