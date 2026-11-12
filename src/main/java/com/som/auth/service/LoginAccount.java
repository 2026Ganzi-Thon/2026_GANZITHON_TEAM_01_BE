package com.som.auth.service;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** 인증 과정에서만 쓰는 계정 정보. 세션에는 저장하지 않는다(세션에는 SessionUser만). toString에 해시 없음. */
public final class LoginAccount implements UserDetails, CredentialsContainer {

    private final Long userId;
    private final String loginId;
    private String passwordHash;

    LoginAccount(Long userId, String loginId, String passwordHash) {
        this.userId = userId;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
    }

    public Long userId() { return userId; }

    @Override public String getUsername() { return loginId; }
    @Override public String getPassword() { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public void eraseCredentials() { passwordHash = null; }

    @Override
    public String toString() {
        return "LoginAccount{userId=" + userId + "}";
    }
}
