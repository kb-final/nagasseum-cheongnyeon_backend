package com.team.independence.policy.mapper;

import com.team.independence.policy.domain.Policy;
import com.team.independence.policy.dto.PolicyListRequest;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PolicyMapper {

    void upsertBatch(@Param("list") List<Policy> policies);

    void deactivateExcept(@Param("sourceApi") String sourceApi,
                          @Param("ids") List<String> policyApiIds);

    List<Policy> findList(@Param("req") PolicyListRequest req);

    long countList(@Param("req") PolicyListRequest req);

    Policy findById(@Param("id") Long id);
}
