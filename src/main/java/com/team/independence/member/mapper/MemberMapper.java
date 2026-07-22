package com.team.independence.member.mapper;

import com.team.independence.member.domain.Member;
import org.apache.ibatis.annotations.Mapper;

/**
 * DB 접근 인터페이스. 실제 SQL은 짝이 되는 XML에 있다.
 *   → src/main/resources/mybatis/mapper/member/MemberMapper.xml
 *
 * @Mapper 를 붙이면 RootConfig의 MapperScannerConfigurer가 자동으로 빈 등록.
 */
@Mapper
public interface MemberMapper {

    /** PK로 회원 조회 */
    Member findById(Long id);
}
