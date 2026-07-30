package com.team.independence.auth.service;

import com.team.independence.auth.dto.KakaoCallbackResponse;
import com.team.independence.auth.dto.KakaoTokenResponse;
import com.team.independence.auth.dto.KakaoUserInfo;
import com.team.independence.auth.dto.SignupRequest;
import com.team.independence.auth.dto.TokenResponse;
import com.team.independence.auth.jwt.JwtUtil;
import com.team.independence.auth.repository.RefreshTokenStore;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.member.domain.Agreement;
import com.team.independence.member.service.AgreementService;
import com.team.independence.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KakaoOAuthServiceImpl implements KakaoOAuthService {

    private static final String KAKAO_TOKEN_URL    = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USERINFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final MemberService memberService;
    private final AgreementService agreementService;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;

    @Value("${kakao.client.id}")
    private String clientId;

    @Value("${kakao.client.secret:}")
    private String clientSecret;

    @Value("${kakao.redirect.uri}")
    private String redirectUri;

    @Override
    public KakaoCallbackResponse handleCallback(String code) {
        KakaoUserInfo userInfo = fetchKakaoUserInfo(code, redirectUri);

        return memberService.findMemberIdByKakaoId(userInfo.getKakaoId())
                .map(memberId -> KakaoCallbackResponse.login(issueTokens(memberId)))
                .orElseGet(() -> KakaoCallbackResponse.signupRequired(
                        userInfo.getKakaoId(), userInfo.getNickname()));
    }

    @Override
    @Transactional
    public TokenResponse signup(SignupRequest request) {
        LocalDate birthDate;
        try {
            birthDate = LocalDate.parse(request.birthDate(), DateTimeFormatter.ofPattern("yyMMdd"));
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Long memberId = memberService.createMember(
                request.kakaoId(), request.nickname(), birthDate, request.incomeBracket());

        if (request.agreements() != null && !request.agreements().isEmpty()) {
            List<Agreement> agreements = request.agreements().stream()
                    .map(SignupRequest.AgreementItem::toDomain)
                    .collect(Collectors.toList());
            agreementService.saveAll(memberId, agreements);
        }

        return issueTokens(memberId);
    }

    private KakaoUserInfo fetchKakaoUserInfo(String code, String redirectUri) {
        KakaoTokenResponse kakaoToken = exchangeCodeForToken(code, redirectUri);
        return getUserInfo(kakaoToken.accessToken());
    }

    private KakaoTokenResponse exchangeCodeForToken(String code, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("redirect_uri", redirectUri);
        body.add("code", code);
        body.add("client_secret", clientSecret);

        KakaoTokenResponse response = restTemplate.postForObject(
                KAKAO_TOKEN_URL,
                new HttpEntity<>(body, headers),
                KakaoTokenResponse.class
        );
        if (response == null) {
            throw new BusinessException(ErrorCode.AUTH_KAKAO_API_ERROR);
        }
        return response;
    }

    private KakaoUserInfo getUserInfo(String kakaoAccessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(kakaoAccessToken);

        KakaoUserInfo userInfo = restTemplate.exchange(
                KAKAO_USERINFO_URL,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                KakaoUserInfo.class
        ).getBody();
        if (userInfo == null) {
            throw new BusinessException(ErrorCode.AUTH_KAKAO_API_ERROR);
        }
        return userInfo;
    }

    @Override
    public TokenResponse refresh(String refreshToken) {
        jwtUtil.validateOrThrow(refreshToken);

        if (!jwtUtil.isRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        Long memberId = jwtUtil.getMemberId(refreshToken);

        String stored = refreshTokenStore.find(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

        if (!stored.equals(refreshToken)) {
            // 저장된 토큰과 불일치 → 탈취 후 재사용 시도
            refreshTokenStore.delete(memberId);
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        return issueTokens(memberId);
    }

    @Override
    public void logout(Long memberId) {
        refreshTokenStore.delete(memberId);
    }

    /** accessToken·refreshToken 발급. Redis에 저장하며 TTL로 만료를 관리한다. */
    private TokenResponse issueTokens(Long memberId) {
        String accessToken  = jwtUtil.createAccessToken(memberId);
        String refreshToken = jwtUtil.createRefreshToken(memberId);

        refreshTokenStore.save(memberId, refreshToken);

        return new TokenResponse(accessToken, refreshToken, memberId);
    }
}