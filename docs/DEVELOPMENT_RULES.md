# 개발 규칙 — 솜(SOM) 백엔드 A

솜은 체크인과 일상 데이터로 지친 신호를 알아차리고, 양 캐릭터 '소미'와 짧은 회복 루틴으로 돌봄을 시작하게 하는 **비진단형 웰빙 웹앱**이다.
이 저장소는 **백엔드 A**다. 공개 API 전체, DB, 인증·동의·보안, 개인정보 수명주기를 맡는다.
피로도 계산·추천·AI 대화는 **백엔드 B**(별도 FastAPI 서비스) 몫이다. A는 `engine` 패키지에 인터페이스·Mock과 B를 부르는 HTTP 클라이언트만 둔다(ADR-001).

- 상세 명세: `spec/A_SPEC.md`(화면 확정값, API 목록, enum, 시드 데이터, 미결정 항목)
- 구조·패턴: `docs/ARCHITECTURE.md`, 기술 결정: `docs/ADR.md`

## 기술 스택
- Java 17 + Spring Boot 3.x (Gradle)
- Spring Web, Spring Security(세션 + CSRF), Spring Data JPA, Bean Validation
- PostgreSQL + Flyway / 테스트: JUnit 5 + MockMvc
- springdoc-openapi(API 문서), Bucket4j(요청 횟수 제한)

## 꼭 지킬 규칙
아래 항목은 개인정보·데이터 무결성과 직결되므로 예외 없이 지킨다.

1. **사용자 데이터는 로그인 세션의 userId로만 조회·수정한다.** 요청 바디·쿼리·경로의 userId는 믿지 않는다. 이유: 다른 사용자 데이터 노출.
2. **피로도 점수·단계·털 무게·추천·AI 응답을 A에서 계산하거나 만들어내지 않는다.** `engine` 결과만 저장·전달한다. B가 실패하면 마지막 정상 값 + `is_stale=true`, 대화는 고정 문구를 쓴다.
3. **데이터가 없으면 `null`이다.** 0점·1단계로 대체하지 않는다. 체크인 건너뛰기·연동 미동의로 피로를 추정하지 않는다.
4. **동의하지 않은 연동 소스의 데이터는 받지도, 저장하지도, engine에 넘기지도 않는다.** 동의 화면의 '안 보는 것'(일정 제목·내용·참석자, 위치·이동 경로, 앱 내용·메시지·검색 기록, 다른 건강 기록)은 필드 자체를 만들지 않는다.
5. **비밀번호·세션 토큰·대화 원문·마음 정리 글·연동 원값을 로그에 남기지 않는다.** 비밀키는 환경변수로만 읽는다(`.env`는 git에 올리지 않고, `.env.example`에는 키만 둔다).
6. **마음 정리 기록(journal)은 engine에 넘기지 않는다.**
7. **루틴 완료·마음 정리 완료(털 깎임 연출)로 실제 점수·단계를 바꾸지 않는다.** 연출 값은 별도 필드로 저장한다.
8. **명세에서 `[결정 필요]`인 값을 임의로 확정하지 않는다.** 설정값 + `// TODO(DECISION): ...` 주석으로 둔다.

## 구현 규칙
- 날짜(`local_date`)는 서버가 `Asia/Seoul` 기준으로 계산한다. 현재 시각은 주입된 `Clock` 빈에서만 얻는다(테스트 가능하게).
- 체크인 제출·루틴 완료는 멱등이다(`Idempotency-Key` 또는 유니크 제약).
- 경로·글자 수·보존 기간·요청 제한·타임아웃·시드 데이터는 `application.yml` 또는 Flyway 시드로 분리하고 하드코딩하지 않는다.
- 패키지 구조와 레이어는 `docs/ARCHITECTURE.md`를 따른다. `engine` 패키지는 repository에 접근하지 않는다.
- 일부 규칙은 테스트로 강제된다: `ArchitectureRulesTest`(engine 의존성, Clock, 컨트롤러 userId), `UserDataEraserTest`(사용자 테이블 삭제 범위), `SecurityGateTest`(로그·비밀값).

## 개발 프로세스
- 새 기능은 테스트를 먼저 작성하고, 테스트가 통과하는 구현을 작성한다(TDD).
- 보안 관련 기능(인가·동의·삭제)은 **다른 사용자 계정으로 접근하는 테스트**를 반드시 포함한다.
- 커밋 메시지는 conventional commits 형식을 따른다(`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`).

## 명령어
Windows는 `gradlew.bat`, macOS/Linux/Git Bash는 `./gradlew`
```
./gradlew bootRun     # 개발 서버 (로컬 PostgreSQL 필요, 접속 정보는 .env)
./gradlew build       # 컴파일 + 테스트 (테스트 DB som_test 필요)
./gradlew test        # 테스트만
```
데모 계정: `demo` 프로필 + 환경변수 `SOM_DEMO_PASSWORD`로 실행하면 데모 계정과 7일치 데모 데이터가 만들어진다.
