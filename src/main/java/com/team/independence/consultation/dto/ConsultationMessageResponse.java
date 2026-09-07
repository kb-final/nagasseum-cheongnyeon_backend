package com.team.independence.consultation.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ConsultationMessageResponse {
    private Long messageId;
    private Long reservationId;
    private String senderType;
    private String content;
    private LocalDateTime createdAt;
}
