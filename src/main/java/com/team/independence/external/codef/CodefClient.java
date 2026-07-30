package com.team.independence.external.codef;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.external.codef.dto.CodefAccountRequest;
import com.team.independence.external.codef.dto.CodefApiResponse;
import com.team.independence.external.codef.dto.CodefBankAccountResponse;
import com.team.independence.external.codef.dto.CodefBankInquiryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CODEF 계정 등록 API 클라이언트 클래스
 * CODEF 응답은 application/x-www-form-urlencoded → URL 디코딩 후 JSON 파싱이 필요합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodefClient {

    private static final String CREATE_PATH = "/v1/account/create";
    private static final String ADD_PATH = "/v1/account/add";
    private static final String DELETE_PATH = "/v1/account/delete";
    private static final String BANK_ACCOUNT_LIST_PATH = "/v1/kr/bank/p/account/account-list";

    private final CodefProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CodefApiResponse createAccount(String accessToken, CodefAccountRequest.CodefAccountItem item) {
        CodefAccountRequest body = CodefAccountRequest.builder()
                .accountList(List.of(item))
                .build();
        return call(accessToken, CREATE_PATH, body);
    }

    public CodefApiResponse addAccount(String accessToken, String connectedId, CodefAccountRequest.CodefAccountItem item) {
        CodefAccountRequest body = CodefAccountRequest.builder()
                .connectedId(connectedId)
                .accountList(List.of(item))
                .build();
        return call(accessToken, ADD_PATH, body);
    }

    public CodefBankAccountResponse getBankAccountList(String accessToken, String connectedId,
                                                       String organization, String birthDate) {
        CodefBankInquiryRequest body = CodefBankInquiryRequest.builder()
                .connectedId(connectedId)
                .organization(organization)
                .birthDate(birthDate)
                .build();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<CodefBankInquiryRequest> request = new HttpEntity<>(body, headers);

            log.debug("CODEF 계좌조회 요청: org={}, connectedId={}...",
                    organization, connectedId.substring(0, Math.min(8, connectedId.length())));

            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getApiDomain() + BANK_ACCOUNT_LIST_PATH,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.debug("CODEF 계좌조회 원본 응답: {}", response.getBody());
            String decoded = URLDecoder.decode(response.getBody(), StandardCharsets.UTF_8);
            CodefBankAccountResponse result = objectMapper.readValue(decoded, CodefBankAccountResponse.class);
            log.debug("CODEF 계좌조회 결과: code={}", result.getResultCode());
            return result;

        } catch (Exception e) {
            log.error("CODEF 계좌조회 실패: org={}", organization, e);
            throw new BusinessException(ErrorCode.ASSET_CODEF_API_ERROR);
        }
    }

    public CodefApiResponse deleteAccount(String accessToken, String connectedId, CodefAccountRequest.CodefAccountItem item) {
        CodefAccountRequest body = CodefAccountRequest.builder()
                .connectedId(connectedId)
                .accountList(List.of(item))
                .build();
        return call(accessToken, DELETE_PATH, body);
    }

    private CodefApiResponse call(String accessToken, String path, CodefAccountRequest body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<CodefAccountRequest> request = new HttpEntity<>(body, headers);

            log.debug("CODEF 요청 URL : {}{}", properties.getApiDomain(), path);
            log.debug("CODEF 요청 토큰: {}...", accessToken.substring(0, Math.min(20, accessToken.length())));
            log.debug("CODEF 요청 Body: {}", objectMapper.writeValueAsString(body));

            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getApiDomain() + path,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.debug("CODEF 원본 응답: {}", response.getBody());
            String decoded = URLDecoder.decode(response.getBody(), StandardCharsets.UTF_8);
            log.debug("CODEF 디코딩 응답: {}", decoded);
            CodefApiResponse result = objectMapper.readValue(decoded, CodefApiResponse.class);

            if (!result.isSuccess()) {
                log.error("CODEF API 오류: code={}, message={}", result.getResult().getCode(), result.getResult().getMessage());
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
