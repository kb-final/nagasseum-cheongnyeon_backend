package com.team.independence.consultation.mapper;

import com.team.independence.consultation.domain.ConsultationMessage;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * DB 접근 인터페이스. 실제 SQL은 짝이 되는 XML에 있다.
 *   → src/main/resources/mybatis/mapper/consultation/ConsultationMessageMapper.xml
 */
@Mapper
public interface ConsultationMessageMapper {

    /** 메시지 저장 (생성된 PK를 message.messageId에 반영) */
    void insert(ConsultationMessage message);

    /** 상담의 전체 메시지. 오래된 순(created_at ASC). */
    List<ConsultationMessage> findByReservationId(@Param("reservationId") Long reservationId);

    /**
     * id로 메시지를 조회한다. insert 직후 DB가 채운 created_at(DEFAULT CURRENT_TIMESTAMP)을
     * 되읽어 응답에 담는 용도(useGeneratedKeys는 PK만 채워준다).
     */
    ConsultationMessage findById(@Param("messageId") Long messageId);
}
