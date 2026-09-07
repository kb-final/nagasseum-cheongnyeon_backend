package com.team.independence.external.ontong;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class OntongProperties {

    @Value("${youth.api.base-url}")
    private String baseUrl;

    @Value("${youth.api.key}")
    private String apiKey;

    @Value("${youth.api.page-size:100}")
    private int pageSize;
}
