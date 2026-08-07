package com.team.independence.member.controller;

import com.team.independence.common.annotation.LoginMember;
import com.team.independence.common.response.ApiResponse;
import com.team.independence.member.domain.Agreement;
import com.team.independence.member.domain.Agreement.AgreementType;
import com.team.independence.member.dto.AgreementSingleUpdateRequest;
import com.team.independence.member.dto.AgreementUpdateRequest;
import com.team.independence.member.dto.MemberProfileResponse;
import com.team.independence.member.dto.MemberUpdateRequest;
import com.team.independence.member.service.AgreementService;
import com.team.independence.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final AgreementService agreementService;

    @GetMapping("/me")
    public ApiResponse<MemberProfileResponse> getMe(@LoginMember Long memberId) {
        return ApiResponse.ok(memberService.getMember(memberId));
    }

    @PatchMapping("/me")
    public ApiResponse<Void> updateMe(
            @LoginMember Long memberId,
            @RequestBody MemberUpdateRequest request) {
        memberService.updateMember(memberId, request.nickname(), request.incomeBracket(),
                request.monthlyIncome(), request.occupationType());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/me/agreements")
    public ApiResponse<Void> updateAgreements(
            @LoginMember Long memberId,
            @RequestBody AgreementUpdateRequest request) {
        List<Agreement> agreements = request.agreements().stream()
                .map(item -> Agreement.builder()
                        .agreementType(item.agreementType())
                        .agreed(item.agreed())
                        .agreementVersion("v1.0")
                        .build())
                .collect(Collectors.toList());
        agreementService.updateAll(memberId, agreements);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/me/agreements/{type}")
    public ApiResponse<Void> updateAgreement(
            @LoginMember Long memberId,
            @PathVariable AgreementType type,
            @RequestBody AgreementSingleUpdateRequest request) {
        agreementService.updateOne(memberId, type, request.agreed());
        return ApiResponse.ok(null);
    }

    /** 서버 기동 확인용 (팀 온보딩 시 이 API로 환경 검증) */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.ok("OK");
    }
}