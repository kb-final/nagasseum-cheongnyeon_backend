package com.team.independence.consultation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.consultation.domain.ConsultationCategory;
import com.team.independence.consultation.domain.ConsultationReservation;
import com.team.independence.consultation.domain.ConsultationStatus;
import com.team.independence.consultation.domain.ConsultationType;
import com.team.independence.consultation.dto.ConsultationCounselorReservationResponse;
import com.team.independence.consultation.dto.ConsultationEndResponse;
import com.team.independence.consultation.dto.ConsultationReservationCreateRequest;
import com.team.independence.consultation.dto.ConsultationReservationResponse;
import com.team.independence.consultation.dto.ConsultationUserReservationResponse;
import com.team.independence.consultation.mapper.ConsultationMapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsultationServiceImpl implements ConsultationService {

    private final ConsultationMapper consultationMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ConsultationReservationResponse createReservation(ConsultationReservationCreateRequest request) {
        ConsultationReservation reservation = ConsultationReservation.builder()
                .userId(request.getUserId())
                .counselorId(request.getCounselorId())
                .consultationType(ConsultationType.valueOf(request.getConsultationType()))
                .category(ConsultationCategory.valueOf(request.getCategory()))
                .reservationDate(request.getReservationDate())
                .reservationTime(request.getReservationTime())
                .requestMessage(request.getRequestMessage())
                .consultInfoJson(toJson(request.getConsultInfo()))
                .diagnosisJson(request.getDiagnosis() != null ? toJson(request.getDiagnosis()) : null)
                .status(ConsultationStatus.RESERVED)
                .build();

        consultationMapper.insert(reservation);

        return ConsultationReservationResponse.builder()
                .reservationId(reservation.getReservationId())
                .status(reservation.getStatus().name())
                .reservationDate(reservation.getReservationDate())
                .reservationTime(reservation.getReservationTime())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsultationUserReservationResponse> getUserReservations(Long userId) {
        return consultationMapper.findByUserId(userId).stream()
                .map(r -> ConsultationUserReservationResponse.builder()
                        .reservationId(r.getReservationId())
                        .counselorId(r.getCounselorId())
                        .consultationType(r.getConsultationType().name())
                        .category(r.getCategory().name())
                        .reservationDate(r.getReservationDate())
                        .reservationTime(r.getReservationTime())
                        .status(r.getStatus().name())
                        .requestMessage(r.getRequestMessage())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsultationCounselorReservationResponse> getCounselorReservations(Long counselorId) {
        return consultationMapper.findByCounselorId(counselorId).stream()
                .map(r -> ConsultationCounselorReservationResponse.builder()
                        .reservationId(r.getReservationId())
                        .userId(r.getUserId())
                        .consultationType(r.getConsultationType().name())
                        .category(r.getCategory().name())
                        .reservationDate(r.getReservationDate())
                        .reservationTime(r.getReservationTime())
                        .status(r.getStatus().name())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ConsultationReservation findReservation(Long reservationId) {
        ConsultationReservation reservation = consultationMapper.findById(reservationId);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND);
        }
        return reservation;
    }

    @Override
    @Transactional
    public void startIfReserved(Long reservationId) {
        consultationMapper.updateStatusToInProgress(reservationId);
    }

    @Override
    @Transactional
    public ConsultationEndResponse endConsultation(Long reservationId) {
        ConsultationReservation reservation = findReservation(reservationId);
        if (reservation.getStatus() == ConsultationStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CONSULTATION_ALREADY_COMPLETED, "이미 종료된 상담입니다.");
        }

        consultationMapper.updateStatusToCompleted(reservationId);
        ConsultationReservation ended = consultationMapper.findById(reservationId);

        return ConsultationEndResponse.builder()
                .reservationId(ended.getReservationId())
                .status(ended.getStatus().name())
                .endedAt(ended.getEndedAt())
                .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "상담 정보를 저장하는 중 오류가 발생했습니다.");
        }
    }
}
