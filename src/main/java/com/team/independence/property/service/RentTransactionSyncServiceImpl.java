package com.team.independence.property.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.domain.RentSyncLog;
import com.team.independence.property.domain.RentTransaction;
import com.team.independence.property.dto.RentItemDto;
import com.team.independence.property.mapper.RegionMapper;
import com.team.independence.property.mapper.RentSyncLogMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final RegionMapper regionMapper;
    private final RentSyncLogMapper rentSyncLogMapper;
    private final RentTransactionUnitSyncService rentTransactionUnitSyncService;

    @Value("${molit.api.key}")
    private String apiKey;

    private final XmlMapper xmlMapper = new XmlMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<HousingType, String> API_URLS = Map.of(
        HousingType.APT, "https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent",
        HousingType.OFFICETEL, "https://apis.data.go.kr/1613000/RTMSDataSvcOffiRent/getRTMSDataSvcOffiRent",
        HousingType.ROW_HOUSE, "https://apis.data.go.kr/1613000/RTMSDataSvcRHRent/getRTMSDataSvcRHRent",
        HousingType.DETACHED, "https://apis.data.go.kr/1613000/RTMSDataSvcSHRent/getRTMSDataSvcSHRent"
    );

    private static final DateTimeFormatter DEAL_YM = DateTimeFormatter.ofPattern("yyyyMM");

    /** 수집 대상 기간(개월). 이보다 오래된 구간은 순회하지 않을 뿐, 이미 쌓인 데이터는 그대로 둔다. */
    private static final int TARGET_MONTHS = 36;

    /**
     * 이력과 무관하게 항상 재수집하는 최근 기간(개월).
     * 전월세 신고 기한이 계약일로부터 30일이라 지난달·전전달 데이터가 오늘도 계속 늘어난다.
     */
    private static final int ALWAYS_RESYNC_MONTHS = 3;

    private static final int PAGE_SIZE = 1000;

    /** 페이지 순회 상한. 초과하면 잘라내지 않고 실패시킨다(잘림 = 삭제이므로). */
    private static final int MAX_PAGES = 20;

    private static final String SUCCESS_RESULT_CODE = "000";

    @Override
    public void syncAll() {
        List<String> regionCodes = regionMapper.findAllCodes();
        Map<String, RentSyncLog> history = loadHistory();

        YearMonth baseMonth = YearMonth.now();
        int skipped = 0;
        int succeeded = 0;
        int failed = 0;

        for (int monthsAgo = 0; monthsAgo < TARGET_MONTHS; monthsAgo++) {
            String dealYm = baseMonth.minusMonths(monthsAgo).format(DEAL_YM);
            boolean alwaysResync = monthsAgo < ALWAYS_RESYNC_MONTHS;

            for (String regionCode : regionCodes) {
                for (HousingType housingType : HousingType.values()) {

                    if (!alwaysResync && isCollected(history, regionCode, dealYm, housingType)) {
                        skipped++;
                        continue;
                    }

                    try {
                        collectAndSync(regionCode, dealYm, housingType);
                        succeeded++;
                    } catch (Exception e) {
                        // 한 유닛이 실패해도 나머지는 계속 진행한다.
                        // 실패 이력은 롤백된 트랜잭션 바깥인 여기서 남겨야 살아남는다.
                        failed++;
                        log.error("[국토부] 유닛 동기화 실패 - regionCode={}, dealYm={}, housingType={}, error={}",
                            regionCode, dealYm, housingType, e.getMessage());
                        markFailed(regionCode, dealYm, housingType);
                    }
                }
            }
        }

        log.info("[국토부] 전체 동기화 종료 - 성공={}, 실패={}, 건너뜀={}", succeeded, failed, skipped);
    }

    @Override
    public void collectAndSync(String regionCode, String dealYm, HousingType housingType) {
        String baseUrl = API_URLS.get(housingType);
        if (baseUrl == null) {
            throw new IllegalArgumentException("지원하지 않는 주택유형: " + housingType);
        }

        // HTTP 호출과 파싱은 트랜잭션 밖에서 모두 끝내고, 결과 리스트만 넘긴다.
        List<RentTransaction> items = fetchAndParse(baseUrl, housingType, regionCode, dealYm);

        rentTransactionUnitSyncService.sync(regionCode, dealYm, housingType, items);
    }

    // ===== 수집 =====

    /**
     * 유닛 하나의 전체 페이지를 수집해 RentTransaction 리스트로 변환한다.
     *
     * <p>페이지를 끝까지 돌지 않으면 뒷부분이 잘리는데, 재적재 구조에서 잘림은 곧 삭제다.
     * 그래서 상한을 넘기면 부분 결과를 반환하지 않고 예외를 던져 유닛 자체를 실패시킨다.
     */
    private List<RentTransaction> fetchAndParse(String baseUrl, HousingType housingType,
                                                String regionCode, String dealYm) {
        List<RentTransaction> result = new ArrayList<>();

        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            RentApiResponse response = fetchPage(baseUrl, regionCode, dealYm, pageNo);

            for (RentItemDto dto : itemsOf(response)) {
                result.add(toRentTransaction(dto, housingType, regionCode));
            }

            int totalCount = totalCountOf(response);
            if (result.size() >= totalCount || itemsOf(response).isEmpty()) {
                return result;
            }
        }

        throw new IllegalStateException(String.format(
            "페이지 상한(%d) 초과 - regionCode=%s, dealYm=%s, housingType=%s",
            MAX_PAGES, regionCode, dealYm, housingType));
    }

    /**
     * 한 페이지를 호출하고 응답 헤더를 검증한다.
     *
     * <p>이 API는 인증키 오류·트래픽 초과에도 HTTP 200을 준다. 헤더를 확인하지 않으면
     * 오류 응답이 "0건"으로 둔갑해, 재적재가 멀쩡한 구간을 지우고 성공으로 기록한다.
     */
    private RentApiResponse fetchPage(String baseUrl, String regionCode, String dealYm, int pageNo) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .queryParam("serviceKey", apiKey)
            .queryParam("LAWD_CD", regionCode)
            .queryParam("DEAL_YMD", dealYm)
            .queryParam("numOfRows", PAGE_SIZE)
            .queryParam("pageNo", pageNo)
            .build(true)
            .toUriString();

        String xml = restTemplate.getForObject(url, String.class);

        RentApiResponse response;
        try {
            response = xmlMapper.readValue(xml, RentApiResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("국토부 응답 파싱 실패: " + e.getMessage(), e);
        }

        // 게이트웨이 단계 오류는 루트 엘리먼트부터 달라서(<OpenAPI_ServiceResponse>)
        // 파싱하면 전 필드가 null이 된다. header가 없는 것도 실패로 본다.
        if (response.header == null || !SUCCESS_RESULT_CODE.equals(response.header.resultCode)) {
            throw new IllegalStateException(String.format(
                "국토부 API 오류 - resultCode=%s, resultMsg=%s",
                response.header == null ? "none" : response.header.resultCode,
                response.header == null ? "none" : response.header.resultMsg));
        }

        return response;
    }

    private List<RentItemDto> itemsOf(RentApiResponse response) {
        if (response.body == null || response.body.items == null || response.body.items.item == null) {
            return List.of();
        }
        return response.body.items.item;
    }

    private int totalCountOf(RentApiResponse response) {
        if (response.body == null || response.body.totalCount == null) {
            return 0;
        }
        return response.body.totalCount;
    }

    // ===== 이력 =====

    private Map<String, RentSyncLog> loadHistory() {
        Map<String, RentSyncLog> history = new HashMap<>();
        for (RentSyncLog syncLog : rentSyncLogMapper.findAll()) {
            history.put(historyKey(syncLog.getRegionCode(), syncLog.getDealYm(), syncLog.getHousingType()),
                syncLog);
        }
        return history;
    }

    private boolean isCollected(Map<String, RentSyncLog> history,
                                String regionCode, String dealYm, HousingType housingType) {
        RentSyncLog syncLog = history.get(historyKey(regionCode, dealYm, housingType));
        return syncLog != null && Boolean.TRUE.equals(syncLog.getIsSuccess());
    }

    private String historyKey(String regionCode, String dealYm, HousingType housingType) {
        return regionCode + "|" + dealYm + "|" + housingType.name();
    }

    /** 실패 기록마저 실패해도 전체 순회는 멈추지 않는다(다음 실행에서 이력 없음 → 재시도 대상). */
    private void markFailed(String regionCode, String dealYm, HousingType housingType) {
        try {
            rentSyncLogMapper.upsert(RentSyncLog.builder()
                .regionCode(regionCode)
                .dealYm(dealYm)
                .housingType(housingType)
                .isSuccess(false)
                .insertedCnt(0)
                .build());
        } catch (Exception e) {
            log.error("[국토부] 실패 이력 기록 실패 - regionCode={}, dealYm={}, housingType={}, error={}",
                regionCode, dealYm, housingType, e.getMessage());
        }
    }

    // ===== 변환 =====

    /**
     * RentItemDto → RentTransaction 변환
     * API 응답 필드명/형식이 DB 컬럼과 달라서 여기서 정제한다.
     */
    private RentTransaction toRentTransaction(RentItemDto dto, HousingType housingType, String regionCode) {
        // "24,000" 형태의 금액 → 콤마 제거 후 원 단위로 변환 (국토부는 만원 단위로 반환)
        long deposit = parseLong(parseAmount(dto.getDeposit())) * 10_000;
        long monthlyRent = parseLong(parseAmount(dto.getMonthlyRent())) * 10_000;

        // dealYear("2015") + dealMonth("12") → dealYm("201512")
        String dealYm = dto.getDealYear().trim()
            + String.format("%02d", Integer.parseInt(dto.getDealMonth().trim()));

        // 4종마다 면적 필드명이 다름 (excluUseAr / totalFloorAr) → DTO 헬퍼로 통일
        String area = dto.getAreaValue();

        return RentTransaction.builder()
            .regionCode(regionCode)
            .housingType(housingType)
            .dongName(trim(dto.getUmdNm()))
            .jibun(trim(dto.getJibun()))
            .complexName(dto.getComplexName())          // 4종 단지명 헬퍼로 통일
            .area(area != null ? new BigDecimal(area) : BigDecimal.ZERO)
            .dealType(DealType.from(monthlyRent))
            .deposit(deposit)
            .monthlyRent(monthlyRent)
            .floor(parseInteger(dto.getFloor()))
            .buildYear(parseInteger(dto.getBuildYear()))
            .dealYm(dealYm)
            .dealDay(trim(dto.getDealDay()))
            .contractType(trim(dto.getContractType()))
            .contractTerm(trim(dto.getContractTerm()))
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

    /** 객체 → JSON 문자열 (DB json 컬럼 저장용) */
    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }

    // ===== XML 파싱용 내부 클래스 =====
    // 국토부 응답을 담는 래퍼. 이 파일에서만 쓰이므로 내부 클래스로 선언.
    @JacksonXmlRootElement(localName = "response")
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RentApiResponse {
        public Header header;
        public Body body;

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Header {
            public String resultCode;
            public String resultMsg;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Body {
            public Items items;
            public Integer totalCount;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Items {
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "item")
            public List<RentItemDto> item;
        }
    }
}
