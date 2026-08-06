package com.team.independence.auth.interceptor;

import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@Profile("local")
public class DevAuthInterceptor implements HandlerInterceptor {

    private static final Long DEV_MEMBER_ID = 1L;

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        String header = request.getHeader("X-Dev-Member-Id");
        Long memberId = (header != null && !header.isBlank())
                ? Long.parseLong(header)
                : DEV_MEMBER_ID;
        request.setAttribute("memberId", memberId);
        return true;
    }
}
