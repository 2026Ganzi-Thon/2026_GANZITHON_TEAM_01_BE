package com.som.auth;

import java.io.Serializable;

/** 세션에 저장하는 principal. userId만 담는다(loginId·해시 금지). 컨트롤러는 @CurrentUserId로만 읽는다. */
public record SessionUser(Long userId) implements Serializable {}
