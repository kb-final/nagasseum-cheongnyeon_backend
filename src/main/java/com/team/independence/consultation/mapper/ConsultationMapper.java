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
}
