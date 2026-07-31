package com.team.independence.external.codef.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodefStockFinancialAssetsResponse {

    private static final String SUCCESS_CODE = "CF-00000";

    private CodefResult result;
    private CodefAssetsData data;
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
    public static class CodefAssetsData {
        private String resAccount;
        private String resAccountEx;
        private String resDepositReceived;
        private List<CodefStockItem> resItemList;

        public List<CodefStockItem> getResItemList() {
            return resItemList != null ? resItemList : Collections.emptyList();
        }
    }

    /**
     * resProductTypeCd: 01=주식, 02=펀드, 03=CMA, 04=해외주식, 05=신탁/퇴직연금,
     *                   06=채권, 07=RP, 08=CD/CP, 09=ELS/DLS, 10=해외무추얼펀드,
     *                   11=Wrap, 12=외화RP, 13=연금저축, 14=선물옵션, 99=기타
     */
    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodefStockItem {
        private String resProductTypeCd;  // 상품유형코드
        private String resProductType;  // 상품유형명
        private String resItemCode;  // 종목코드
        private String resItemName;  // 종목명
        private String resQuantity;  // 수량
        private String resPurchaseAmount; // 매입금액
        private String resValuationAmt;  // 평가금액
        private String resValuationPL;  // 평가손익
        private String resEarningsRate;  // 수익률
        private String resPresentAmt;  // 현재가
        private String resAvgPresentAmt;  // 평균매입가
        private String resDepositReceived;  // 예수금
        private String resAccountCurrency;  // 통화코드
        private String resBalanceType;  // 잔고유형
        private String resSettleQuantity;  // 정산수량
        private String resResultCode;  // 결과코드
        private String resResultDesc;  // 결과메시지
    }
}
