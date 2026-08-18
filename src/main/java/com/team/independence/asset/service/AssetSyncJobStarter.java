package com.team.independence.asset.service;

import com.team.independence.asset.dto.sync.SyncJobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssetSyncJobStarter {

    private final AssetSyncJobStore jobStore;
    private final AssetSyncJobRunner jobRunner;

    public SyncJobResponse start(Long memberId) {
        String jobId = jobStore.tryAcquireNewJob(memberId);
        if (jobId == null) {
            return SyncJobResponse.builder().jobId(jobStore.getActiveJobId(memberId)).build();
        }
        jobRunner.runAsync(memberId, jobId);
        return SyncJobResponse.builder().jobId(jobId).build();
    }
}
