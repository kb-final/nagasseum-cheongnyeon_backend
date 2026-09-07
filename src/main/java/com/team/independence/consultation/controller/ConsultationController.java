package com.team.independence.consultation.controller;

import com.team.independence.common.response.ApiResponse;
import com.team.independence.consultation.dto.ConsultationCounselorReservationResponse;
import com.team.independence.consultation.dto.ConsultationEndResponse;
import com.team.independence.consultation.dto.ConsultationReservationCreateRequest;
import com.team.independence.consultation.dto.ConsultationReservationResponse;
import com.team.independence.consultation.dto.ConsultationUserReservationResponse;
import com.team.independence.consultation.service.ConsultationService;
import java.util.List;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 1:1 상담 예약. 상담사 로그인/권한 구조가 아직 확정되지 않아
 * userId/counselorId를 요청에서 명시적으로 받는다(임시 구조).
 */
@RestController
@RequestMapping("/api/v1/consultations")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;

    @PostMapping
    public ApiResponse<ConsultationReservationResponse> createReservation(
            @Valid @RequestBody ConsultationReservationCreateRequest request) {
        return ApiResponse.ok(consultationService.createReservation(request));
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<List<ConsultationUserReservationResponse>> getUserReservations(
            @PathVariable Long userId) {
        return ApiResponse.ok(consultationService.getUserReservations(userId));
    }

    @GetMapping("/counselors/{counselorId}")
    public ApiResponse<List<ConsultationCounselorReservationResponse>> getCounselorReservations(
            @PathVariable Long counselorId) {
        return ApiResponse.ok(consultationService.getCounselorReservations(counselorId));
    }

    @PatchMapping("/{reservationId}/end")
    public ApiResponse<ConsultationEndResponse> endConsultation(@PathVariable Long reservationId) {
        return ApiResponse.ok(consultationService.endConsultation(reservationId));
    }
}
