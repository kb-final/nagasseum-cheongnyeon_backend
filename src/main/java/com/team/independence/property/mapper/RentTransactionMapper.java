package com.team.independence.property.mapper;

import com.team.independence.property.domain.HousingType;
import com.team.independence.property.domain.RentTransaction;
import com.team.independence.property.dto.RentMedianAmount;
import com.team.independence.property.dto.RentMedianRequest;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RentTransactionMapper {
    void insert(RentTransaction rentTransaction);
    void insertBatch(List<RentTransaction> rentTransactions);

    /** (지역, 연월, 유형) 단위 재적재를 위한 구간 삭제 */
    int deleteByUnit(@Param("regionCode") String regionCode,
                     @Param("dealYm") String dealYm,
                     @Param("housingType") HousingType housingType);

    /**
     * 분위값 계산용 금액 목록. 조건에 맞는 거래가 없으면 빈 리스트를 반환한다.
     * 정렬은 보증금·월세를 각각 따로 해야 하므로 SQL에서 하지 않는다.
     *
     * @param areaMinSqm 요청의 평수를 ㎡로 환산한 하한 (버림)
     * @param areaMaxSqm 요청의 평수를 ㎡로 환산한 상한 (버림)
     */
    List<RentMedianAmount> findAmountsForMedian(@Param("request") RentMedianRequest request,
                                                @Param("areaMinSqm") long areaMinSqm,
                                                @Param("areaMaxSqm") long areaMaxSqm,
                                                @Param("startYm") String startYm,
                                                @Param("endYm") String endYm);

    /** 조건에 맞는 보증금(deposit)을 오름차순 정렬해 반환 (백분위수 계산용) */
    List<Long> findDeposits(@Param("regionCode") String regionCode,
                            @Param("housingType") String housingType,
                            @Param("dealType") String dealType,
                            @Param("areaMin") BigDecimal areaMin,
                            @Param("areaMax") BigDecimal areaMax);
}
