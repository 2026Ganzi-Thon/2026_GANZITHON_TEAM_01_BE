# Architecture Decision Records

## 철학
해커톤 MVP 속도가 최우선이되, 정신·심리 건강을 다루는 서비스이므로 **개인정보 보호와 데이터 무결성은 속도와 바꾸지 않는다.** 확정되지 않은 값은 코드에 박지 않고 설정으로 둔다. 작동하는 최소 구현을 고른다.

---

### ADR-001: A는 Java + Spring Boot, B는 별도 FastAPI 서비스 (2026-11-12 개정)
**결정**: A는 Java 17 + Spring Boot 3.x(Gradle). B는 별도 FastAPI 서비스로 두고 A가 HTTP(JSON)로 호출한다. A의 `engine` 패키지는 인터페이스·DTO·Mock과, real 모드에서 B를 부르는 HTTP 클라이언트 구현만 둔다. 요청·응답 형식은 "솜 A↔B engine API 계약" 문서를 따른다.
**이유**: 팀이 Spring을 학습했고 인증·동의·보안은 Spring Security가 강하다. B는 LLM·데이터 라이브러리가 많은 Python을 원했고, 팀이 합의했다(2026-11-12).
**트레이드오프**: 서버 간 인증(내부 토큰, 환경변수)·배포 2회·네트워크 장애 처리가 필요하다 → 타임아웃·재시도 상한은 `EngineGateway`, 실패 시 마지막 정상 값 + `is_stale`. 개정 전 결정: B도 같은 프로젝트의 `engine` 패키지(Java).

### ADR-002: engine 인터페이스 + Mock 우선 개발
**결정**: A는 `engine` 인터페이스와 Mock만 작성하고, `som.engine.mode=mock|real`로 전환한다.
**이유**: B 개발과 병렬로 A의 저장·조회·삭제 흐름을 끝까지 테스트하기 위해.
**트레이드오프**: Mock과 실제 B 응답의 차이는 통합 단계에서 발견된다 → DTO 필드를 B와 먼저 합의한다.

### ADR-003: 세션 기반 인증 + CSRF
**결정**: Spring Security 서버 세션 + CSRF 쿠키 토큰. 프론트는 `/api/*`를 같은 출처로 프록시한다.
**이유**: 브라우저 웹앱에서 토큰을 localStorage에 두는 XSS 위험을 피하고, Spring Security 기본 기능을 쓴다.
**트레이드오프**: 프론트·백엔드 도메인이 다르면 쿠키가 막힐 수 있다 → 프록시 구성을 프론트와 확인해야 한다.

### ADR-004: PostgreSQL + Flyway, 표준 JPA 매핑
**결정**: 운영 DB는 PostgreSQL, 스키마·시드는 Flyway. 배열·jsonb 같은 PostgreSQL 전용 타입 대신 표준 매핑(별도 테이블, 문자열 JSON)을 쓴다.
**이유**: 스키마 변경을 A가 단일 출처로 관리하고, 테스트 DB 선택지를 넓힌다.
**트레이드오프**: 일부 쿼리가 길어진다.

### ADR-005: 비밀번호 해시 Argon2id
**결정**: Spring Security `Argon2PasswordEncoder`(BouncyCastle 의존성). 의존성 문제가 생기면 BCrypt로 대체하고 이 ADR을 갱신한다.
**이유**: 명세 요구사항(Argon2id 또는 동급).
**트레이드오프**: 의존성 1개 추가.

### ADR-006: 연동 데이터는 데모 데이터로
**결정**: 웹앱은 브라우저에서 수면·걸음·사용시간을 읽을 수 없으므로, MVP는 동의·연결 상태만 실제로 저장하고 값은 `provider=demo`로 주입한다.
**이유**: 시연 가능성 확보, 동의·철회·삭제 흐름은 실제와 동일하게 검증.
**트레이드오프**: 실제 연동은 P1(네이티브 래퍼 등 별도 검토).

### ADR-007: 시간은 Clock 빈 + Asia/Seoul
**결정**: `Clock` 빈을 주입해 현재 시각을 얻고, 모든 `local_date`는 `Asia/Seoul` 기준으로 서버가 계산한다.
**이유**: 하루 경계·주간(월~일) 계산을 테스트로 고정하기 위해.
**트레이드오프**: 없음.

### ADR-008: API 문서 springdoc-openapi, 요청 제한 Bucket4j
**결정**: OpenAPI는 springdoc으로 자동 생성, 로그인·대화·수집·삭제 API는 Bucket4j로 요청 횟수 제한(값은 설정).
**이유**: 명세 A-09(문서와 실제 응답 일치), 접근 남용 통제.
**트레이드오프**: 메모리 기반 제한이라 서버 다중화 시 재검토.
