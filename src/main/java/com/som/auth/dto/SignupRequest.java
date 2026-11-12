package com.som.auth.dto;

/**
 * 회원가입 요청. 규칙 검사는 CredentialPolicy가 한다(값은 som.auth 설정).
 * 이메일·전화번호·이름·약관 동의는 받지 않는다(약관은 별도 API).
 */
public record SignupRequest(String loginId, String password, String passwordConfirm) {

    /** 아이디·비밀번호·확인값을 로그에 남기지 않는다. */
    @Override
    public String toString() {
        return "SignupRequest[***]";
    }
}
