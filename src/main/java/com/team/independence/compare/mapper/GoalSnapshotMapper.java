package com.team.independence.compare.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.team.independence.compare.domain.GoalSnapshot;
import com.team.independence.compare.dto.AchievementBucketCount;
import com.team.independence.compare.dto.CohortAverages;
import com.team.independence.compare.dto.CohortCondition;
import com.team.independence.compare.dto.DealTypeCount;
import com.team.independence.compare.dto.IncomeBracketCount;
import com.team.independence.compare.dto.OccupationTypeCount;
import com.team.independence.compare.dto.RegionCount;
import com.team.independence.compare.dto.SavingRangeResult;

/**
 * 또래 비교 집계 DB 접근. 실제 SQL은 짝이 되는 XML에 있다.
 *   → src/main/resources/mybatis/mapper/compare/GoalSnapshotMapper.xml
 */
@Mapper
public interface GoalSnapshotMapper {

    /** 가장 최근 집계월(YYYYMM). 스냅샷이 하나도 없으면 null */
    String findLatestSnapshotYm();

    /** 기준 회원의 스냅샷. 코호트 범위를 계산하는 기준값이 된다 */
    GoalSnapshot findByMemberAndYm(@Param("memberId") Long memberId,
                                   @Param("snapshotYm") String snapshotYm);

    GoalSnapshot findLiveAssetsByMember(@Param("memberId") Long memberId,
                                        @Param("baseDate") LocalDate baseDate);

    /**
     * 기준 회원의 지금 값. 스냅샷이 아직 없을 때만 쓴다.
     *
     * <p>스냅샷은 매월 1일 배치가 만든다. 그래서 이번 달에 목표를 세운 사람은 다음 달까지
     * 자기 기준값이 없어 비교를 아예 볼 수 없었다. 그 한 달을 메우려고, 배치가 넣는 것과
     * 같은 값을 goal · goal_housing · member · asset_summary에서 그 자리에서 계산해 온다.
     *
     * <p>DB에 쓰지 않는다. 조회 API가 데이터를 만들지 않게 하려는 것이다. 대신 이번 달에는
     * 내가 남의 코호트에 잡히지 않는다. 다음 배치가 돌면 자연히 들어간다.
     *
     * <p>자산 연동을 안 했으면 asset_summary 행이 없어 null이 나온다. 순자산이 코호트 범위의
     * 기준이라 0으로 대신 채우면 엉뚱한 또래와 묶인다. 그래서 채우지 않고 null로 두고,
     * 서비스가 '자산 연동이 필요하다'고 안내한다.
     *
     * @param baseDate 나이 계산 기준일(오늘)
     */
    GoalSnapshot findLiveByMember(@Param("memberId") Long memberId,
                                  @Param("baseDate") LocalDate baseDate);

    /** 코호트 인원 수. k-익명성 판단에 쓴다 */
    int countCohort(CohortCondition condition);

    /**
     * 거래 유형별 인원 수(많은 순).
     *
     * <p>서비스는 이 순서를 그대로 순위로 쓴다.
     */
    List<DealTypeCount> countByDealType(CohortCondition condition);

    /** 평균 목표금액 · 준비기간 · 달성률. 세 값을 한 번에 가져온다 */
    CohortAverages findAverages(CohortCondition condition);

    /** 희망 지역별 인원 수 상위 3개(많은 순). 지역명은 region 테이블에서 가져온다 */
    List<RegionCount> countTopRegions(CohortCondition condition);

    /**
     * 달성률 10% 구간별 인원 수. 마지막 구간(80~100%)만 폭이 20이다.
     *
     * <p>인원이 0인 구간은 결과에 없다. 빈 구간 채우기는 서비스 계층에서 한다.
     */
    List<AchievementBucketCount> countByAchievementBucket(CohortCondition condition);

    /** 월 저축액의 가운데 50%(25~75 백분위) 구간 */
    SavingRangeResult findSavingRange(CohortCondition condition);

    /** 코호트 소득분위 분포. income_bracket IS NOT NULL인 행만 집계 */
    List<IncomeBracketCount> countByIncomeBracket(CohortCondition condition);

    /** 코호트 직업군 분포. occupation_type IS NOT NULL인 행만 집계 */
    List<OccupationTypeCount> countByOccupationType(CohortCondition condition);

    /**
     * 회원에게 진행 중인 목표가 있는지.
     *
     * <p>스냅샷이 없을 때 "목표를 안 세운 것"과 "목표는 있으나 아직 집계 전"을 가르는 데 쓴다.
     * goal 테이블을 읽지만 EXISTS 한 번이고, 아래 배치(insertSnapshots)도 이미 goal을 읽고 있어
     * 의존 범위가 늘지 않는다.
     */
    boolean existsActiveGoal(@Param("memberId") Long memberId);

    /**
     * 해당 월의 스냅샷을 만든다(배치 전용).
     *
     * <p>이미 있는 회원은 최신 값으로 덮어쓴다. 같은 달에 여러 번 돌려도 안전하다.
     *
     * @param snapshotYm 집계 기준월 YYYYMM
     * @param baseDate   나이 계산 기준일(해당 월 1일)
     * @return 삽입 또는 갱신된 건수
     */
    int insertSnapshots(@Param("snapshotYm") String snapshotYm,
                        @Param("baseDate") LocalDate baseDate);
}