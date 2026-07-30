package com.team.independence.scheduler;

import com.team.independence.property.service.RentTransactionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 전월세 실거래 동기화 배치.
 *
 * <p>BatchConfig(@Profile("batch"))의 컴포넌트 스캔 대상이라 batch 프로필로 띄울 때만
 * 등록된다. api 프로필이나 프로필 미지정으로 실행하면 이 빈은 생성조차 되지 않는다.
 *
 * <p>로직은 없다. Service를 호출만 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentTransactionSyncScheduler implements InitializingBean {

    private final RentTransactionSyncService rentTransactionSyncService;

    /**
     * 순수 Spring은 Boot와 달리 활성 프로필을 기동 로그에 찍어주지 않는다.
     * 이 줄이 보이면 batch 프로필이 걸렸다는 뜻이다.
     */
    @Override
    public void afterPropertiesSet() {
        log.info("[배치] 전월세 실거래 동기화 스케줄러 등록됨 (batch 프로필 활성)");
    }

    /** 매월 1일 새벽 4시 전월세 실거래 동기화 */
    @Scheduled(cron = "0 0 4 1 * *", zone = "Asia/Seoul")
    public void syncRentTransactions() {
        log.info("[배치] 전월세 실거래 동기화 시작");
        rentTransactionSyncService.syncAll();
        log.info("[배치] 전월세 실거래 동기화 완료");
    }
}
