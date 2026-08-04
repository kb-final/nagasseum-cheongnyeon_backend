package com.team.independence.asset.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SyncJobStatusResponse {
    private String jobId;
    private String status;  // PENDING / SUCCESS / FAILED
    private String errorMessage;  // FAILED 시에만 채워짐
    private String resultUrl;  // SUCCESS 시에만 채워짐
}
