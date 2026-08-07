package com.team.independence.asset.dto.sync;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AssetSyncResponse {
    private int syncedCount;
    private int failedCount;
    private List<InstitutionSyncResult> institutions;

    @Getter
    @Builder
    public static class InstitutionSyncResult {
        private String organizationCode;
        private String organizationName;
        private boolean success;
        private int assetAccountCount;
        private int loanAccountCount;
        private int cardAccountCount;
        private String errorCode;
        private String errorMessage;
    }
}
