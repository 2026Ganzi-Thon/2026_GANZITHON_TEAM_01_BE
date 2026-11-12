package com.som.settings.dto;

/** @param actionValue LINK면 URL, TEL이면 전화번호. 확인 전이면 null */
public record HelpResourceResponse(String situation, String name, String description, String actionType,
                                   String actionValue) {}
