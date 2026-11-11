package com.som.common.error;

/** 공개 API 에러. message는 그대로 응답에 나가므로 사용자 입력값·내부 정보를 넣지 마라. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
