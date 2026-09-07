package com.team.independence.policy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.external.ontong.OntongPolicyClient;
import com.team.independence.external.ontong.dto.OntongPolicyItem;
import com.team.independence.external.slack.SlackNotifier;
import com.team.independence.policy.domain.Policy;
import com.team.independence.policy.domain.PolicyApplyPeriodType;
import com.team.independence.policy.mapper.PolicyMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicySyncServiceImpl implements PolicySyncService {

    private static final String SOURCE_API = "YOUTH";
    private static final String APPLICABLE_GOAL_TYPES = "HOUSING";
    private static final int BATCH_SIZE = 100;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OntongPolicyClient ontongPolicyClient;
    private final PolicyMapper policyMapper;
    private final SlackNotifier slackNotifier;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void syncAll() {
        log.info("[정책배치] 온통청년 주거 정책 동기화 시작");
        try {
            List<OntongPolicyItem> items = ontongPolicyClient.fetchHousingPolicies();

            List<Policy> policies = items.stream()
                    .map(this::toPolicy)
                    .collect(Collectors.toList());

            // 100건씩 배치 upsert
            for (int i = 0; i < policies.size(); i += BATCH_SIZE) {
                List<Policy> batch = policies.subList(i, Math.min(i + BATCH_SIZE, policies.size()));
                policyMapper.upsertBatch(batch);
            }

            // 이번 배치에 없는 정책 비활성화
            List<String> currentIds = items.stream()
                    .map(OntongPolicyItem::getPlcyNo)
                    .collect(Collectors.toList());
            if (!currentIds.isEmpty()) {
                policyMapper.deactivateExcept(SOURCE_API, currentIds);
            }

            log.info("[정책배치] 온통청년 주거 정책 동기화 완료: {}건 upsert", policies.size());

        } catch (Exception e) {
            log.error("[정책배치] 동기화 실패", e);
            slackNotifier.sendBatchFailureSummary("policy-sync", e.getMessage());
            throw new RuntimeException("[정책배치] 동기화 실패: " + e.getMessage(), e);
        }
    }

    private Policy toPolicy(OntongPolicyItem item) {
        String applyUrl = resolveUrl(item.getAplyUrlAddr(), item.getRefUrlAddr1());
        LocalDate startDate = parseDateFromAplyYmd(item.getAplyYmd(), true);
        LocalDate endDate   = parseDateFromAplyYmd(item.getAplyYmd(), false);
        if (startDate == null) startDate = parseDate(item.getBizPrdBgngYmd());
        if (endDate   == null) endDate   = parseDate(item.getBizPrdEndYmd());

        Integer minAge = parseAge(item.getSprtTrgtAgeLmtYn(), item.getSprtTrgtMinAge());
        Integer maxAge = parseAge(item.getSprtTrgtAgeLmtYn(), item.getSprtTrgtMaxAge());

        return Policy.builder()
                .sourceApi(SOURCE_API)
                .policyApiId(item.getPlcyNo())
                .largeCategory(item.getLclsfNm())
                .mediumCategory(item.getMclsfNm())
                .providingMethod(item.getPlcyPvsnMthdCd())
                .policyName(item.getPlcyNm())
                .policySummary(item.getPlcyExplnCn())
                .targetDescription(item.getAddAplyQlfcCndCn())
                .benefitDescription(item.getPlcySprtCn())
                .providingOrgName(item.getSprvsnInstCdNm())
                .applyPeriodType(PolicyApplyPeriodType.fromCode(item.getAplyPrdSeCd()).name())
                .applyStartDate(startDate)
                .applyEndDate(endDate)
                .applyUrl(applyUrl)
                .minAge(minAge)
                .maxAge(maxAge)
                .extra(toJson(item))
                .isActive(true)
                .applicableGoalTypes(APPLICABLE_GOAL_TYPES)
                .build();
    }

    /** aplyYmd 형식: "YYYYMMDD ~ YYYYMMDD" */
    private LocalDate parseDateFromAplyYmd(String aplyYmd, boolean isStart) {
        if (aplyYmd == null || aplyYmd.trim().isEmpty()) return null;
        String[] parts = aplyYmd.split("~");
        if (parts.length < 2) return null;
        String raw = isStart ? parts[0].trim() : parts[1].trim();
        return parseDate(raw);
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(raw.trim(), DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveUrl(String primary, String fallback) {
        if (primary != null && !primary.trim().isEmpty()) return primary.trim();
        if (fallback != null && !fallback.trim().isEmpty()) return fallback.trim();
        return null;
    }

    private Integer parseAge(String ageLmtYn, String ageValue) {
        if (!"Y".equals(ageLmtYn)) return null;
        if (ageValue == null || ageValue.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(ageValue.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toJson(OntongPolicyItem item) {
        try {
            return objectMapper.writeValueAsString(item);
        } catch (Exception e) {
            return null;
        }
    }
}
