package com.som.common.web;

import jakarta.servlet.http.HttpServletRequest;

/** 현재 요청의 요청 ID 조회. 에러 응답의 requestId 등에서 사용한다. */
public final class RequestIds {

    private RequestIds() {}

    /** RequestIdFilter가 넣은 요청 ID. 필터를 거치지 않았으면 null. */
    public static String current(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value instanceof String id ? id : null;
    }
}
