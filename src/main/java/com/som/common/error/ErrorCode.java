package com.som.common.error;

import org.springframework.http.HttpStatus;

/** 공개 API 에러 코드(명세 §13). 기본 문구는 사용자에게 보여도 되는 한국어 문구다. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않아요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없어요."),
    CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "필요한 동의를 먼저 완료해 주세요."),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "보안 확인이 만료됐어요. 새로고침 후 다시 시도해 주세요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없어요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식이에요."),
    CONFLICT(HttpStatus.CONFLICT, "요청이 현재 상태와 충돌해요."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많아요. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 문제가 생겼어요. 잠시 후 다시 시도해 주세요."),
    ENGINE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 결과를 불러올 수 없어요. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
