package com.team.independence.consultation.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ConsultationMessageCreateRequest {

    @NotBlank
    @Pattern(regexp = "USER|COUNSELOR")
    private String senderType;

    /** trim 후 빈 문자열이면 @NotBlank가 걸러낸다. */
    @NotBlank
    private String content;
}
