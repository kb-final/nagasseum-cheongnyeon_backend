package com.team.independence.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.common.resolver.LoginMemberArgumentResolver;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 웹 계층 설정 (Controller · ControllerAdvice 스캔, JSON 변환).
 * DispatcherServlet 전용 컨텍스트.
 */
@Configuration
@EnableWebMvc
@ComponentScan(
        basePackages = "com.team.independence",
        useDefaultFilters = false,
        includeFilters = {
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Controller.class),
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = ControllerAdvice.class)
        })
public class WebConfig implements WebMvcConfigurer {

    private final HandlerInterceptor authInterceptor;
    private final ObjectMapper objectMapper;
    private final LoginMemberArgumentResolver loginMemberArgumentResolver;

    public WebConfig(HandlerInterceptor authInterceptor, ObjectMapper objectMapper,
                     LoginMemberArgumentResolver loginMemberArgumentResolver) {
        this.authInterceptor = authInterceptor;
        this.objectMapper = objectMapper;
        this.loginMemberArgumentResolver = loginMemberArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/v1/oauth/**",
                        "/api/v1/auth/refresh",
                        "/api/v1/members/health",
                        "/api/v1/admin/batch/**",
                        "/api/v1/policies",
                        "/api/v1/policies/**"
                );
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginMemberArgumentResolver);
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, com.team.independence.member.domain.Agreement.AgreementType.class,
                source -> com.team.independence.member.domain.Agreement.AgreementType.valueOf(source.toUpperCase()));
    }

    /** LocalDate/LocalDateTime을 ISO-8601 문자열로 직렬화 */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(new StringHttpMessageConverter(StandardCharsets.UTF_8));
        converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
    }
}
