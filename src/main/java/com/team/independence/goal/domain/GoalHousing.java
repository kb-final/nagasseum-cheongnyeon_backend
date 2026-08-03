package com.team.independence.goal.domain;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoalHousing {
    private Long goalId;
    private String regionCode;
    private HousingType housingType;
    private DealType dealType;
    private Integer areaMin;
    private Integer areaMax;
    private Long depositMin;
    private Long depositMax;
    private Long monthlyRentMin;
    private Long monthlyRentMax;
}
