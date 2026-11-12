-- 알림 설정(A_SPEC §10). 사용자당 1행, 행이 없으면 기본값(설정). 실제 발송은 P1(만들지 않음).
-- slot은 온보딩 Q5 enum [제안]. time은 HH:mm(선택). updated_at은 애플리케이션 Clock 값.
CREATE TABLE notification_settings (
    user_id    BIGINT                   PRIMARY KEY,
    enabled    BOOLEAN                  NOT NULL,
    slot       VARCHAR(20)              NULL,
    time       TIME                     NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_notification_settings_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_notification_settings_slot
        CHECK (slot IN ('MORNING', 'LUNCH', 'AFTER_WORK', 'BEFORE_SLEEP', 'NONE'))
);
