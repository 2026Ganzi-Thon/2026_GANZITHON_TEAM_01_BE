package com.som.auth.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러가 현재 로그인 사용자 id를 얻는 유일한 방법. 값은 세션의 SecurityContext에서만 온다.
 * 요청 바디·쿼리·경로의 userId를 쓰지 마라(docs/DEVELOPMENT_RULES.md). 로그인하지 않았으면 401 UNAUTHORIZED.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {}
