package com.team.independence.property.mapper;

import com.team.independence.property.domain.HousingType;
import com.team.independence.property.domain.RentTransaction;
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
}
