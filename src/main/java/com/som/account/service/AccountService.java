package com.som.account.service;

import com.som.account.dto.MeResponse;
import com.som.auth.domain.User;
import com.som.auth.repository.UserRepository;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.consent.service.RequiredConsentChecker;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserRepository users;
    private final RequiredConsentChecker consentChecker;

    public AccountService(UserRepository users, RequiredConsentChecker consentChecker) {
        this.users = users;
        this.consentChecker = consentChecker;
    }

    /** 세션 사용자의 계정 정보. 사용자 행이 없으면(삭제됨) empty. */
    @Transactional(readOnly = true)
    public Optional<MeResponse> me(Long userId) {
        return users.findById(userId).map(user -> new MeResponse(
                user.getLoginId(), consentChecker.isCompleted(userId), user.getCreatedAt(), user.getNickname()));
    }

    @Transactional
    public MeResponse updateNickname(Long userId, String input) {
        String nickname = input == null ? "" : input.strip();
        if (nickname.isEmpty() || nickname.length() > 30
                || nickname.codePoints().anyMatch(Character::isISOControl)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "nickname: 1~30자의 한 줄 이름을 입력해 주세요");
        }
        User user = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        user.changeNickname(nickname);
        return new MeResponse(user.getLoginId(), consentChecker.isCompleted(userId),
                user.getCreatedAt(), user.getNickname());
    }
}
