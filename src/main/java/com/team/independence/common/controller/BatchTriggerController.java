package com.team.independence.common.controller;

import com.team.independence.common.response.ApiResponse;
import com.team.independence.compare.service.GoalSnapshotBatchService;
import com.team.independence.goal.service.GoalMarketTrendBatchService;
import com.team.independence.property.service.RentTransactionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배치 수동 트리거 엔드포인트.
 *
 * 인프라 초기 구축 직후처럼 스케줄 실행 전에 데이터를 채워야 할 때 사용한다.
 * 각 배치는 독립 호출 가능하지만, 의존 순서가 있으므로 /all 사용을 권장한다.
 *   1. rent-sync → rent_transaction 적재
 *   2. snapshots → 자산/목표 기반 스냅샷 생성 (rent-sync 이후)
 *   3. market-trends → 실거래 중앙값 기반 시세 갱신 (rent-sync 이후)
 *
 * TODO: 인증 완성 후 관리자 권한 체크 추가
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/batch")
@RequiredArgsConstructor
public class BatchTriggerController {

    private final RentTransactionSyncService rentTransactionSyncService;
    private final GoalSnapshotBatchService goalSnapshotBatchService;
    private final GoalMarketTrendBatchService goalMarketTrendBatchService;

    /** 전체 배치를 순서대로 실행한다. 초기 데이터 세팅 시 사용. */
    @PostMapping("/all")
    public ApiResponse<String> runAll() {
        log.info("[수동 배치] 전체 배치 시작");

        log.info("[수동 배치] 1/3 전월세 실거래 동기화 시작");
        rentTransactionSyncService.syncAll();
        log.info("[수동 배치] 1/3 전월세 실거래 동기화 완료");

        log.info("[수동 배치] 2/3 목표 스냅샷 생성 시작");
        int snapshotCount = goalSnapshotBatchService.generateForCurrentMonth();
        log.info("[수동 배치] 2/3 목표 스냅샷 생성 완료: {}건", snapshotCount);

        log.info("[수동 배치] 3/3 목표 시세 변화 갱신 시작");
        goalMarketTrendBatchService.refreshAll();
        log.info("[수동 배치] 3/3 목표 시세 변화 갱신 완료");

        log.info("[수동 배치] 전체 배치 완료");
        return ApiResponse.ok("전체 배치 완료. 스냅샷 " + snapshotCount + "건 생성.");
    }

    /** 전월세 실거래 동기화만 실행한다. */
    @PostMapping("/rent-sync")
    public ApiResponse<String> syncRentTransactions() {
        log.info("[수동 배치] 전월세 실거래 동기화 시작");
        rentTransactionSyncService.syncAll();
        log.info("[수동 배치] 전월세 실거래 동기화 완료");
        return ApiResponse.ok("전월세 실거래 동기화 완료.");
    }

    /** 이번 달 목표 스냅샷 생성만 실행한다. */
    @PostMapping("/snapshots")
    public ApiResponse<String> generateSnapshots() {
        log.info("[수동 배치] 목표 스냅샷 생성 시작");
        int count = goalSnapshotBatchService.generateForCurrentMonth();
        log.info("[수동 배치] 목표 스냅샷 생성 완료: {}건", count);
        return ApiResponse.ok("목표 스냅샷 " + count + "건 생성 완료.");
    }

    /** 모든 활성 목표의 시세 변화 캐시를 갱신한다. */
    @PostMapping("/market-trends")
    public ApiResponse<String> refreshMarketTrends() {
        log.info("[수동 배치] 목표 시세 변화 갱신 시작");
        goalMarketTrendBatchService.refreshAll();
        log.info("[수동 배치] 목표 시세 변화 갱신 완료");
        return ApiResponse.ok("목표 시세 변화 갱신 완료.");
    }
}
