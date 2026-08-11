package com.team.independence.goal.service;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.dto.summary.AssetSummaryResponse;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.goal.dto.AlgorithmType;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.dto.LoanPlans;
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
import java.util.List;
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
 * <h3>보호 필드와 자유 필드</h3>
 * 사용자가 값을 준 조건은 <b>방향과 무관하게 건드리지 않는다</b>(보호). 값을 주지 않은 조건만
 * 알고리즘이 위아래로 움직인다(자유). 예산이 남으면 자유 필드를 올려 더 나은 집을 잡고,
 * 모자라면 내려 잡는다. 지역(시도)은 필수 입력이며 어떤 경우에도 벗어나지 않는다.
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
 * <h3>탐색 순서</h3>
 * <ol>
 *   <li>시군구 선정 — 기준 조합 하나로 시도 내 시군구 시세를 훑어 예산에 맞는 가장 좋은 곳을 고른다</li>
 *   <li>조건 최적화 — 그 시군구 안에서 평수·주거유형·거래유형 조합을 평가해 예산 이하 최선을 고른다</li>
 * </ol>
 * 주거유형과 시군구의 우열은 우리가 정하지 않고 <b>실거래 가격이 정한다</b>. 임의로 정한 선호 순서를
 * 만들지 않기 위해서다.
 *
 * <p><b>알려진 한계</b>: 시군구를 1단계에서 확정하므로, "지역을 더 낮추면 더 좋은 평수·유형이
 * 가능한" 조합은 놓칠 수 있다. 전 조합 탐색은 시군구 수 × 유형 × 평수 × 거래유형이라 요청 한 번에
 * 수백 번의 실거래 집계 쿼리가 필요해 의도적으로 포기한 범위다. 사전집계 테이블이 생기면 넓힐 수 있다.
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

    /**
     * 평수 구간(평). 넓은 쪽이 앞이다.
     *
     * <p>사용자가 평수를 지정하지 않았을 때 이 구간들을 후보로 쓴다. 구간을 끊어 쓰는 이유는,
     * 범위를 넓게 잡으면 성격이 다른 매물이 한 median에 섞여 대표값의 의미가 흐려지기 때문이다.
     */
    private static final int[][] SIZE_BUCKETS = {{20, 25}, {15, 19}, {10, 14}, {4, 9}};

    /** 시군구 시세를 서로 비교할 때 기준으로 삼는 평수 구간 (15~19평) */
    private static final int REFERENCE_SIZE_BUCKET = 1;

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
        long monthlySaving = resolveMonthlySaving(memberId);

        Budget budget = new Budget(netWorth, monthlySaving, desiredMonths);

        // 1단계: 시군구 확정
        String regionCode = selectRegion(request, budget);
        if (regionCode == null) {
            log.warn("추천 가능한 시군구를 찾지 못했습니다. memberId={}, regionCode={}",
                    memberId, request.getRegionCode());
            return Optional.empty();
        }

        // 2단계: 그 시군구 안에서 조건 최적화
        List<Candidate> candidates = evaluateConditions(regionCode, request, budget);
        if (candidates.isEmpty()) {
            log.warn("실거래 표본이 있는 조합이 없습니다. memberId={}, regionCode={}", memberId, regionCode);
            return Optional.empty();
        }

        Candidate chosen = choose(candidates);
        return Optional.of(assemble(memberId, chosen, targetDate, desiredMonths, request));
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
    private String selectRegion(GoalRecommendationRequest request, Budget budget) {
        String requested = request.getRegionCode();
        if (requested.length() == 5) {
            return requested; // 사용자가 지정한 시군구는 보호 대상
        }

        List<String> sigunguCodes = regionMapper.findCodesBySidoPrefix(requested);
        if (sigunguCodes.isEmpty()) {
            return null;
        }

        HousingType housingType = request.getPropertyType() != null
                ? request.getPropertyType() : REFERENCE_HOUSING_TYPE;
        DealType dealType = request.getTradeType() != null
                ? request.getTradeType() : REFERENCE_DEAL_TYPE;
        int[] size = resolveReferenceSize(request);

        List<Candidate> ranked = new ArrayList<>();
        for (String code : sigunguCodes) {
            evaluate(code, housingType, dealType, size[0], size[1], request, budget)
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
     * 확정된 시군구 안에서 평수·주거유형·거래유형의 모든 조합을 평가한다.
     * 사용자가 지정한 항목은 후보가 하나로 고정되므로 그만큼 조합 수가 줄어든다.
     */
    private List<Candidate> evaluateConditions(
            String regionCode, GoalRecommendationRequest request, Budget budget) {

        List<Candidate> candidates = new ArrayList<>();
        for (int[] size : resolveSizeCandidates(request)) {
            for (HousingType housingType : resolveHousingTypeCandidates(request)) {
                for (DealType dealType : resolveDealTypeCandidates(request)) {
                    evaluate(regionCode, housingType, dealType, size[0], size[1], request, budget)
                            .ifPresent(candidates::add);
                }
            }
        }
        return candidates;
    }

    /**
     * 최종 추천을 고른다.
     *
     * <p>목표 시점 안에 도달 가능한 것 중 <b>가장 비싼</b> 것을 고른다. 가격이 곧 주거 수준의 대리
     * 지표라, 예산을 남기지 않고 쓰는 쪽이 사용자에게 더 나은 집이다. 이 규칙 하나로 "여유로우면 상향,
     * 모자라면 하향"이 모두 처리된다.
     *
     * <p>도달 가능한 것이 하나도 없으면 가장 싼 것을 고른다. 그것이 이 조건에서 목표 시점에 가장 가까운
     * 후보이며, 카드는 "여기까지가 가능하다" 대신 "가장 가까운 것이 이것이고 N개월 걸린다"고 말한다.
     */
    private Candidate choose(List<Candidate> candidates) {
        return candidates.stream()
                .filter(Candidate::withinTarget)
                .max(Comparator.comparingLong(Candidate::getComparableAmount))
                .orElseGet(() -> candidates.stream()
                        .min(Comparator.comparingLong(Candidate::getComparableAmount))
                        .orElseThrow(IllegalStateException::new));
    }

    // ===== 후보 평가 =====

    /** 조합 하나의 실거래 median을 조회해 환산보증금과 도달 개월까지 계산한다. */
    private Optional<Candidate> evaluate(
            String regionCode, HousingType housingType, DealType dealType,
            int areaMin, int areaMax, GoalRecommendationRequest request, Budget budget) {

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

        Long reachMonths = goalService.calculateMonthToReach(
                budget.netWorth, budget.monthlySaving, comparableAmount);

        return Optional.of(new Candidate(
                regionCode, median.getRegionName(), housingType, dealType,
                areaMin, areaMax, deposit, monthlyRent, comparableAmount,
                reachMonths, budget.desiredMonths));
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

    // ===== 후보군 산출 (보호 필드는 후보가 하나로 고정된다) =====

    private List<int[]> resolveSizeCandidates(GoalRecommendationRequest request) {
        if (request.getSizeMin() != null && request.getSizeMax() != null) {
            return List.of(new int[]{request.getSizeMin(), request.getSizeMax()});
        }
        return Arrays.asList(SIZE_BUCKETS);
    }

    private List<HousingType> resolveHousingTypeCandidates(GoalRecommendationRequest request) {
        return request.getPropertyType() != null
                ? List.of(request.getPropertyType())
                : Arrays.asList(HousingType.values());
    }

    private List<DealType> resolveDealTypeCandidates(GoalRecommendationRequest request) {
        return request.getTradeType() != null
                ? List.of(request.getTradeType())
                : Arrays.asList(DealType.values());
    }

    /** 시군구 비교에 쓸 평수. 사용자가 지정했으면 그 값으로 비교해야 순위가 사용자 기준이 된다. */
    private int[] resolveReferenceSize(GoalRecommendationRequest request) {
        if (request.getSizeMin() != null && request.getSizeMax() != null) {
            return new int[]{request.getSizeMin(), request.getSizeMax()};
        }
        return SIZE_BUCKETS[REFERENCE_SIZE_BUCKET];
    }

    // ===== 응답 조립 =====

    private GoalRecommendationResponse.RecommendationItem assemble(
            long memberId, Candidate chosen, YearMonth targetDate,
            long desiredMonths, GoalRecommendationRequest request) {

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
                .build();

        return GoalRecommendationResponse.RecommendationItem.builder()
                .type(AlgorithmType.REALISTIC)
                .title(buildTitle(chosen, desiredMonths))
                .reason(buildReason(chosen, desiredMonths, request))
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
    private String buildReason(Candidate chosen, long desiredMonths, GoalRecommendationRequest request) {
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

        if (isUnchanged(chosen, request)) {
            return String.format("입력하신 조건 그대로 %d개월 안에 도달할 수 있습니다. (%s)", desiredMonths, condition);
        }
        return String.format("%d개월을 지키면서 갈 수 있는 가장 나은 조건은 %s입니다.", desiredMonths, condition);
    }

    /** 사용자가 조건을 모두 지정해 알고리즘이 정한 것이 없는 경우 */
    private boolean isUnchanged(Candidate chosen, GoalRecommendationRequest request) {
        return request.getRegionCode().equals(chosen.getRegionCode())
                && request.getPropertyType() != null
                && request.getTradeType() != null
                && request.getSizeMin() != null
                && request.getSizeMax() != null;
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
     * 월 저축액을 자산 요약에서 가져온다. 추천 요청이 받는 값이 아니라 이미 등록돼 있는 값이다.
     *
     * <p>미등록(null)이면 0으로 본다. 0이면 도달 개월이 계산되지 않아 자연히 "목표 시점 안에 불가"로
     * 흘러가므로, 별도로 막지 않고 결과가 스스로 말하게 둔다.
     */
    private long resolveMonthlySaving(long memberId) {
        AssetSummaryResponse summary = assetSummaryService.getSummary(memberId);
        return summary.getMonthlySavings() != null ? summary.getMonthlySavings() : 0L;
    }

    /** 목표 시점이 고정이라 후보마다 다시 계산할 필요가 없는 값들 */
    private static class Budget {
        private final AssetNetWorthBreakdown netWorth;
        private final long monthlySaving;
        private final long desiredMonths;

        private Budget(AssetNetWorthBreakdown netWorth, long monthlySaving, long desiredMonths) {
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
        /** 도달까지 걸리는 개월. null이면 탐색 상한 안에 도달 불가 */
        private final Long reachMonths;
        private final long desiredMonths;

        private Candidate(String regionCode, String regionName, HousingType housingType, DealType dealType,
                int areaMin, int areaMax, long deposit, long monthlyRent, long comparableAmount,
                Long reachMonths, long desiredMonths) {
            this.regionCode = regionCode;
            this.regionName = regionName;
            this.housingType = housingType;
            this.dealType = dealType;
            this.areaMin = areaMin;
            this.areaMax = areaMax;
            this.deposit = deposit;
            this.monthlyRent = monthlyRent;
            this.comparableAmount = comparableAmount;
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

        private long getComparableAmount() {
            return comparableAmount;
        }

        private Long getReachMonths() {
            return reachMonths;
        }
    }
}
