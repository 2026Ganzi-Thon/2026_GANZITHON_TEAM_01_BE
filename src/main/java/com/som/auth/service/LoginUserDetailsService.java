package com.som.auth.service;

import com.som.auth.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * loginId로 계정을 찾는다. 빈으로 등록하지 않고 AuthenticationManager 안에서만 쓴다
 * (전역 인증 자동 구성·기본 사용자 생성 방지). 예외 메시지에 loginId를 넣지 않는다.
 */
public class LoginUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public LoginUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String loginId) {
        return users.findByLoginId(loginId)
                .map(u -> new LoginAccount(u.getId(), u.getLoginId(), u.getPasswordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
    }
}
