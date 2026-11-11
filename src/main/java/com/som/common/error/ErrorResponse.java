package com.som.common.error;

/** 공통 에러 응답: {"error": {"code", "message", "requestId"}} (명세 §13). 다른 필드를 추가하지 마라. */
public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message, String requestId) {}

    public static ErrorResponse of(ErrorCode code, String message, String requestId) {
        return new ErrorResponse(new ErrorBody(code.name(), message, requestId));
    }
}
