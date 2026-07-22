package com.team.independence.config;

import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

import javax.servlet.Filter;

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

    /** UTF-8 인코딩 필터 */
    @Override
    protected Filter[] getServletFilters() {
        CharacterEncodingFilter encodingFilter = new CharacterEncodingFilter();
        encodingFilter.setEncoding("UTF-8");
        encodingFilter.setForceEncoding(true);
        return new Filter[]{ encodingFilter };
    }
}
