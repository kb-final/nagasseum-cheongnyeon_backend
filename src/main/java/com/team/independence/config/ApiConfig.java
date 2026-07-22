package com.team.independence.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * API 전용 설정. profile=api 로 실행할 때만 활성화된다.
 * (JWT 인터셉터 등록, CORS 등 API 서버에만 필요한 설정을 여기에 둔다)
 *
 * 실행 예: -Dspring.profiles.active=prod,api
 */
@Configuration
@Profile("api")
public class ApiConfig {
    // TODO: JwtInterceptor 등록, CORS 설정
}
