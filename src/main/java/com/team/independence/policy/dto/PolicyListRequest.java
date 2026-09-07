package com.team.independence.policy.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PolicyListRequest {

    private String keyword;
    private Integer age;
    private String lclsfNm;
    private int page = 1;
    private int size = 20;

    public int getOffset() {
        return (page - 1) * size;
    }
}
