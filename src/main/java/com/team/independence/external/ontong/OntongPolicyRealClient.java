package com.team.independence.external.ontong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.external.ontong.dto.OntongApiResponse;
import com.team.independence.external.ontong.dto.OntongPolicyItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@Profile("!local")
@RequiredArgsConstructor
public class OntongPolicyRealClient implements OntongPolicyClient {

    private static final String HOUSING_CATEGORY = "주거";

    private final OntongProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<OntongPolicyItem> fetchHousingPolicies() {
        List<OntongPolicyItem> all = new ArrayList<>();
        int pageNum = 1;

        do {
            String url = UriComponentsBuilder
                    .fromHttpUrl(properties.getBaseUrl() + "/getPlcy")
                    .queryParam("apiKeyNm", properties.getApiKey())
                    .queryParam("rtnType", "json")
                    .queryParam("lclsfNm", HOUSING_CATEGORY)
                    .queryParam("pageNum", pageNum)
                    .queryParam("pageSize", properties.getPageSize())
                    .build(false)
                    .toUriString();

            String raw = restTemplate.getForObject(url, String.class);
            OntongApiResponse response = parse(raw);

            if (response.getResultCode() != 200 || response.getResult() == null) {
                throw new RuntimeException("[온통청년] API 오류. resultCode=" + response.getResultCode()
                        + " message=" + response.getResultMessage());
            }

            List<OntongPolicyItem> items = response.getResult().getYouthPolicyList();
            if (items == null || items.isEmpty()) {
                break;
            }

            all.addAll(items);
            int totCount = response.getResult().getPagging().getTotCount();
            log.debug("[온통청년] 페이지={} 수집={}/{}", pageNum, all.size(), totCount);

            if (all.size() >= totCount) {
                break;
            }
            pageNum++;

        } while (true);

        log.info("[온통청년] 주거 정책 수집 완료: {}건", all.size());
        return all;
    }

    private OntongApiResponse parse(String raw) {
        try {
            return objectMapper.readValue(raw, OntongApiResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("[온통청년] 응답 파싱 실패: " + e.getMessage(), e);
        }
    }
}
