package com.team.independence.member.dto;

import com.team.independence.member.domain.Agreement.AgreementType;

import java.util.List;

public record AgreementUpdateRequest(List<AgreementItem> agreements) {

    public record AgreementItem(AgreementType agreementType, boolean agreed) {}
}