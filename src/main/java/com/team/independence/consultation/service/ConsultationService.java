package com.team.independence.consultation.service;

import com.team.independence.consultation.dto.ConsultationCounselorReservationResponse;
import com.team.independence.consultation.dto.ConsultationReservationCreateRequest;
import com.team.independence.consultation.dto.ConsultationReservationResponse;
import com.team.independence.consultation.dto.ConsultationUserReservationResponse;
import java.util.List;

public interface ConsultationService {

    /** 상담 예약 생성. 상태는 항상 RESERVED로 시작한다. */
    ConsultationReservationResponse createReservation(ConsultationReservationCreateRequest request);

    /** 사용자 기준 상담 목록(예약일 최신순). */
    List<ConsultationUserReservationResponse> getUserReservations(Long userId);

    /** 상담사 기준 상담 목록(예약일시 오름차순). */
    List<ConsultationCounselorReservationResponse> getCounselorReservations(Long counselorId);
}
