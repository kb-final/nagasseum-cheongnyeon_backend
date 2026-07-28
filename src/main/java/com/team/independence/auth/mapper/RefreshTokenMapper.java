package com.team.independence.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface RefreshTokenMapper {

    void save(@Param("memberId") Long memberId,
              @Param("token") String token,
              @Param("expiresAt") LocalDateTime expiresAt);

    void deleteByMemberId(@Param("memberId") Long memberId);
}