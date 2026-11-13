package com.som.engine.http;

import org.springframework.web.client.RestClientException;

/** B가 2xx가 아닌 상태로 응답했다. 응답 본문은 담지 않는다(요청 값이 되돌아올 수 있음). */
public class EngineHttpException extends RestClientException {

    private final int status;

    public EngineHttpException(int status) {
        super("engine responded with HTTP " + status);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
