package com.som.auth.service;

import com.som.auth.SessionUser;
import com.som.auth.domain.User;
import com.som.auth.repository.UserRepository;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final Clock clock;
    private final CredentialPolicy credentialPolicy;
    private final PasswordEncoder passwordEncoder;
    private final SubjectKeyGenerator subjectKeyGenerator;

    public AuthService(AuthenticationManager authenticationManager, UserRepository users, Clock clock,
                       CredentialPolicy credentialPolicy, PasswordEncoder passwordEncoder,
                       SubjectKeyGenerator subjectKeyGenerator) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.clock = clock;
        this.credentialPolicy = credentialPolicy;
        this.passwordEncoder = passwordEncoder;
        this.subjectKeyGenerator = subjectKeyGenerator;
    }

    /**
     * 계정을 만들고 principal이 SessionUser(userId)뿐인 인증 객체를 돌려준다(세션 수립은 호출자가 로그인과 같은 절차로).
     * 입력값은 trim·소문자 변환하지 않는다. 위반 메시지에 입력값을 넣지 않는다.
     * 트랜잭션을 걸지 않는다: saveAndFlush가 자체 트랜잭션에서 유니크 위반을 내야 여기서 409로 바꿀 수 있다.
     */
    public Authentication signup(String loginId, String password, String passwordConfirm) {
        List<String> errors = new ArrayList<>(credentialPolicy.checkLoginId(loginId));
        errors.addAll(credentialPolicy.checkPassword(password));
        if (passwordConfirm == null) {
            errors.add("passwordConfirm: 필수입니다");
        } else if (!Objects.equals(password, passwordConfirm)) {
            errors.add("passwordConfirm: 비밀번호가 일치하지 않습니다");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, String.join(", ", errors.stream().sorted().distinct().toList()));
        }
        if (users.existsByLoginId(loginId)) {
            throw duplicateLoginId();
        }
        User user;
        try {
            user = users.saveAndFlush(User.register(loginId, passwordEncoder.encode(password),
                    subjectKeyGenerator.next(), OffsetDateTime.now(clock)));
        } catch (DataIntegrityViolationException ex) { // 동시 가입: 사전 검사 뒤 다른 요청이 먼저 저장한 경우
            throw duplicateLoginId();
        }
        return UsernamePasswordAuthenticationToken.authenticated(new SessionUser(user.getId()), null, List.of());
    }

    private static ApiException duplicateLoginId() {
        return new ApiException(ErrorCode.CONFLICT, "이미 사용 중인 아이디예요.");
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
