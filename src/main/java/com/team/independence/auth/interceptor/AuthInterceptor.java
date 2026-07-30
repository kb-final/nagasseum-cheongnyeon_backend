package com.team.independence.auth.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.auth.jwt.JwtUtil;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final Long DEV_MEMBER_ID = 1L;

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Value("${kakao.auth.enabled:false}")
    private boolean kakaoAuthEnabled;

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        if (!kakaoAuthEnabled) {
            request.setAttribute("memberId", DEV_MEMBER_ID);
            return true;
        }

        String token = extractToken(request);
        if (token == null) {
            writeError(response, new BusinessException(
                    com.team.independence.common.exception.ErrorCode.UNAUTHORIZED));
            return false;
        }

        try {
            jwtUtil.validateOrThrow(token);
            if (!jwtUtil.isAccessToken(token)) {
                writeError(response, new BusinessException(
                        com.team.independence.common.exception.ErrorCode.AUTH_INVALID_TOKEN));
                return false;
            }
            request.setAttribute("memberId", jwtUtil.getMemberId(token));
            return true;
        } catch (BusinessException e) {
            writeError(response, e);
            return false;
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void writeError(HttpServletResponse response, BusinessException e) throws Exception {
        response.setStatus(e.getErrorCode().getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(e.getErrorCode())));
    }
}