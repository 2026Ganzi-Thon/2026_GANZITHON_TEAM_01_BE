package com.som.auth.service;

import com.som.auth.SessionUser;
import com.som.auth.repository.UserRepository;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final Clock clock;

    public AuthService(AuthenticationManager authenticationManager, UserRepository users, Clock clock) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.clock = clock;
    }

    /**
     * 자격을 확인하고 principal이 SessionUser(userId)뿐인 인증 객체를 돌려준다.
     * 없는 아이디·틀린 비밀번호·그 밖의 인증 실패는 모두 같은 401 INVALID_CREDENTIALS다. 실패 로그를 남기지 않는다.
     */
    public Authentication authenticate(String loginId, String password) {
        try {
            Authentication result = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(loginId, password));
            LoginAccount account = (LoginAccount) result.getPrincipal();
            return UsernamePasswordAuthenticationToken.authenticated(
                    new SessionUser(account.userId()), null, List.of());
        } catch (AuthenticationException ex) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Transactional
    public void recordLogin(Long userId) {
        users.findById(userId).ifPresent(user -> user.recordLogin(OffsetDateTime.now(clock)));
    }
}
