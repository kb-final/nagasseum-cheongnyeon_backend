package com.team.independence.external.codef;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.external.codef.dto.CodefTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * CODEF OAuth2 토큰을 발급하고 Redis에 캐시합니다.
 * CODEF 토큰 유효기간: 7일 → Redis TTL: 6일 (만료 전 갱신 보장)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodefTokenManager {

    private static final String REDIS_KEY = "codef:oauth:token";
    private static final long TTL_DAYS = 6L;

    private final CodefProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public String getAccessToken() {
        String cached = redisTemplate.opsForValue().get(REDIS_KEY);
        if (cached != null) {
            return cached;
        }
        return issueAndCache();
    }

    private String issueAndCache() {
        try {
            String credentials = properties.getClientId() + ":" + properties.getClientSecret();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes("UTF-8"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);

            HttpEntity<String> request = new HttpEntity<>("grant_type=client_credentials&scope=read", headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getOauthDomain() + "/oauth/token",
                    HttpMethod.POST,
                    request,
                    String.class
            );

            CodefTokenResponse tokenResponse = objectMapper.readValue(response.getBody(), CodefTokenResponse.class);
            String token = tokenResponse.getAccessToken();

            redisTemplate.opsForValue().set(REDIS_KEY, token, TTL_DAYS, TimeUnit.DAYS);
            return token;

        } catch (Exception e) {
            log.error("CODEF 토큰 발급 실패", e);
            throw new BusinessException(ErrorCode.ASSET_CODEF_API_ERROR);
        }
    }
}
