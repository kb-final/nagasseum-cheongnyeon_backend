package com.team.independence.property.service;

import com.team.independence.property.dto.RentMarketStatsResponse;
import com.team.independence.property.mapper.RentTransactionMapper;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RentMarketQueryServiceImpl implements RentMarketQueryService {

    private final RentTransactionMapper rentTransactionMapper;

    @Override
    @Transactional(readOnly = true)
    public RentMarketStatsResponse getMarketStats(String regionCode, String housingType, String dealType,
                                                   BigDecimal areaMinSqm, BigDecimal areaMaxSqm) {
        List<Long> deposits = rentTransactionMapper.findDeposits(
                regionCode, housingType, dealType, areaMinSqm, areaMaxSqm);

        return RentMarketStatsResponse.builder()
                .p25(percentile(deposits, 25))
                .median(percentile(deposits, 50))
                .p75(percentile(deposits, 75))
                .sampleCount(deposits.size())
                .build();
    }

    /** 정렬된 리스트에서 선형보간법으로 백분위수를 계산한다. */
    private Long percentile(List<Long> sortedDeposits, double p) {
        int n = sortedDeposits.size();
        if (n == 0) {
            return null;
        }
        if (n == 1) {
            return sortedDeposits.get(0);
        }

        double index = p / 100.0 * (n - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sortedDeposits.get(lower);
        }

        double fraction = index - lower;
        double value = sortedDeposits.get(lower)
                + (sortedDeposits.get(upper) - sortedDeposits.get(lower)) * fraction;
        return Math.round(value);
    }
}
