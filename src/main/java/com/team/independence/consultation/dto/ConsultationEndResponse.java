package com.team.independence.consultation.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ConsultationEndResponse {
    private Long reservationId;
    private String status;
    private LocalDateTime endedAt;
}
