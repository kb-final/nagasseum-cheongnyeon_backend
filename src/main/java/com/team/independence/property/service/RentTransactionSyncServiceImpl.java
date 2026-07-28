package com.team.independence.property.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.team.independence.property.domain.RentTransaction;
import com.team.independence.property.dto.RentItemDto;
import com.team.independence.property.mapper.RegionMapper;
import com.team.independence.property.mapper.RentTransactionMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentTransactionSyncServiceImpl implements RentTransactionSyncService {

    private final RestTemplate restTemplate;
    private final RentTransactionMapper rentTransactionMapper;
    private final RegionMapper regionMapper;

    @Value("${molit.api.key}")
    private String apiKey;

    private final XmlMapper xmlMapper = new XmlMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, String> API_URLS = Map.of(
        "APT", "https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent",
        "OFFICETEL", "https://apis.data.go.kr/1613000/RTMSDataSvcOffiRent/getRTMSDataSvcOffiRent",
        "ROW_HOUSE", "https://apis.data.go.kr/1613000/RTMSDataSvcRHRent/getRTMSDataSvcRHRent",
        "DETACHED", "https://apis.data.go.kr/1613000/RTMSDataSvcSHRent/getRTMSDataSvcSHRent"
    );

    @Override
    public void syncAll() {

    }

    @Override
    public void sync(String regionCode, String dealYm) {
        List<RentTransaction> result = new ArrayList<>();

        for (Map.Entry<String, String> entry : API_URLS.entrySet()) {
            String housingType = entry.getKey();
            String url = entry.getValue();

            try {
                result.addAll(fetchAndParse(url, housingType, regionCode, dealYm));
            } catch (Exception e) {
                // 한 종류 실패해도 나머지 3종은 계속 진행
                log.error("[국토부] API 실패 - housingType={}, regionCode={}, error={}",
                    housingType, regionCode, e.getMessage());
            }
        }

        if (!result.isEmpty()) {
            rentTransactionMapper.insertBatch(result);
            log.info("[국토부] 저장 완료 - regionCode={}, dealYm={}, count={}",
                regionCode, dealYm, result.size());
        }
    }

    /**
     * API URL 조립 → XML 응답 수신 → RentTransaction 리스트로 변환
     */
    private List<RentTransaction> fetchAndParse(String baseUrl, String housingType,
                                                String regionCode, String dealYm) throws Exception {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .queryParam("serviceKey", apiKey)
            .queryParam("LAWD_CD", regionCode)
            .queryParam("DEAL_YMD", dealYm)
            .queryParam("numOfRows", 1000)
            .queryParam("pageNo", 1)
            .build(true)
            .toUriString();

        String xml = restTemplate.getForObject(url, String.class);

        List<RentTransaction> result = new ArrayList<>();
        for (RentItemDto dto : parseXml(xml)) {
            result.add(toRentTransaction(dto, housingType, regionCode));
        }
        return result;
    }

    /**
     * XML 문자열 → RentItemDto 리스트
     * 국토부 응답 구조: <response><body><items><item>...</item></items></body></response>
     */
    private List<RentItemDto> parseXml(String xml) throws Exception {
        RentApiResponse response = xmlMapper.readValue(xml, RentApiResponse.class);
        if (response.body == null
            || response.body.items == null
            || response.body.items.item == null) {
            return List.of();
        }
        return response.body.items.item;
    }

    /**
     * RentItemDto → RentTransaction 변환
     * API 응답 필드명/형식이 DB 컬럼과 달라서 여기서 정제한다.
     */
    private RentTransaction toRentTransaction(RentItemDto dto, String housingType, String regionCode) {
        // "24,000" 형태의 금액 → 콤마 제거 후 숫자 문자열로
        String deposit = parseAmount(dto.getDeposit());
        String monthlyRent = parseAmount(dto.getMonthlyRent());

        // dealYear("2015") + dealMonth("12") → dealYm("201512")
        String dealYm = dto.getDealYear().trim()
            + String.format("%02d", Integer.parseInt(dto.getDealMonth().trim()));

        // 4종마다 면적 필드명이 다름 (excluUseAr / totalFloorAr) → DTO 헬퍼로 통일
        String area = dto.getAreaValue();

        // 중복 방지용 SHA-256 해시 (같은 거래가 재수집되어도 INSERT IGNORE로 무시됨)
        String dedupKey = generateDedupKey(
            regionCode, housingType,
            nullToEmpty(dto.getUmdNm()), nullToEmpty(dto.getJibun()),
            dealYm, nullToEmpty(dto.getDealDay()),
            deposit, monthlyRent, nullToEmpty(area),
            nullToEmpty(dto.getFloor()));

        return RentTransaction.builder()
            .regionCode(regionCode)
            .housingType(housingType)
            .dongName(trim(dto.getUmdNm()))
            .jibun(trim(dto.getJibun()))
            .complexName(dto.getComplexName())          // 4종 단지명 헬퍼로 통일
            .area(area != null ? new BigDecimal(area) : BigDecimal.ZERO)
            .dealType("0".equals(monthlyRent) ? "전세" : "월세") // monthlyRent=0이면 전세
            .deposit(parseLong(deposit))
            .monthlyRent(parseLong(monthlyRent))
            .floor(parseInteger(dto.getFloor()))
            .buildYear(parseInteger(dto.getBuildYear()))
            .dealYm(dealYm)
            .dealDay(trim(dto.getDealDay()))
            .contractType(trim(dto.getContractType()))
            .contractTerm(trim(dto.getContractTerm()))
            .dedupKey(dedupKey)
            .json(toJson(dto))                          // DTO를 JSON으로 직렬화해 원본 보존
            .build();
    }

    // ===== 유틸 메서드 =====

    /** "24,000" → "24000" */
    private String parseAmount(String value) {
        if (value == null || value.isBlank()) return "0";
        return value.trim().replace(",", "");
    }

    private Long parseLong(String value) {
        try { return Long.parseLong(value); } catch (Exception e) { return 0L; }
    }

    /** 층, 건축년도처럼 없을 수 있는 숫자 → null 허용 */
    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return null; }
    }

    /** 공백만 있으면 null 반환 */
    private String trim(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    /** dedupKey 생성용 - null을 빈 문자열로 (해시 일관성 유지) */
    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /** 객체 → JSON 문자열 (DB json 컬럼 저장용) */
    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }

    /**
     * 주요 필드 조합 → SHA-256 해시 → 64자리 hex 문자열
     * 같은 거래는 항상 같은 dedupKey가 나와야 하므로 필드 선택이 중요
     */
    private String generateDedupKey(String... parts) {
        try {
            String raw = String.join("|", parts);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("dedup_key 생성 실패", e);
        }
    }

    // ===== XML 파싱용 내부 클래스 =====
    // 국토부 응답을 담는 래퍼. 이 파일에서만 쓰이므로 내부 클래스로 선언.
    @JacksonXmlRootElement(localName = "response")
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RentApiResponse {
        public Body body;

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Body {
            public Items items;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Items {
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "item")
            public List<RentItemDto> item;
        }
    }
}
