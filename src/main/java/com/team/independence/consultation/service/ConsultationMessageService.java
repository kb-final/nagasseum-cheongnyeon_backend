package com.team.independence.consultation.service;

import com.team.independence.consultation.dto.ConsultationMessageCreateRequest;
import com.team.independence.consultation.dto.ConsultationMessageResponse;
import java.util.List;

public interface ConsultationMessageService {

    /**
     * 메시지를 저장한다. 상담이 없으면 CONSULTATION_NOT_FOUND, COMPLETED면 CONSULTATION_ALREADY_COMPLETED.
     * 기존 상태가 RESERVED였다면 저장과 같은 트랜잭션에서 IN_PROGRESS로 바꾼다.
     */
    ConsultationMessageResponse createMessage(Long reservationId, ConsultationMessageCreateRequest request);

    /** 상담의 전체 메시지를 오래된 순으로 반환한다. 메시지가 없으면 빈 배열. 상담이 없으면 CONSULTATION_NOT_FOUND. */
    List<ConsultationMessageResponse> getMessages(Long reservationId);
}
