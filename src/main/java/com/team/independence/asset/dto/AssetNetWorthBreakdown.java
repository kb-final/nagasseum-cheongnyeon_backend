package com.team.independence.asset.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 예산 계산을 위한 순자산 분해.
 * 예적금(DEPOSIT_SAVINGS)만 이자 성장 대상이고, 나머지는 원금 그대로 인정한다.
 */
@Getter
@Builder
public class AssetNetWorthBreakdown {
    /** 이자(복리) 성장 대상 — 예적금 합계 */
    private Long interestBearingAssets;
    /** 이자 성장 없이 원금 그대로 인정하는 금액 — (CASH+ETC) + INVESTMENT×인정률 + manual_assets − 대출 */
    private Long flatRecognizedAssets;
}
