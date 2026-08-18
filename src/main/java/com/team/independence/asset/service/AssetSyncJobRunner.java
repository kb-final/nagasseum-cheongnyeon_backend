package com.team.independence.asset.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssetSyncJobRunner {

    private final AssetSyncService assetSyncService;
    private final AssetSyncJobStore jobStore;

    @Async("assetSyncExecutor")
    public void runAsync(Long memberId, String jobId) {
        log.info("[비동기 동기화] 시작: memberId={}, jobId={}", memberId, jobId);
        try {
            assetSyncService.syncAccounts(memberId);
            jobStore.markSuccess(jobId, "/api/v1/assets/summary/" + memberId);
            log.info("[비동기 동기화] 완료: memberId={}, jobId={}", memberId, jobId);
        } catch (Exception e) {
            log.error("[비동기 동기화] 실패: memberId={}, jobId={}", memberId, jobId, e);
            jobStore.markFailed(jobId, e.getMessage());
        } finally {
            jobStore.clearActiveJob(memberId);
        }
    }
}
