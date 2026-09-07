package com.team.independence.consultation.service;

import com.team.independence.consultation.domain.ConsultationReservation;
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

    /** id로 상담 예약을 조회한다. 없으면 CONSULTATION_NOT_FOUND. 같은 도메인의 다른 서비스(메시지)가 재사용한다. */
    ConsultationReservation findReservation(Long reservationId);

    /** RESERVED 상태일 때만 IN_PROGRESS로 바꾼다. 이미 IN_PROGRESS/COMPLETED면 아무 일도 하지 않는다(멱등). */
    void startIfReserved(Long reservationId);
}
