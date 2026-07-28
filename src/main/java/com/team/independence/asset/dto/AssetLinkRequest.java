package com.team.independence.asset.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@ToString(exclude = "password")
public class AssetLinkRequest {
    private String organization;
    private String businessType;
    private String countryCode;
    private String loginType;
    private String clientType;
    private String id;
    private String password;  // 서버에서 RSA 암호화 (평문으로 수신)
    private String birthDate;  // 형식: YYMMDD
    private String loginTypeLevel;
    private String clientTypeLevel;
    private String cardNo;
    private String cardPassword;
}
