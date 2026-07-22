package com.team.independence.member.controller;

import com.team.independence.common.response.ApiResponse;
import com.team.independence.member.dto.MemberResponse;
import com.team.independence.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * ★ 팀원들이 복제할 표준 패턴 ★
 *  - @RestController + /api/v1/{도메인 복수형}
 *  - 비즈니스 로직 없음. Service 호출 후 ApiResponse로 감싸 반환만 한다.
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/{id}")
    public ApiResponse<MemberResponse> getMember(@PathVariable Long id) {
        return ApiResponse.ok(memberService.getMember(id));
    }

    /** 서버 기동 확인용 (팀 온보딩 시 이 API로 환경 검증) */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.ok("OK");
    }
}
