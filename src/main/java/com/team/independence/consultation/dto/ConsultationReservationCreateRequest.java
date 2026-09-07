package com.team.independence.consultation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상담 예약 생성 요청.
 * consultInfo/diagnosis는 내부 구조를 검증하지 않고 그대로 JSON 스냅샷으로 저장한다(해커톤 MVP).
 */
@Getter
@NoArgsConstructor
public class ConsultationReservationCreateRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Long counselorId;

    @NotBlank
    @Pattern(regexp = "GENERAL|GOAL_DIAGNOSIS")
    private String consultationType;

    @NotBlank
    @Pattern(regexp = "GOAL|SAVING|HOUSING|LOAN|ASSET")
    private String category;

    @NotNull
    private LocalDate reservationDate;

    @NotNull
    private LocalTime reservationTime;

    /** 예약 과정에서 작성한 상담 희망 내용. 선택 입력. */
    private String requestMessage;

    @NotNull
    private Object consultInfo;

    /** GOAL_DIAGNOSIS 상담이 아니면 null. */
    private Object diagnosis;
}
