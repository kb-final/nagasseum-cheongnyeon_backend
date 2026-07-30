package com.team.independence.member.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agreement {

    private Long id;
    private Long memberId;
    private AgreementType agreementType;
    private boolean agreed;
    private String agreementVersion;
    private LocalDateTime agreedAt;

    public enum AgreementType {
        /** 알림 수신 동의 */
        NOTIFICATION,
        /** 비교 기능에 내 자산·목표 데이터 제공 동의 */
        COMPARE_DATA
    }
}