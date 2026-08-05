package com.team.independence.scheduler;

import com.team.independence.goal.service.GoalMarketTrendBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 홈 화면 「매물 시세 변화」 카드용 목표 시세 변화 갱신 배치.
 *
 * <p>BatchConfig(@Profile("batch"))의 컴포넌트 스캔 대상이라 batch 프로필로 띄울 때만
 * 등록된다. api 프로필로 실행하면 이 빈은 생성되지 않는다.
 *
 * <p>로직은 없다. Service를 호출만 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalMarketTrendScheduler implements InitializingBean {

    private final GoalMarketTrendBatchService goalMarketTrendBatchService;

    /**
     * 순수 Spring은 Boot와 달리 활성 프로필을 기동 로그에 찍어주지 않는다.
     * 이 줄이 보이면 batch 프로필이 걸렸다는 뜻이다.
     */
    @Override
    public void afterPropertiesSet() {
        log.info("[배치] 목표 시세 변화 갱신 스케줄러 등록됨 (batch 프로필 활성)");
    }

    /**
     * 매월 1일 새벽 6시.
     *
     * <p>전월세 실거래 동기화(4시)와 자산 동기화(4시)가 끝난 뒤로 뺐다. 이 배치는 그 둘의
     * 최신 데이터(실거래 중앙값, 순자산)를 그대로 갖다 쓰므로 먼저 갱신돼 있어야 한다.
     */
    @Scheduled(cron = "0 0 6 1 * *", zone = "Asia/Seoul")
    public void refreshGoalMarketTrends() {
        log.info("[배치] 목표 시세 변화 갱신 시작");
        goalMarketTrendBatchService.refreshAll();
        log.info("[배치] 목표 시세 변화 갱신 종료");
    }
}