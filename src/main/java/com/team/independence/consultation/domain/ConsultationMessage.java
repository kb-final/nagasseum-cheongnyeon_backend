package com.team.independence.consultation.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 기본 생성자와 setter는 MyBatis가 조회 결과를 매핑할 때 쓴다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationMessage {
    private Long messageId;
    private Long reservationId;
    private MessageSenderType senderType;
    private String content;
    private LocalDateTime createdAt;
}
