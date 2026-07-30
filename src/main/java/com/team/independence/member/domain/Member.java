package com.team.independence.member.domain;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
    private IncomeBracket incomeBracket;
    private LocalDateTime incomeBracketUpdatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}