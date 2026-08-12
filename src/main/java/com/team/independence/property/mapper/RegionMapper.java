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

    /**
     * 시도 코드(법정동코드 앞 2자리)로 그 시도에 속한 시군구 코드를 모두 조회한다.
     *
     * <p>region 테이블은 시군구 단위로만 존재해 시도 자체를 가리키는 행이 없다.
     * 시도 범위로 무언가를 훑어야 하는 쪽에서 대상 시군구를 먼저 확보하는 용도다.
     */
    List<String> findCodesBySidoPrefix(@Param("sidoPrefix") String sidoPrefix);

    String findFullNameByCode(@Param("code") String code);
}
