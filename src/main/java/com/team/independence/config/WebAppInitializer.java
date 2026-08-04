package com.team.independence.config;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

import javax.servlet.Filter;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * web.xml 대체 (자바 Config 방식).
 * 톰캣이 기동될 때 이 클래스를 찾아 DispatcherServlet을 등록한다.
 *
 * ★ Tomcat 9 필수: Spring 5.3은 javax.servlet 기반. Tomcat 10(jakarta)에서는 동작하지 않음.
 */
public class WebAppInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {

    /** 공통 빈: DataSource, MyBatis, Redis, 트랜잭션 + 프로필별 설정 */
    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class[]{ RootConfig.class, ApiConfig.class, BatchConfig.class };
    }

    /** 웹 계층 빈: Controller, 인터셉터, 메시지 컨버터 */
    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class[]{ WebConfig.class };
    }

    /** 모든 요청을 DispatcherServlet이 처리 */
    @Override
    protected String[] getServletMappings() {
        return new String[]{ "/" };
    }

    @Override
    protected Filter[] getServletFilters() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.addAllowedOrigin("http://localhost:5173");
        corsConfig.addAllowedOrigin("https://www.nagasseum.com");
        corsConfig.addAllowedMethod("*");
        corsConfig.addAllowedHeader("*");
        corsConfig.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", corsConfig);

        CharacterEncodingFilter encodingFilter = new CharacterEncodingFilter();
        encodingFilter.setEncoding("UTF-8");
        encodingFilter.setForceEncoding(true);

        // CorsFilter가 CharacterEncodingFilter보다 먼저 실행되어야 preflight를 MVC 전에 처리함
        return new Filter[]{ new CorsFilter(source), encodingFilter };
    }

    /**
     * Spring 컨텍스트 초기화 전에 .env 파일을 읽어 시스템 프로퍼티로 주입합니다.
     * VM options(-D)나 이미 설정된 시스템 프로퍼티가 있으면 .env 값을 덮어쓰지 않습니다.
     */
    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        loadDotEnv(servletContext);
        super.onStartup(servletContext);
    }

    private void loadDotEnv(ServletContext servletContext) {
        Path envFile = findEnvFile(servletContext);
        if (envFile == null) return;

        try {
            Files.lines(envFile)
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#") && line.contains("="))
                    .forEach(line -> {
                        int eq = line.indexOf('=');
                        String key   = line.substring(0, eq).trim();
                        String value = line.substring(eq + 1).trim();
                        if (System.getProperty(key) == null) {
                            System.setProperty(key, value);
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    /**
     * <.env 탐색 순서>
     * 1) 배포된 WAR 실제 경로에서 상위로 탐색
     * 2) user.dir 에서 상위로 탐색
     */
    private Path findEnvFile(ServletContext servletContext) {
        String realPath = servletContext.getRealPath("/");
        if (realPath != null) {
            Path dir = Paths.get(realPath);
            for (int i = 0; i < 4; i++) {
                Path candidate = dir.resolve(".env");
                if (Files.exists(candidate)) return candidate;
                Path parent = dir.getParent();
                if (parent == null) break;
                dir = parent;
            }
        }
        Path dir = Paths.get(System.getProperty("user.dir", "."));
        for (int i = 0; i < 4; i++) {
            Path candidate = dir.resolve(".env");
            if (Files.exists(candidate)) return candidate;
            Path parent = dir.getParent();
            if (parent == null) break;
            dir = parent;
        }
        return null;
    }
}
