package com.team.independence.goal.controller;

import com.team.independence.common.response.ApiResponse;
import com.team.independence.goal.dto.GoalCreateRequest;
import com.team.independence.goal.dto.GoalDiagnosisRequest;
import com.team.independence.goal.dto.GoalDiagnosisResponse;
import com.team.independence.goal.dto.GoalResponse;
import com.team.independence.goal.service.GoalService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/diagnosis")
    public ApiResponse<GoalDiagnosisResponse> diagnose(
            @RequestParam Long memberId,
            @Valid @RequestBody GoalDiagnosisRequest request) {
        return ApiResponse.ok(goalService.diagnose(memberId, request));
    }

    @PostMapping
    public ApiResponse<GoalResponse> createGoal(
            @RequestParam Long memberId,
            @Valid @RequestBody GoalCreateRequest request) {
        return ApiResponse.ok(goalService.createGoal(memberId, request));
    }
}
