# 솜(SOM) 백엔드 A 상세 명세

> 출처: 「솜 백엔드 A·B 업무 명세서 v1.0 (2026.11.07)」 + Figma 「화면 명세서」「화면」「와이어프레임」
> 표기: `[화면]` 화면에서 확인된 확정값 / `[제안]` 기본값으로 쓰되 설정으로 분리 / `[결정 필요]` 임의 확정 금지 / `[B 합의 대기]` B 담당과 맞출 항목(제안값으로 진행, 변경 쉽게)

---

## 1. 작업 목록과 완료 기준

| ID | 구현 항목 | 완료 기준 |
|---|---|---|
| A-01 | 회원가입·로그인·로그아웃, 본인 조회, 세션 만료 | 사용자별 데이터 분리; 만료·오류 코드 테스트 |
| A-02 | 필수 약관·선택 연동 동의 이력: 항목별 허용/철회 시각·정책 버전 | 선택 동의 없이도 주요 기능 사용 가능 |
| A-03 | 체크인 3문항: enum 검증, 하루 단위 저장, 재제출 | 중복·누락·범위 외 값 처리; KST 기준 |
| A-04 | 연동 데이터 수신: 소스·기간·값·단위·출처·수집 시각 | 동의한 소스만 수신; 미연동/결측 명확 표기 |
| A-05 | 오늘 상태, 이번 주 변화, 7/30일 기록, 최신 계산 시각 | engine 결과를 안정적으로 공급 |
| A-06 | 대화(사용자 입력·engine 응답·카드) 저장·날짜별 조회, 마음 정리 기록 | 본인 외 조회 차단, 페이지네이션 |
| A-07 | 루틴 목록, 시작/완료/건너뜀, 주간 이행, 마음 정리 잠금 | 중복 완료에도 기록·보상 중복 없음 |
| A-08 | 연결 해제, 데이터 삭제, 탈퇴, 알림 설정 | DB·캐시·engine 처리물까지 삭제, 처리 상태 확인 |
| A-09 | 인증 필터, 입력 검증, 에러 형식, 요청 ID, 요청 제한 | OpenAPI 문서와 실제 응답 일치 |

---

## 2. 화면 흐름과 A 책임 `[화면]`

진입: 시작하기("로그인 하러 가기") → 로그인 / 회원가입 → 약관 동의 → (온보딩 5문항 `[결정 필요]`) → 데이터 연결 선택 → 항목별 동의 시트 → 홈
하단 탭: 홈 / 대화 / 루틴 / 마이

| 화면 | 동작 | A 제공 |
|---|---|---|
| 회원가입 | 아이디·비밀번호·비밀번호 확인 → 완료 | 가입, 형식 검증 |
| 로그인 | 아이디·비밀번호 / 회원가입 / 아이디·비밀번호 찾기 | 로그인·세션 (찾기는 미구현) |
| 약관 동의 | 전체 동의 + 필수 3개 → 동의하고 시작하기 | 필수 동의 저장, 미동의 시 서비스 API 차단 |
| 데이터 연결 | 소스별 토글 → 다음 / 나중에 연결할게요 | 소스별 동의·연결 상태 |
| 항목별 동의 시트 | 연결할게요 / 나중에 할게요 | 고지 내용·버전·이력 |
| 홈① 하루 체크인 | 3문항, 다음/이전/다음에 하기, `>` → 홈② | 체크인 저장 → 계산 → 스냅샷 |
| 홈② 양 상태 | 상단 1~4단계 표시, 말풍선 클릭 → 대화, `<` 체크인, `>` 이번 주 | 오늘 상태 + 말풍선 |
| 홈③ 이번 주 변화 | 털 무게·상태 문구·주간 평균·월~일 막대, "오늘도 조금 덜어낼까요?" → 루틴-1 | 주간 요약 |
| 대화 | 지난 대화(달력으로 날짜 선택), 전송, 추천 루틴 카드(지금 해볼게요 → 루틴-1 / 오늘은 그냥 둘래요) | 메시지 저장·조회, engine 중계 |
| 루틴-1 | 월~일 이행 표시, 오늘 상태, 추천 4개(완료 시 체크), 마음 정리(4개 완료 시 열림) | 오늘 목록·완료·주간 이행·잠금 |
| 루틴-2 | 4개 순서 진행(타이머), 이전/다음, 마지막 종료 → 마음 정리, `<` 루틴-1 | 루틴별 완료(멱등) |
| 마음 정리 | 감정 1개(건너뛰기 가능), 자유 기록(건너뛰기 가능), "털 한 뭉치를 덜어냈어요!" → 저장 후 홈 | 기록 저장, 연출 이벤트 |
| 마이 | 나의 기록 7/30일 + 요약, 데이터 연결 관리, 알림 설정, 개인정보 데이터 삭제, 도움 받을 곳 | 조회·설정·삭제 |

---

## 3. 계정 · 약관 `[화면]`

- 회원가입: 아이디 **4~13자**, 비밀번호 **10~12자**, 비밀번호 확인 일치.
  - 아이디 허용 문자, 비밀번호 조합 규칙: `[결정 필요]` → 설정값.
  - 비밀번호 상한 12자는 보안상 낮다 → 설정값으로 두고 보고(확대 권고).
  - 이메일·전화번호를 받지 않으므로 **아이디/비밀번호 찾기는 미구현**(P1). 호출 시 "준비 중" 에러.
- 로그인 실패 메시지는 아이디 존재 여부를 드러내지 않는다. 로그인 API 요청 제한.
- 필수 약관 (전체 동의 + 3개 → "동의하고 시작하기"):

| 키 `[제안]` | 화면 문구 |
|---|---|
| `AGE_14_PLUS` | [필수] 만 14세 이상이에요 |
| `TERMS_OF_SERVICE` | [필수] 서비스 이용약관 |
| `PRIVACY_COLLECTION` | [필수] 개인정보 수집 이용 (하루 체크인 응답을 저장해요) |

- 필수 3개 미동의 상태에서는 체크인·대화·루틴 등 서비스 API를 `403 CONSENT_REQUIRED`로 차단. 계정·약관 API는 허용.
- 동의는 `policy_version`, 시각과 함께 **이력 누적**(덮어쓰기 금지).

---

## 4. 온보딩 5문항 — MVP 포함 `[결정 필요]`

> 2026-11-13: 팀(B)이 포함해 구현했다(V19). 선택지 enum은 아래 `[제안]` 값 그대로다.

포함이 결정되면 저장(모든 필드 nullable — 각 문항 건너뛰기 가능). engine 입력으로 쓰인다.

| 문항 | 방식 | 선택지 → enum `[제안]` |
|---|---|---|
| 요즘 어떤 시기를 보내고 있어요? | 단일 | 회사 1년 미만 `EMPLOYED_UNDER_1Y` / 회사 1~3년 `EMPLOYED_1_3Y` / 취업 준비 `JOB_SEEKING` / 그 밖에요 `OTHER` |
| 지칠 때 먼저 오는 변화는? | 복수 | 예민해져요 `IRRITABLE` / 쉬어도 피곤해요 `TIRED_AFTER_REST` / 잠이 달라져요 `SLEEP_CHANGE` / 사람 만나기가 부담돼요 `SOCIAL_BURDEN` / 집중이 안 돼요 `CANT_FOCUS` / 잘 모르겠어요 `NOT_SURE` |
| 기운이 돌아올 때 나는 주로? | 복수 | 푹 자기 `SLEEP_WELL` / 몸 움직이기 `MOVE_BODY` / 혼자 조용히 `QUIET_ALONE` / 음악 듣기 `MUSIC` / 글로 정리 `WRITE` / 영상·게임 `VIDEO_GAME` / 사람 만나기 `MEET_PEOPLE` |
| 하루에 낼 수 있는 시간은? | 단일 | 3 / 5 / 10 / 30(이상) 분 |
| 솜이 말을 걸면 좋은 때는? | 단일 | 출근 전·아침 `MORNING` / 점심 `LUNCH` / 퇴근 직후 `AFTER_WORK` / 잠들기 전 `BEFORE_SLEEP` / 알림 없이 `NONE` → 알림 설정 기본값 |

---

## 5. 데이터 연결 · 선택 동의 `[화면]`

"솜이 살펴볼 정보를 골라주세요 — 회사나 다른 사람에게는 보이지 않아요. 언제든 '나'에서 끄고 지울 수 있어요."

| 소스 `[제안]` | 화면 | 보는 것 (수집 허용) | 안 보는 것 (필드 생성 금지) |
|---|---|---|---|
| (체크인) | 하루 체크인 `필수` | 내가 고른 답 | — (필수 약관, 토글 불가) |
| `SLEEP` | 수면 시간 `추천` | 잠든 시각, 깬 시각 | 다른 건강 기록 |
| `STEPS` | 걸음 수 | 하루 걸음 수 | 위치, 이동 경로 |
| `SCREEN_TIME` | 휴대폰 사용 시간 | 시간대별 총 사용 시간 | 앱 내용, 메시지, 검색 기록 |
| `CALENDAR_PERSONAL` | 개인 캘린더 | 일정 수, 바쁜 시간대 | 일정 제목, 내용, 참석자 |
| `CALENDAR_WORK` | 회사 캘린더 | (화면 잘림) 바쁜 정도만 | 제목·내용·참석자 |

- 캘린더 1개/2개 분리, 사용시간 시간대별 여부는 `[B 합의 대기]` — 소스 enum은 2개로 두되 engine 입력은 합의 후 확정.
- 항목별 동의 시트(수면 예): 태그 `건강 정보 · 별도 동의` / 모으는 것: 잠든 시각, 깬 시각 / 쓰임: 평소와 달라졌는지 살피는 데만 / 보관: **[보관 기간]** · 탈퇴하면 바로 지워요 / 거부: 동의하지 않아도 솜을 쓸 수 있어요.
  - 동의 레코드에 소스별 `items`, `purpose`, `retention`, `policy_version` 저장.
  - 보관 기간 `[결정 필요]` → 설정값. **탈퇴 시 즉시 삭제**는 확정. 걸음 수의 건강정보 분류 `[결정 필요]`.
- 철회: 즉시 재수집 차단 → 다음 계산부터 제외 → 보유 데이터 삭제 정책 적용 → 연결 상태 해제.
- 웹앱이라 실제 수집 불가 → 값은 `provider=DEMO`로 주입(시연 계정 시드 + 개발용 수신 API, 운영 프로필에서 비활성 가능).
- 저장 형태: 수면 = 잠든/깬 시각 + 파생 수면 분 / 사용시간 = 시간대별 합계 / 걸음 = 일 합계 / 캘린더 = 일정 수·바쁜 분. 세부 로그 저장 금지.

---

## 6. 하루 체크인 `[화면]`

| 문항 | 안내 | 선택지 → enum `[제안]` | 방식 |
|---|---|---|---|
| Q1 오늘 마음은 어떤가요? `mood` | 지금 느끼는 마음을 골라주세요! | 편안해요 `CALM` / 괜찮아요 `OKAY` / 그냥 그래요 `SO_SO` / 불안해요 `ANXIOUS` / 지쳤어요 `TIRED` / 기타 `OTHER` | **6중 1, 복수 불가 (확정)** |
| Q2 무엇이 마음을 무겁게 했나요? `stressors` | 오늘 가장 신경 쓰였던 일을 알려주세요. | 취업이나 업무 `WORK` / 미래에 대한 걱정 `FUTURE_WORRY` / 다른 사람과의 비교 `COMPARISON` / 인간관계 `RELATIONSHIP` / 특별한 이유는 없어요 `NO_REASON` / 기타 `OTHER` | 목록으로 받고 최대 개수 설정(기본 1) `[결정 필요]` |
| Q3 지금 어떤 도움이 필요해요? `supportNeed` | 솜이 오늘의 마음을 함께 돌봐줄게요. | 따뜻한 위로 `COMFORT` / 마음 털어 놓기 `VENT` / 잠깐 쉬어가기 `SHORT_BREAK` / 작은 행동 추천 `SMALL_ACTION` | 단일 `[결정 필요]` |

- '기타'는 선택지일 뿐 **자유 입력란 없음** → 자유 텍스트 필드 만들지 마라.
- 화면 동작: 다음/이전, **다음에 하기** → 해당 문항 null, `>` → 홈으로 이탈.
- 저장 시점 `[결정 필요]` — 기본: 마지막 문항에서 제출 1회, `>` 이탈 시 미저장.
- `local_date`는 서버가 산출, 과거 날짜 불허. `(user_id, local_date)` 유니크.
- 재제출 `[결정 필요]` — 기본: 최신으로 갱신 → 재계산 → 스냅샷 교체.
- 재시도 중복은 `Idempotency-Key`로 방지.

---

## 7. 홈 `[화면]`

**양 상태 보기:** 상단 양 아이콘 4개 + 현재 단계(1~4). 말풍선은 "양이 먼저 말을 건네고 소통을 유도", 클릭 시 대화로.
- 말풍선 문구 출처 `[B 합의 대기]` — 기본: 하루 1회 선제 인사 결과를 저장해 홈 말풍선과 대화 첫 메시지에 같이 사용. 근거 없으면 단계별 고정 문구.
- `GET /state/today` 응답 `[제안]`: `localDate, score, level(1~4|null), status(CALCULATED|INSUFFICIENT_DATA), statusText, subText, assetKey, bubbleText, usedSources, algorithmVersion, calculatedAt, isStale, woolTrimmedToday`

**이번 주 변화 — "이번 주 소미의 털 무게":** 털 무게(예: 3.2kg), 단계 문구, **이번 주 평균 피로도**(예: 2.4 / 4, 이번 주 체크인 기반), **월~일 일별 막대**, "오늘도 조금 덜어낼까요?" → 루틴-1.
- 기간: 이번 주 월~일(KST). 미기록일 `null`. 평균은 유효 일별 level 산술평균(소수 첫째 자리) + 기록일 수.
- 털 무게가 주간 값인지 일별 값인지 `[B 합의 대기]` → 응답에 둘 다 담을 수 있게 설계.

**단계별 상태 문구 `[화면]`** (engine Mock·시드 기본값)

| 단계 | 상태 문구 | 보조 문구 |
|---|---|---|
| 1 | 마음이 가벼워요 | 지금의 편안함을 느껴보세요. |
| 2 | 조금 지쳐 있어요 | 잠깐 쉬어가도 괜찮아요. |
| 3 | 피로가 쌓였어요 | 오늘은 나를 먼저 돌봐주세요. |
| 4 | 충분한 쉼이 필요해요 | 혼자 애쓰지 않아도 괜찮아요. |

---

## 8. 대화 `[화면]`

- 메시지 유형 `messageType` + `payload`(JSON 문자열):
  - `TEXT` — 소미/사용자 말풍선
  - `WEEK_SUMMARY` — "이번 주 달라진 점: 수면 평소 -1시간, 일정 사이 빈 시간 적음" (근거 없는 수치 금지)
  - `ROUTINE_RECOMMENDATION` — "추천 루틴 · 호흡 따라하기 - 2분" + 지금 해볼게요 / 오늘은 그냥 둘래요
- 추천 카드 수락/거절 기록 `[제안]`.
- 지난 대화: 달력에서 날짜 선택 → 해당 날짜 대화. 필요 API: 날짜별 조회 + 월별 대화 있는 날짜 목록. 스레드 = 사용자별 하루(`threadDate`).
- 입력 최대 길이 `[결정 필요]` → 설정값.
- 와이어프레임의 "대화 마치기"는 최종 화면에 없음 → 구현 금지.
- 파이프라인: ① A 인증·요청 제한·길이 검증 → ② 사용자 메시지 저장 → ③ `ChatEngine.respond`(오늘 체크인, 주간 요약, 최근 N개 메시지 `[B 합의 대기]`, 동의 상태만) → ④ 응답·riskFlag·카드 저장 → ⑤ 반환.
  - 위기 분류 시 응답에 도움 받을 곳 목록 포함. engine 실패 시 고정 문구 `"지금은 잠시 답변이 어려워요. 호흡 루틴을 먼저 해볼까요?"`
  - 사용자 원문을 engine(외부 LLM)에 보낼지, 그 고지 방식 `[결정 필요]`.

---

## 9. 루틴 · 마음 정리 `[화면]`

**루틴-1:** "오늘은 조금 덜어내 볼까요? / 지금 나에게 필요한 작은 쉼을 선택해요"
- 월~일 이행 표시: 이행한 날 분홍, 안 한 날 회색, 미래 없음. '이행' 기준 `[결정 필요]` — 기본: 1개 이상 완료.
- 오늘의 추천 루틴 4가지(순서는 `RoutineEngine`), 완료 시 체크.
- **마음 정리하기는 오늘 루틴 4개 모두 완료 시 열림.** 서버가 `journalUnlocked` 계산. 서버 강제 여부는 `som.journal.requires-all-routines`(기본 true).

**루틴-2:** 4개 순서 진행, 진행 바, 타이머, 양 이미지는 사용자 단계에 따라 변화, 이전/다음, 마지막 "종료" → 마음 정리. 건너뛰기 버튼은 없으나 `SKIPPED` 상태는 지원.

**루틴 카탈로그 시드 (Flyway)**

| routineId | 제목 | 타이머 | 실행 방법 |
|---|---|---|---|
| `BREATHING` | 숨 고르기 | 60초 | 의자에 앉아 한 손을 배에 두고, 코로 5초 들이쉬고 입으로 5초 내쉽니다. 5회 반복하세요! |
| `SHOULDER_ROLL` | 어깨 천천히 돌리기 | 30초 | 어깨를 귀 쪽으로 올렸다가 뒤로 크게 돌려 내립니다. 앞 뒤 각각 8회 천천히 호흡하며 합니다. |
| `SEATED_TWIST` | 의자에 앉아 허리 비틀기 | 30초 | 어깨를 내리고, 한쪽 귀를 같은 쪽 어깨 방향으로 천천히 기울여 반대편 목이 늘어나게 합니다. 2회 반복하세요! (제목·설명 불일치 — 화면 그대로) |
| `WRIST_ARM_STRETCH` | 손목, 팔 스트레칭 | 30초 | 한 팔을 앞으로 뻗고 손바닥을 아래로 한 뒤, 다른 손으로 손가락을 몸쪽으로 당겨 팔 안쪽을 늘입니다. 2회 반복하세요! |

목록 화면은 모두 "1분"·부제 "호흡에 집중하여 마음 가라 앉히기"(플레이스홀더로 보임) → `durationSec`(타이머 값), `subtitle`, `instruction`, `displayOrder`, `assetKey` 컬럼 분리.

**마음 정리하기**
- "지금 마음은 어디에 가까워요?" — 1개만, 건너뛰기 가능: 불안 `ANXIOUS` / 답답함 `FRUSTRATED` / 피곤함 `TIRED` / 무기력 `LETHARGIC` / 괜찮음 `OKAY`
- "오늘 나를 가장 지치게 한 일은 무엇인가요?" — 자유 기록, 건너뛰기 가능, 최대 길이 `[결정 필요]`(설정), XSS 안전 출력.
- "털 한 뭉치를 덜어냈어요!" → 저장 → 홈 → 털 깎임 연출. 둘 다 비어도 완료 가능 `[결정 필요]`(기본 허용).
- 연출은 실제 점수 불변. `wool_trim_events`에 기록하고 `state/today.woolTrimmedToday`로 노출. 연출 값 형식 `[B 합의 대기]`.

---

## 10. 마이 `[화면]`

- 나의 기록: 7일/30일 토글 → 피로도 그래프 + 요약("털이 가장 무거웠던 날은 목요일이에요. 그날 잠이 평소보다 2시간 짧았어요." — 근거 없으면 근거 문장 생략). 기간 기본 `[제안]`: 오늘 포함 최근 7·30일.
- 데이터 연결 관리 → 소스별 상태·연결·해제·철회.
- 알림 설정 → 활성화 여부 + 알림 시간. 저장: `enabled`, `slot`(§4 enum), `time`(HH:mm, 선택). 실제 발송은 P1.
- 개인정보 데이터 삭제 → 안내·확인 → 기록 삭제 또는 탈퇴.
- 도움 받을 곳 (설정 파일/시드로 관리, 시연 전 최신 정보 확인 — URL 추측 금지):

| 상황 | 기관 | 설명 | 동작 |
|---|---|---|---|
| 마음이 지치고 힘들 때 | 정신 건강 복지 센터 | 심리 상담과 지역 전문기관 연계 | 기관 알아보기 → 안내 페이지 (URL 확인 필요) |
| 취업 준비가 막막할 때 | 대학 일자리 플러스 센터 | 진로·취업 상담 및 프로그램 | 고용24 → 홈페이지 (URL 확인 필요) |
| 직장 문제로 힘들 때 | 고용노동부 고객상담센터 | 근로조건·임금·노동 관련 상담 | 1350 전화 연결 |
| 지금 당장 도움이 필요할 때 | 자살예방상담전화 | 24시간 상담 가능 | 109 전화 연결 |

---

## 11. 보안 · 개인정보 통제

| 구분 | 구현 | 검증 |
|---|---|---|
| 인증/인가 | 모든 사용자 데이터 API 세션 인증 + 소유권 필터, 필수 약관 게이트 | 다른 계정 리소스 GET/DELETE → 403/404 |
| 입력 | enum·길이·날짜·타입 검증, '안 보는 것' 필드 거부, 자유 텍스트 XSS 안전·상한 | 변조·초과·중복 제출 테스트 |
| 저장 | TLS, Argon2id, 비밀키 환경변수, DB 최소 권한 | 저장소·DB에 평문 비밀번호/키 없음 (`.env`는 gitignore) |
| 선택 동의 | 소스별 동의·철회, 수면은 건강정보 별도 동의, 철회 후 재수집 차단 | 철회 후 수집 거부 테스트 |
| 외부 제공 | engine에는 최소·정규화 데이터만, 원문 전송은 정책 확정 후 | 로그에 원문·식별정보 없음 |
| 접근 남용 | 로그인·대화·수집·삭제 요청 제한, 대화는 사용자당 분당/일일 제한, CSRF | 연속 요청·위조 테스트 |
| 삭제 | 사용자 소유 데이터·캐시·engine 처리물 연쇄 정리, 탈퇴 시 즉시 | 삭제 후 재조회 불가 |
| 운영 | 민감정보 마스킹 로그, 요청 ID, 장애 알림, 백업 삭제 정책 분리 | 로그 샘플 점검 |

---

## 12. engine 계약 `[B 합의 대기]`

| 인터페이스 | A → engine | engine → A |
|---|---|---|
| `FatigueEngine.calculate` | subjectKey, localDate, timezone, checkin(mood, stressors, supportNeed), signals(동의분만, 결측 null), consentedSources, (온보딩 요약) | score, level, woolDisplayKg, status, statusText, subText, assetKey, usedSources, algorithmVersion, calculatedAt |
| `InsightEngine.summarize` | 기간(이번 주 월~일 / 최근 7·30일) 일별 상태 목록 | 평균, 기록일 수, 변화 요약, 가장 무거웠던 날 + 근거 |
| `RoutineEngine.recommend` | 오늘 단계, supportNeed, 수행 이력, (회복 방식·가용 시간) | 루틴 ID 순서 + 이유 |
| `ChatEngine.respond` | 모드(GREETING/REPLY), 최근 메시지, 오늘 체크인, 주간 요약, 동의 상태 | 텍스트, riskLevel, 카드 목록 |

계산 응답 예시:
```json
{ "score": 48, "level": 2, "woolDisplayKg": 1.6, "status": "CALCULATED",
  "usedSources": ["CHECKIN", "CALENDAR_PERSONAL"], "algorithmVersion": "mvp-0.1",
  "calculatedAt": "2026-11-07T23:00:00+09:00" }
```

- 미기록: `score=null, level=null, status=INSUFFICIENT_DATA`. 0·1단계 금지.
- engine 예외·타임아웃: 마지막 정상 스냅샷 + `isStale=true`, 또는 조회 일시 제한. 점수 생성 금지. 타임아웃·재시도 상한은 설정.
- 추천 routineId가 카탈로그에 있는지 A가 확인.
- 삭제·철회 시 `EngineCacheInvalidator`(가칭)로 무효화 호출.
- **Mock 규칙(A 작성):** 체크인·신호 모두 없으면 INSUFFICIENT_DATA. 있으면 결정적 계산(같은 입력 → 같은 출력). 점수→단계: 0~24=1, 25~49=2, 50~74=3, 75~100=4 / 털 무게 0.8·1.6·2.4·3.2kg(B 초안, 상수 분리). 문구는 §7 표. 대화 Mock은 고정 문구 + 카드 예시.

---

## 13. 공개 API `[제안 경로]`

| 영역 | 메서드 / 경로 |
|---|---|
| 인증 | `POST /api/v1/auth/signup`, `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/csrf` |
| 계정 | `GET /api/v1/me` (필수 동의 완료 여부 포함) |
| 동의 | `GET·PATCH /api/v1/me/consents` |
| 온보딩 | `GET·PUT /api/v1/me/onboarding` |
| 연결 | `GET /api/v1/me/connections`, `POST·DELETE /api/v1/me/connections/{source}` |
| 수집 | `POST /api/v1/signals` (데모·개발용) |
| 체크인 | `POST /api/v1/checkins`, `GET /api/v1/checkins?date=` / `?from=&to=` |
| 홈 | `GET /api/v1/state/today`, `GET /api/v1/insights/week` |
| 마이 | `GET /api/v1/insights?days=7\|30` |
| 대화 | `POST /api/v1/chat/messages`, `GET /api/v1/chat/messages?date=&cursor=`, `GET /api/v1/chat/dates?month=YYYY-MM`, `POST /api/v1/chat/recommendations/{id}/response` |
| 루틴 | `GET /api/v1/routines/today`, `POST /api/v1/routines/{id}/start·complete·skip` |
| 마음 정리 | `POST /api/v1/journal`, `GET /api/v1/journal?date=` |
| 설정 | `GET·PATCH /api/v1/me/notification-settings`, `GET /api/v1/help-resources` |
| 삭제 | `DELETE /api/v1/me/data`, `DELETE /api/v1/me`, `GET /api/v1/me/deletion-status` |
| 익명 공유 | `GET·POST /api/v1/recovery-shares`, `DELETE /api/v1/recovery-shares/{id}` (업무명세서상 P1 '털 담요') |

공통: JSON, ISO 8601(오프셋 포함), `localDate`(YYYY-MM-DD, KST), `X-Request-ID`, `Idempotency-Key`, 커서 페이지네이션.
에러 형식: `{"error": {"code": "VALIDATION_ERROR", "message": "...", "requestId": "..."}}` — 401 / 403(`CONSENT_REQUIRED` 등) / 404 / 400 / 409 / 415 / 429 / 503(engine 장애).

### 13-1. 구현 현황 (2026-11-13, 백엔드 A·B)

위 표의 경로는 **모두 구현**했다(온보딩·익명 공유·삭제 재시도는 B가 구현, A가 테스트 보강). 실제 요청·응답 형식은 OpenAPI 문서(`/v3/api-docs`, `/swagger-ui.html`)를 기준으로 한다.

| 항목 | 구현 |
|---|---|
| 온보딩 `GET·PUT /api/v1/me/onboarding` | 모든 문항 null 허용(건너뛰기). PUT은 전체 교체. Q4는 3·5·10·30만. Q5는 알림 설정이 없을 때 기본 시간대, Q3·Q4는 루틴 추천 입력(B). 기록 삭제로는 남고 탈퇴 시 삭제 |
| 익명 공유 `recovery-shares` | 한 줄 200자, 최근 30개, 작성자 정보 없이 `mine`만. 본인 글만 삭제. 기록 삭제·탈퇴 시 삭제 |
| engine 삭제 재시도 | B 삭제 실패 시 `ENGINE_PENDING`, 1분 뒤 재시도·이후 5분 간격(real 모드). 성공하면 `COMPLETED` |
| 아이디/비밀번호 찾기 | 미구현. 경로가 정해지지 않아 "준비 중" 에러도 없다(호출하면 404) — 경로 결정 필요 |
| `Idempotency-Key` | `POST /api/v1/checkins`에서만 받는다(선택). 루틴 완료는 사용자·루틴·날짜 유니크로 멱등 |
| 커서 페이지네이션 | `GET /api/v1/chat/messages`(`cursor`, `size` 기본 30·최대 100, 응답 `nextCursor`) |
| 삭제·탈퇴 확인 | `DELETE /api/v1/me/data`, `DELETE /api/v1/me`는 바디 `{"confirm": true}` 필수 |
| 필수 약관 게이트 예외 | `/api/v1/auth/**`, `/api/v1/me`, `/api/v1/me/consents`, `/api/v1/me/data`, `/api/v1/me/deletion-status`, `/api/v1/help-resources` |
| 요청 제한 `[제안]` | 로그인(IP·아이디), 가입(IP), 수신(사용자), 대화(사용자 분당·일일), 삭제·탈퇴(사용자), 익명 공유 글쓰기(사용자). 값은 설정 `som.rate-limit.*` |
| 422 | 쓰지 않는다. 형식·규칙 위반은 모두 400 |

**에러 코드** (`error.code`, 메시지는 기본 문구이며 검증 오류는 `"{필드}: {이유}"`로 바뀐다)

| code | HTTP | 언제 |
|---|---|---|
| `VALIDATION_ERROR` | 400 | 입력 규칙 위반, 정의되지 않은 필드, 모르는 enum 값 |
| `MALFORMED_REQUEST` | 400 | JSON을 읽을 수 없음 |
| `UNAUTHORIZED` | 401 | 로그인 필요, 세션 만료, 탈퇴한 사용자의 세션 |
| `INVALID_CREDENTIALS` | 401 | 로그인 실패(아이디 존재 여부를 드러내지 않음) |
| `FORBIDDEN` | 403 | 권한 없음 |
| `CONSENT_REQUIRED` | 403 | 필수 약관 미동의 |
| `SOURCE_NOT_CONSENTED` | 403 | 동의하지 않은 연동 소스의 데이터 수신 |
| `CSRF_INVALID` | 403 | CSRF 토큰 없음·불일치 |
| `JOURNAL_LOCKED` | 403 | 오늘 루틴을 모두 마치기 전 마음 정리 저장 |
| `NOT_FOUND` | 404 | 없는 리소스, 다른 사용자의 리소스 |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 메서드 |
| `CONFLICT` | 409 | 아이디 중복, 같은 Idempotency-Key에 다른 요청, 완료한 루틴 건너뛰기, 카드 응답 변경 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | JSON이 아닌 요청 |
| `RATE_LIMITED` | 429 | 요청 제한 초과(`Retry-After` 헤더) |
| `INTERNAL_ERROR` | 500 | 서버 오류 |
| `ENGINE_UNAVAILABLE` | 503 | engine 장애이고 보여줄 마지막 정상 값이 없음 |

---

## 14. 데이터 모델 `[제안]`

| 테이블 | 핵심 필드 |
|---|---|
| `users` | id, login_id(유니크), password_hash, subject_key(유니크), auth_provider, created_at, last_login_at |
| `user_consents` | user_id, purpose, source, items, retention, is_granted, policy_version, changed_at (이력) |
| `onboarding_profiles` | user_id, life_stage, daily_minutes, nudge_slot, updated_at (+ 복수 선택은 별도 테이블) |
| `connections` | user_id, source, provider, status, connected_at, revoked_at |
| `checkins` | id, user_id, local_date, mood, support_need, status, updated_at (+ `checkin_stressors`) |
| `daily_signals` | user_id, local_date, source, metric_key, value, unit, hour_bucket(nullable), provider, collected_at |
| `fatigue_snapshots` | user_id, local_date, score, level, wool_display_kg, status, status_text, sub_text, asset_key, algorithm_version, used_sources, is_stale, calculated_at |
| `routine_catalog` | routine_id, title, subtitle, instruction, duration_sec, display_order, asset_key |
| `routine_sessions` | id, user_id, routine_id, local_date, status, finished_at (유니크: user_id+routine_id+local_date) |
| `chat_messages` | id, user_id, thread_date, role, message_type, content, payload, risk_flag, created_at |
| `recommendation_responses` | id, user_id, message_id, routine_id, response, created_at |
| `journal_entries` | id, user_id, local_date, emotion, text, created_at |
| `wool_trim_events` | id, user_id, local_date, source, visual_value, created_at |
| `notification_settings` | user_id, enabled, slot, time, updated_at |
| `help_resources` | id, situation, name, description, action_type(LINK/TEL), action_value, display_order |
| `idempotency_keys` | key, user_id, endpoint, response_hash, created_at |
| `request_audit` | request_id, event_type, outcome, created_at (PII 없음) |
| `deletion_requests` | id, user_id(익명화), scope, status, requested_at, completed_at |

- 일자 조회 테이블 `user_id + local_date` 인덱스, `chat_messages (user_id, thread_date, created_at)`.
- 삭제 범위: 위 사용자 소유 테이블 전체 + 캐시 + engine 처리물. 동의 이력 보존 여부 `[결정 필요]`.

---

## 15. 구현 순서와 통합 시나리오

| 단계 | A 작업 |
|---|---|
| 0 | 프로젝트 생성, 공통(에러·요청 ID·Clock·설정), Flyway V1, SecurityConfig 골격, OpenAPI |
| 1 | 가입·로그인·세션, 필수 약관 게이트, 선택 동의·연결 |
| 2 | engine 인터페이스·Mock, 체크인 → 계산 → 스냅샷, 오늘 상태·이번 주·7/30일 |
| 3 | 대화(날짜별·카드), 루틴(오늘·완료·잠금), 마음 정리·연출, 알림 설정, 도움 기관 |
| 4 | 삭제·탈퇴, 요청 제한, 보안 테스트, 데모 계정·데모 연동 시드 |

통합 시나리오:
1. 가입 → 약관 미동의 상태 체크인 → 403 → 동의 후 성공
2. 체크인 → 계산 → 스냅샷 → 오늘 상태·이번 주 갱신 / engine 장애 시 isStale
3. 대화 → 선제 인사 → 메시지 → 추천 카드 → 루틴 4개 완료 → 마음 정리 열림 → 저장 → woolTrimmedToday=true, level 불변
4. 대화 있는 날짜 목록 → 특정 날짜 조회
5. 수면 연결 → 철회 → 수집 거부·계산 제외 → 전체 삭제 → 재조회 불가

---

## 16. 완료 기준 · 보안 출시 게이트

- [x] 가입/로그인/로그아웃, 약관 게이트, 사용자별 접근 통제 통과
- [x] 체크인 저장/수정/건너뛰기, 동의 철회, 결측 처리
- [x] 이번 주·7/30일, 날짜별 대화, 루틴·잠금, 마음 정리·연출, 알림, 삭제 API 동작
- [x] 오류 처리·요청 제한·OpenAPI 문서·테스트
- [x] 보안 게이트: 테스트 계정 2개 접근 제어 ✅, 철회 후 재수집 차단 ✅, 전체 삭제 후 재조회 불가 ✅, 비밀값 노출 검사 ✅, 로그 마스킹 ✅, 도움 기관 번호·링크 ✅(링크는 V18, 시연 전 실제 접속 확인)
  - 점검 2026-11-13: 테스트 396개 통과, A real 모드 + B 실서버 통합 흐름 40/40, A·B 로그에서 대화·마음 정리 원문·비밀번호·세션·engine 토큰 미검출, 저장소 전체 이력 비밀값 미검출

필수 테스트: 만료 세션, CSRF 누락, 아이디 3/4/13/14자·비밀번호 9/10/12/13자 경계, enum 외 값, '안 보는 것' 필드, 초과 길이, 중복 제출, 루틴 중복 완료, 4개 미완료 마음 정리, engine 장애 isStale, 미기록일 null, 요청 제한.

---

## 17. 화면 ↔ 명세 불일치 (A는 형식만 유연하게, 값 결정은 B·디자인)

| # | 항목 | 내용 | A 처리 |
|---|---|---|---|
| 1 | 털 무게↔단계 | 화면 3.2kg + 3단계 문구 / B 초안 4단계=3.2kg | engine 결과 그대로 저장 |
| 2 | 털 무게 단위 | "이번 주 털 무게"(주간) / 명세 일별 | 응답에 둘 다 담을 수 있게 |
| 3 | 주간 기준 | 홈 월~일 / 명세 최근 7일 | 홈 월~일, 마이 최근 N일 |
| 4 | 캘린더 | 개인/회사 분리 / 명세 1개 | 소스 2개 저장 |
| 5 | 사용시간 | 시간대별 / 명세 일별 | 시간대 합계 + 일 합계 파생 |
| 6 | 루틴 시간 | 목록 1분 / 타이머 60·30·30·30초 / 대화 예시 "2분" | durationSec = 타이머 값 |
| 7 | 털 깎임 | 와이어프레임 "2단계→1단계" / 명세 연출만 | level 불변, 연출 필드 별도 |
| 8 | 상태 문구 | 홈 표 vs 루틴-1 카드 문구 다름 | 홈 표를 시드 기본값 |
| 9 | 비밀번호 상한 | 12자 | 설정값, 확대 권고 보고 |
