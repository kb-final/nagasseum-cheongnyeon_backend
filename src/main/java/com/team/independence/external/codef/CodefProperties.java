package com.team.independence.external.codef;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * CODEF 연동에 필요한 5개의 환경변수(.env 파일에 존재)
 */
@Getter
@Component
public class CodefProperties {

    @Value("${CODEF_CLIENT_ID}")
    private String clientId;

    @Value("${CODEF_CLIENT_SECRET}")
    private String clientSecret;

    @Value("${CODEF_PUBLIC_KEY}")
    private String publicKey;

    @Value("${CODEF_OAUTH_DOMAIN}")
    private String oauthDomain;

    @Value("${CODEF_API_DOMAIN}")
    private String apiDomain;
}
