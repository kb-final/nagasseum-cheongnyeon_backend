package com.team.independence.goal.service;

public interface GoalMarketTrendBatchService {

    /**
     * ACTIVE 상태인 모든 목표의 「매물 시세 변화」 카드 데이터를 다시 계산해 Redis 캐시를 갱신한다.
     * 개별 목표 실패는 서로 격리되며(한 목표 실패가 배치 전체를 죽이지 않음), 실패가 있으면 요약 알림 1건을 보낸다.
     */
    void refreshAll();
}