package com.team.independence.external.codef;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.external.codef.dto.CodefAccountRequest;
import com.team.independence.external.codef.dto.CodefApiResponse;
import com.team.independence.external.codef.dto.CodefBankAccountResponse;
import com.team.independence.external.codef.dto.CodefBankInquiryRequest;
import com.team.independence.external.codef.dto.CodefCardInquiryRequest;
import com.team.independence.external.codef.dto.CodefCardResponse;
import com.team.independence.external.codef.dto.CodefStockAccountResponse;
import com.team.independence.external.codef.dto.CodefStockFinancialAssetsRequest;
import com.team.independence.external.codef.dto.CodefStockFinancialAssetsResponse;
import com.team.independence.external.codef.dto.CodefStockInquiryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class CodefRealClient implements CodefClient {

    private static final String CREATE_PATH = "/v1/account/create";
    private static final String ADD_PATH = "/v1/account/add";
    private static final String DELETE_PATH = "/v1/account/delete";
    private static final String BANK_ACCOUNT_LIST_PATH = "/v1/kr/bank/p/account/account-list";
    private static final String STOCK_ACCOUNT_LIST_PATH = "/v1/kr/stock/a/account/account-list";
    private static final String STOCK_FINANCIAL_ASSETS_PATH = "/v1/kr/stock/a/account/financial-assets";
    private static final String CARD_LIST_PATH = "/v1/kr/card/p/account/card-list";

    private final CodefProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public CodefApiResponse createAccount(String accessToken, CodefAccountRequest.CodefAccountItem item) {
        return call(accessToken, CREATE_PATH, CodefAccountRequest.builder().accountList(List.of(item)).build());
    }

    @Override
    public CodefApiResponse addAccount(String accessToken, String connectedId, CodefAccountRequest.CodefAccountItem item) {
        return call(accessToken, ADD_PATH, CodefAccountRequest.builder().connectedId(connectedId).accountList(List.of(item)).build());
    }

    @Override
    public CodefApiResponse deleteAccount(String accessToken, String connectedId, CodefAccountRequest.CodefAccountItem item) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            CodefAccountRequest body = CodefAccountRequest.builder()
                    .connectedId(connectedId).accountList(List.of(item)).build();
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getApiDomain() + DELETE_PATH,
                    HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            String decoded = URLDecoder.decode(response.getBody(), StandardCharsets.UTF_8);
            CodefApiResponse result = objectMapper.readValue(decoded, CodefApiResponse.class);
            String code = result.getResult().getCode();
            if ("CF-04011".equals(code)) {
                log.warn("CODEF deleteAccount: 기관 등록 정보 없음(CF-04011), DB 정리 진행 org={}", item.getOrganization());
                return result;
            }
            if (!result.isSuccess()) {
                log.error("CODEF API 오류: code={}", code);
                throw new BusinessException(ErrorCode.ASSET_CODEF_API_ERROR, result.getResult().getMessage());
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("CODEF deleteAccount 실패: org={}", item.getOrganization(), e);
            throw new BusinessException(ErrorCode.ASSET_CODEF_API_ERROR);
        }
    }

    @Override
    public CodefBankAccountResponse getBankAccountList(String accessToken, String connectedId,
                                                       String organization, String birthDate) {
        CodefBankInquiryRequest body = CodefBankInquiryRequest.builder()
                .connectedId(connectedId).organization(organization).birthDate(birthDate).build();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getApiDomain() + BANK_ACCOUNT_LIST_PATH,
                    HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            String decoded = URLDecoder.decode(response.getBody(), StandardCharsets.UTF_8);
            log.debug("CODEF 계좌조회 응답: org={}", organization);
            return objectMapper.readValue(decoded, CodefBankAccountResponse.class);
        } catch (Exception e) {
            log.error("CODEF 계좌조회 실패: org={}", organization, e);
            throw new BusinessException(ErrorCode.ASSET_CODEF_API_ERROR);
        }
    }

    @Override
    public CodefStockAccountResponse getStockAccountList(String accessToken, String connectedId, String organization) {
        CodefStockInquiryRequest body = CodefStockInquiryRequest.builder()
                .connectedId(connectedId).organization(organization).build();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getApiDomain() + STOCK_ACCOUNT_LIST_PATH,
                    HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            String decoded = URLDecoder.decode(response.getBody(), StandardCharsets.UTF_8);
            log.debug("CODEF 증권 계좌목록 응답: org={}", organization);
            return objectMapper.readValue(decoded, CodefStockAccountResponse.class);
        } catch (Exception e) {
            log.error("CODEF 증권 계좌목록 조회 실패: org={}", organization, e);
            throw new BusinessException(ErrorCode.ASSET_CODEF_API_ERROR);
        }
    }

    @Override
    public CodefStockFinancialAssetsResponse getStockFinancialAssets(String accessToken, String connectedId,
                                                                      String organization, String account) {
        CodefStockFinancialAssetsRequest body = CodefStockFinancialAssetsRequest.builder()
                .connectedId(connectedId).organization(organization).account(account).build();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getApiDomain() + STOCK_FINANCIAL_ASSETS_PATH,
                    HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            String decoded = URLDecoder.decode(response.getBody(), StandardCharsets.UTF_8);
            log.debug("CODEF 종합자산 응답: org={}, account={}...", organization,
                    account.substring(0, Math.min(4, account.length())));
            return objectMapper.readValue(decoded, CodefStockFinancialAssetsResponse.class);
        } catch (Exception e) {
            log.error("CODEF 종합자산 조회 실패: org={}", organization, e);
            throw new BusinessException(ErrorCode.ASSET_CODEF_API_ERROR);
        }
    }

    @Override
    public CodefCardResponse getCardList(String accessToken, String connectedId, String organization,
                                         String cardNo, String cardPassword, String birthDate) {
        CodefCardInquiryRequest body = CodefCardInquiryRequest.builder()
                .connectedId(connectedId)
                .organization(organization)
                .cardNo(cardNo != null ? cardNo : "")
                .cardPassword(cardPassword != null ? cardPassword : "")
                .birthDate(birthDate != null ? birthDate : "")
                .build();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getApiDomain() + CARD_LIST_PATH,
                    HttpMethod.POST, new HttpEntity<>(body, headers), String.class
            );
            String decoded = URLDecoder.decode(response.getBody(), StandardCharsets.UTF_8);
            log.debug("CODEF 카드목록 응답: org={}", organization);
            return objectMapper.readValue(decoded, CodefCardResponse.class);
        } catch (Exception e)  {
            log.error("CODEF 카드목록 조회 실패={}", organization, e);
            throw new BusinessException(ErrorCode.ASSET_CODEF_API_ERROR);
        }
    }

    private CodefApiResponse call(String accessToken, String path, CodefAccountRequest body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getApiDomain() + path,
                    HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            String decoded = URLDecoder.decode(response.getBody(), StandardCharsets.UTF_8);
            CodefApiResponse result = objectMapper.readValue(decoded, CodefApiResponse.class);
            if (!result.isSuccess()) {
                log.error("CODEF API 오류: code={}", result.getResult().getCode());
                throw new BusinessException(ErrorCode.ASSET_CODEF_API_ERROR, result.getResult().getMessage());
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("CODEF API 호출 실패: path={}", path, e);
            throw new BusinessException(ErrorCode.ASSET_CODEF_API_ERROR);
        }
    }
}
