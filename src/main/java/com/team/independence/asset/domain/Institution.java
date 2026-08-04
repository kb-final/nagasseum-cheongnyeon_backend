package com.team.independence.asset.domain;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Institution {
    private String code;
    private String name;
    private String institutionType;
    private String businessType;
    private String loginType;
    private String productLabel;
    private String logoUrl;
    private int displayOrder;
    private boolean isActive;
    private boolean isConnected;
}
