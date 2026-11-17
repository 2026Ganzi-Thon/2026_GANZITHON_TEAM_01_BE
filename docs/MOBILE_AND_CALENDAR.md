# 모바일 데이터 및 웹 캘린더 동기화

공통으로 솜 로그인 세션, 필수 동의, 변경 요청의 CSRF 토큰이 필요하다.
소스별 동의는 먼저 `POST /api/v1/me/connections/{source}`로 기록한다.
날짜는 솜 서비스의 KST 기준이다.

## 기기에서 가져온 일별 집계 전송

- `PUT /api/v1/mobile-sync/steps` (`STEPS` 동의): `{"localDate":"2026-11-18","total":5200}`
- `PUT /api/v1/mobile-sync/calendar/personal` 또는 `/calendar/work` (해당 캘린더 소스 동의):
  `{"localDate":"2026-11-18","eventCount":3,"busyMinutes":90,"averageGapMinutes":35}`.
  평균 간격을 계산할 수 없다면 `averageGapMinutes`는 생략한다.

날짜별 전체 집계를 보내며 재전송 시 덧셈하지 않고 교체한다. 일반 웹앱은 HealthKit 또는 Health Connect에서
걸음 수를 읽을 수 없으므로 이 API를 호출할 별도 기기 클라이언트가 필요하다. 기존 피로도 산식은 걸음 수를 사용하지 않는다.
캘린더 이벤트의 제목·참석자·내용은 전송하지 않는다.

## Google 캘린더 (웹)

1. Google Cloud에서 Calendar API를 켜고 웹 애플리케이션 OAuth 클라이언트를 만든다.
2. 웹앱의 OAuth 콜백 URL을 Google에 등록하고 동일한 값을 `SOM_GOOGLE_CALENDAR_REDIRECT_URI`에 설정한다.
   Render 환경에 `SOM_GOOGLE_CALENDAR_CLIENT_ID`, `SOM_GOOGLE_CALENDAR_CLIENT_SECRET`,
   `SOM_GOOGLE_CALENDAR_ENABLED=true`를 설정한다.
3. 개인 캘린더 동의 후 웹앱에서 `POST /api/v1/me/google-calendar/authorize`를 호출하고
   반환된 `authorizationUrl`로 이동한다.
4. 웹앱 콜백에서 URL의 `code`, `state`를 읽어 주소 표시줄에서 제거하고, 같은 솜 세션으로
   `POST /api/v1/me/google-calendar/sync`에 `{"code":"…","state":"…"}`를 전송한다.

동기화 요청마다 Google 기본 캘린더에서 오늘의 바쁜 구간만 조회해 겹친 시간을 합친다.
Google 토큰은 저장하지 않으며, 이후 자동 동기화는 하지 않는다.
