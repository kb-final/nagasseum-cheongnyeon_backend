package com.team.independence.property.service;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.property.dto.RentMedianAmount;
import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.dto.RentMedianResponse.Quartile;
import com.team.independence.property.mapper.RegionMapper;
import com.team.independence.property.mapper.RentTransactionMapper;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.ToLongFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RentMedianServiceImpl implements RentMedianService {

    /** 집계 구간 길이. 조회 시점 월을 포함해 6개월 */
    private static final int MONTHS = 6;

    /** 평 → ㎡ 환산 계수 (1평 = 3.305785㎡) */
    private static final double PYEONG_TO_SQM = 3.305785;

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final RegionMapper regionMapper;
    private final RentTransactionMapper rentTransactionMapper;

    @Override
    @Transactional(readOnly = true)
    public RentMedianResponse getMedian(RentMedianRequest request) {
        // full_name은 NOT NULL이므로 null이면 그 지역 코드가 없다는 뜻이다.
        String regionName = regionMapper.findFullNameByCode(request.getRegionCode());
        if (regionName == null) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND);
        }

        YearMonth end = YearMonth.now();
        YearMonth start = end.minusMonths(MONTHS - 1);
        String startYm = start.format(YM);
        String endYm = end.format(YM);

        List<RentMedianAmount> amounts = rentTransactionMapper.findAmountsForMedian(
                request,
                toSqm(request.getAreaMin()),
                toSqm(request.getAreaMax()),
                startYm,
                endYm);

        return RentMedianResponse.builder()
                .regionCode(request.getRegionCode())
                .regionName(regionName)
                .housingType(request.getHousingType())
                .dealType(request.getDealType())
                .baseStartYm(startYm)
                .baseEndYm(endYm)
                .sampleCount(amounts.size())
                .deposit(quartile(amounts, RentMedianAmount::getDeposit))
                // 전세는 월세가 0으로 적재되어 분위값이 전부 0이 되므로 계산하지 않는다.
                .monthlyRent(request.usesMonthlyRentFilter()
                        ? quartile(amounts, RentMedianAmount::getMonthlyRent)
                        : Quartile.empty())
                .build();
    }

    /** 평수를 ㎡로 환산한다. 양쪽 모두 버림이라 하한은 넓어지고 상한은 좁아지는데, 명세서가 정한 규칙이다. */
    private long toSqm(int pyeong) {
        return (long) Math.floor(pyeong * PYEONG_TO_SQM);
    }

    private Quartile quartile(List<RentMedianAmount> amounts, ToLongFunction<RentMedianAmount> amount) {
        if (amounts.isEmpty()) {
            return Quartile.empty();
        }
        long[] sorted = amounts.stream().mapToLong(amount).sorted().toArray();
        return Quartile.of(
                percentile(sorted, 0.25),
                percentile(sorted, 0.50),
                percentile(sorted, 0.75));
    }

    /**
     * nearest-rank 방식. 보간하지 않으므로 결과가 항상 실제 거래 금액이고,
     * 표본이 한 자릿수인 조건(단독다가구·비수도권)에서도 값이 왜곡되지 않는다.
     */
    private long percentile(long[] sorted, double ratio) {
        int index = (int) Math.ceil(ratio * sorted.length) - 1;
        return sorted[Math.max(index, 0)];
    }
}
