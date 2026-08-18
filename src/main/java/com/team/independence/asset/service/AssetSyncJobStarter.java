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
        if (jobId != null) {
            jobRunner.runAsync(memberId, jobId);
            return SyncJobResponse.builder().jobId(jobId).build();
        }
        String activeJobId = jobStore.getActiveJobId(memberId);
        if (activeJobId != null) {
            return SyncJobResponse.builder().jobId(activeJobId).build();
        }
        // tryAcquireNewJob 실패와 getActiveJobId 호출 사이에 잡이 완료된 경우: 새 잡으로 재시도
        jobId = jobStore.tryAcquireNewJob(memberId);
        if (jobId != null) {
            jobRunner.runAsync(memberId, jobId);
        }
        return SyncJobResponse.builder().jobId(
                jobId != null ? jobId : jobStore.getActiveJobId(memberId)).build();
    }
}
