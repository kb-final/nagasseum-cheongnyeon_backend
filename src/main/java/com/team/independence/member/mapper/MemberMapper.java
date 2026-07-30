package com.team.independence.member.mapper;

import com.team.independence.member.domain.Member;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

/**
 * DB 접근 인터페이스. 실제 SQL은 짝이 되는 XML에 있다.
 *   → src/main/resources/mybatis/mapper/member/MemberMapper.xml
 *
 * @Mapper 를 붙이면 RootConfig의 MapperScannerConfigurer가 자동으로 빈 등록.
 */
@Mapper
public interface MemberMapper {

    /** PK로 회원 조회 */
    Optional<Member> findById(Long id);

    /** 카카오 고유 ID로 회원 조회 */
    Optional<Member> findByKakaoId(String kakaoId);

    /** 신규 회원 등록 (생성된 PK를 member.id에 반영) */
    void insert(Member member);
}
