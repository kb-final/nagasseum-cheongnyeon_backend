package com.team.independence.property.mapper;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.domain.RentTransaction;
import com.team.independence.property.dto.MonthlyPricePoint;
import com.team.independence.property.dto.RentMedianAmount;
import com.team.independence.property.dto.RentMedianRequest;
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

    /** 가격 모델(μ, σ) 산출용 월별 (거래연월, 보증금, 면적) 목록. 보증금 0 제외 */
    List<MonthlyPricePoint> findAmountsForPriceModel(@Param("regionCode") String regionCode,
                                                     @Param("housingType") HousingType housingType,
                                                     @Param("dealType") DealType dealType,
                                                     @Param("areaMinSqm") long areaMinSqm,
                                                     @Param("areaMaxSqm") long areaMaxSqm,
                                                     @Param("startYm") String startYm,
                                                     @Param("endYm") String endYm);
}
