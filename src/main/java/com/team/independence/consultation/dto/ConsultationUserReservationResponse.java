package com.team.independence.consultation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Builder;
import lombok.Getter;

/** /consult/my 화면용. 상담사 프로필(이름/사진)은 프론트 Mock을 계속 쓰므로 counselorId만 내려준다. */
@Getter
@Builder
public class ConsultationUserReservationResponse {
    private Long reservationId;
    private Long counselorId;
    private String consultationType;
    private String category;
    private LocalDate reservationDate;
    private LocalTime reservationTime;
    private String status;
    private String requestMessage;
}
