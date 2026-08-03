package com.team.independence.scheduler;

import com.team.independence.asset.service.AssetSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 배치 표준 패턴:
 *  BatchConfig(@Profile("batch"))의 컴포넌트 스캔 대상
 *  api 프로필로 실행하면 이 빈은 등록 자체가 되지 않습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetSyncScheduler {

    private final AssetSyncService assetSyncService;

    // 매일 새벽 4시 전체 회원 자산 동기화 (추후 변경 가능)
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void syncAllMembers() {
        log.info("[배치] 자산 동기화 시작");
        assetSyncService.syncAll();
        log.info("[배치] 자산 동기화 종료");
    }
}
