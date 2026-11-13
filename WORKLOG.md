# 작업 기록 (WORKLOG)

솜(SOM) 백엔드 A의 작업 흐름·결정·결과를 시간순으로 남긴다. 작업할 때마다 맨 아래에 추가한다.
- 상세 명세: `spec/A_SPEC.md`
- 설계 문서: `docs/`(PRD·ARCHITECTURE·ADR)

---

## 현황 요약

| phase | 상태 | 브랜치 | 비고 |
|---|---|---|---|
| `0-foundation` | ✅ completed | `main`에 병합 | 테스트 75개 |
| `1-auth-consent` | ✅ completed | `main`에 병합 | 10 steps + 리뷰 수정, 테스트 217개 |
| `2-checkin-state` | ✅ completed | `main`에 병합 | 11 steps, 테스트 295개 |
| `3-chat-routine` | ✅ completed | `main`에 병합 | 11 steps, 테스트 358개 |
| `4-deletion-hardening` | ✅ completed | `main`에 병합 | 7 steps, 테스트 384개 |

## 확정된 결정

| 날짜 | 결정 | 이유·출처 |
|---|---|---|
| 2026-11-12 | 테스트 DB = 로컬 PostgreSQL 17 (`som_test`) | Docker 없음. 로컬 설치. H2 우회 금지 |
| 2026-11-12 | JDK 25로 빌드, `options.release = 17` | 팀 결정 Java 17(ADR-001). PC에는 JDK 25만 있음 |
| 2026-11-12 | DB `som`/`som_test`, 앱 계정 `som_app`(최소 권한), 접속 정보는 `.env` | 비밀값은 환경변수로만(docs/DEVELOPMENT_RULES.md). `.env.example`에는 키만 |
| 2026-11-12 | 온보딩 5문항(§4)은 계획에서 제외 | `[결정 필요]`. 동의 범위(PRIVACY_COLLECTION 문구)·B 사용 여부 미정 |
| 2026-11-12 | 마이그레이션은 phase마다 필요한 테이블만 추가 | scope 최소화. 4단계에서 삭제 대상 테이블 목록 점검 필요 |
| 2026-11-12 | 브랜치 `master` → `main`으로 이름 변경 | git 기본 브랜치 설정과 맞춤 |
| 2026-11-12 | 에러 코드 `UNSUPPORTED_MEDIA_TYPE(415)` 추가 | 리뷰 지적: 클라이언트 잘못이 500으로 응답됨 |
| 2026-11-12 | 아이디 임시 규칙 = 영문 소문자+숫자 (`TODO(DECISION)`) | 명세 `[결정 필요]` → 설정값 |
| 2026-11-12 | 비밀번호 임시 규칙 = 영문+숫자 각 1자 이상 (`TODO(DECISION)`) | 명세 `[결정 필요]` → 설정값. 12자 상한은 확대 권고 |
| 2026-11-12 | 가입 후 자동 로그인 | 화면 흐름: 회원가입 → 약관 동의(세션 필요) |
| 2026-11-12 | 로그인 요청 제한은 1단계에 먼저 | 로그인 API가 생기는 시점부터 대입 공격 차단 |
| 2026-11-12 | 철회·수신은 `(userId, source)` advisory 잠금으로 직렬화 | 리뷰 지적: 동시 실행 시 미동의 데이터가 남는 경쟁 조건 |
| 2026-11-12 | engine 호출은 `EngineGateway`(타임아웃 2s, 최대 2회) 경유, 실패는 마지막 값 + `isStale`/503 | A_SPEC §12, ARCHITECTURE |
| 2026-11-12 | 스냅샷 재계산은 체크인·수신·철회 커밋 후(AFTER_COMMIT) | 커밋 전 데이터·미동의 데이터가 engine에 들어가지 않게 |
| 2026-11-12 | ADR-001 개정: B는 별도 FastAPI 서비스, A가 HTTP(JSON)로 호출 | 팀 합의. 계약 문서 "솜 A↔B engine API 계약"(팀 공유 문서) |
| 2026-11-12 | 가입(IP 10/1h)·수신(사용자 30/1m) 요청 제한 `[제안]` (`TODO(DECISION)`) | 리뷰 지적: ADR-008 수집 제한 누락, 가입 해시 DoS |

## 남은 할 일·확인 필요

- [ ] (선택) `REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;` — `som_app`이 관리용 DB에 접속 가능
- [x] `spec/A_SPEC.md` §13 에러 목록·구현 현황 반영(§13-1, 2026-11-12)
- [ ] 아이디/비밀번호 찾기 "준비 중" 에러 경로 결정(§3, 현재 404)
- [ ] 온보딩 5문항 포함 여부 결정(§4, 미구현)
- [ ] 세션 쿠키 `secure=true`: Safari 등에서 로컬 http 연동 시 확인
- [ ] 로그인 제한 IP 판별: 프론트 프록시 구성 확정 후 신뢰 프록시 설정 (`TODO(DECISION)`)
- [ ] 연동 소스 purpose·retention·걸음 수 분류 확정 (현재 `"TBD"` / `TODO(DECISION)`)
- [ ] 철회 시 보유 데이터 삭제 정책 확정 (임시: 즉시 삭제)
- [x] GitHub 원격 저장소 연결
- [x] 컨트롤러를 `controller/` 하위로 정리 (2026-11-12 완료)
- [ ] 가입·수신 요청 제한 값 확정 (현재 `[제안]`)
- [ ] 홈 말풍선 문구(`som.state.bubble-text`)는 `[제안]` 임시 문구 — B·디자인과 확정
- [x] A↔B HTTP 연동(B가 `BE_AB`로 구현, A가 보완, 2026-11-13)
- [x] B 서버를 실제로 띄워 A real 모드로 전 흐름 확인(2026-11-13, 40/40)
- [ ] B `requirements.txt`에 `tzdata` 추가 요청(Windows Python은 시간대 DB가 없어 피로 계산이 500)
- [ ] 연동 지표 맞추기: B는 분 단위 지표만 쓴다(걸음 수·잠든/깬 시각·일정 수는 B로 보내지 않음). 야간 휴대폰 사용·일정 사이 여유 시간은 A가 아직 만들지 않음
- [ ] 마음 정리 저장 트랜잭션 안에서 털 깎임 B 호출(최대 2초×2회)이 일어난다 → 트랜잭션 밖으로 뺄지 검토
- [x] `backend-b/`(B 서버 코드) 위치: `main`의 `backend-b/`로 일원화, `BE_B` 브랜치는 쓰지 않음(2026-11-13 팀 결정)
- [x] 4단계 삭제 대상 전체 반영(`UserDataEraserTest` 안전망: user_id 컬럼이 있는 테이블은 모두 삭제 목록에 있어야 통과)
- [ ] 도움 기관 링크 URL 2건 확인 후 마이그레이션으로 넣기(현재 NULL)
- [ ] 삭제 ENGINE_PENDING 재시도 방식 결정(현재 기록만)
- [ ] 탈퇴 시 동의 이력 보존 여부 결정(현재 삭제)
- [x] real 모드 B HTTP 클라이언트 구현(`som.engine.mode=real`, 2026-11-13)
- [ ] (리뷰 2) 주간 요약 engine 결과 캐시: B 실서버의 응답 속도를 보고 결정(ARCHITECTURE "캐시하고 무효화"와 아직 다름)
- [ ] (리뷰 3) 동의 철회 시 지난 날짜 스냅샷 처리 정책 결정: 지금은 오늘만 다시 계산하고, 과거 값은 남는다(`[결정 필요]` 철회 시 보유 데이터 삭제 정책과 함께)
- [ ] 데모 시연: `SPRING_PROFILES_ACTIVE=demo`, `SOM_DEMO_PASSWORD` 환경변수 설정 후 실행
- [ ] 홈 말풍선·대화 Mock 문구, 루틴 부제(플레이스홀더) 디자인 확인
- [x] 4단계: 삭제·탈퇴 API 경로를 `som.consent.gate.exempt-paths`에 추가

---

## 기록

### 2026-11-12 — 탐색·환경 준비
- `docs/`(PRD·ARCHITECTURE·ADR)와 `spec/A_SPEC.md`를 읽고 phase 0~4 분할을 제안했다.
- 환경 점검 결과: JDK 25만 있음, gradle·Postgres·Docker 없음, `python3` 있음.
- 로컬에 PostgreSQL 17.11을 설치했다. `som`, `som_test`, `som_app`을 생성했다.
  - 권한 검증 결과: 슈퍼유저·DB 생성 불가, `public` 스키마 생성만 가능.
  - 비밀번호가 담긴 임시 SQL은 삭제했다.

### 2026-11-12 — phase `0-foundation` 실행 (step 0~6)
- 7 steps 모두 완료
- 산출물:
  - Spring Boot 3.5.16 / Gradle 9.8.2 / springdoc 2.10.0
  - `common/time`: `Clock` 빈, `ServiceDates`
  - `common/web`: `RequestIdFilter`, `OpenApiConfig`
  - `common/error`: 공통 에러 형식
  - `auth`: `SecurityConfig`(세션+CSRF SPA), `GET /api/v1/auth/csrf`
  - Flyway `V1__init.sql`(`users`)
- 참고:
  - start.spring.io 기본값이 Boot 4.x여서 step 0이 3.5.16으로 다시 작성했다.

### 2026-11-12 — 리뷰·수정·병합
- `/review`: 아키텍처·스택·CRITICAL·빌드 ✅
- 지적 1건: Spring 표준 예외(415, 헤더 누락)가 500으로 응답됨
  - TDD로 수정: 실패 테스트 3개 → 구현 → 75개 통과. 커밋 `0b1ac18`
- 참고 사항: `/auth/**` 전체 허용 범위, OpenAPI 공개 경로에 401 표기, 쿠키 secure → 1단계 step에 반영
- `master` → `main` 이름 변경, `feat-0-foundation` 병합(`d755324`)

### 2026-11-12 — phase `1-auth-consent` 설계·실행 시작
- step 10개: strict-json → user-domain → login-session → signup → login-rate-limit → required-consent → me → consent-gate → connection → signal-ingest
- step 파일을 검토한 뒤 실행했다.

### 2026-11-12 — phase `1-auth-consent` step 0 (strict-json)
- `application.yml`: `spring.jackson.deserialization.fail-on-unknown-properties: true`
- `GlobalExceptionHandler`: `UnrecognizedPropertyException` → 400 `VALIDATION_ERROR` `"{필드}: 허용되지 않는 필드입니다"` (값은 응답에 넣지 않음)
- TDD: 테스트 3건 추가(`userId`·`title` 거부와 값 미노출, 정상 요청 회귀). 먼저 2건 실패를 확인하고 구현했다.
- `./gradlew build`: 78개 통과
  - 구현 직후 첫 전체 빌드에서 `SecurityConfigTest` 4건이 실패했다(CSRF 헤더가 기본값 `X-CSRF-TOKEN`으로 나옴).
  - 단독 실행과 이후 전체 빌드 4회(`--rerun-tasks`)는 모두 통과했다. 재현되지 않아 원인을 찾지 못했다. 다시 나오면 조사가 필요하다.

### 2026-11-12 — phase `1-auth-consent` step 1 실행 중단·재실행
- step 1 작업이 개발 환경 문제(메모리 부족)로 두 번 중단돼 다시 진행했다. 코드 문제는 아니었다.
- 중단 때 남은 미커밋 구현은 검토 후 이어서 썼다.

### 2026-11-12 — phase `1-auth-consent` step 1 `user-domain` 완료
- 중단 때 남은 미커밋 구현을 검토했다. 명세와 맞아서 그대로 썼다(테스트는 `b80d7ab`에 먼저 커밋돼 있었다).
- `./gradlew build --rerun-tasks`: 97개 통과, 실패 0
- step 1 상태를 `completed`로 바꿨다. step 2 `login-session`부터 이어서 하면 된다.

### 2026-11-12 — phase `1-auth-consent` step 2 `login-session` 완료
- JSON 로그인·로그아웃, `SessionUser(userId)` 세션 principal, `@CurrentUserId` 인자 해석기, `INVALID_CREDENTIALS` 추가
- `/api/v1/auth/**` 전체 허용 → login·signup·logout·csrf 명시 목록으로 축소, OpenAPI 공개 경로에서 공통 401 제거
- 이전에 재현하지 못한 `SecurityConfigTest` CSRF 실패 원인 확인: 같은 캐시 컨텍스트의 다른 테스트가 spring-security-test `csrf()`를 쓰면 CSRF 저장소가 세션 저장소로 영구 교체된다 → `@DirtiesContext(BEFORE_CLASS)`로 수정
- `./gradlew build --rerun-tasks` 2회: 112개 통과, 실패 0
- step 1 `user-domain` ✅ completed 11:56 (커밋 `9f8ac4d`, `b4da144`): `User` 엔티티(`toString`에서 `passwordHash` 제외), `UserRepository`, `CredentialPolicy`(yml 규칙, 메시지에 입력값 미노출), `PasswordConfig`

### 2026-11-12 — phase `1-auth-consent` step 3 `signup` 완료
- `POST /api/v1/auth/signup`: 성공하면 201 `{loginId}`, 바로 로그인 상태. 세션 수립 코드는 로그인과 같은 `AuthController.establishSession`을 함께 쓴다(로그인 코드에서 뽑아냄).
- 응답 400·409: 규칙 위반과 비밀번호 확인 불일치는 400 `VALIDATION_ERROR`(입력값 미노출). 중복 아이디는 사전 검사에서 걸려도, 동시 가입으로 유니크 제약에 걸려도 409 `CONFLICT`.
- 테스트 먼저 작성(레드 23건 확인 → 그린): `SignupTest`, `SignupRaceTest`(`@MockitoSpyBean`으로 사전 검사 우회)
- `./gradlew build`: 136개 통과, 실패 0
- 참고: 동시 가입 시 Hibernate `SqlExceptionHelper`가 PostgreSQL 오류 상세(`Key (login_id)=(...)`)를 ERROR 로그에 남긴다. 비밀번호가 아니라 아이디이고 드문 경우라 그대로 뒀다. 막으려면 별도 결정이 필요하다.

### 2026-11-12 — phase `1-auth-consent` step 4 `login-rate-limit` 완료
- Bucket4j `bucket4j_jdk17-core:8.22.0` 추가, `com.som.common.ratelimit.RateLimiter`(키별 버킷, `max-tracked-keys` 상한 LRU, Clock 기반 시간)
- 로그인만 제한: 인증 전에 IP(`remoteAddr`)·loginId 버킷 둘 다 소비, 부족하면 429 `RATE_LIMITED` + `Retry-After`. 가입·기타 API는 제한 없음(4단계)
- 값은 yml에만 둠. 20/1m·5/5m는 [제안] 값이라 `TODO(DECISION)`. X-Forwarded-For 신뢰도 `TODO(DECISION)`(프록시 구성 미확정)
- 테스트 먼저 작성(컴파일 레드 확인 → 그린): `RateLimiterTest` 4건, `LoginRateLimitTest` 6건. test 프로필은 기존 로그인 테스트가 걸리지 않게 값을 크게 둠
- `./gradlew build`: 146개 통과, 실패 0
- 참고: 키가 상한을 넘으면 오래된 버킷이 지워져 그 키의 카운트가 초기화된다(IP당 제한이 있어 한 IP가 빠르게 밀어낼 수는 없다). 로그인 429는 아직 OpenAPI 문서에 없다(4단계 공통 정리 때 추가 권장)

### 2026-11-12 — phase `1-auth-consent` step 5 `required-consent` 완료
- `V2__user_consents.sql`: 동의 이력 테이블(FK `users`, 인덱스 `(user_id, consent_type, source, changed_at)`). 선택 연동 동의(step 8)도 같은 테이블에 쌓는다
- `GET·PATCH /api/v1/me/consents`: 상태가 바뀔 때만 새 행 INSERT(서버 설정 정책 버전 + Clock 시각). UPDATE·DELETE 코드 없음(`@Immutable` 엔티티, 저장소가 delete를 노출하지 않음)
- 다른 서비스가 쓸 `RequiredConsentChecker.isCompleted(userId)` 추가. 약관 게이트(403)는 step 7 범위라 만들지 않았다
- 정책 버전 `v1`은 [제안] 값이라 `TODO(DECISION)`
- 테스트를 먼저 작성했다(컴파일 실패로 레드 확인 → 그린). `RequiredConsentTest` 13건, `MigrationTest` 2건 추가
- `./gradlew build`: 161개 통과, 실패 0
- 명세에 없어서 내가 정한 것: 이력이 없을 때 `policyVersion`·`changedAt`은 null. 이력이 없는데 `granted=false`가 오면 행을 만들지 않는다. 한 요청에 같은 key가 두 번 오면 400. 빈 목록도 400
- 참고: `requiredCompleted`는 정책 버전을 따지지 않는다. 정책이 v2로 바뀌었을 때 v1 동의를 무효로 볼지는 아직 정해지지 않았다

### 2026-11-12 — phase `1-auth-consent` step 6 `me` 완료
- `GET /api/v1/me`: `{loginId, requiredConsentsCompleted, createdAt}`. 프론트가 로그인 직후 약관 화면/홈 분기에 쓴다
- `com.som.account` controller → service → `UserRepository`·`RequiredConsentChecker` 순으로 호출(레이어 규칙 준수). 사용자는 `@CurrentUserId`로만 찾는다
- 세션은 있는데 사용자 행이 없으면(삭제됨) 세션을 무효화하고 401 `UNAUTHORIZED`
- 응답에 내부 id·`subjectKey`·`passwordHash`·`authProvider` 없음. 약관 게이트 예외 목록 등록은 step 7에서 한다
- 테스트를 먼저 작성했다(레드 확인 → 그린). `MeTest` 6건(다른 계정 격리, `?userId=` 무시, 미인증 401, 삭제된 사용자 401 + 세션 무효화)
- `./gradlew build`: 167개 통과, 실패 0

### 2026-11-12 — phase `1-auth-consent` step 7 `consent-gate` 완료
- 필수 약관 3개를 다 동의하지 않은 로그인 사용자가 서비스 API(`/api/**`)를 부르면 403 `CONSENT_REQUIRED`(공통 에러 형식 + `requestId`)
- 예외 경로는 `application.yml`의 `som.consent.gate.exempt-paths`에만 있다: `/api/v1/auth/**`, `/api/v1/me`, `/api/v1/me/consents`. 설정이 없으면 서버가 뜨지 않는다
- `RequiredConsentGateFilter`는 Security 필터 체인에서 `AuthorizationFilter` 다음에 실행된다. 미인증은 그 전에 401로 끝난다. 동의 상태는 요청마다 DB에서 조회(캐시 없음 → 철회 즉시 반영). 선택 연동 동의는 보지 않는다
- 테스트를 먼저 작성했다(4건 레드 확인 → 그린). `RequiredConsentGateTest` 6건 + 테스트 전용 `/api/v1/test-service/ping`(`src/test`에만)
- 기존 테스트는 고칠 필요가 없었다: 기존 `/api/**` 호출은 모두 예외 경로였고, 나머지 테스트 엔드포인트는 `/test/**`라 게이트 대상이 아니다
- `./gradlew build`: 173개 통과, 실패 0
- 4단계 할 일: 삭제·탈퇴 API(`DELETE /api/v1/me` 등)를 `exempt-paths`에 추가. 참고로 `/api/v1/me`는 경로 기준 예외라 같은 경로의 DELETE도 이미 통과한다

### 2026-11-12 — phase `1-auth-consent` step 8 `connection` 완료
- 연동 소스 5종(`SLEEP`, `STEPS`, `SCREEN_TIME`, `CALENDAR_PERSONAL`, `CALENDAR_WORK`)의 선택 동의 + 연결 상태. 실제 기기 연동은 없고 provider는 `DEMO`(ADR-006)
- `GET /api/v1/me/connections`: 5개 소스 모두 반환(이력 없으면 `NOT_CONNECTED`). `POST`·`DELETE /api/v1/me/connections/{source}`: 연결·철회 후 해당 소스 항목 200
- 연결: `user_consents`에 `DATA_SOURCE` 행 INSERT(고지 내용 items·purpose·retention·policy_version은 `som.consent.sources` 서버 설정값) + `connections`를 `CONNECTED`. 철회: `is_granted=false` 행 INSERT + `SourceRevokedEvent` 발행 + `REVOKED`. 모두 한 트랜잭션
- 같은 상태로 다시 요청하면 이력·이벤트를 만들지 않는다. 이력이 없는데 철회하면 아무것도 쓰지 않고 `NOT_CONNECTED`
- 바디에 필드(`items`·`retention`·`userId` 등)가 오면 400, 모르는 source(`LOCATION`, 소문자 `sleep`)도 400 `VALIDATION_ERROR`. 필수 약관 게이트 대상(미완료 403 `CONSENT_REQUIRED`)
- 다른 기능용 `SourceConsentChecker.isConsented`·`consentedSources`(최신 동의 이력 기준)
- 미정 값은 모두 `application.yml` + `TODO(DECISION)`: 보관 기간, STEPS 등 purpose 문구, 걸음 수 건강정보 분류, 정책 버전. Java 코드에는 "TBD"·보관 기간 숫자 없음. '안 보는 것' 필드·소스 없음
- 테스트를 먼저 작성했다. `ConnectionTest` 12건(2계정 격리, 403, 401, CSRF 포함) + `MigrationTest` 4건(V3 컬럼·유니크·FK·status CHECK)
- `./gradlew build`: 189개 통과, 실패 0
- 명세에 없어서 내가 정한 것: `connectedAt`은 `CONNECTED`일 때만 값이 있고 `REVOKED`·`NOT_CONNECTED`는 null. 철회 행에도 그 시점 고지 내용을 같이 기록. DB `status`는 `CONNECTED`/`REVOKED`만 허용(CHECK)
- 참고: 같은 사용자가 처음 연결을 동시에 두 번 보내면 유니크 제약 위반으로 500이 날 수 있다(현재 처리 없음)
- step 9 할 일: `SourceRevokedEvent` 리스너로 해당 소스 signals 삭제. 연동 수집 API는 `SourceConsentChecker`로 미동의 소스를 거부

### 2026-11-12 — phase `1-auth-consent` step 9 `signal-ingest` 완료
- `POST /api/v1/signals`(데모·개발용, provider `DEMO`): `{source, localDate, entries:[{metric, value|valueAt, hourBucket?}]}` → 200 `{source, localDate, stored}`
- 허용 지표는 `Metric` enum 7개뿐: `SLEEP_ONSET`·`SLEEP_WAKE`(시각), `SLEEP_MINUTES`(서버가 파생, 클라이언트가 보내면 400), `STEPS_DAILY`(count), `SCREEN_TIME_MINUTES`(min, `hourBucket` 0~23 필수), `CALENDAR_EVENT_COUNT`(count)·`CALENDAR_BUSY_MINUTES`(min, 캘린더 2종 공용). 단위는 서버가 정한다
- 동의 확인이 먼저다. 미연결 소스는 403 `SOURCE_NOT_CONSENTED`(새 ErrorCode)이고 값을 읽지도 쓰지도 않는다. 그 다음 요청 전체를 검증하며 하나라도 틀리면 400, 부분 저장 없음
- 같은 키는 덮어쓴다(`ON CONFLICT ON CONSTRAINT uk_daily_signals_key`, `UNIQUE NULLS NOT DISTINCT`). 결측은 행을 만들지 않는다
- 철회 연결: `SignalRevokeListener`가 `SourceRevokedEvent`(철회 트랜잭션 안)에서 해당 사용자·소스 `daily_signals` 삭제. `som.consent.delete-signals-on-revoke: true`는 `TODO(DECISION)` 임시값
- `som.signals.ingest-enabled=false`면 컨트롤러를 등록하지 않아 404 `NOT_FOUND`(철회 시 삭제는 계속 동작)
- 명세에 없어서 내가 정한 것: 한 요청 안의 중복 키는 400. 깬 시각이 잠든 시각보다 늦지 않으면 400. 잠든·깬 시각 중 하나만 오면 저장된 다른 값과 합쳐 수면 분을 다시 파생(둘 다 없으면 수면 분 행 없음). `stored`에는 파생 행도 포함. value는 0 이상·소수 2자리·정수부 10자리 이하(NUMERIC(12,2) 초과로 500 나는 것 방지)
- 테스트 인프라: 새 테스트 컨텍스트 2개가 추가되면서 som_app 역할의 접속 상한(20)을 넘었다. `src/test/resources/spring.properties`에 `spring.test.context.cache.maxSize=6`을 넣어 오래된 컨텍스트를 닫게 했다
- 테스트를 먼저 작성했다. `SignalIngestTest` 19건(통합 시나리오 5, 2계정 격리, 403/401/CSRF), `SignalIngestDisabledTest` 1건, `MigrationTest` 4건. `GlobalExceptionHandlerTest`의 ErrorCode 개수를 14에서 15로 수정
- `./gradlew build`: 213개 통과, 실패 0
- 남은 일: 수집 API 요청 제한(ADR-008)은 이번 step 범위가 아니라 넣지 않았다. `delete-signals-on-revoke=false` 분기는 테스트가 없다
- step 2~9 모두 completed (12:03~12:34). phase 상태 `completed`(`8654a72`).
- 검증 결과:
  - `./gradlew clean build --no-daemon` ✅ 성공. 테스트 213개 통과, 실패 0
  - Clock 없이 `now()`를 호출하는 곳 없음
  - 민감값 로그 없음
  - `.env` 미추적, git 기록에 비밀번호 없음
  - `TODO(DECISION)` 17곳
  - 마이그레이션 V1~V4
- 다음: `/review`로 리뷰한 뒤 `main`에 병합

### 2026-11-12 — phase `1-auth-consent` 리뷰
- `/review` 결과: 기술 스택·테스트·빌드 ✅. 다른 계정 접근 테스트는 Me·Login·Signup·Consent·Gate·Connection·Signal에 있다.
- 지적 사항:
  1. (중) 동의 철회와 데이터 수신이 동시에 실행되면, 철회된 소스의 데이터가 남을 수 있다. 수신 쪽 동의 확인이 이미 끝난 뒤 철회 쪽 삭제가 먼저 커밋되는 경우다. CRITICAL "미동의 데이터 저장 금지"에 가깝다.
  2. (중) `POST /api/v1/signals`에 요청 제한이 없다. ADR-008은 수집 API도 제한 대상으로 정한다.
  3. (하) 가입 API에 요청 제한이 없다. Argon2 해시는 CPU 비용이 크다. ADR-008 목록에는 없는 항목이다.
  4. (하) 컨트롤러·설정 클래스가 기능 패키지 최상위에 있다(`auth/`·`consent/`·`connection/`). `account`는 `controller/` 하위에 둬서 서로 다르다.
- 결정: 1·2·3을 수정하고 4는 별도 정리
- TDD로 수정했다. 실패 테스트를 먼저 확인했다(경쟁 조건 1건, 요청 제한 3건).
  - `c1d7359` fix(connection): 수신과 철회가 같은 잠금을 쓴다(`pg_advisory_xact_lock`). `DailySignalRepository.lockSource` 추가. 수신은 동의 확인 전에 잠금을 잡는다.
  - `a1b1834` fix(ratelimit): `som.rate-limit.signup.per-ip`, `som.rate-limit.signals.per-user` 추가
- `./gradlew clean build` ✅ 테스트 217개 통과
- `feat-1-auth-consent`를 `main`에 병합했다.

### 2026-11-12 — 컨트롤러 패키지 정리 (리뷰 지적 4)
- `AuthController`·`CsrfController` → `auth/controller/`, `CsrfTokenResponse` → `auth/dto/`
- `ConsentController` → `consent/controller/`, `ConnectionController`·`SignalController` → `connection/controller/`
- 설정 클래스(`*Properties`, `SecurityConfig`, 필터 등)는 레이어가 아니어서 기능 패키지 최상위에 그대로 둔다.
- 패키지 선언과 import만 바꿨다(동작 변경 없음). `./gradlew clean build` ✅ 테스트 217개 통과
- `refactor-controller-packages` 브랜치를 `main`에 병합했다.

### 2026-11-12 — phase `2-checkin-state` 설계
- 남은 phase 2·3·4(명세 §15 단계 2~4)를 이어서 진행한다.
- step 11개: fatigue-engine → idempotency → checkin-domain → checkin-api → engine-gateway → fatigue-snapshot → state-today → insight-week → insight-range → recalc-triggers → phase2-scenarios
- `[결정 필요]` 기본값(모두 설정값 + `TODO(DECISION)`):
  - Q2 최대 1개, 재제출은 갱신, 저장은 마지막 문항에서 1회
  - 말풍선은 단계별 고정 문구, 털 무게는 주간·일별 둘 다 응답
- 마이그레이션: V5 `idempotency_keys`, V6 `checkins`, V7 `fatigue_snapshots`

### 2026-11-12 — phase `2-checkin-state` 실행 (step 0~10)
- `feat-2-checkin-state` 브랜치에서 step 순서대로 구현했다.
- 산출물:
  - `engine.fatigue`·`engine.insight`: 인터페이스·DTO·Mock(결정적, 입력이 없으면 `INSUFFICIENT_DATA`)
  - `common.idempotency`: `Idempotency-Key`. V5
  - `common.lock.AdvisoryLock`
  - `checkin`: `POST`·`GET /api/v1/checkins`. V6
  - `state`: `EngineGateway`, `SnapshotService`, `GET /api/v1/state/today`, `/insights/week`, `/insights?days=7|30`. V7
  - 재계산 트리거: 체크인·수신·철회 커밋 후
  - 통합 시나리오 1·2·5, 아키텍처 규칙 테스트
- 도중에 고친 문제:
  - 멱등 재생 시 시각 오프셋이 UTC로 바뀌는 문제
  - `level SMALLINT` 매핑
  - 기존 테스트 cleanup에 스냅샷 삭제 추가
- `./gradlew clean build` ✅ 테스트 295개 통과. `main`에 병합

### 2026-11-12 — phase `3-chat-routine` 설계
- step 11개: help-resources → routine-catalog → routine-engine → routine-sessions → journal → chat-engine → chat-messages → chat-greeting → recommendation-response → notification-settings → phase3-scenarios
- `[결정 필요]` 기본값(모두 설정값 + `TODO(DECISION)`):
  - 이행 기준: 1개 이상 완료
  - 마음 정리: 4개 모두 완료해야 열림, 빈 기록 허용, 최대 1000자
  - 대화: 최대 500자. 사용자 원문은 engine에 보내지 않음(기본 false)
  - 카드 응답: 최초 응답 고정
- 루틴 추천 engine이 실패하면 마지막 저장 추천 + `isStale`, 그것도 없으면 503(A가 추천을 만들지 않는다)
- 도움 기관 링크 URL은 넣지 않음(명세 "URL 추측 금지"). 전화번호 1350·109만 넣었다.

### 2026-11-12 — phase `3-chat-routine` step 0~2 완료, B 연동 방식 논의
- step 0 help-resources, step 1 routine-catalog, step 2 routine-engine ✅ (테스트 307개 통과)
- B가 FastAPI 사용을 제안해 연동 방식을 논의하느라 step 3 전에 잠시 멈췄다.
- 결론: A는 Spring 유지, B는 별도 FastAPI 서비스. 현재 구조(`engine` 인터페이스 + `som.engine.mode=mock|real` + `EngineGateway`)는 B가 HTTP 서비스여도 A 쪽 수정이 적다.

### 2026-11-12 — B용 engine API 계약 문서 작성
- 팀 공유 문서 "솜 A↔B engine API 계약"(저장소 밖)
- 내용:
  - 공통 규칙: Bearer 토큰, 타임아웃 2s, 재시도 2회, 결측은 null, 보내지 않는 데이터
  - API 4종: 피로 계산, 기간 요약, 루틴 추천, 대화 초안
  - 삭제 API 제안, B와 정할 것 9개

### 2026-11-12 — ADR-001 개정 (B = 별도 FastAPI 서비스)
- 팀 합의 완료. 계약 문서의 "B와 정할 것" 1번을 완료로 표시했다.
- `docs/ADR.md` ADR-001, `docs/ARCHITECTURE.md` engine 설명, 개발 규칙 문서의 소개 문장을 고쳤다.
- A 코드 변경은 없다. real 모드 HTTP 클라이언트는 계약이 확정된 뒤 만든다.

### 2026-11-12 — phase `3-chat-routine` 실행 (step 3~10)
- `feat-3-chat-routine` 브랜치에서 step 순서대로 구현했다.
- 산출물:
  - 루틴 실행 기록(V11): 중복 완료 1행, COMPLETED 최종. 오늘 화면, 주간 이행, 마음 정리 잠금
  - 마음 정리·털 깎임(V12): `JOURNAL_LOCKED` 403 추가, engine 전달 없음, `woolTrimmedToday` 연결
  - 대화 engine 계약·Mock, 대화 저장·조회(V13): 커서 페이지, 월별 날짜, 분당·일일 제한, 원문 미전송 기본값, 위기 시 도움 기관, 고정 문구 대체
  - 하루 1회 선제 인사: 대화 첫 메시지이자 홈 말풍선
  - 추천 카드 응답(V14): 최초 응답 고정
  - 알림 설정(V15): PATCH는 보낸 필드만 반영
  - 통합 시나리오 3·4
- 도중에 고친 문제:
  - 추천 서비스 ↔ 실행 기록 서비스 순환 의존 → `SessionRecentCompletions`로 분리
  - 알림 PATCH에서 "필드 없음"과 "null"을 구분하지 못해 `ObjectNode`로 변경
- `./gradlew clean build` ✅ 테스트 358개 통과. `main`에 병합

### 2026-11-12 — phase `4-deletion-hardening` 설계
- step 7개: user-data-eraser → engine-eraser → data-deletion-api → account-withdrawal → security-hardening → demo-seed → phase4-scenarios
- `[결정 필요]` 기본값:
  - 기록 삭제: 계정·동의 이력·연결·알림 설정은 남긴다
  - 탈퇴: 모두 삭제(동의 이력 포함, `TODO(DECISION)`)
  - 삭제 요청 제한: 3회/1h `[제안]`
- `deletion_requests`는 user_id 없이 subjectKey 해시로 익명화한다.
- 데모 계정은 `demo` 프로필에서만 만들고, 비밀번호는 `SOM_DEMO_PASSWORD` 환경변수에서만 읽는다.

### 2026-11-12 — phase `4-deletion-hardening` 실행 (step 0~6)
- 산출물:
  - `UserDataEraser`: DATA·ACCOUNT 범위, FK 순서. user_id 컬럼 테이블을 모두 덮는지 검사하는 안전망 테스트 포함
  - `deletion_requests`(V16): subjectKey 해시로 익명화
  - `EngineDataEraser` 계약·Mock(B `DELETE /v1/subjects/{subjectKey}`에 대응)
  - 기록 삭제(`DELETE /api/v1/me/data`), 처리 상태 조회, 탈퇴(`DELETE /api/v1/me`). 모두 `{"confirm":true}` 필수, 요청 제한, 게이트 예외
  - 탈퇴한 사용자의 다른 세션은 모든 API에서 401(게이트 필터에서 사용자 존재 확인)
  - 보안 게이트 테스트: 만료 세션, 로그 마스킹, 비밀값, 도움 기관 형식, 두 계정 접근 제어 매트릭스
  - 데모 시드(`demo` 프로필, `SOM_DEMO_PASSWORD`)
  - 통합 시나리오 5
- API 계약 문서의 대화·삭제 상태를 "Mock 동작"으로 갱신했다.
- `./gradlew clean build` ✅ 테스트 384개 통과. `TODO(DECISION)` 40곳. `main`에 병합
- 명세 §15의 단계 0~4를 모두 마쳤다.

### 2026-11-12 — phase 2~4 리뷰 (`212ba3d..HEAD`, `src/main` 160개 파일)
- 결과: 기술 스택·테스트·빌드 ✅(384개). 아키텍처는 대체로 ✅. CRITICAL 1건이 걸린다.
- 지적 사항:
  1. (중, CRITICAL 관련) 스냅샷 재계산 결과가 늦게 도착한 쪽으로 덮인다.
     - 경로: 체크인·수신·철회·삭제가 겹쳐 재계산 두 개가 돌 때, 먼저 시작해 이전 입력(철회 전 수면 등)으로 계산한 결과가 나중에 저장될 수 있다. 그러면 철회·삭제된 데이터로 만든 값이 오늘 상태에 남는다.
     - 저장할 때 입력이 바뀌었는지 다시 확인하지 않는다.
  2. (하, ARCHITECTURE) 문서는 "재사용 가능한 engine 결과(주간 요약)는 캐시하고 무효화"라고 하지만 캐시가 없다. 조회할 때마다 engine을 호출한다.
  3. (하, 정책) 동의를 철회해도 지난 날짜 스냅샷에는 철회한 소스로 계산한 값과 `usedSources`가 남는다. 다시 계산하는 것은 오늘만이다.
  4. (하, 문서) `demo/` 패키지가 ARCHITECTURE 디렉토리 구조에 없다.
- 결정: 1·4 수정, 2·3은 남은 할 일로
- TDD로 수정했다. `SnapshotStaleWriteTest`로 철회 전 계산이 늦게 저장돼 SLEEP이 남는 것을 재현한 뒤 고쳤다.
  - 저장 직전 잠금 안에서 입력을 다시 읽어 계산에 쓴 입력과 비교한다.
  - 다르면 그 결과를 버리고 다시 계산한다(최대 3회).
- `docs/ARCHITECTURE.md` 디렉토리 구조에 `demo/` 추가
- `./gradlew clean build` ✅ 테스트 385개 통과. `main`에 병합

### 2026-11-12 — 명세 문서 맞추기 (`spec/A_SPEC.md` §13)
- 구현한 API 32개를 §13 표와 대조했다. 온보딩을 빼고 모든 경로가 표와 같았다.
- §13-1 "구현 현황"을 추가했다.
  - 에러 코드 16개(HTTP·발생 조건)
  - Idempotency-Key·커서 적용 범위, 삭제 확인 바디, 게이트 예외 경로, 요청 제한 대상
  - 422는 쓰지 않음(검증 오류는 모두 400)
- 명세와 다른 점 2건은 남은 할 일에 추가했다.
  - 아이디/비밀번호 찾기: 명세는 "준비 중" 에러를 말하지만 경로가 없어 404
  - 온보딩: 미구현
- 공통 에러 형식 줄의 상태 코드 목록을 실제와 맞췄다(422 제거, 415 추가).

### 2026-11-12 — GitHub 업로드
- 원격 저장소 `origin` = `github.com/2026Ganzi-Thon/2026_GANZITHON_TEAM_01_BE`, `main` push
- 로컬 개발 도구 파일은 저장소에 포함하지 않는다(`.git/info/exclude`).

### 2026-11-12 — 개발 규칙 문서 정리
- 팀이 같이 지킬 개발 규칙(꼭 지킬 규칙 8개·구현 규칙·개발 프로세스·명령어)을 `docs/DEVELOPMENT_RULES.md`로 정리했다.
- 코드 주석·WORKLOG의 규칙 출처를 이 문서로 바꿨다.

### 2026-11-12 — engine API 계약 초안 전달 준비
- A↔B engine API 계약 초안을 마크다운 파일(`engine-api-contract.md`, 저장소 밖)로 정리해 B에 전달하기로 했다.
  - 내용: 공통 규칙, 엔드포인트 5개, 피로 계산·기간 요약·루틴 추천·대화의 요청·응답 JSON, 데이터 삭제 제안, B와 정할 것
- 이후 계약은 B가 주도한다. B는 초안 형식(필드 이름·결측 null 규칙)을 출발점으로 FastAPI 서버를 만들고, 자동 생성되는 `/openapi.json`을 기준 문서로 공유한다.
- B가 형식을 바꾸면 A의 `engine` DTO·Mock도 같이 고친다(필드 추가는 영향 적음, 이름 변경·삭제는 수정 필요).

### 2026-11-13 — A↔B HTTP 연동 병합·보완
- B가 `BE_AB` 브랜치(`main` 기준)에 A↔B HTTP 연동을 올렸다 → `main`에 병합
  - `HttpEngine`(engine 인터페이스 6개 HTTP 구현, `som.engine.mode=real`), B 서버 코드 `backend-b/`
  - 털 깎임 연출을 B에 묻기(`WoolTrimEngine`), 개인 기준용 최근 14일 신호 전송, 변화 근거 저장(`V17`)
  - 대화는 타임아웃 15초·재시도 없음, real 모드는 토큰 필수
  - 같은 작업을 A도 해 두었지만(push 전) B 쪽이 범위가 넓어 B 것을 기준으로 삼고 A 것은 버렸다.
- A 보완:
  - HTTP 구성을 `EngineHttpConfig`로 분리(테스트 가능하게). 주소·토큰 검증은 B 코드 그대로
  - engine 전용 ObjectMapper: 응답의 모르는 필드 무시(전역 설정은 거부 → B가 필드만 늘려도 실패했음), null 필드 미전송
  - 2xx 외 응답은 `EngineHttpException`(상태 코드만, 응답 본문 제외)
  - 대화 원문 전송 설정의 `TODO(DECISION)` 주석 복원(기본 false 유지)
  - 테스트 추가: `HttpEngineTest`(6개 호출의 요청·응답 JSON, 인증 헤더, 오류), `EngineHttpConfigTest`(real/mock 빈, 잘못된 주소·빈 토큰이면 시작 실패)
- `./gradlew clean build` ✅ 테스트 396개 통과(신규 11개)

### 2026-11-13 — B 코드 위치 결정
- B 서버 코드(Python FastAPI)는 이 저장소 `main`의 `backend-b/`에서 관리한다. A·B 연동 변경을 한 저장소에서 같이 맞추기 위해서다.
- `BE_B` 브랜치는 더 이상 쓰지 않는다(정리는 B가 확인한 뒤).

### 2026-11-13 — A real 모드 + B 실서버 통합 점검
- 로컬에서 B(FastAPI, `backend-b/`, Ollama 미사용 템플릿 응답)와 A(`SOM_ENGINE_MODE=real`)를 같이 띄우고 공개 API로 전 흐름을 돌렸다.
  - 가입 → 로그인 → 약관 동의 → 수면·사용 시간 연결 → 7일 신호 수집 → 체크인 → 오늘 상태 → 이번 주·7일 기록 → 루틴 4개 시작·완료 → 대화 → 마음 정리 → 탈퇴
  - B 호출 6종(피로 계산·기간 요약·루틴 추천·대화·털 깎임·처리물 삭제) 모두 200/204
- 결과: 40/40 통과
  - 오늘 상태는 B 결과(점수·단계·털 무게·`usedSources`·`algorithmVersion`) 그대로, `isStale=false`
  - 소미 인사·이번 주 카드에 B가 만든 근거 문구(수면 시간 변화)가 들어간다
  - 마음 정리 완료 후 `woolTrimmedToday=true`, 점수·단계는 그대로
  - 탈퇴 시 B `DELETE /v1/subjects/{subjectKey}` 204
- 발견 1(B): Windows Python에 시간대 DB가 없어 B가 `Asia/Seoul`을 못 찾고 500 → A는 재시도 후 503 `ENGINE_UNAVAILABLE`(이전 스냅샷 없음)로 정상 대응. `tzdata` 설치로 해결 → B에 `requirements.txt` 추가 요청
- 발견 2(B와 확인): 휴대폰 사용 시간을 보냈지만 `usedSources`에 `SCREEN_TIME`이 없다(B 지표 기준 — 연동 지표 맞추기 항목과 같이 확인)
