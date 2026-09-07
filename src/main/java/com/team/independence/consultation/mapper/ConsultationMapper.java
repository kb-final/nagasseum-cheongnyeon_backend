package com.team.independence.consultation.mapper;

import com.team.independence.consultation.domain.ConsultationReservation;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * DB 접근 인터페이스. 실제 SQL은 짝이 되는 XML에 있다.
 *   → src/main/resources/mybatis/mapper/consultation/ConsultationMapper.xml
 */
@Mapper
public interface ConsultationMapper {

    /** 상담 예약 생성 (생성된 PK를 reservation.reservationId에 반영) */
    void insert(ConsultationReservation reservation);

    /** 사용자 기준 상담 목록. 예약일 최신순. */
    List<ConsultationReservation> findByUserId(@Param("userId") Long userId);

    /** 상담사 기준 상담 목록. 예정된 상담을 시간순으로 쓰기 쉽게 예약일시 오름차순. */
    List<ConsultationReservation> findByCounselorId(@Param("counselorId") Long counselorId);

    /** id로 상담 예약을 조회한다. 없으면 null. */
    ConsultationReservation findById(@Param("reservationId") Long reservationId);

    /**
     * RESERVED일 때만 IN_PROGRESS로 바꾼다(첫 메시지 전송 시점). status를 조건에 함께 걸어
     * 이미 IN_PROGRESS/COMPLETED면 0건이 된다(멱등).
     */
    int updateStatusToInProgress(@Param("reservationId") Long reservationId);

    /**
     * RESERVED/IN_PROGRESS일 때만 COMPLETED로 바꾸고 종료 시각(ended_at)을 기록한다.
     * status를 조건에 함께 걸어 이미 COMPLETED면 0건이 된다(중복 종료 방지).
     */
    int updateStatusToCompleted(@Param("reservationId") Long reservationId);
}
