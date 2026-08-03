package com.team.independence.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 도메인별 프리픽스로 구역을 나눈다.
 * 각자 자기 도메인 구역에만 코드를 추가할 것 (git 충돌 최소화).
 */
@Getter
public enum ErrorCode {

    // ===== 공통 COMMON_xxx =====
    INVALID_INPUT("COMMON_001", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR("COMMON_002", "서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHORIZED("COMMON_003", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),

    // ===== 인증 AUTH_xxx =====
    AUTH_INVALID_TOKEN("AUTH_001", "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
    AUTH_EXPIRED_TOKEN("AUTH_002", "만료된 토큰입니다.", HttpStatus.UNAUTHORIZED),
    AUTH_KAKAO_API_ERROR("AUTH_003", "카카오 API 호출에 실패했습니다.", HttpStatus.BAD_GATEWAY),

    // ===== 회원 MEMBER_xxx =====
    MEMBER_NOT_FOUND("MEMBER_001", "회원을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    MEMBER_WITHDRAWN("MEMBER_002", "탈퇴한 회원입니다.", HttpStatus.FORBIDDEN),
    MEMBER_ALREADY_EXISTS("MEMBER_003", "이미 가입된 회원입니다.", HttpStatus.CONFLICT),
    AGREEMENT_NOT_FOUND("MEMBER_004", "약관 동의 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // ===== 자산 ASSET_xxx =====
    ASSET_NOT_LINKED("ASSET_001", "자산 연동이 필요합니다.", HttpStatus.BAD_REQUEST),
    ASSET_SYNC_IN_PROGRESS("ASSET_002", "동기화가 이미 진행 중입니다.", HttpStatus.CONFLICT),
    ASSET_RSA_ENCRYPT_FAILED("ASSET_003", "비밀번호 암호화에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    ASSET_CODEF_API_ERROR("ASSET_004", "금융 연동 서버 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),
    ASSET_ORGANIZATION_NOT_CONNECTED("ASSET_005", "연동되지 않은 기관입니다.", HttpStatus.NOT_FOUND),
    ASSET_SUMMARY_NOT_FOUND("ASSET_006", "자산 연동 정보가 없습니다. 먼저 금융기관을 연동해주세요.", HttpStatus.NOT_FOUND),
    ASSET_MANUAL_NOT_FOUND("ASSET_007", "수동 자산을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // ===== 목표 GOAL_xxx =====
    GOAL_NOT_FOUND("GOAL_001", "목표를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // ===== 또래 비교 COMPARE_xxx =====
    COMPARE_SNAPSHOT_NOT_FOUND("COMPARE_001", "비교할 집계 데이터가 없습니다.", HttpStatus.NOT_FOUND),
    COMPARE_INVALID_RANGE("COMPARE_002", "비교 기준 범위가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    COMPARE_CONSENT_REQUIRED("COMPARE_003", "또래 비교 약관 동의가 필요합니다.", HttpStatus.FORBIDDEN),

    // ===== 정책 POLICY_xxx =====
    POLICY_NOT_FOUND("POLICY_001", "정책을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // ===== 부동산 PROPERTY_xxx =====
    REGION_NOT_FOUND("PROPERTY_001", "존재하지 않는 지역 코드입니다.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
