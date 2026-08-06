package com.team.independence.compare.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * goal_snapshot 테이블 매핑 객체.
 *
 * <p>또래 비교 집계 전용 테이블이다. 매월 1일 배치가 활성 목표 보유 회원의
 * 그 시점 목표 상태를 1행씩 저장한다.
 */
@Getter
@Setter
public class GoalSnapshot {

    private Long id;
    private Long memberId;
    private Long goalId;

    /** 희망 지역 코드 → 인기 지역 TOP 3 */
    private String regionCode;

    /** 집계 기준월 YYYYMM */
    private String snapshotYm;

    /** 그 시점 만 나이(고정) → 코호트 연령 필터 */
    private Integer age;

    /** 그 시점 순자산 → 코호트 자산 필터 기준값 */
    private Long netAssets;

    /** 목표 종류(현재 HOUSING) */
    private String goalType;

    /** 주거 형태 → 향후 분포 확장 대비 */
    private String housingType;

    /** 거래 유형 → 목표 유형 분포 */
    private String dealType;

    /** 목표 금액 → 평균 목표 자산 */
    private Long targetAmount;

    /** 그 시점 달성률(%) → 달성률 분포 */
    private Double achievementRate;

    /** 준비 기간(개월) → 평균 준비 기간 */
    private Integer prepMonths;

    /** 그 시점 월 저축액 → 저축액 구간 */
    private Long monthlySaving;

    /** 월소득 → 소득 구간 코호트 필터 */
    private Long monthlyIncome;

    /** 소득분위 → 통계 분포 표시용 */
    private String incomeBracket;

    /** 직업군 → 직업군 코호트 필터 및 분포 표시용 */
    private String occupationType;

    private LocalDateTime createdAt;
}