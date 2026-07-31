package com.team.independence.property.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RegionMapper {
    List<String> findAllCodes();

    /**
     * 지역 코드의 전체 지명을 조회한다.
     * 존재하지 않는 코드면 null을 반환하므로, 코드 유효성 검증도 이 메서드로 겸한다.
     */
    String findFullNameByCode(@Param("code") String code);
}
