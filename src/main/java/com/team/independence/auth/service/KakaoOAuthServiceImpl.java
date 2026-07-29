package com.team.independence.auth.service;

import com.team.independence.auth.dto.KakaoCallbackResponse;
import com.team.independence.auth.dto.KakaoTokenResponse;
import com.team.independence.auth.dto.KakaoUserInfo;
import com.team.independence.auth.dto.SignupRequest;
import com.team.independence.auth.dto.TokenResponse;
import com.team.independence.auth.jwt.JwtUtil;
import com.team.independence.auth.mapper.RefreshTokenMapper;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class KakaoOAuthServiceImpl implements KakaoOAuthService {

    private static final String KAKAO_TOKEN_URL    = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USERINFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final MemberService memberService;
    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;

    @Value("${kakao.client.id}")
    private String clientId;

    @Value("${kakao.client.secret:}")
    private String clientSecret;

    @Value("${kakao.redirect.uri}")
    private String redirectUri;

    /**
     * 카카오 인가 코드로 사용자 정보를 조회한 뒤 로그인/회원가입 여부를 판단한다.
     * <p>
     * DB에 kakaoId가 존재하면 JWT를 발급(LOGIN),
     * 존재하지 않으면 kakaoId·닉네임만 반환해 추가 정보 입력을 유도(SIGNUP_REQUIRED).
     * 외부 API 호출이 포함되므로 트랜잭션을 열지 않는다.
     */
    @Override
    public KakaoCallbackResponse handleCallback(String code) {
        KakaoUserInfo userInfo = fetchKakaoUserInfo(code, redirectUri);

        return memberService.findMemberIdByKakaoId(userInfo.getKakaoId())
                .map(memberId -> KakaoCallbackResponse.login(issueTokens(memberId)))
                .orElseGet(() -> KakaoCallbackResponse.signupRequired(
                        userInfo.getKakaoId(), userInfo.getNickname()));
    }

    /**
     * 신규 회원을 생성하고 JWT를 발급한다.
     * <p>
     * birthDate는 YYMMDD 6자리로 받아 LocalDate로 변환한다.
     * 이미 가입된 kakaoId이면 MEMBER_ALREADY_EXISTS 예외를 던진다.
     */
    @Override
    public TokenResponse signup(SignupRequest request) {
        LocalDate birthDate = LocalDate.parse(request.birthDate(),
                DateTimeFormatter.ofPattern("yyMMdd"));

        Long memberId = memberService.createMember(
                request.kakaoId(), request.nickname(), birthDate);

        return issueTokens(memberId);
    }

    private KakaoUserInfo fetchKakaoUserInfo(String code, String redirectUri) {
        KakaoTokenResponse kakaoToken = exchangeCodeForToken(code, redirectUri);
        return getUserInfo(kakaoToken.accessToken());
    }

    /**
     * 카카오 인가 코드를 액세스 토큰으로 교환한다.
     * redirect_uri는 인가 요청 시 사용한 값과 반드시 일치해야 한다.
     */
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

    /**
     * accessToken·refreshToken을 발급하고 refreshToken을 DB에 저장한다.
     * 기존 refreshToken은 교체(deleteByMemberId 후 save)한다.
     */
    private TokenResponse issueTokens(Long memberId) {
        String accessToken  = jwtUtil.createAccessToken(memberId);
        String refreshToken = jwtUtil.createRefreshToken(memberId);

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtUtil.getRefreshTokenValidityMs() / 1000);

        refreshTokenMapper.deleteByMemberId(memberId);
        refreshTokenMapper.save(memberId, refreshToken, expiresAt);

        return new TokenResponse(accessToken, refreshToken, memberId);
    }
}