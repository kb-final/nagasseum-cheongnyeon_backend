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

    /**
     * 시도 코드(법정동코드 앞 2자리)에 해당하는 시도명을 조회한다. 없으면 null.
     *
     * <p>region 테이블에 시도 자체를 가리키는 행은 없지만 모든 시군구 행이 {@code sido}를 갖고 있어,
     * 그중 하나만 읽으면 시도명을 알 수 있다. 시도 행을 따로 만들면 전체 지역을 순회하는 실거래
     * 동기화가 존재하지 않는 sggCd까지 호출하게 되므로 마스터를 늘리지 않는다.
     */
    String findSidoNameByPrefix(@Param("sidoPrefix") String sidoPrefix);

    String findFullNameByCode(@Param("code") String code);
}
