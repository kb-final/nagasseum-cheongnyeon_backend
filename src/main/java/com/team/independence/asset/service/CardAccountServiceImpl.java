package com.team.independence.asset.service;

import com.team.independence.asset.dto.account.CardAccountResponse;
import com.team.independence.asset.mapper.CardAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CardAccountServiceImpl implements CardAccountService {

    private final CardAccountMapper cardAccountMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CardAccountResponse> getCardList(Long memberId) {
        return cardAccountMapper.findByMemberId(memberId).stream()
                .map(CardAccountResponse::from)
                .toList();
    }
}
