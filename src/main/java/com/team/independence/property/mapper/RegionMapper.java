package com.team.independence.property.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import javax.validation.constraints.NotBlank;

@Mapper
public interface RegionMapper {
    List<String> findAllCodes();

    /** 시/도 + 군/구 이름으로 region_code 조회. 없으면 null. */
    String findCodeBySidoAndSigungu(@Param("sido") String sido, @Param("sigungu") String sigungu);

    String findFullNameByCode(@Param("code") String code);
}
