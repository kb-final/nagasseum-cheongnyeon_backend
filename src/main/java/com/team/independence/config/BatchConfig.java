package com.team.independence.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 배치 전용 설정. profile=batch 로 실행할 때만 활성화된다.
 *
 * ★ @EnableScheduling 이 여기에만 있으므로, api 프로필로 실행하면
 *   스케줄링 기능 자체가 켜지지 않는다(이중 안전장치).
 *
 * 실행 예: -Dspring.profiles.active=prod,batch
 */
@Configuration
@Profile("batch")
@EnableScheduling
@ComponentScan(basePackages = "com.team.independence.scheduler")
public class BatchConfig {
}
