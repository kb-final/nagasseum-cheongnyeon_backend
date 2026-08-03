package com.team.independence.goal.mapper;

import com.team.independence.goal.domain.GoalHousing;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GoalHousingMapper {
    void insert(GoalHousing goalHousing);
}
