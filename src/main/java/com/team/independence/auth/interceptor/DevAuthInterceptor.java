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
        request.setAttribute("memberId", DEV_MEMBER_ID);
        return true;
    }
}
