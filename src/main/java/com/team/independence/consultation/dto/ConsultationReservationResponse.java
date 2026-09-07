package com.team.independence.consultation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ConsultationReservationResponse {
    private Long reservationId;
    private String status;
    private LocalDate reservationDate;
    private LocalTime reservationTime;
}
