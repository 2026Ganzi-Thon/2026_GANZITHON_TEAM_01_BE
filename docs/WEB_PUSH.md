# Web Push

Render에 VAPID 키 쌍과 연락처(`mailto:` 주소)를 넣고 `SOM_WEB_PUSH_ENABLED=true`로 켠다.
환경 변수 이름은 `.env.example`을 참고한다. 개인 키는 서버에서만 사용한다.
솜의 로그인 세션과 필수 동의가 필요하고, 변경 요청에는 CSRF 토큰을 보낸다.

1. HTTPS 웹앱에서 서비스 워커를 등록한다. 아이폰은 홈 화면에 추가한 웹앱에서 알림을 요청한다.
2. 사용자가 알림 켜기 버튼을 누르면 알림 권한을 요청하고, `GET /api/v1/me/push/public-key`의
   `publicKey`를 사용해 `PushManager.subscribe({ userVisibleOnly: true, applicationServerKey })`로 구독한다.
3. 구독에서 `endpoint`, `keys.p256dh`, `keys.auth`를 꺼내
   `PUT /api/v1/me/push/subscription`에 `{"endpoint":"…","keys":{"p256dh":"…","auth":"…"}}`를 보낸다.
4. 기존 `PATCH /api/v1/me/notification-settings`에 `{"enabled":true,"time":"20:00"}`를 보낸다.
   시간을 지정하지 않으면 선택한 알림 시간대의 기본 시간(09:00, 12:30, 18:30, 22:00)을 사용한다. 시간은 KST다.
5. 서비스 워커의 `push` 이벤트에서 JSON `title`, `body`, `tag`로 알림을 표시하고,
   알림을 누르면 페이로드의 내부 경로 `url`을 연다.
6. 기기별 구독을 끄려면 `DELETE /api/v1/me/push/subscription`에 `{"endpoint":"…"}`를 보내고
   브라우저에서도 구독을 해제한다.

서버가 켜져 있을 때 사용자별 설정 시각에 기기당 하루 한 번 발송을 시도한다.
서버가 알림 시각을 지나 5분 넘게 중단됐거나 공급자가 발송을 거부한 경우 다음 날 일정을 기다린다.
Google FCM, Apple Web Push, Mozilla Autopush 구독을 지원한다.
