package com.team.independence.asset.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class AssetSyncJobStore {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private static final String KEY_STATUS = "asset:sync:job:%s:status";
    private static final String KEY_ERROR = "asset:sync:job:%s:error";
    private static final String KEY_RESULT = "asset:sync:job:%s:result";
    private static final String KEY_ACTIVE_JOB = "asset:sync:member:%d:activeJob";
    private static final long TTL_MINUTES = 10;

    private final StringRedisTemplate redisTemplate;

    public String tryAcquireNewJob(Long memberId) {
        String jobId = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                String.format(KEY_ACTIVE_JOB, memberId), jobId, TTL_MINUTES, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(acquired)) {
            return null;
        }
        redisTemplate.opsForValue().set(statusKey(jobId), STATUS_PENDING, TTL_MINUTES, TimeUnit.MINUTES);
        return jobId;
    }

    public String getStatus(String jobId) {
        return redisTemplate.opsForValue().get(statusKey(jobId));
    }

    public String getError(String jobId) {
        return redisTemplate.opsForValue().get(errorKey(jobId));
    }

    public void markSuccess(String jobId, String resultUrl) {
        redisTemplate.opsForValue().set(statusKey(jobId), STATUS_SUCCESS, TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(resultKey(jobId), resultUrl, TTL_MINUTES, TimeUnit.MINUTES);
    }

    public String getResultUrl(String jobId) {
        return redisTemplate.opsForValue().get(resultKey(jobId));
    }

    public void markFailed(String jobId, String errorMessage) {
        redisTemplate.opsForValue().set(statusKey(jobId), STATUS_FAILED, TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(
                errorKey(jobId),
                errorMessage != null ? errorMessage : "알 수 없는 오류",
                TTL_MINUTES, TimeUnit.MINUTES);
    }

    public String getActiveJobId(Long memberId) {
        return redisTemplate.opsForValue().get(String.format(KEY_ACTIVE_JOB, memberId));
    }

    public void clearActiveJob(Long memberId) {
        redisTemplate.delete(String.format(KEY_ACTIVE_JOB, memberId));
    }

    private String statusKey(String jobId) {
        return String.format(KEY_STATUS, jobId);
    }

    private String errorKey(String jobId) {
        return String.format(KEY_ERROR, jobId);
    }

    private String resultKey(String jobId) {
        return String.format(KEY_RESULT, jobId);
    }
}
