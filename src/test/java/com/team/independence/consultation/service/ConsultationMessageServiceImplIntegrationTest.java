package com.team.independence.consultation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.config.RootConfig;
import com.team.independence.consultation.domain.ConsultationCategory;
import com.team.independence.consultation.domain.ConsultationReservation;
import com.team.independence.consultation.domain.ConsultationStatus;
import com.team.independence.consultation.domain.ConsultationType;
import com.team.independence.consultation.dto.ConsultationMessageCreateRequest;
import com.team.independence.consultation.dto.ConsultationMessageResponse;
import com.team.independence.consultation.mapper.ConsultationMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
 * 상담 채팅 메시지 저장/조회 + RESERVED→IN_PROGRESS 상태 전이를 실제 MySQL에 붙여 확인하는 통합 테스트.
 *
 * <p>테스트가 만든 행은 끝나고 모두 지운다. 실제 DB가 필요해 CI에서는 돌릴 수 없다.
 * 로컬에서 확인할 때만 아래 @Disabled를 잠시 주석 처리하고 실행한다.
 *
 * <p>사전 조건: member에 행이 하나라도 있어야 한다(consultation_reservation.user_id FK).
 */
@Disabled("실제 DB가 필요해 로컬에서만 실행")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
class ConsultationMessageServiceImplIntegrationTest {

    private static final Long COUNSELOR_ID = 9001L;

    @Autowired
    private ConsultationService consultationService;

    @Autowired
    private ConsultationMessageService consultationMessageService;

    @Autowired
    private ConsultationMapper consultationMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private Long userId;
    private Long reservationId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        userId = jdbc.queryForList("SELECT id FROM member ORDER BY id LIMIT 1", Long.class)
                .stream().findFirst().orElse(null);
        assumeTrue(userId != null, "member에 데이터가 없어 건너뜁니다. sql/full_schema.sql과 초기 데이터를 넣어주세요.");

        reservationId = insertReservation(ConsultationStatus.RESERVED);
    }

    @AfterEach
    void tearDown() {
        if (reservationId != null) {
            jdbc.update("DELETE FROM consultation_message WHERE reservation_id = ?", reservationId);
            jdbc.update("DELETE FROM consultation_reservation WHERE reservation_id = ?", reservationId);
        }
    }

    @Test
    @DisplayName("RESERVED 상담에서 첫 메시지를 보내면 저장되고 상태가 IN_PROGRESS로 바뀐다")
    void RESERVED_상담_첫_메시지() {
        ConsultationMessageResponse response =
                consultationMessageService.createMessage(reservationId, request("USER", "안녕하세요."));

        assertNotNull(response.getMessageId());
        assertNotNull(response.getCreatedAt());
        assertEquals("USER", response.getSenderType());

        ConsultationReservation reservation = consultationMapper.findById(reservationId);
        assertEquals(ConsultationStatus.IN_PROGRESS, reservation.getStatus());
    }

    @Test
    @DisplayName("IN_PROGRESS 상담에서 메시지를 보내면 저장만 되고 상태는 그대로다")
    void IN_PROGRESS_상담_메시지() {
        consultationMessageService.createMessage(reservationId, request("USER", "첫 메시지"));
        consultationMessageService.createMessage(reservationId, request("COUNSELOR", "두번째 메시지"));

        ConsultationReservation reservation = consultationMapper.findById(reservationId);
        assertEquals(ConsultationStatus.IN_PROGRESS, reservation.getStatus());

        List<ConsultationMessageResponse> messages = consultationMessageService.getMessages(reservationId);
        assertEquals(2, messages.size());
        assertEquals("첫 메시지", messages.get(0).getContent());
        assertEquals("두번째 메시지", messages.get(1).getContent());
    }

    @Test
    @DisplayName("COMPLETED 상담에는 메시지를 보낼 수 없다")
    void COMPLETED_상담_메시지_거부() {
        jdbc.update("UPDATE consultation_reservation SET status = 'COMPLETED' WHERE reservation_id = ?", reservationId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> consultationMessageService.createMessage(reservationId, request("USER", "안녕하세요.")));
        assertEquals(ErrorCode.CONSULTATION_ALREADY_COMPLETED, ex.getErrorCode());
    }

    @Test
    @DisplayName("존재하지 않는 상담이면 CONSULTATION_NOT_FOUND")
    void 존재하지_않는_상담() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> consultationMessageService.createMessage(999_999_999L, request("USER", "안녕하세요.")));
        assertEquals(ErrorCode.CONSULTATION_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("메시지가 없는 상담을 조회하면 빈 배열을 반환한다")
    void 메시지_없는_상담_조회() {
        List<ConsultationMessageResponse> messages = consultationMessageService.getMessages(reservationId);
        assertTrue(messages.isEmpty());
    }

    private ConsultationMessageCreateRequest request(String senderType, String content) {
        String json = "{\"senderType\":\"" + senderType + "\",\"content\":\"" + content + "\"}";
        try {
            return objectMapper.readValue(json, ConsultationMessageCreateRequest.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Long insertReservation(ConsultationStatus status) {
        ConsultationReservation reservation = ConsultationReservation.builder()
                .userId(userId)
                .counselorId(COUNSELOR_ID)
                .consultationType(ConsultationType.GENERAL)
                .category(ConsultationCategory.HOUSING)
                .reservationDate(LocalDate.now().plusDays(3))
                .reservationTime(LocalTime.of(14, 0))
                .requestMessage("현재 조건으로 독립이 가능한지 상담받고 싶습니다.")
                .consultInfoJson("{\"currentAsset\":45000000}")
                .status(status)
                .build();
        consultationMapper.insert(reservation);
        return reservation.getReservationId();
    }
}
