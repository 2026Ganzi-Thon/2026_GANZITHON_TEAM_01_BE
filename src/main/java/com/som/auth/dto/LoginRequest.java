package com.som.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 로그인 요청. 길이·조합 규칙은 가입에서만 검사한다(로그인은 형식만). */
public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {

    /** 아이디·비밀번호를 로그에 남기지 않는다. */
    @Override
    public String toString() {
        return "LoginRequest[***]";
    }
}
