package com.team.independence.property.service;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.property.dto.MonthlyPricePoint;
import com.team.independence.property.dto.PriceModelBatchRow;
import com.team.independence.property.dto.PriceModelKey;
import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.dto.PriceModelResponse;
import com.team.independence.property.dto.TypeDealPair;
import com.team.independence.property.mapper.RegionMapper;
import com.team.independence.property.mapper.RentTransactionMapper;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
    private final PriceModelStore priceModelStore;

    @Override
    @Transactional(readOnly = true, noRollbackFor = BusinessException.class)
    public PriceModelResponse estimate(PriceModelRequest request) {
        Optional<PriceModelResponse> cached = priceModelStore.find(request);
        if (cached.isPresent()) {
            return cached.get();
        }

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

        PriceModelResponse response = PriceModelResponse.builder()
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
        priceModelStore.save(request, response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<PriceModelKey, PriceModelResponse> estimateBatch(
            String regionCode, Collection<PriceModelKey> keys) {

        Map<PriceModelKey, PriceModelResponse> result = new HashMap<>();
        if (keys == null || keys.isEmpty()) {
            return result;
        }

        // 1) 캐시 조회 — MGET 한 번으로 전체 조합을 확인하고, 미스만 다음 단계로 넘긴다.
        //    조합마다 GET을 돌리면 40개 키에 왕복 40회가 나가고 Realistic·HoldOut이 연달아 불러 80회가 된다.
        result.putAll(priceModelStore.findAll(regionCode, keys));

        List<PriceModelKey> misses = new ArrayList<>();
        for (PriceModelKey key : keys) {
            if (!result.containsKey(key)) {
                misses.add(key);
            }
        }
        if (misses.isEmpty()) {
            return result;
        }

        // 2) 미스 조합의 (housing_type, deal_type) 유니크 쌍만 뽑아 단일 배치 쿼리.
        //    같은 (ht, dt)의 여러 area 버킷은 한 번의 인덱스 서브레인지 스캔에서 함께 딸려 나온다.
        String regionName = regionMapper.findFullNameByCode(regionCode);
        if (regionName == null) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND);
        }

        YearMonth end = YearMonth.now();
        YearMonth start = end.minusMonths(WINDOW_MONTHS - 1);
        String startYm = start.format(YM);
        String endYm = end.format(YM);

        Set<TypeDealPair> pairSet = new HashSet<>();
        for (PriceModelKey k : misses) {
            pairSet.add(new TypeDealPair(k.housingType(), k.dealType()));
        }
        List<TypeDealPair> typePairs = new ArrayList<>(pairSet);

        List<PriceModelBatchRow> rows = rentTransactionMapper.findAmountsForPriceModelBatch(
                regionCode, typePairs, startYm, endYm);

        // 3) 조합 키(ht|dt|areaMin|areaMax)별로 행을 재그룹핑한다. area는 SIZE_BUCKETS 하드코딩 경계와 정확히 일치.
        Map<PriceModelKey, List<MonthlyPricePoint>> perKey = new HashMap<>();
        for (PriceModelKey miss : misses) {
            perKey.put(miss, new ArrayList<>());
        }
        for (PriceModelBatchRow row : rows) {
            PriceModelKey routingKey = routeToKey(row, misses);
            if (routingKey == null) continue;
            perKey.get(routingKey).add(new MonthlyPricePoint(
                    row.getDealYm(), row.getDeposit(), row.getArea()));
        }

        // 4) 조합별로 PriceModel.estimate → 결과 맵에 담기.
        Map<PriceModelKey, PriceModelResponse> computed = new HashMap<>();
        for (PriceModelKey miss : misses) {
            List<MonthlyPricePoint> raw = perKey.get(miss);
            PriceModelResponse response = buildPriceModel(regionCode, regionName, miss, raw, startYm, endYm);
            if (response == null) {
                // 표본 부족은 배치에서는 예외를 던지지 않고 스킵한다. 호출자는 null을 폴백 신호로 쓴다.
                continue;
            }
            computed.put(miss, response);
        }

        // 5) 새로 계산한 조합만 파이프라인 한 번으로 캐싱한다.
        priceModelStore.saveAll(regionCode, computed);
        result.putAll(computed);
        return result;
    }

    /** 배치 행 하나를 (housingType, dealType, area 버킷) 기준으로 미스 키 중 어디에 속하는지 매핑. */
    private PriceModelKey routeToKey(PriceModelBatchRow row, List<PriceModelKey> misses) {
        double areaSqm = row.getArea().doubleValue();
        for (PriceModelKey k : misses) {
            if (k.housingType() != row.getHousingType()) continue;
            if (k.dealType() != row.getDealType()) continue;
            double minSqm = toSqm(k.areaMin());
            double maxSqm = toSqm(k.areaMax());
            if (areaSqm >= minSqm && areaSqm <= maxSqm) {
                return k;
            }
        }
        return null;
    }

    /** 배치 조합의 raw 행에서 월별 median 시계열을 만들고 PriceModel.estimate. 표본이 부족하면 null. */
    private PriceModelResponse buildPriceModel(
            String regionCode, String regionName, PriceModelKey key,
            List<MonthlyPricePoint> raw, String startYm, String endYm) {

        List<Double> monthlyMedians = raw.stream()
                .collect(Collectors.groupingBy(MonthlyPricePoint::getDealYm))
                .entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_SAMPLES_PER_MONTH)
                .sorted(Map.Entry.comparingByKey())
                .map(e -> medianPerPyeong(e.getValue()))
                .collect(Collectors.toList());

        if (monthlyMedians.size() < MIN_VALID_MONTHS) {
            return null;
        }

        PriceModel model = PriceModel.estimate(monthlyMedians);
        return PriceModelResponse.builder()
                .regionCode(regionCode)
                .regionName(regionName)
                .housingType(key.housingType())
                .dealType(key.dealType())
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
