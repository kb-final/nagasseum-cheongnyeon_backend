package com.team.independence.goal.service.algorithm;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.goal.dto.AlgorithmType;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.dto.LoanPlans;
import com.team.independence.goal.service.GoalService;
import com.team.independence.goal.service.calculator.LoanPlanCalculator;
import com.team.independence.goal.service.RecommendationAlgorithm;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.mapper.RegionMapper;
import com.team.independence.property.service.RentMedianService;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 현실 우선 추천 — 목표 시점을 고정한 채 조건을 맞춘다.
 *
 * <p>이 카드의 정체성은 <b>시점을 절대 늘리지 않는 것</b>이다. 사용자가 "2년 안에"라고 하면
 * 2년을 그대로 두고 "그 안에 도달 가능한 조건은 여기까지"를 답한다. 조건을 고정하고 시점을 늘리는
 * {@code HOLD_OUT}과 정확히 반대 방향이며, 같은 사람에게 같은 데이터로 상반된 답을 주는 한 쌍이다.
 *
 * <h3>사용자 조건에서 출발해, 안 될 때만 푼다</h3>
 * 탐색의 시작점은 기본값이 아니라 <b>사용자가 입력한 조건</b>이다. 빈 칸만 기본값으로 채운다.
 *
 * <ol>
 *   <li><b>1차</b> — 사용자가 준 조건을 지킨 채, 주지 않은 항목만 움직여 본다.
 *       목표 시점 안에 되는 것이 있으면 <b>거기서 끝낸다.</b> 갈 수 있는 사람의 조건을
 *       마음대로 바꾸지 않는다</li>
 *   <li><b>2차</b> — 1차로는 목표 시점을 못 지킬 때만 사용자가 준 조건까지 풀고 다시 찾는다.
 *       "그 조건으로는 어려우니 이런 대안은 어떠냐"에 해당한다</li>
 * </ol>
 *
 * <p>지역은 2차에서도 풀지 않는다. 사는 곳을 옮기는 것은 조건을 조정하는 것과 성격이 다르다.
 *
 * <p>2차까지 가는 이유는, 조건을 전부 지정한 사용자에게 조정 여지가 없다고 "입력하신 조건은
 * 108개월 걸립니다"라고만 답하면 대안을 주는 카드가 아니라 진단만 하는 카드가 되기 때문이다.
 * 입력 그대로의 결과는 {@code PREFERENCE} 카드가 이미 보여준다.
 *
 * <h3>전세와 월세를 같은 저울에 올리는 방법</h3>
 * 월세는 "보증금이 작으니 싸다"가 아니다. 전세도 보증금이 묶여 이자를 못 버는 만큼 비용이 있다.
 * 그래서 월세를 전세 환산 보증금으로 바꿔 비교한다.
 * <pre>환산보증금 = 보증금 + (월세 × 12 ÷ 이자율)</pre>
 * 이때 쓰는 이자율은 외부 전월세전환율 통계가 아니라 이 서비스가 이미 쓰고 있는 연 5%
 * ({@code GoalServiceImpl.ANNUAL_INTEREST_RATE})다. 자산이 5%로 불어난다고 계산해 놓고 기회비용은
 * 다른 이율로 잡으면 모순이라, 모델 내부에서 일관된 값을 쓴다.
 *
 * <p>환산보증금은 <b>비교·판정에만</b> 쓴다. 화면에 나가는 목표 금액은 사용자가 실제로 모아야 하는
 * 보증금이다. 월세 매물을 추천하면서 환산값을 목표 금액으로 보여주면 실제보다 몇 배 큰 금액을
 * 모으라는 말이 되기 때문이다.
 *
 * <h3>조건을 내리는 순서는 없다</h3>
 * 평수를 먼저 깎고 안 되면 유형을 깎는 식의 순서를 두지 않았다. 그런 순서는 우리가 정할 근거가 없고,
 * 무엇보다 <b>한 방향으로 내려가면 처음 걸린 곳에서 멈춰 더 나은 답을 놓친다</b>. "아파트 4~9평"에서
 * 예산이 맞았다고 멈추면 "오피스텔 15~19평"이 더 나은 선택인데도 확인조차 하지 않게 된다.
 *
 * <p>대신 후보를 <b>전부 평가한 뒤 예산에 맞는 것 중 가장 비싼 것</b>을 고른다. 가격이 곧 주거 수준의
 * 대리 지표라, 이 규칙 하나가 "여유로우면 더 좋은 조건, 모자라면 낮은 조건"을 모두 처리한다.
 * 주거유형과 시군구의 우열도 우리가 정하지 않고 실거래 가격이 정한다.
 *
 * <h3>지역은 한 번만 정하고 다시 내리지 않는다</h3>
 * 시군구는 조건 탐색보다 <b>먼저</b> 확정한다. 이때 예산에 맞는 시군구가 하나도 없으면 가장 싼
 * 시군구를 고르므로, 지역을 낮추는 일은 이 시점에 이미 끝난다. 조건을 다 풀어도 목표 시점을 못
 * 지키는 경우에도 지역을 다시 건드리지 않는다. 더 내릴 지역이 남아 있지 않기 때문이다.
 *
 * <p><b>알려진 한계</b>: 여기서 고르는 "가장 싼 시군구"는 어디까지나 <b>시작 조합 기준</b>이다.
 * 다른 유형·평수로 보면 더 싼 시군구가 있을 수 있는데, 지역과 조건을 함께 훑으려면
 * 시군구 수 × 유형 × 평수 × 거래유형이라 요청 한 번에 수백 번의 집계 쿼리가 필요해
 * 의도적으로 포기한 범위다. 사전집계 테이블이 생기면 넓힐 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealisticAlgorithm implements RecommendationAlgorithm {

    /**
     * 허용 배수 상한. (도달 개월 ÷ 희망 개월)이 이 값 이하인 후보만 "목표 시점 안에 가능"으로 본다.
     *
     * <p>1.0은 목표 시점을 정확히 지킨다는 뜻이다. 올리면 그만큼 목표 시점을 넘긴 후보도 받아들이게
     * 되므로, 이 카드가 "시점을 지킨다"고 한 약속을 얼마나 느슨하게 볼지의 값이다.
     */
    private static final double MAX_REACH_RATIO = 1.0;

    /**
     * 목표 시점 미지정 시 기본값(개월).
     *
     * <p>주택임대차보호법상 기본 임대차 기간이 2년이라, 사용자가 정하지 않았을 때 다음 계약 주기를
     * 목표로 잡는 것이 자연스럽다.
     */
    private static final int DEFAULT_TARGET_MONTHS = 24;

    /** 시군구 시세를 서로 비교할 때 기준으로 삼는 평수 구간 (15~19평, SIZE_BUCKETS[2]) */
    private static final int REFERENCE_SIZE_BUCKET = 2;

    /** 시군구 비교 기준 주거유형. 실거래 표본이 가장 많아 빈 지역이 생길 확률이 낮다. */
    private static final HousingType REFERENCE_HOUSING_TYPE = HousingType.APT;

    /** 시군구 비교 기준 거래유형. 환산 없이 그대로 목돈으로 비교되는 쪽이다. */
    private static final DealType REFERENCE_DEAL_TYPE = DealType.JEONSE;

    /**
     * 이보다 표본이 적은 조합은 median을 신뢰하지 않고 건너뛴다.
     *
     * <p>단독다가구나 비수도권처럼 거래가 드문 조건에서 한두 건짜리 median이 추천을 좌우하는 것을 막는다.
     */
    private static final int MIN_SAMPLE_COUNT = 3;

    /** 보증금 필터 미지정 시 사용할 상한(원). 사실상 무제한. */
    private static final long DEPOSIT_MAX_DEFAULT = 100_000_000_000L;

    /**
     * 월세를 목돈으로 환산할 때 쓰는 연 이자율.
     *
     * <p>{@code GoalServiceImpl.ANNUAL_INTEREST_RATE}와 같은 값이어야 한다. 자산 성장률과 기회비용률이
     * 어긋나면 "전세가 유리한지 월세가 유리한지"의 판단이 모델 내부에서 모순된다.
     */
    private static final double ANNUAL_INTEREST_RATE = 0.05;

    private final AssetSummaryService assetSummaryService;
    private final RentMedianService rentMedianService;
    private final RegionMapper regionMapper;
    private final LoanPlanCalculator loanPlanCalculator;
    /** 저축 복리 계산을 재사용하기 위한 의존. 같은 식을 두 곳에 두지 않으려는 것이다. */
    private final GoalService goalService;

    @Override
    public Optional<GoalRecommendationResponse.RecommendationItem> recommend(
            long memberId, GoalRecommendationRequest request) {

        YearMonth targetDate = resolveTargetDate(request);
        long desiredMonths = monthsUntil(targetDate);

        AssetNetWorthBreakdown netWorth = assetSummaryService.getNetWorthBreakdown(memberId);
        long monthlySaving = assetSummaryService.getMonthlySavingsOrZero(memberId);

        Search search = new Search(memberId, netWorth, monthlySaving, desiredMonths);

        // 1단계: 시군구 확정
        String regionCode = selectRegion(request, search);
        if (regionCode == null) {
            log.warn("추천 가능한 시군구를 찾지 못했습니다. memberId={}, regionCode={}",
                    memberId, request.getRegionCode());
            return Optional.empty();
        }

        // 2단계: 사용자가 준 조건을 지킨 채, 주지 않은 항목만 움직여 본다
        List<Candidate> asRequested = evaluateConditions(regionCode, request, search, true);
        Optional<Candidate> keepingInput = bestWithinTarget(asRequested);
        if (keepingInput.isPresent()) {
            return Optional.of(assemble(memberId, keepingInput.get(), targetDate, desiredMonths, false));
        }

        // 3단계: 입력한 조건으로는 목표 시점을 못 지킨다. 그때만 조건을 풀고 다시 찾는다.
        // 준 조건이 하나도 없으면 1차가 이미 전 범위 탐색이라 다시 조회하지 않는다.
        List<Candidate> relaxed = hasInputCondition(request)
                ? evaluateConditions(regionCode, request, search, false)
                : asRequested;
        if (relaxed.isEmpty()) {
            log.warn("실거래 표본이 있는 조합이 없습니다. memberId={}, regionCode={}", memberId, regionCode);
            return Optional.empty();
        }

        Candidate chosen = bestWithinTarget(relaxed).orElseGet(() -> cheapest(relaxed));
        return Optional.of(assemble(memberId, chosen, targetDate, desiredMonths, true));
    }

    /** 사용자가 지역 외에 조정 가능한 조건을 하나라도 줬는가 */
    private boolean hasInputCondition(GoalRecommendationRequest request) {
        return request.getPropertyType() != null
                || request.getTradeType() != null
                || (request.getSizeMin() != null && request.getSizeMax() != null);
    }

    // ===== 1단계: 지역 =====

    /**
     * 추천할 시군구를 정한다. 사용자가 시군구(5자리)를 줬으면 그대로 쓰고, 시도(2자리)만 줬으면
     * 기준 조합 하나로 시도 내 시군구를 훑어 예산에 맞는 가장 좋은 곳을 고른다.
     *
     * <p>예산에 맞는 곳이 하나도 없으면 가장 싼 시군구를 돌려준다. 여기서 포기하지 않는 이유는,
     * 그 경우에도 "이 시도에서 가장 가까운 조건은 이것이고 N개월이 필요하다"고 답하는 편이
     * 빈손보다 낫기 때문이다.
     *
     * @return 시군구 코드. 시도에 조회 가능한 시군구가 아예 없으면 null
     */
    private String selectRegion(GoalRecommendationRequest request, Search search) {
        String requested = request.getRegionCode();
        if (requested.length() == 5) {
            return requested; // 사용자가 지정한 시군구는 보호 대상
        }

        List<String> sigunguCodes = regionMapper.findCodesBySidoPrefix(requested);
        if (sigunguCodes.isEmpty()) {
            return null;
        }

        // 지역끼리 비교하려면 같은 잣대여야 하므로 조합 하나를 고정하는데, 그 조합은 기본값이 아니라
        // 사용자가 입력한 조건이다. 빈 칸만 기본값으로 채운다.
        HousingType housingType = request.getPropertyType() != null
                ? request.getPropertyType() : REFERENCE_HOUSING_TYPE;
        DealType dealType = request.getTradeType() != null
                ? request.getTradeType() : REFERENCE_DEAL_TYPE;
        int[] size = inputSize(request) != null ? inputSize(request) : SIZE_BUCKETS[REFERENCE_SIZE_BUCKET];

        List<Candidate> ranked = new ArrayList<>();
        for (String code : sigunguCodes) {
            evaluate(code, housingType, dealType, size[0], size[1], request, search)
                    .ifPresent(ranked::add);
        }
        if (ranked.isEmpty()) {
            return null;
        }

        // 예산에 맞는 곳 중 가장 좋은(비싼) 곳. 없으면 가장 싼 곳.
        return ranked.stream()
                .filter(Candidate::withinTarget)
                .max(Comparator.comparingLong(Candidate::getComparableAmount))
                .orElseGet(() -> ranked.stream()
                        .min(Comparator.comparingLong(Candidate::getComparableAmount))
                        .orElseThrow(IllegalStateException::new))
                .getRegionCode();
    }

    // ===== 2단계: 조건 =====

    /**
     * 확정된 시군구 안에서 조합들을 평가한다.
     *
     * @param keepInput true면 사용자가 준 항목은 그 값으로 고정하고 주지 않은 항목만 펼친다.
     *                  false면 사용자가 준 항목까지 전부 펼친다.
     */
    private List<Candidate> evaluateConditions(
            String regionCode, GoalRecommendationRequest request, Search search, boolean keepInput) {

        List<Candidate> candidates = new ArrayList<>();
        for (int[] size : sizeCandidates(request, keepInput)) {
            for (HousingType housingType : housingTypeCandidates(request, keepInput)) {
                for (DealType dealType : dealTypeCandidates(request, keepInput)) {
                    evaluate(regionCode, housingType, dealType, size[0], size[1], request, search)
                            .ifPresent(candidates::add);
                }
            }
        }
        return candidates;
    }

    /**
     * 목표 시점 안에 도달 가능한 것 중 <b>가장 비싼</b> 것.
     *
     * <p>가격이 곧 주거 수준의 대리 지표라, 예산을 남기지 않고 쓰는 쪽이 더 나은 집이다.
     * 이 규칙 하나로 "여유로우면 더 좋은 조건, 모자라면 낮은 조건"이 모두 처리되어
     * 상향·하향을 따로 구현하지 않아도 된다.
     */
    private Optional<Candidate> bestWithinTarget(List<Candidate> candidates) {
        return candidates.stream()
                .filter(Candidate::withinTarget)
                .max(Comparator.comparingLong(Candidate::getComparableAmount));
    }

    /**
     * 가장 싼 것. 목표 시점 안에 되는 것이 하나도 없을 때 쓴다.
     *
     * <p>예산이 고정이므로 가장 싼 것이 곧 목표 시점에 가장 가까운 후보다. 카드는 이때
     * "여기까지 가능하다" 대신 "가장 가까운 것이 이것이고 N개월 걸린다"고 말한다.
     */
    private Candidate cheapest(List<Candidate> candidates) {
        return candidates.stream()
                .min(Comparator.comparingLong(Candidate::getComparableAmount))
                .orElseThrow(IllegalStateException::new);
    }

    // ===== 후보군 산출 =====

    private List<int[]> sizeCandidates(GoalRecommendationRequest request, boolean keepInput) {
        int[] input = inputSize(request);
        return keepInput && input != null ? List.of(input) : Arrays.asList(SIZE_BUCKETS);
    }

    private List<HousingType> housingTypeCandidates(GoalRecommendationRequest request, boolean keepInput) {
        return keepInput && request.getPropertyType() != null
                ? List.of(request.getPropertyType())
                : Arrays.asList(HousingType.values());
    }

    private List<DealType> dealTypeCandidates(GoalRecommendationRequest request, boolean keepInput) {
        return keepInput && request.getTradeType() != null
                ? List.of(request.getTradeType())
                : Arrays.asList(DealType.values());
    }

    /** 사용자가 준 평수. 하한·상한 중 하나라도 없으면 범위가 성립하지 않아 없는 것으로 본다. */
    private int[] inputSize(GoalRecommendationRequest request) {
        if (request.getSizeMin() == null || request.getSizeMax() == null) {
            return null;
        }
        return new int[]{request.getSizeMin(), request.getSizeMax()};
    }

    // ===== 후보 평가 =====

    /**
     * 조합 하나의 실거래 median을 조회해 환산보증금과 도달 개월까지 계산한다.
     *
     * <p>이미 확인한 조합은 다시 조회하지 않는다. 1차(입력 조건 유지)에서 본 조합은 2차(조건 해제)의
     * 후보에 그대로 포함되고, 시군구를 고를 때 쓴 조합도 1차 후보와 겹칠 수 있다. 실거래 집계는
     * 6개월치를 훑는 쿼리라 중복 조회가 그대로 응답 시간이 된다.
     */
    private Optional<Candidate> evaluate(
            String regionCode, HousingType housingType, DealType dealType,
            int areaMin, int areaMax, GoalRecommendationRequest request, Search search) {

        String key = regionCode + "|" + housingType + "|" + dealType + "|" + areaMin + "|" + areaMax;
        Optional<Candidate> cached = search.evaluated.get(key);
        if (cached != null) {
            return cached;
        }
        Optional<Candidate> result = lookUp(regionCode, housingType, dealType, areaMin, areaMax, request, search);
        search.evaluated.put(key, result);
        return result;
    }

    private Optional<Candidate> lookUp(
            String regionCode, HousingType housingType, DealType dealType,
            int areaMin, int areaMax, GoalRecommendationRequest request, Search search) {

        RentMedianResponse median;
        try {
            median = rentMedianService.getMedian(
                    buildMedianRequest(regionCode, housingType, dealType, areaMin, areaMax, request));
        } catch (RuntimeException e) {
            // 지역 코드 오류 등으로 한 조합이 실패해도 나머지 탐색은 계속한다.
            log.debug("실거래 조회 실패로 조합을 건너뜁니다. regionCode={}, housingType={}, dealType={}",
                    regionCode, housingType, dealType, e);
            return Optional.empty();
        }

        if (median.getSampleCount() < MIN_SAMPLE_COUNT || median.getDeposit().getMedian() == null) {
            return Optional.empty();
        }

        long deposit = median.getDeposit().getMedian();
        long monthlyRent = dealType == DealType.WOLSE && median.getMonthlyRent().getMedian() != null
                ? median.getMonthlyRent().getMedian() : 0L;
        long comparableAmount = toComparableAmount(deposit, monthlyRent);

        Long reachMonths = goalService.calculateEffectiveMonthToReach(
                search.memberId, search.netWorth, search.monthlySaving, comparableAmount);

        return Optional.of(new Candidate(
                regionCode, median.getRegionName(), housingType, dealType,
                areaMin, areaMax, deposit, monthlyRent, comparableAmount,
                median.getSampleCount(), reachMonths, search.desiredMonths));
    }

    /**
     * 월세를 전세 환산 보증금으로 바꾼다. 전세는 이미 목돈이라 그대로 둔다.
     *
     * <p>월세 × 12를 이자율로 나눈 값은 "그 월세만큼의 이자를 낳는 원금"이다. 즉 이 금액을 보증금으로
     * 묶어 두는 것과 매달 그 월세를 내는 것이 같은 부담이라는 뜻이다.
     */
    private long toComparableAmount(long deposit, long monthlyRent) {
        if (monthlyRent <= 0) {
            return deposit;
        }
        return deposit + Math.round(monthlyRent * 12 / ANNUAL_INTEREST_RATE);
    }

    private RentMedianRequest buildMedianRequest(
            String regionCode, HousingType housingType, DealType dealType,
            int areaMin, int areaMax, GoalRecommendationRequest request) {

        RentMedianRequest median = new RentMedianRequest();
        median.setRegionCode(regionCode);
        median.setHousingType(housingType);
        median.setDealType(dealType);
        median.setAreaMin(areaMin);
        median.setAreaMax(areaMax);
        // 사용자가 준 금액 범위는 보호 대상이므로 조회 필터로 그대로 넘긴다.
        median.setDepositMin(request.getDepositMin() != null ? request.getDepositMin() : 0L);
        median.setDepositMax(request.getDepositMax() != null ? request.getDepositMax() : DEPOSIT_MAX_DEFAULT);
        median.setMonthlyRentMin(request.getMonthlyRentMin());
        median.setMonthlyRentMax(request.getMonthlyRentMax());
        return median;
    }

    // ===== 응답 조립 =====

    private GoalRecommendationResponse.RecommendationItem assemble(
            long memberId, Candidate chosen, YearMonth targetDate,
            long desiredMonths, boolean adjusted) {

        // 화면에 나가는 목표 금액은 환산값이 아니라 실제로 모아야 하는 보증금이다.
        LoanPlans plans = loanPlanCalculator.calculate(memberId, chosen.getDeposit(), targetDate);

        GoalRecommendationResponse.Condition condition = GoalRecommendationResponse.Condition.builder()
                .regionCode(chosen.getRegionCode())
                .regionName(chosen.getRegionName())
                .housingType(chosen.getHousingType())
                .dealType(chosen.getDealType())
                .areaMin(chosen.getAreaMin())
                .areaMax(chosen.getAreaMax())
                .monthlyRent(chosen.getMonthlyRent())
                .sampleCount(chosen.getSampleCount())
                .build();

        return GoalRecommendationResponse.RecommendationItem.builder()
                .type(AlgorithmType.REALISTIC)
                .title(buildTitle(chosen, desiredMonths))
                .reason(buildReason(chosen, desiredMonths, adjusted))
                .condition(condition)
                .loanX(plans.getLoanX())
                .loanO(plans.getLoanO())
                .build();
    }

    private String buildTitle(Candidate chosen, long desiredMonths) {
        if (chosen.withinTarget()) {
            return String.format("%d개월 안에 갈 수 있는 %s %s",
                    desiredMonths, chosen.getRegionName(), label(chosen.getHousingType()));
        }
        return String.format("%s에서 가장 가까운 %s", chosen.getRegionName(), label(chosen.getHousingType()));
    }

    /**
     * 무엇을 왜 그렇게 정했는지 서술한다.
     *
     * <p>목표 시점 안에 되는 경우와 못 되는 경우는 카드가 하는 말 자체가 다르므로 문구를 갈라 쓴다.
     * 후자는 조건을 조정해도 시점을 못 지킨다는 뜻이라, 가능하다고 말하면 거짓이 된다.
     */
    private String buildReason(Candidate chosen, long desiredMonths, boolean adjusted) {
        String condition = String.format("%s %s %d~%d평 %s",
                chosen.getRegionName(), label(chosen.getHousingType()),
                chosen.getAreaMin(), chosen.getAreaMax(), label(chosen.getDealType()));

        if (!chosen.withinTarget()) {
            if (chosen.getReachMonths() == null) {
                return String.format(
                        "지금 저축 속도로는 %s 조건에 도달하기 어렵습니다. 저축액을 늘리면 목표가 잡힙니다.", condition);
            }
            return String.format(
                    "%d개월 안에 가능한 조건은 찾지 못했습니다. %s가 가장 가까우며 %d개월이 필요합니다.",
                    desiredMonths, condition, chosen.getReachMonths());
        }
        if (adjusted) {
            return String.format(
                    "입력하신 조건으로는 %d개월을 지키기 어렵습니다. %s로 바꾸면 그 안에 도달할 수 있습니다.",
                    desiredMonths, condition);
        }
        return String.format("%s로 %d개월 안에 도달할 수 있습니다.", condition, desiredMonths);
    }

    private static String label(HousingType housingType) {
        switch (housingType) {
            case APT:
                return "아파트";
            case ROW_HOUSE:
                return "연립다세대";
            case OFFICETEL:
                return "오피스텔";
            case DETACHED:
                return "단독다가구";
            default:
                return housingType.name();
        }
    }

    private static String label(DealType dealType) {
        return dealType == DealType.JEONSE ? "전세" : "월세";
    }

    // ===== 입력 정규화 =====

    private YearMonth resolveTargetDate(GoalRecommendationRequest request) {
        return request.getTargetDate() != null
                ? request.getTargetDate()
                : YearMonth.now().plusMonths(DEFAULT_TARGET_MONTHS);
    }

    /** 배수의 분모다. 0이 되면 나눗셈이 깨지므로 최소 1개월로 본다. */
    private long monthsUntil(YearMonth targetDate) {
        long months = YearMonth.now().until(targetDate, ChronoUnit.MONTHS);
        return Math.max(months, 1);
    }

    /**
     * 추천 한 번을 처리하는 동안 바뀌지 않는 값들과, 그동안 이미 확인한 조합.
     *
     * <p>목표 시점을 절대 바꾸지 않는 카드라서 예산 계산의 재료가 후보와 무관하게 고정된다.
     * 같은 이유로 조합별 조회 결과도 한 번의 추천 안에서는 언제 조회하든 같은 값이라 재사용할 수 있다.
     */
    private static class Search {
        private final long memberId;
        private final AssetNetWorthBreakdown netWorth;
        private final long monthlySaving;
        private final long desiredMonths;
        /** 조합 키 → 평가 결과. 표본이 없어 후보가 되지 못한 조합도 담아 재조회를 막는다. */
        private final Map<String, Optional<Candidate>> evaluated = new HashMap<>();

        private Search(long memberId, AssetNetWorthBreakdown netWorth, long monthlySaving, long desiredMonths) {
            this.memberId = memberId;
            this.netWorth = netWorth;
            this.monthlySaving = monthlySaving;
            this.desiredMonths = desiredMonths;
        }
    }

    /** 평가가 끝난 조합 하나 */
    private static class Candidate {
        private final String regionCode;
        private final String regionName;
        private final HousingType housingType;
        private final DealType dealType;
        private final int areaMin;
        private final int areaMax;
        /** 실거래 보증금 중앙값 — 사용자가 실제로 모아야 하는 금액 */
        private final long deposit;
        /** 실거래 월세 중앙값 — 전세면 0 */
        private final long monthlyRent;
        /** 전세 환산 보증금 — 후보끼리 비교할 때만 쓰는 내부 저울 */
        private final long comparableAmount;
        /** 대표값을 뽑는 데 쓰인 실거래 건수 */
        private final int sampleCount;
        /** 도달까지 걸리는 개월. null이면 탐색 상한 안에 도달 불가 */
        private final Long reachMonths;
        private final long desiredMonths;

        private Candidate(String regionCode, String regionName, HousingType housingType, DealType dealType,
                int areaMin, int areaMax, long deposit, long monthlyRent, long comparableAmount,
                int sampleCount, Long reachMonths, long desiredMonths) {
            this.regionCode = regionCode;
            this.regionName = regionName;
            this.housingType = housingType;
            this.dealType = dealType;
            this.areaMin = areaMin;
            this.areaMax = areaMax;
            this.deposit = deposit;
            this.monthlyRent = monthlyRent;
            this.comparableAmount = comparableAmount;
            this.sampleCount = sampleCount;
            this.reachMonths = reachMonths;
            this.desiredMonths = desiredMonths;
        }

        /** 목표 시점 안에 도달 가능한가 */
        private boolean withinTarget() {
            return reachMonths != null && (double) reachMonths / desiredMonths <= MAX_REACH_RATIO;
        }

        private String getRegionCode() {
            return regionCode;
        }

        private String getRegionName() {
            return regionName;
        }

        private HousingType getHousingType() {
            return housingType;
        }

        private DealType getDealType() {
            return dealType;
        }

        private int getAreaMin() {
            return areaMin;
        }

        private int getAreaMax() {
            return areaMax;
        }

        private long getDeposit() {
            return deposit;
        }

        private long getMonthlyRent() {
            return monthlyRent;
        }

        private int getSampleCount() {
            return sampleCount;
        }

        private long getComparableAmount() {
            return comparableAmount;
        }

        private Long getReachMonths() {
            return reachMonths;
        }
    }
}
