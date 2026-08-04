package com.team.independence.goal.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.independence.external.slack.SlackNotifier;
import com.team.independence.goal.dto.GoalMarketTrendResponse;
import com.team.independence.goal.mapper.GoalMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoalMarketTrendBatchServiceImplTest {

    @Mock GoalMapper goalMapper;
    @Mock GoalService goalService;
    @Mock SlackNotifier slackNotifier;

    GoalMarketTrendBatchServiceImpl batchService;

    @BeforeEach
    void setUp() {
        batchService = new GoalMarketTrendBatchServiceImpl(goalMapper, goalService, slackNotifier);
    }

    @Test
    @DisplayName("refreshAll - 전체 성공 시 Slack 알림을 보내지 않는다")
    void refreshAll_allSucceed_noSlackAlert() {
        when(goalMapper.findAllActiveGoalIds()).thenReturn(List.of(1L, 2L, 3L));

        batchService.refreshAll();

        verify(goalService, times(1)).refreshMarketTrend(1L);
        verify(goalService, times(1)).refreshMarketTrend(2L);
        verify(goalService, times(1)).refreshMarketTrend(3L);
        verify(slackNotifier, never()).sendBatchFailureSummary(any(), any());
    }

    @Test
    @DisplayName("refreshAll - 일부 목표가 실패해도 나머지는 계속 처리되고, 실패는 요약 알림 1건으로 모아 보낸다")
    void refreshAll_partialFailure_isolatedAndSummarized() {
        when(goalMapper.findAllActiveGoalIds()).thenReturn(List.of(1L, 2L, 3L));
        when(goalService.refreshMarketTrend(1L)).thenReturn(GoalMarketTrendResponse.builder().build());
        doThrow(new RuntimeException("실거래 데이터 없음")).when(goalService).refreshMarketTrend(2L);
        when(goalService.refreshMarketTrend(3L)).thenReturn(GoalMarketTrendResponse.builder().build());

        batchService.refreshAll();

        verify(goalService, times(1)).refreshMarketTrend(1L);
        verify(goalService, times(1)).refreshMarketTrend(2L);
        verify(goalService, times(1)).refreshMarketTrend(3L); // 2번 실패해도 3번은 계속 진행됨

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(slackNotifier, times(1)).sendBatchFailureSummary(eq("goal-market-trend"), messageCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(messageCaptor.getValue())
                .contains("goalId=2")
                .contains("실거래 데이터 없음");
    }

    @Test
    @DisplayName("refreshAll - 전체 실패해도 배치 자체는 예외 없이 끝난다")
    void refreshAll_allFail_doesNotThrow() {
        when(goalMapper.findAllActiveGoalIds()).thenReturn(List.of(1L, 2L));
        doThrow(new RuntimeException("실패1")).when(goalService).refreshMarketTrend(1L);
        doThrow(new RuntimeException("실패2")).when(goalService).refreshMarketTrend(2L);

        batchService.refreshAll();

        verify(slackNotifier, times(1)).sendBatchFailureSummary(eq("goal-market-trend"), contains("2건"));
    }
}
