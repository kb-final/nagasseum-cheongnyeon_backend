package com.team.independence.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.*;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import javax.sql.DataSource;

/**
 * 공통 인프라 빈 설정 (DataSource, MyBatis, Redis, 트랜잭션).
 * 프로필과 무관하게 api/batch 양쪽 모두에서 로딩된다.
 *
 * ※ Mapper 스캔은 @MapperScan 애노테이션으로 처리한다.
 *   @Bean 메서드로 MapperScannerConfigurer를 만들면 이 클래스가 너무 일찍
 *   인스턴스화되어 @Value 주입이 안 되는 문제가 발생한다.
 */
@Configuration
@PropertySource("classpath:config/app.properties")
@PropertySource(value = "file:${user.dir}/.env", ignoreResourceNotFound = true)
@ComponentScan(
        basePackages = "com.team.independence",
        excludeFilters = {
                // Controller / ControllerAdvice 는 서블릿 컨텍스트(WebConfig)가 담당
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Controller.class),
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = ControllerAdvice.class),
                // 설정 클래스는 WebAppInitializer가 직접 등록하므로 스캔 제외
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.team\\.independence\\.config\\..*"),
                // 스케줄러는 BatchConfig(@Profile("batch"))만 스캔 → api 프로필에서는 미등록
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.team\\.independence\\.scheduler\\..*")
        })
@MapperScan(basePackages = "com.team.independence", annotationClass = Mapper.class)
@EnableTransactionManagement
@EnableAsync
public class RootConfig {

    @Value("${DB_URL:jdbc:mysql://localhost:3306/independence?serverTimezone=Asia/Seoul&characterEncoding=UTF-8}")
    private String dbUrl;

    @Value("${DB_USERNAME:root}")
    private String dbUsername;

    @Value("${DB_PASSWORD:1234}")
    private String dbPassword;

    @Value("${REDIS_HOST:localhost}")
    private String redisHost;

    @Value("${REDIS_PORT:6379}")
    private int redisPort;

    /**
     * ${...} 플레이스홀더 치환기.
     * ★ 반드시 static 이어야 한다 (BeanFactoryPostProcessor 이므로
     *   RootConfig 인스턴스 생성보다 먼저 실행되어야 함).
     */
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    // ===== DataSource =====
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        // 추천 요청 하나가 요청 스레드와 알고리즘 스레드에서 각각 커넥션을 잡는다.
        // algorithmExecutor(core 8 / max 16)와 함께 봐야 하며, 10이면 동시 요청 서너 건에서 이미 고갈된다.
        config.setMaximumPoolSize(20);
        config.setConnectionTimeout(5000);
        config.setConnectionInitSql("SET NAMES utf8mb4");
        return new HikariDataSource(config);
    }

    // ===== MyBatis =====
    @Bean
    public SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTypeAliasesPackage("com.team.independence");
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:mybatis/mapper/**/*.xml"));
        factory.setConfigLocation(new PathMatchingResourcePatternResolver()
                .getResource("classpath:config/mybatis-config.xml"));
        return factory;
    }

    // ===== 트랜잭션 =====
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    // ===== JdbcTemplate (통합 테스트 용도) =====
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    // ===== Jackson =====
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ===== HTTP Client =====
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    // ===== Redis =====
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(redisHost, redisPort));
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

    @Bean("assetSyncExecutor")
    public Executor assetSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("asset-sync-");
        executor.initialize();
        return executor;
    }

    /**
     * 목표 추천 알고리즘 병렬 실행용 풀.
     *
     * <p>ThreadPoolExecutor는 큐가 가득 차기 전에는 스레드를 core 이상으로 늘리지 않는다.
     * core 3 / queue 20이면 큐에 20개가 쌓일 때까지 실질 병렬도가 3에 묶여, 요청 하나가
     * 태스크 3개를 던지는 이 경로에서는 동시 요청 두 번째부터 바로 대기가 시작됐다.
     * core를 실사용 병렬도에 맞추고 큐를 짧게 잡아 부하 시 max까지 늘어나도록 한다.
     *
     * <p>큐까지 넘치면 CallerRunsPolicy로 요청 스레드가 직접 실행한다. 무한정 쌓아 두고
     * 전부 느려지는 것보다, 유입 속도를 처리 속도에 맞춰 자연스럽게 낮추는 편이 낫다.
     * 풀 크기는 DataSource 커넥션 풀(20)과 함께 봐야 한다 — 알고리즘 스레드는 각자 커넥션을 잡는다.
     */
    @Bean("algorithmExecutor")
    public Executor algorithmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(8);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("algo-rec-");
        executor.initialize();
        return executor;
    }

}
