package com.team.independence.scheduler;

import com.team.independence.policy.service.PolicySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicySyncScheduler implements InitializingBean {

    private final PolicySyncService policySyncService;

    @Override
    public void afterPropertiesSet() {
        log.info("[배치] 정책 동기화 스케줄러 등록됨 (batch 프로필 활성)");
    }

    /** 매일 새벽 3시 온통청년 주거 정책 동기화 */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void syncPolicies() {
        log.info("[배치] 온통청년 정책 동기화 시작");
        policySyncService.syncAll();
        log.info("[배치] 온통청년 정책 동기화 완료");
    }
}
