package com.team.independence.consultation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Builder;
import lombok.Getter;

/** 상담사 홈 화면용. 신청자 이름 등은 조인하지 않고 userId만 내려준다. */
@Getter
@Builder
public class ConsultationCounselorReservationResponse {
    private Long reservationId;
    private Long userId;
    private String consultationType;
    private String category;
    private LocalDate reservationDate;
    private LocalTime reservationTime;
    private String status;
}
