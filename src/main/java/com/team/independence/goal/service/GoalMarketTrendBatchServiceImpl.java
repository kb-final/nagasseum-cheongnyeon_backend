package com.team.independence.goal.service;

import com.team.independence.external.slack.SlackNotifier;
import com.team.independence.goal.mapper.GoalMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 목표 시세 변화 배치 구현.
 *
 * <p>목표별로 실거래 재조회 + 자산 조회가 필요해 SQL 한 방으로 끝낼 수 없다(goal_snapshot과 다름).
 * 회원 단위로 순회하며 개별 실패를 격리하는 자산 동기화 배치({@code AssetSyncServiceImpl})와 동일한 패턴을 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalMarketTrendBatchServiceImpl implements GoalMarketTrendBatchService {

    private static final String BATCH_NAME = "goal-market-trend";

    private final GoalMapper goalMapper;
    private final GoalService goalService;
    private final SlackNotifier slackNotifier;

    // 활성 상태인 모든 목표의 시세 변화 데이터를 갱신
    @Override
    public void refreshAll() {
        List<Long> goalIds = goalMapper.findAllActiveGoalIds();
        log.info("[배치] 목표 시세 변화 갱신 대상 목표 수: {}", goalIds.size());

        List<RefreshFailure> failures = new ArrayList<>();
        for (Long goalId : goalIds) {
            try {
                goalService.refreshMarketTrend(goalId);
            } catch (Exception e) {
                log.error("[배치] 목표 시세 변화 갱신 실패: goalId={}", goalId, e);
                failures.add(new RefreshFailure(goalId, e));
            }
        }

        if (!failures.isEmpty()) {
            StringBuilder sb = new StringBuilder("[목표 시세 변화 배치 실패] ")
                    .append(failures.size()).append("건\n");
            for (RefreshFailure f : failures) {
                sb.append("• goalId=").append(f.goalId)
                  .append(" / ").append(f.reason).append("\n");
            }
            slackNotifier.sendBatchFailureSummary(BATCH_NAME, sb.toString().trim());
        }

        log.info("[배치] 목표 시세 변화 갱신 완료 — 성공: {}, 실패: {}",
                goalIds.size() - failures.size(), failures.size());
    }

    private static class RefreshFailure {
        final Long goalId;
        final String reason;

        RefreshFailure(Long goalId, Throwable e) {
            this.goalId = goalId;
            this.reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
    }
}