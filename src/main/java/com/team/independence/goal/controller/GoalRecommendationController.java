package com.team.independence.goal.controller;

import com.team.independence.common.annotation.LoginMember;
import com.team.independence.common.response.ApiResponse;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.service.GoalRecommendationService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalRecommendationController {

    private final GoalRecommendationService recommendationService;

    @GetMapping("/recommendations")
    public ApiResponse<GoalRecommendationResponse> recommend(
            @LoginMember Long memberId,
            @Valid @ModelAttribute GoalRecommendationRequest request) {
        return ApiResponse.ok(recommendationService.recommend(memberId, request));
    }
}
