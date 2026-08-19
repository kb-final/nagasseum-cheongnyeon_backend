package com.team.independence.property.mapper;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.domain.RentTransaction;
import com.team.independence.property.dto.MonthlyPricePoint;
import com.team.independence.property.dto.BulkMedianResult;
import com.team.independence.property.dto.MedianAggResult;
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
     * SQL 윈도우 함수로 분위값을 계산해 1행으로 반환한다.
     * 조건에 맞는 거래가 없으면 sampleCount=null 인 결과 1행을 반환한다.
     *
     * @param areaMinSqm 요청의 평수를 ㎡로 환산한 하한 (버림)
     * @param areaMaxSqm 요청의 평수를 ㎡로 환산한 상한 (버림)
     */
    MedianAggResult findAggregatedMedian(@Param("request") RentMedianRequest request,
                                         @Param("areaMinSqm") long areaMinSqm,
                                         @Param("areaMaxSqm") long areaMaxSqm,
                                         @Param("startYm") String startYm,
                                         @Param("endYm") String endYm);

    /**
     * HoldOut 알고리즘 전용. 지역·기간 조건만 받아 (주거유형, 거래유형, 평수 버킷)별
     * 분위값을 한 번의 쿼리로 반환한다. 최대 40행(4유형 × 2거래 × 5버킷).
     */
    List<BulkMedianResult> findBulkMedian(@Param("regionCode") String regionCode,
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
