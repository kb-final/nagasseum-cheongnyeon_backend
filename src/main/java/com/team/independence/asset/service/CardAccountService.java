package com.team.independence.asset.service;

import com.team.independence.asset.dto.account.CardAccountResponse;

import java.util.List;

public interface CardAccountService {
    List<CardAccountResponse> getCardList(Long memberId);
}
