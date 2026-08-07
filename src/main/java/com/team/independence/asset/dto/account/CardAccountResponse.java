package com.team.independence.asset.dto.account;

import com.team.independence.asset.domain.account.CardAccount;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class CardAccountResponse {
    private String cardNo;
    private boolean isSleep;
    private String cardName;
    private String cardType;
    private boolean isTraffic;
    private String imageLink;
    private LocalDate issueDate;
    private String validPeriod;
    private String state;

    public static CardAccountResponse from(CardAccount card) {
        return CardAccountResponse.builder()
                .cardNo(card.getCardNo())
                .isSleep("Y".equals(card.getIsSleep()))
                .cardName(card.getCardName())
                .cardType(card.getCardType())
                .isTraffic("Y".equals(card.getIsTraffic()))
                .imageLink(card.getImageLink())
                .issueDate(card.getIssueDate())
                .validPeriod(card.getValidPeriod())
                .state(card.getState())
                .build();
    }
}
