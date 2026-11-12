package com.som.settings.repository;

import com.som.settings.domain.NotificationSettings;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** 키가 user_id라 조회는 항상 세션 사용자 행만 본다. */
public interface NotificationSettingsRepository extends Repository<NotificationSettings, Long> {

    NotificationSettings save(NotificationSettings settings);

    Optional<NotificationSettings> findById(Long userId);
}
