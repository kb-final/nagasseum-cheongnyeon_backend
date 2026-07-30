package com.team.independence.auth.dto;

import com.team.independence.member.domain.Agreement;
import com.team.independence.member.domain.Agreement.AgreementType;
import com.team.independence.member.domain.IncomeBracket;

import java.util.List;

public record SignupRequest(
        String kakaoId,
        String nickname,
        String birthDate,       // YYMMDD (6자리)
        IncomeBracket incomeBracket,   // nullable
        List<AgreementItem> agreements
) {
    public record AgreementItem(
            AgreementType agreementType,
            boolean agreed
    ) {
        public Agreement toDomain() {
            return Agreement.builder()
                    .agreementType(agreementType)
                    .agreed(agreed)
                    .agreementVersion("v1.0")
                    .build();
        }
    }
}