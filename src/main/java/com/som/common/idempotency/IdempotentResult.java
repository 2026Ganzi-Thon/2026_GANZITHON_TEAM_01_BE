package com.som.common.idempotency;

/**
 * 멱등 처리 결과.
 *
 * @param status   처음 처리했을 때의 HTTP 상태(재생 시에도 같은 값)
 * @param replayed 저장된 응답을 돌려줬으면 true
 */
public record IdempotentResult<T>(int status, T body, boolean replayed) {

    /** action이 돌려주는 처음 처리 결과. */
    public record Outcome<T>(int status, T body) {}
}
