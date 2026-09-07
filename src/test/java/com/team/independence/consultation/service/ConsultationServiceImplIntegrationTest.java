package com.team.independence.consultation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.config.RootConfig;
import com.team.independence.consultation.domain.ConsultationReservation;
import com.team.independence.consultation.dto.ConsultationCounselorReservationResponse;
import com.team.independence.consultation.dto.ConsultationReservationCreateRequest;
import com.team.independence.consultation.dto.ConsultationReservationResponse;
import com.team.independence.consultation.dto.ConsultationUserReservationResponse;
import com.team.independence.consultation.mapper.ConsultationMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 상담 예약 생성/조회를 실제 MySQL에 붙여 확인하는 통합 테스트.
 * 서비스(JSON 직렬화) → 매퍼/XML(컬럼 매핑) → DB까지 한 번에 검증한다.
 *
 * <p>테스트가 만든 행은 끝나고 모두 지운다. 실제 DB가 필요해 CI에서는 돌릴 수 없다.
 * 로컬에서 확인할 때만 아래 @Disabled를 잠시 주석 처리하고 실행한다.
 *
 * <p>사전 조건: member에 행이 하나라도 있어야 한다(user_id FK). 없으면 건너뛴다.
 * counselor_id는 아직 FK가 없어 임의 값을 그대로 쓴다.
 */
@Disabled("실제 DB가 필요해 로컬에서만 실행")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
class ConsultationServiceImplIntegrationTest {

    private static final Long COUNSELOR_ID = 9001L;

    @Autowired
    private ConsultationService consultationService;

    @Autowired
    private ConsultationMapper consultationMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private Long userId;
    private Long generalReservationId;
    private Long diagnosisReservationId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        userId = jdbc.queryForList("SELECT id FROM member ORDER BY id LIMIT 1", Long.class)
                .stream().findFirst().orElse(null);
        assumeTrue(userId != null, "member에 데이터가 없어 건너뜁니다. sql/full_schema.sql과 초기 데이터를 넣어주세요.");
    }

    @AfterEach
    void tearDown() {
        if (generalReservationId != null) {
            jdbc.update("DELETE FROM consultation_reservation WHERE reservation_id = ?", generalReservationId);
        }
        if (diagnosisReservationId != null) {
            jdbc.update("DELETE FROM consultation_reservation WHERE reservation_id = ?", diagnosisReservationId);
        }
    }

    @Test
    @DisplayName("GENERAL 상담을 예약하면 RESERVED 상태로 저장되고 consultInfo만 채워진다")
    void GENERAL_상담_예약_생성() {
        Map<String, Object> consultInfo = new LinkedHashMap<>();
        consultInfo.put("currentAsset", 45_000_000);
        consultInfo.put("monthlySaving", 900_000);

        ConsultationReservationCreateRequest request = createRequest("GENERAL", consultInfo, null);

        ConsultationReservationResponse response = consultationService.createReservation(request);
        generalReservationId = response.getReservationId();

        assertNotNull(generalReservationId);
        assertEquals("RESERVED", response.getStatus());

        ConsultationReservation saved = consultationMapper.findByUserId(userId).stream()
                .filter(r -> r.getReservationId().equals(generalReservationId))
                .findFirst().orElseThrow();

        assertTrue(saved.getConsultInfoJson().contains("45000000"));
        assertNull(saved.getDiagnosisJson());
    }

    @Test
    @DisplayName("GOAL_DIAGNOSIS 상담은 diagnosis 스냅샷도 함께 저장된다")
    void GOAL_DIAGNOSIS_상담_예약_생성() {
        Map<String, Object> consultInfo = new LinkedHashMap<>();
        consultInfo.put("currentAsset", 45_000_000);

        Map<String, Object> diagnosis = new LinkedHashMap<>();
        diagnosis.put("recommendedMonthlySaving", 1_100_000);

        ConsultationReservationCreateRequest request = createRequest("GOAL_DIAGNOSIS", consultInfo, diagnosis);

        ConsultationReservationResponse response = consultationService.createReservation(request);
        diagnosisReservationId = response.getReservationId();

        ConsultationReservation saved = consultationMapper.findByUserId(userId).stream()
                .filter(r -> r.getReservationId().equals(diagnosisReservationId))
                .findFirst().orElseThrow();

        assertNotNull(saved.getDiagnosisJson());
        assertTrue(saved.getDiagnosisJson().contains("1100000"));
    }

    @Test
    @DisplayName("userId/counselorId 기준으로 각각 목록 조회가 된다")
    void 사용자_상담사_목록_조회() {
        ConsultationReservationCreateRequest request = createRequest(
                "GENERAL", Map.of("currentAsset", 10_000_000), null);
        generalReservationId = consultationService.createReservation(request).getReservationId();

        List<ConsultationUserReservationResponse> userList = consultationService.getUserReservations(userId);
        assertTrue(userList.stream().anyMatch(r -> r.getReservationId().equals(generalReservationId)));

        List<ConsultationCounselorReservationResponse> counselorList =
                consultationService.getCounselorReservations(COUNSELOR_ID);
        assertTrue(counselorList.stream().anyMatch(r -> r.getReservationId().equals(generalReservationId)));
    }

    private ConsultationReservationCreateRequest createRequest(
            String consultationType, Map<String, Object> consultInfo, Map<String, Object> diagnosis) {
        String json = "{"
                + "\"userId\":" + userId + ","
                + "\"counselorId\":" + COUNSELOR_ID + ","
                + "\"consultationType\":\"" + consultationType + "\","
                + "\"category\":\"HOUSING\","
                + "\"reservationDate\":\"" + LocalDate.now().plusDays(3) + "\","
                + "\"reservationTime\":\"" + LocalTime.of(14, 0) + "\","
                + "\"requestMessage\":\"현재 조건으로 독립이 가능한지 상담받고 싶습니다.\","
                + "\"consultInfo\":" + writeJson(consultInfo) + ","
                + "\"diagnosis\":" + (diagnosis == null ? "null" : writeJson(diagnosis))
                + "}";
        try {
            return objectMapper.readValue(json, ConsultationReservationCreateRequest.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
