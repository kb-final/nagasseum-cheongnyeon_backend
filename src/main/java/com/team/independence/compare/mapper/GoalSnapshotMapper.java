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

    /** 코호트 인원 수. k-익명성 판단에 쓴다 */
    int countCohort(CohortCondition condition);

    /**
     * 거래 유형별 인원 수(많은 순).
     *
     * <p>'기타' 병합은 여기서 하지 않는다. 인원이 적은 유형을 합치는 건
     * 개인 역추적을 막기 위한 정책이라 서비스 계층에서 처리한다.
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