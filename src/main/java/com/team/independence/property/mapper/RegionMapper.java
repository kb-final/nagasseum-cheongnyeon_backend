package com.team.independence.property.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RegionMapper {
    List<String> findAllCodes();
}
