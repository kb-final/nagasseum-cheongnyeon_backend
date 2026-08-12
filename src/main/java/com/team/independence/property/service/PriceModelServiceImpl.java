package com.team.independence.property.service;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.property.dto.MonthlyPricePoint;
import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.dto.PriceModelResponse;
import com.team.independence.property.mapper.RegionMapper;
import com.team.independence.property.mapper.RentTransactionMapper;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PriceModelServiceImpl implements PriceModelService {

    private static final int WINDOW_MONTHS = 36;
    private static final int MIN_SAMPLES_PER_MONTH = 5;
    private static final int MIN_VALID_MONTHS = 6;
    private static final double PYEONG_TO_SQM = 3.305785;
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final RegionMapper regionMapper;
    private final RentTransactionMapper rentTransactionMapper;

    @Override
    @Transactional(readOnly = true)
    public PriceModelResponse estimate(PriceModelRequest request) {
        String regionName = regionMapper.findFullNameByCode(request.getRegionCode());
        if (regionName == null) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND);
        }

        YearMonth end = YearMonth.now();
        YearMonth start = end.minusMonths(WINDOW_MONTHS - 1);
        String startYm = start.format(YM);
        String endYm = end.format(YM);

        List<MonthlyPricePoint> raw = rentTransactionMapper.findAmountsForPriceModel(
                request.getRegionCode(),
                request.getHousingType(),
                request.getDealType(),
                toSqm(request.getAreaMin()),
                toSqm(request.getAreaMax()),
                startYm,
                endYm);

        // 월별 그룹핑 → 표본 부족 월 제외 → 평단가 중앙값 시계열
        List<Double> monthlyMedians = raw.stream()
                .collect(Collectors.groupingBy(MonthlyPricePoint::getDealYm))
                .entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_SAMPLES_PER_MONTH)
                .sorted(Map.Entry.comparingByKey())  // YYYYMM은 사전순 = 시간순
                .map(e -> medianPerPyeong(e.getValue()))
                .collect(Collectors.toList());

        if (monthlyMedians.size() < MIN_VALID_MONTHS) {
            throw new BusinessException(ErrorCode.PROPERTY_INSUFFICIENT_DATA);
        }

        PriceModel model = PriceModel.estimate(monthlyMedians);

        return PriceModelResponse.builder()
                .regionCode(request.getRegionCode())
                .regionName(regionName)
                .housingType(request.getHousingType())
                .dealType(request.getDealType())
                .annualDrift(model.annualDrift())
                .cagr(model.cagr())
                .annualVol(model.annualVol())
                .months(model.months())
                .startYm(startYm)
                .endYm(endYm)
                .build();
    }

    /** 한 달치 거래 목록에서 평당 보증금(원/평)의 중앙값을 반환 */
    private double medianPerPyeong(List<MonthlyPricePoint> points) {
        double[] sorted = points.stream()
                .mapToDouble(p -> p.getDeposit() / (p.getArea().doubleValue() / PYEONG_TO_SQM))
                .sorted()
                .toArray();
        int mid = sorted.length / 2;
        return sorted.length % 2 == 0
                ? (sorted[mid - 1] + sorted[mid]) / 2.0
                : sorted[mid];
    }

    private long toSqm(int pyeong) {
        return (long) Math.floor(pyeong * PYEONG_TO_SQM);
    }
}
