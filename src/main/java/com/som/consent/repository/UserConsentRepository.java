package com.som.consent.repository;

import com.som.consent.domain.ConsentType;
import com.som.consent.domain.UserConsent;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * 동의 이력 저장소. JpaRepository가 아닌 Repository를 상속해 delete·deleteAll 등을 노출하지 않는다(이력 누적).
 * 모든 조회는 userId 조건을 포함한다.
 */
public interface UserConsentRepository extends Repository<UserConsent, Long> {

    UserConsent save(UserConsent consent);

    /** 필수 약관(source 없음)의 현재 상태 = 가장 최근 changed_at 행. 같은 시각이면 나중에 INSERT된 행. */
    Optional<UserConsent> findFirstByUserIdAndConsentTypeAndSourceIsNullOrderByChangedAtDescIdDesc(
            Long userId, ConsentType consentType);

    /** 선택 연동 동의(source 있음)의 현재 상태 = 해당 source의 가장 최근 changed_at 행. 같은 시각이면 나중에 INSERT된 행. */
    Optional<UserConsent> findFirstByUserIdAndConsentTypeAndSourceOrderByChangedAtDescIdDesc(
            Long userId, ConsentType consentType, String source);
}
