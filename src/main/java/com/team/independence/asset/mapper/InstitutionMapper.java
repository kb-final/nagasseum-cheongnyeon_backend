package com.team.independence.asset.mapper;

import com.team.independence.asset.domain.Institution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InstitutionMapper {
    List<Institution> findAllActive();
    List<Institution> findAllActiveWithConnectionStatus(@Param("memberId") Long memberId);
    Institution findByCode(String code);
}
