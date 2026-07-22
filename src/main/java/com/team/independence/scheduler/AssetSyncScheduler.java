package com.team.independence.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ★ 배치 표준 패턴 ★
 *  - BatchConfig(@Profile("batch"))의 컴포넌트 스캔 대상
 *  - api 프로필로 실행하면 이 빈은 등록조차 되지 않는다
 *  - 로직은 없다. Service를 호출만 한다 (Controller의 배치 버전)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetSyncScheduler {

    // private final AssetSyncService assetSyncService;   // 담당자가 구현 후 주입

    /** 매일 새벽 4시 전체 회원 자산 동기화 */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void syncAllMembers() {
        log.info("[배치] 자산 동기화 시작");
        // assetSyncService.syncAll();   // 로직은 Service에
        log.info("[배치] 자산 동기화 완료");
    }
}
