package com.team.independence.external.codef;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.external.codef.dto.CodefAccountRequest;
import com.team.independence.external.codef.dto.CodefApiResponse;
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

    // 계정 등록 데모 버전 Endpoint
    private static final String CREATE_PATH = "/v1/account/create";
    private static final String ADD_PATH = "/v1/account/add";

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
