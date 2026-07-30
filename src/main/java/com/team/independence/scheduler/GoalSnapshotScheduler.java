package com.team.independence.scheduler;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.team.independence.compare.service.GoalSnapshotBatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 또래 비교용 목표 스냅샷 생성 배치.
 *
 * <p>BatchConfig(@Profile("batch"))의 컴포넌트 스캔 대상이라 batch 프로필로 띄울 때만
 * 등록된다. api 프로필로 실행하면 이 빈은 생성되지 않는다.
 *
 * <p>로직은 없다. Service를 호출만 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalSnapshotScheduler implements InitializingBean {

    private final GoalSnapshotBatchService goalSnapshotBatchService;

    /**
     * 순수 Spring은 Boot와 달리 활성 프로필을 기동 로그에 찍어주지 않는다.
     * 이 줄이 보이면 batch 프로필이 걸렸다는 뜻이다.
     */
    @Override
    public void afterPropertiesSet() {
        log.info("[배치] 목표 스냅샷 생성 스케줄러 등록됨 (batch 프로필 활성)");
    }

    /**
     * 매월 1일 새벽 5시.
     *
     * <p>자산 동기화가 4시에 돌기 때문에 그 뒤로 뺐다. 스냅샷은 asset_snapshot의
     * 순자산을 가져다 쓰므로, 자산이 먼저 갱신돼 있어야 그 달 값이 맞는다.
     */
    @Scheduled(cron = "0 0 5 1 * *", zone = "Asia/Seoul")
    public void generateGoalSnapshots() {
        log.info("[배치] 목표 스냅샷 생성 시작");
        goalSnapshotBatchService.generateForCurrentMonth();
        log.info("[배치] 목표 스냅샷 생성 종료");
    }
}