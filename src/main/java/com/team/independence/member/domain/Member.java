package com.team.independence.member.domain;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * member 테이블과 매핑되는 도메인 객체.
 * MyBatis가 조회 결과를 이 객체로 만들어준다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {
    private Long id;
    private String kakaoId;
    private String nickname;
    private LocalDate birthDate;
    private String incomeBracket;
    private LocalDateTime incomeBracketUpdatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
