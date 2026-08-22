package com.team.independence.goal.controller;

import com.team.independence.common.annotation.LoginMember;
import com.team.independence.common.response.ApiResponse;
import com.team.independence.goal.dto.GoalDetailResponse;
import com.team.independence.goal.dto.GoalDiagnosisRequest;
import com.team.independence.goal.dto.GoalDiagnosisResponse;
import com.team.independence.goal.dto.GoalMarketTrendResponse;
import com.team.independence.goal.dto.GoalForecastResponse;
import com.team.independence.goal.dto.GoalResponse;
import com.team.independence.goal.dto.GoalSaveRequest;
import com.team.independence.goal.dto.GoalSavingCurrentResponse;
import com.team.independence.goal.dto.GoalSavingCurrentUpdateRequest;
import com.team.independence.goal.dto.GoalSummaryResponse;
import com.team.independence.goal.dto.MonteCarloResponse;
import com.team.independence.goal.service.GoalDetailService;
import com.team.independence.goal.service.GoalService;
import com.team.independence.goal.service.MonteCarloService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final MonteCarloService monteCarloService;

    @PostMapping("/diagnosis")
    public ApiResponse<GoalDiagnosisResponse> diagnose(
            @LoginMember Long memberId,
            @Valid @RequestBody GoalDiagnosisRequest request) {
        return ApiResponse.ok(goalService.diagnose(memberId, request));
    }

    @PostMapping
    public ApiResponse<GoalResponse> createGoal(
            @LoginMember Long memberId,
            @Valid @RequestBody GoalSaveRequest request) {
        return ApiResponse.ok(goalService.createGoal(memberId, request));
    }

    @GetMapping("/active")
    public ApiResponse<GoalResponse> getActiveGoal(@LoginMember Long memberId) {
        return ApiResponse.ok(goalService.getActiveGoal(memberId));
    }

    @GetMapping("/{goalId}")
    public ApiResponse<GoalResponse> getGoal(
            @LoginMember Long memberId,
            @PathVariable Long goalId) {
        return ApiResponse.ok(goalService.getGoal(memberId, goalId));
    }

    @PutMapping("/{goalId}")
    public ApiResponse<GoalResponse> updateGoal(
            @LoginMember Long memberId,
            @PathVariable Long goalId,
            @Valid @RequestBody GoalSaveRequest request) {
        return ApiResponse.ok(goalService.updateGoal(memberId, goalId, request));
    }

    @DeleteMapping("/{goalId}")
    public ApiResponse<Void> deleteGoal(
            @LoginMember Long memberId,
            @PathVariable Long goalId) {
        goalService.deleteGoal(memberId, goalId);
        return ApiResponse.ok(null);
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

    @GetMapping("/{goalId}/simulation")
    public ApiResponse<MonteCarloResponse> simulate(
            @LoginMember Long memberId,
            @PathVariable Long goalId) {
        return ApiResponse.ok(monteCarloService.simulate(memberId, goalId));
    }

    @GetMapping("/market-trend")
    public ApiResponse<GoalMarketTrendResponse> getMarketTrend(@LoginMember Long memberId) {
        return ApiResponse.ok(goalService.getMarketTrend(memberId));
    }

    @GetMapping("/summary")
    public ApiResponse<GoalSummaryResponse> getSummary(@LoginMember Long memberId) {
        return ApiResponse.ok(goalService.getSummary(memberId));
    }

    @GetMapping("/savings/current")
    public ApiResponse<GoalSavingCurrentResponse> getCurrentSaving(@LoginMember Long memberId) {
        return ApiResponse.ok(goalService.getCurrentSaving(memberId));
    }

    @PutMapping("/savings/current")
    public ApiResponse<GoalSavingCurrentResponse> updateCurrentSaving(
            @LoginMember Long memberId,
            @Valid @RequestBody GoalSavingCurrentUpdateRequest request) {
        return ApiResponse.ok(goalService.updateCurrentSaving(memberId, request));
    }
}
