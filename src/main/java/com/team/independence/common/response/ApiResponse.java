package com.team.independence.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team.independence.common.exception.ErrorCode;
import lombok.Getter;

/**
 * 모든 API의 공통 응답 포맷.
 *  성공: { "success": true,  "data": {...}, "error": null }
 *  실패: { "success": false, "data": null,  "error": { "code": "...", "message": "..." } }
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorInfo error;

    private ApiResponse(boolean success, T data, ErrorInfo error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ErrorCode code) {
        return new ApiResponse<>(false, null, new ErrorInfo(code.getCode(), code.getMessage()));
    }

    public static ApiResponse<Void> fail(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, new ErrorInfo(code.getCode(), message));
    }

    @Getter
    public static class ErrorInfo {
        private final String code;
        private final String message;

        public ErrorInfo(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}
