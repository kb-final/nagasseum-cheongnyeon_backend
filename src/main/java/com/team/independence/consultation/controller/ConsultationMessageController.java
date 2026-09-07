package com.team.independence.consultation.controller;

import com.team.independence.common.response.ApiResponse;
import com.team.independence.consultation.dto.ConsultationMessageCreateRequest;
import com.team.independence.consultation.dto.ConsultationMessageResponse;
import com.team.independence.consultation.service.ConsultationMessageService;
import java.util.List;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 1:1 상담 채팅 메시지. WebSocket 없이 HTTP 저장/조회만 제공한다(폴링은 프론트 책임).
 * senderType은 로그인/role 구조가 확정되지 않아 요청 바디로 명시 전달받는다(임시 구조).
 */
@RestController
@RequestMapping("/api/v1/consultations")
@RequiredArgsConstructor
public class ConsultationMessageController {

    private final ConsultationMessageService consultationMessageService;

    @PostMapping("/{reservationId}/messages")
    public ApiResponse<ConsultationMessageResponse> createMessage(
            @PathVariable Long reservationId,
            @Valid @RequestBody ConsultationMessageCreateRequest request) {
        return ApiResponse.ok(consultationMessageService.createMessage(reservationId, request));
    }

    @GetMapping("/{reservationId}/messages")
    public ApiResponse<List<ConsultationMessageResponse>> getMessages(@PathVariable Long reservationId) {
        return ApiResponse.ok(consultationMessageService.getMessages(reservationId));
    }
}
