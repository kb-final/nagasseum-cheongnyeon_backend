package com.team.independence.property.mapper;

import com.team.independence.property.domain.RentSyncLog;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RentSyncLogMapper {

    /** 수집 대상 판단용 이력 전량 조회. 유닛마다 조회하면 수천 번 왕복하므로 한 번에 읽는다. */
    List<RentSyncLog> findAll();

    /** 수집 결과 기록 (성공/실패 모두. 같은 조합이면 갱신) */
    void upsert(RentSyncLog rentSyncLog);
}
