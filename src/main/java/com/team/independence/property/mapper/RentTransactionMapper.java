package com.team.independence.property.mapper;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.domain.RentTransaction;
import com.team.independence.property.dto.BulkMedianResult;
import com.team.independence.property.dto.MedianAggResult;
import com.team.independence.property.dto.MonthlyPricePoint;
import com.team.independence.property.dto.PriceModelBatchRow;
import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.SigunguMedianResult;
import com.team.independence.property.dto.TypeDealPair;
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
     * 지역·기간을 공유하는 여러 (주거유형, 거래유형) 쌍에 대해 평수버킷별 분위값을 단일 왕복으로 반환한다.
     * (housing_type, deal_type) IN 절이 idx_rent_query 서브레인지를 조합별로 그대로 태우므로
     * 처리 행 수는 페어별 개별 쿼리 합과 동일하고, 왕복·파서·옵티마이저 오버헤드만 사라진다.
     * 결과 최대 8 × 5 = 40행.
     */
    List<BulkMedianResult> findBulkMedianBatch(@Param("regionCode") String regionCode,
                                               @Param("typePairs") List<TypeDealPair> typePairs,
                                               @Param("startYm") String startYm,
                                               @Param("endYm") String endYm);

    /**
     * 시군구 코드 목록에 대해 보증금·월세 중앙값을 단일 쿼리로 반환한다.
     * LIKE '11%' 전체 스캔 대신 IN(코드 목록)을 사용해 인덱스를 코드별로 탄다.
     * RealisticAlgorithm.selectRegion()에서 N개 시군구 개별 조회를 대체한다.
     */
    List<SigunguMedianResult> findMediansByRegionCodes(
            @Param("sigunguCodes") List<String> sigunguCodes,
            @Param("housingType") HousingType housingType,
            @Param("dealType") DealType dealType,
            @Param("areaMinSqm") long areaMinSqm,
            @Param("areaMaxSqm") long areaMaxSqm,
            @Param("startYm") String startYm,
            @Param("endYm") String endYm,
            @Param("depositMin") long depositMin,
            @Param("depositMax") long depositMax,
            @Param("monthlyRentMin") Long monthlyRentMin,
            @Param("monthlyRentMax") Long monthlyRentMax);

    /** 가격 모델(μ, σ) 산출용 월별 (거래연월, 보증금, 면적) 목록. 보증금 0 제외 */
    List<MonthlyPricePoint> findAmountsForPriceModel(@Param("regionCode") String regionCode,
                                                     @Param("housingType") HousingType housingType,
                                                     @Param("dealType") DealType dealType,
                                                     @Param("areaMinSqm") long areaMinSqm,
                                                     @Param("areaMaxSqm") long areaMaxSqm,
                                                     @Param("startYm") String startYm,
                                                     @Param("endYm") String endYm);

    /**
     * 배치 PriceModel 산출용. 지역·기간을 공유하는 여러 (주거유형, 거래유형) 조합을 단일 왕복으로 조회한다.
     * area는 SIZE_BUCKETS 5개 구간의 합집합만 반환하고, 조합별 버킷 분리는 서비스에서 처리한다.
     * 조합별로 개별 쿼리를 40회 돌리던 것을 1회 스캔으로 대체해 콜드 캐시 응답 시간을 크게 낮춘다.
     */
    List<PriceModelBatchRow> findAmountsForPriceModelBatch(
            @Param("regionCode") String regionCode,
            @Param("typePairs") List<TypeDealPair> typePairs,
            @Param("startYm") String startYm,
            @Param("endYm") String endYm);
}
