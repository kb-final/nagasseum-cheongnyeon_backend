package com.team.independence.asset.mapper;

import com.team.independence.asset.domain.Institution;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface InstitutionMapper {
    List<Institution> findAllActive();
    Institution findByCode(String code);
}
