-- 루틴 카탈로그 시드(A_SPEC §9). 사용자 데이터가 아니다.
-- duration_sec = 루틴-2 타이머 값(§17-6: 목록 "1분" 표기와 다름 — 타이머 값을 쓴다).
-- subtitle은 화면의 공통 문구를 그대로 넣었다. 플레이스홀더로 보이므로 디자인 확인이 필요하다.
-- SEATED_TWIST는 제목(허리 비틀기)과 설명(목 스트레칭)이 맞지 않지만 화면 그대로 넣었다(값 결정은 디자인 몫, §17).
CREATE TABLE routine_catalog (
    routine_id    VARCHAR(40)  PRIMARY KEY,
    title         VARCHAR(100) NOT NULL,
    subtitle      VARCHAR(200) NOT NULL,
    instruction   VARCHAR(500) NOT NULL,
    duration_sec  INT          NOT NULL,
    display_order INT          NOT NULL,
    asset_key     VARCHAR(100) NOT NULL,
    CONSTRAINT uk_routine_catalog_order UNIQUE (display_order),
    CONSTRAINT ck_routine_catalog_duration CHECK (duration_sec > 0)
);

INSERT INTO routine_catalog (routine_id, title, subtitle, instruction, duration_sec, display_order, asset_key) VALUES
    ('BREATHING', '숨 고르기', '호흡에 집중하여 마음 가라 앉히기',
     '의자에 앉아 한 손을 배에 두고, 코로 5초 들이쉬고 입으로 5초 내쉽니다. 5회 반복하세요!',
     60, 1, 'routine_breathing'),
    ('SHOULDER_ROLL', '어깨 천천히 돌리기', '호흡에 집중하여 마음 가라 앉히기',
     '어깨를 귀 쪽으로 올렸다가 뒤로 크게 돌려 내립니다. 앞 뒤 각각 8회 천천히 호흡하며 합니다.',
     30, 2, 'routine_shoulder_roll'),
    ('SEATED_TWIST', '의자에 앉아 허리 비틀기', '호흡에 집중하여 마음 가라 앉히기',
     '어깨를 내리고, 한쪽 귀를 같은 쪽 어깨 방향으로 천천히 기울여 반대편 목이 늘어나게 합니다. 2회 반복하세요!',
     30, 3, 'routine_seated_twist'),
    ('WRIST_ARM_STRETCH', '손목, 팔 스트레칭', '호흡에 집중하여 마음 가라 앉히기',
     '한 팔을 앞으로 뻗고 손바닥을 아래로 한 뒤, 다른 손으로 손가락을 몸쪽으로 당겨 팔 안쪽을 늘입니다. 2회 반복하세요!',
     30, 4, 'routine_wrist_arm_stretch');
