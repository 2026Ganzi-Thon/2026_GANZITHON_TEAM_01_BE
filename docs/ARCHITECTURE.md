# 아키텍처

## 디렉토리 구조
```
src/main/java/com/som/
├── common/       # 에러 형식(@RestControllerAdvice), 요청 ID 필터, Clock(KST), 멱등 키, 요청 횟수 제한
├── auth/         # 회원가입·로그인·세션, SecurityConfig, 필수 약관 게이트
├── consent/      # 필수 약관·선택 동의 이력
├── connection/   # 연동 소스 연결·해제, 연동 데이터(signal) 수신
├── checkin/      # 하루 체크인
├── state/        # 오늘 상태·이번 주·기간 기록 (engine 결과 스냅샷 저장·조회)
├── chat/         # 메시지·카드 저장, 날짜별 조회, engine 대화 중계
├── routine/      # 루틴 카탈로그·실행 기록·주간 이행·마음 정리 잠금
├── journal/      # 마음 정리 기록, 털 깎임 연출 이벤트
├── settings/     # 알림 설정, 도움 받을 곳 목록
├── account/      # 데이터 삭제·탈퇴·삭제 상태
├── demo/         # 시연용 데모 계정·데모 연동 시드 (demo 프로필에서만)
└── engine/       # B 연동 경계. 인터페이스 + DTO + Mock + B(FastAPI) HTTP 클라이언트 (계산 로직은 B 서비스)
src/main/resources/
├── application.yml
└── db/migration/ # Flyway: V1__init.sql, V2__seed_routines.sql, V3__seed_help.sql ...
src/test/java/com/som/...  # 패키지 구조 동일
```

## 패턴
- 기능 패키지마다 `controller / service / repository / domain / dto` 레이어. 컨트롤러는 DTO만 주고받고 엔티티를 그대로 반환하지 않는다.
- 입력 검증은 DTO + Bean Validation. 선택지는 Java enum으로 받고, 모르는 값·형식 오류는 `@RestControllerAdvice`에서 공통 에러 형식으로 변환한다.
- engine 연동은 **인터페이스 의존**. `som.engine.mode=mock|real` 설정으로 구현을 바꾼다. mock은 A의 Mock, real은 B(별도 FastAPI 서비스)를 HTTP로 부르는 클라이언트다(ADR-001).
  ```java
  public interface FatigueEngine  { FatigueResult  calculate(FatigueRequest req); }
  public interface InsightEngine  { InsightResult  summarize(InsightRequest req); }
  public interface RoutineEngine  { RoutineRecommendation recommend(RoutineRequest req); }
  public interface ChatEngine     { ChatResult     respond(ChatRequest req); }
  ```
  - 요청 DTO에는 userId 대신 `subjectKey`(서버 발급 가명 ID)를 넣는다.
  - engine 호출은 타임아웃·재시도 상한을 둔 서비스(예: `EngineGateway`)를 거친다.
- 삭제는 하나의 트랜잭션에서 사용자 소유 테이블 전체를 지우고, engine 캐시 무효화를 호출한다.
- API 문서는 springdoc-openapi로 생성하고, 컨트롤러 테스트로 실제 응답 형식을 검증한다.

## 데이터 흐름
```
[체크인]  Front → POST /api/v1/checkins → 인증·약관 게이트·검증 → checkins 저장
          → FatigueEngine.calculate(동의한 소스만, 결측 null) → fatigue_snapshots 저장 → 응답
          (engine 실패 → 마지막 정상 스냅샷 + is_stale=true, 점수 생성 금지)

[홈]      Front → GET /state/today, /insights/week → snapshots 조회 (+ InsightEngine 요약 캐시)

[대화]    Front → POST /chat/messages → 인증·요청 제한·길이 검증 → 사용자 메시지 저장
          → ChatEngine.respond(오늘 체크인·주간 요약·최근 맥락·동의 상태) → 응답·risk_flag·카드 저장 → 응답
          (위기 분류 → 도움 받을 곳 목록 포함 / 실패 → 고정 문구)

[루틴]    GET /routines/today → RoutineEngine.recommend 순서 + 완료 상태 + journal_unlocked
          POST /routines/{id}/complete (멱등) → routine_sessions

[마음정리] POST /journal → journal_entries + wool_trim_events (engine 전달 없음, 실제 점수 불변)

[삭제]    DELETE /me/data | /me → 사용자 소유 테이블 연쇄 삭제 → engine 캐시 무효화 → 삭제 상태 기록
```

## 상태 관리
- 서버 상태의 원본은 PostgreSQL. 세션은 Spring Security 서버 세션(JSESSIONID, HttpOnly·Secure·SameSite) + CSRF 쿠키 토큰.
- 계산 결과는 `fatigue_snapshots`에 날짜·알고리즘 버전별로 저장한다. 재체크인 시 재계산 후 스냅샷을 교체한다.
- 주간 요약 등 재사용 가능한 engine 결과는 사용자·기간 키로 캐시하고, 체크인·동의 철회·삭제 시 무효화한다.
