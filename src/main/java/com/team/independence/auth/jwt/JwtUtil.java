package com.team.independence.auth.jwt;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-validity-ms}")
    private long accessTokenValidityMs;

    @Value("${jwt.refresh-token-validity-ms}")
    private long refreshTokenValidityMs;

    private Key key;

    @PostConstruct
    private void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public long getRefreshTokenValidityMs() {
        return refreshTokenValidityMs;
    }

    public String createAccessToken(Long memberId) {
        return createToken(memberId, accessTokenValidityMs, "ACCESS");
    }

    public String createRefreshToken(Long memberId) {
        return createToken(memberId, refreshTokenValidityMs, "REFRESH");
    }

    private String createToken(Long memberId, long validityMs, String type) {
        Date now = new Date();

        return Jwts.builder()
                .setSubject(memberId.toString())
                .claim("type", type)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + validityMs))
                .signWith(key)
                .compact();
    }

    public Long getMemberId(String token) {
        return Long.valueOf(getClaims(token).getSubject());
    }

    public boolean isAccessToken(String token) {
        return "ACCESS".equals(getClaims(token).get("type"));
    }

    public boolean isRefreshToken(String token) {
        return "REFRESH".equals(getClaims(token).get("type"));
    }

    public boolean validate(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public void validateOrThrow(String token) {
        try {
            getClaims(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.AUTH_EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}