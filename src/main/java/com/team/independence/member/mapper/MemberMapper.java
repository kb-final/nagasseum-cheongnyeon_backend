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

    /**
     * 월소득만 필요한 곳을 위한 경량 조회. id·monthly_income만 채워진 Member를 반환한다.
     *
     * <p>DSR 한도 계산은 monthly_income 하나만 쓰는데 {@code getMember()}를 부르면
     * 회원 전체 컬럼에 약관 동의 목록까지 조인 없이 한 번 더 조회된다.
     * 회원 존재 여부와 소득 null을 구분해야 해서 스칼라가 아니라 Member로 받는다.
     */
    Optional<Member> findIncomeById(Long id);

    /** 카카오 고유 ID로 회원 조회 */
    Optional<Member> findByKakaoId(String kakaoId);

    /** 신규 회원 등록 (생성된 PK를 member.id에 반영) */
    void insert(Member member);

    /** 닉네임·소득분위 수정 (null 필드는 UPDATE 제외) */
    void update(Member member);
}
