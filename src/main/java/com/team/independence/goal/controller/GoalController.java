package com.team.independence.goal.controller;

import com.team.independence.common.annotation.LoginMember;
import com.team.independence.common.response.ApiResponse;
import com.team.independence.goal.dto.GoalCreateRequest;
import com.team.independence.goal.dto.GoalDetailResponse;
import com.team.independence.goal.dto.GoalDiagnosisRequest;
import com.team.independence.goal.dto.GoalDiagnosisResponse;
import com.team.independence.goal.dto.GoalMarketTrendResponse;
import com.team.independence.goal.dto.GoalForecastResponse;
import com.team.independence.goal.dto.GoalResponse;
import com.team.independence.goal.dto.GoalSummaryResponse;
import com.team.independence.goal.service.GoalDetailService;
import com.team.independence.goal.service.GoalService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;
    private final GoalDetailService goalDetailService;

    @PostMapping("/diagnosis")
    public ApiResponse<GoalDiagnosisResponse> diagnose(
            @LoginMember Long memberId,
            @Valid @RequestBody GoalDiagnosisRequest request) {
        return ApiResponse.ok(goalService.diagnose(memberId, request));
    }

    @PostMapping
    public ApiResponse<GoalResponse> createGoal(
            @LoginMember Long memberId,
            @Valid @RequestBody GoalCreateRequest request) {
        return ApiResponse.ok(goalService.createGoal(memberId, request));
    }

    @GetMapping("/{goalId}/detail")
    public ApiResponse<GoalDetailResponse> getGoalDetail(
            @LoginMember Long memberId,
            @PathVariable Long goalId) {
        return ApiResponse.ok(goalDetailService.getGoalDetail(memberId, goalId));
    }

    @GetMapping("/{goalId}/simulations/monthly-saving")
    public ApiResponse<GoalForecastResponse> simulateMonthlySaving(
            @LoginMember Long memberId,
            @PathVariable Long goalId,
            @RequestParam Long monthlySaving) {
        return ApiResponse.ok(goalService.simulateMonthlySaving(memberId, goalId, monthlySaving));
    }

    @GetMapping("/market-trend")
    public ApiResponse<GoalMarketTrendResponse> getMarketTrend(@LoginMember Long memberId) {
        return ApiResponse.ok(goalService.getMarketTrend(memberId));
    }

    @GetMapping("/summary")
    public ApiResponse<GoalSummaryResponse> getSummary(@LoginMember Long memberId) {
        return ApiResponse.ok(goalService.getSummary(memberId));
    }
}
