package com.team.independence.compare.service;

/**
 * 또래 비교용 목표 스냅샷 생성 배치.
 *
 * <p>비교 API는 goal_snapshot만 읽는다. 실제 목표·자산 테이블을 직접 훑지 않는 이유는
 * 두 가지다. 하나는 성능이고, 다른 하나는 "그 시점의 값"을 굳혀둬야 하기 때문이다.
 * 목표 금액이나 자산은 계속 바뀌는데, 비교 통계가 조회할 때마다 달라지면 안 된다.
 */
public interface GoalSnapshotBatchService {

    /**
     * 해당 월의 스냅샷을 만든다.
     *
     * <p>같은 달에 다시 돌려도 안전하다. 이미 있으면 최신 값으로 덮어쓴다.
     *
     * @param snapshotYm 집계 기준월 YYYYMM
     * @return 생성되거나 갱신된 건수
     */
    int generate(String snapshotYm);

    /** 이번 달 스냅샷을 만든다. 스케줄러가 부르는 입구다. */
    int generateForCurrentMonth();
}