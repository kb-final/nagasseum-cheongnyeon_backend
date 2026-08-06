package com.team.independence.external.codef;

import com.team.independence.external.codef.dto.CodefAccountRequest;
import com.team.independence.external.codef.dto.CodefApiResponse;
import com.team.independence.external.codef.dto.CodefBankAccountResponse;
import com.team.independence.external.codef.dto.CodefCardResponse;
import com.team.independence.external.codef.dto.CodefStockAccountResponse;
import com.team.independence.external.codef.dto.CodefStockFinancialAssetsResponse;

public interface CodefClient {
    CodefApiResponse createAccount(String accessToken, CodefAccountRequest.CodefAccountItem item);
    CodefApiResponse addAccount(String accessToken, String connectedId, CodefAccountRequest.CodefAccountItem item);
    CodefApiResponse deleteAccount(String accessToken, String connectedId, CodefAccountRequest.CodefAccountItem item);
    CodefBankAccountResponse getBankAccountList(String accessToken, String connectedId, String organization, String birthDate);
    CodefStockAccountResponse getStockAccountList(String accessToken, String connectedId, String organization);
    CodefStockFinancialAssetsResponse getStockFinancialAssets(String accessToken, String connectedId, String organization, String account);
    CodefCardResponse getCardList(String accessToken, String connectedId, String organization, String cardNo, String cardPassword, String birthDate);
}
