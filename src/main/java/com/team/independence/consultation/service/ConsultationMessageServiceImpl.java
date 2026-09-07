package com.team.independence.consultation.service;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.consultation.domain.ConsultationMessage;
import com.team.independence.consultation.domain.ConsultationReservation;
import com.team.independence.consultation.domain.ConsultationStatus;
import com.team.independence.consultation.domain.MessageSenderType;
import com.team.independence.consultation.dto.ConsultationMessageCreateRequest;
import com.team.independence.consultation.dto.ConsultationMessageResponse;
import com.team.independence.consultation.mapper.ConsultationMessageMapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsultationMessageServiceImpl implements ConsultationMessageService {

    private final ConsultationService consultationService;
    private final ConsultationMessageMapper consultationMessageMapper;

    @Override
    @Transactional
    public ConsultationMessageResponse createMessage(Long reservationId, ConsultationMessageCreateRequest request) {
        ConsultationReservation reservation = consultationService.findReservation(reservationId);
        if (reservation.getStatus() == ConsultationStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CONSULTATION_ALREADY_COMPLETED);
        }

        ConsultationMessage message = ConsultationMessage.builder()
                .reservationId(reservationId)
                .senderType(MessageSenderType.valueOf(request.getSenderType()))
                .content(request.getContent())
                .build();
        consultationMessageMapper.insert(message);

        if (reservation.getStatus() == ConsultationStatus.RESERVED) {
            consultationService.startIfReserved(reservationId);
        }

        // created_at은 DB의 DEFAULT CURRENT_TIMESTAMP가 채우므로 되읽어 응답에 담는다.
        ConsultationMessage saved = consultationMessageMapper.findById(message.getMessageId());

        return ConsultationMessageResponse.builder()
                .messageId(saved.getMessageId())
                .reservationId(saved.getReservationId())
                .senderType(saved.getSenderType().name())
                .content(saved.getContent())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsultationMessageResponse> getMessages(Long reservationId) {
        consultationService.findReservation(reservationId);

        return consultationMessageMapper.findByReservationId(reservationId).stream()
                .map(m -> ConsultationMessageResponse.builder()
                        .messageId(m.getMessageId())
                        .reservationId(m.getReservationId())
                        .senderType(m.getSenderType().name())
                        .content(m.getContent())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
