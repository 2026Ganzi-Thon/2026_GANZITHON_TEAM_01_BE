# 솜 백엔드 B API

## 실행

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python -m uvicorn som_engine.api:app --host 127.0.0.1 --port 8000 --no-access-log
```

기본 주소는 `http://127.0.0.1:8000`이다. [Swagger UI](http://127.0.0.1:8000/docs)와 [OpenAPI JSON](http://127.0.0.1:8000/openapi.json)에서 요청 스키마를 볼 수 있다.

AI 답변을 사용하려면 Ollama를 실행한 상태에서 모델을 내려받고, B 서버를 아래처럼 시작한다. 모델 사전 로딩 명령은 선택 사항이다. [Qwen3 4B 모델](https://ollama.com/library/qwen3:4b)

```sh
ollama pull qwen3:4b
curl -fsS --max-time 60 http://127.0.0.1:11434/api/generate \
  -H 'Content-Type: application/json' \
  -d '{"model":"qwen3:4b","keep_alive":"15m"}'
SOM_OLLAMA_MODEL=qwen3:4b \
  .venv/bin/python -m uvicorn som_engine.api:app --host 127.0.0.1 --port 8000 --no-access-log
```

## API 목록

| 메서드 | 경로 | 응답 |
| --- | --- | --- |
| POST | [`/v1/fatigue/calculate`](#1-피로도-계산) | 점수, 단계, 털 무게, 개인 기준 대비 변화 |
| POST | [`/v1/insights/summarize`](#2-기록-요약) | 기간 평균, 일별 그래프, 가장 무거웠던 날과 근거 |
| POST | [`/v1/routines/recommend`](#3-루틴-추천) | 추천 순서, 소요 시간, 추천 이유 |
| POST | [`/v1/chat/respond`](#4-소미-대화) | 선제 인사 또는 대화 답변, 위험 수준, 카드 |
| POST | [`/v1/routines/wool-trim`](#5-털-깎임-연출) | 연출 재생 여부와 문구 |
| DELETE | [`/v1/subjects/{subject_key}`](#6-사용자-처리물-삭제) | `204 No Content` |
| GET | [`/healthz`](#7-상태-확인) | `{"status":"ok"}` |

`POST` 요청은 `Content-Type: application/json`을 사용하며 성공 시 `200 OK`를 반환한다. `subjectKey`는 길이 1~128자의 사용자 식별 키다. 날짜는 한국 시간 기준 `YYYY-MM-DD`로 보내고, `calculatedAt`은 시간대 오프셋이 포함된 ISO 8601 문자열로 반환된다. 필수 필드 누락, 정의되지 않은 필드, 허용되지 않는 값은 `422`로 응답한다.

```json
{"error":{"code":"VALIDATION_ERROR"}}
```

## 1. 피로도 계산

`POST /v1/fatigue/calculate`

| 요청 필드 | 필수 | 설명 |
| --- | --- | --- |
| `subjectKey`, `localDate` | 예 | 사용자 키, 계산 대상 날짜 |
| `timezone` | 아니요 | `Asia/Seoul`. 생략 시 동일한 기본값 사용 |
| `checkin` | 아니요 | 감정 `mood`, 부담 요인 배열 `stressors`, 원하는 도움 `supportNeed` |
| `signals` | 아니요 | 당일 관측값 배열. 각 항목에 `source`, `metric`, `value`, `unit: "min"`을 전달. 시간대별 휴대폰 사용값은 `hourBucket`에 0~23시 지정 |
| `consentedSources` | 아니요 | 현재 동의된 소스 배열. 생략하면 연동 데이터는 계산에 반영하지 않음 |
| `recentSignals` | 아니요 | 이전 날짜의 `{localDate, signals}` 배열, 최대 14개. 날짜는 모두 다르고 계산 대상일보다 앞서야 함 |
| `baseline` | 아니요 | 수면 `sleepMinutes`·`sleepRecordedDays`, 하루 휴대폰 사용 `screenTimeMinutes`·`screenRecordedDays`의 기준값과 기록 일수 |

체크인 필드의 허용 값은 다음과 같다. 건너뛴 문항은 생략하거나 `null`로 보낼 수 있다. `stressors`는 생략하거나 빈 배열로 보낸다.

| 필드 | 값 |
| --- | --- |
| `mood` | `CALM`, `OKAY`, `SO_SO`, `ANXIOUS`, `TIRED`, `OTHER` |
| `stressors` | `WORK`, `FUTURE_WORRY`, `COMPARISON`, `RELATIONSHIP`, `NO_REASON`, `OTHER` |
| `supportNeed` | `COMFORT`, `VENT`, `SHORT_BREAK`, `SMALL_ACTION` |

| 소스 | 계산에 사용하는 지표 |
| --- | --- |
| `SLEEP` | `SLEEP_MINUTES` 수면 시간 |
| `SCREEN_TIME` | `SCREEN_TIME_MINUTES` 전체 사용 시간, `NIGHT_SCREEN_MINUTES` 야간 사용 시간. 22~05시 시간대별 자료가 있으면 야간 사용값을 우선 사용 |
| `CALENDAR_PERSONAL`, `CALENDAR_WORK` | `CALENDAR_BUSY_MINUTES` 일정으로 바쁜 시간, `CALENDAR_AVERAGE_GAP_MINUTES` 일정 사이 평균 여유 시간 |

직전 14일 안에 지표별 유효 기록이 5일 이상이면 중앙값을 개인 기준으로 사용한다. 수면·하루 휴대폰 사용의 경우 `recentSignals`가 부족하면 5일 이상 기록한 `baseline` 요약을 사용할 수 있다. `baselineStatus`는 개인 기준 적용 시 `PERSONAL`, 공통 기준이 사용된 경우 `DEFAULT`, 기준 비교를 하지 않은 경우 `NOT_USED`다.

**요청**

```json
{
  "subjectKey": "som-demo",
  "localDate": "2026-11-12",
  "checkin": {
    "mood": "TIRED",
    "stressors": [
      "WORK"
    ],
    "supportNeed": "SHORT_BREAK"
  },
  "signals": [
    {
      "source": "SLEEP",
      "metric": "SLEEP_MINUTES",
      "value": 420,
      "unit": "min"
    }
  ],
  "consentedSources": [
    "SLEEP"
  ],
  "baseline": {
    "sleepMinutes": 480,
    "sleepRecordedDays": 7
  }
}
```

**응답**

```json
{
  "score": 68,
  "level": 3,
  "woolDisplayKg": 2.4,
  "status": "CALCULATED",
  "statusText": "피로가 쌓였어요",
  "subText": "잠시 쉬어갈 시간을 마련해봐요.",
  "assetKey": "sheep_level_3",
  "usedSources": [
    "CHECKIN",
    "SLEEP"
  ],
  "baselineStatus": "PERSONAL",
  "changes": [
    {
      "source": "SLEEP",
      "metric": "SLEEP_MINUTES",
      "deltaMinutes": -60
    }
  ],
  "algorithmVersion": "b-mvp-0.2",
  "calculatedAt": "2026-11-12T21:00:00+09:00"
}
```

`score`는 0~100이다. `level`은 0~24점이면 1단계, 25~49점이면 2단계, 50~74점이면 3단계, 75~100점이면 4단계다. 계산할 수 있는 값이 없으면 `status: "INSUFFICIENT_DATA"`와 `score: null`, `level: null`, `woolDisplayKg: null`을 반환한다. `changes[].deltaMinutes`는 **관측값에서 개인 기준을 뺀 분 단위 차이**다. 위 응답의 `-60`은 평소보다 수면이 60분 짧았다는 뜻이다.

## 2. 기록 요약

`POST /v1/insights/summarize`

| 요청 필드 | 필수 | 설명 |
| --- | --- | --- |
| `subjectKey` | 예 | 사용자 키 |
| `from`, `to` | 예 | 조회 시작일과 종료일. 양 끝 날짜 포함, 최대 30일 |
| `days` | 예 | 기간 내 일별 기록 배열. 각 항목의 `localDate`는 필수. `level`, `score`, `woolDisplayKg`, `changes`는 피로도 응답에서 전달 가능 |
| `consentedSources` | 아니요 | 변화 근거에 사용할 현재 동의 소스 |

**요청**

```json
{
  "subjectKey": "som-demo",
  "from": "2026-11-10",
  "to": "2026-11-12",
  "days": [
    {
      "localDate": "2026-11-10",
      "level": 2,
      "score": 40,
      "woolDisplayKg": 1.6
    },
    {
      "localDate": "2026-11-12",
      "level": 3,
      "score": 68,
      "woolDisplayKg": 2.4,
      "changes": [
        {
          "source": "SLEEP",
          "metric": "SLEEP_MINUTES",
          "deltaMinutes": -60
        }
      ]
    }
  ],
  "consentedSources": [
    "SLEEP"
  ]
}
```

**응답**

```json
{
  "averageLevel": 2.5,
  "recordedDays": 2,
  "changeSummary": "최근 기록의 단계가 이 기간 첫 기록과 비교해 1단계 높았어요.",
  "heaviestDate": "2026-11-12",
  "heaviestReason": "그날 수면 시간이 평소보다 60분 짧았어요.",
  "woolWeightKg": 2.4,
  "statusText": "피로가 쌓였어요",
  "subText": "잠시 쉬어갈 시간을 마련해봐요.",
  "dailyLevels": [
    {
      "localDate": "2026-11-10",
      "level": 2
    },
    {
      "localDate": "2026-11-11",
      "level": null
    },
    {
      "localDate": "2026-11-12",
      "level": 3
    }
  ],
  "changes": [
    {
      "source": "SLEEP",
      "metric": "SLEEP_MINUTES",
      "deltaMinutes": -60,
      "changedDays": 1
    }
  ]
}
```

`dailyLevels`는 기간의 모든 날짜를 채운다. 미기록 날짜는 `level: null`이다. `averageLevel`은 유효한 단계의 평균이며, `woolWeightKg`와 상태 문구는 기간의 마지막 유효 기록 기준이다. `heaviestDate`는 단계가 가장 높은 날짜로, 동점이면 점수가 높은 날, 그래도 같으면 가장 최근 날짜를 고른다. `heaviestReason`에는 해당 날짜의 동의된 변화 근거가 있으면 이를 사용한다. `changes[].changedDays`는 그 지표의 변화가 기록된 날짜 수이고, `deltaMinutes`는 그날들의 평균 변화다.

## 3. 루틴 추천

`POST /v1/routines/recommend`

| 요청 필드 | 필수 | 설명 |
| --- | --- | --- |
| `subjectKey`, `localDate` | 예 | 사용자 키, 추천 기준일 |
| `catalogIds` | 예 | 추천할 수 있는 루틴 ID 배열. 최대 50개이며 중복 불가 |
| `level`, `mood`, `supportNeed` | 아니요 | 현재 피로 단계와 체크인 값 |
| `preferredRoutineIds` | 아니요 | 사용자가 선호하는 루틴 ID |
| `completedRecently` | 아니요 | 최근 완료한 루틴 ID |
| `availableSeconds` | 아니요 | 지금 사용할 수 있는 시간, 1~3600초 |
| `catalogDurations` | 아니요 | 루틴 ID별 소요 시간, 1~3600초. `catalogIds`에 있는 ID만 지정 |

**요청**

```json
{
  "subjectKey": "som-demo",
  "localDate": "2026-11-12",
  "level": 3,
  "supportNeed": "SMALL_ACTION",
  "catalogIds": [
    "BREATHING",
    "WRIST_ARM_STRETCH"
  ],
  "preferredRoutineIds": [
    "WRIST_ARM_STRETCH"
  ],
  "availableSeconds": 30
}
```

**응답**

```json
{
  "routineIds": [
    "WRIST_ARM_STRETCH",
    "BREATHING"
  ],
  "reason": "선택한 회복 방식에 맞는 루틴을 먼저 골랐어요.",
  "items": [
    {
      "routineId": "WRIST_ARM_STRETCH",
      "durationSec": 30
    },
    {
      "routineId": "BREATHING",
      "durationSec": 60
    }
  ]
}
```

`routineIds`와 `items`에는 카탈로그 전체가 추천 순서대로 들어간다. 가용 시간을 전달하면 그 안에 완료 가능한 루틴을 먼저 정렬한다. `reason`은 첫 번째 추천의 이유다. 기본 소요 시간은 `BREATHING` 60초, `SHOULDER_ROLL`·`SEATED_TWIST`·`WRIST_ARM_STRETCH` 각 30초이며, `catalogDurations`로 덮어쓸 수 있다. 소요 시간이 정의되지 않은 루틴은 `durationSec: null`을 반환한다.

## 4. 소미 대화

`POST /v1/chat/respond`

| 요청 필드 | 필수 | 설명 |
| --- | --- | --- |
| `subjectKey`, `localDate` | 예 | 사용자 키, 대화 날짜 |
| `mode` | 예 | `GREETING`은 소미의 선제 인사, `REPLY`는 사용자의 메시지에 답변 |
| `userText` | 아니요 | 현재 사용자 메시지. 최대 1500자. 비어 있으면 체크인 또는 기본 문구로 답변 |
| `recentMessages` | 아니요 | 오래된 순서의 `{role, text}` 배열. `role`은 `USER` 또는 `SOMI`, 배열 최대 30개, 메시지당 최대 1500자. 모델에는 최근 최대 6개를 사용 |
| `todayCheckin` | 아니요 | 오늘의 체크인. 도움 유형을 답변과 루틴 선택에 사용 |
| `weekSummary` | 아니요 | 기록 요약 응답의 `averageLevel`, `recordedDays`, `changes`. `recordedDays`는 0~7 |
| `routineContext` | 아니요 | 추천 API의 `catalogIds`, `level`, `preferredRoutineIds`, `completedRecently`, `availableSeconds`, `catalogDurations` |
| `consentedSources` | 아니요 | 선제 인사와 요약 카드에 사용할 현재 동의 소스 |

**선제 인사 요청과 응답**

```json
{
  "subjectKey": "som-demo",
  "localDate": "2026-11-12",
  "mode": "GREETING",
  "weekSummary": {
    "averageLevel": 2.5,
    "recordedDays": 2,
    "changes": [
      {
        "source": "SLEEP",
        "metric": "SLEEP_MINUTES",
        "deltaMinutes": -60,
        "changedDays": 1
      }
    ]
  },
  "consentedSources": [
    "SLEEP"
  ]
}
```

```json
{
  "text": "안녕하세요, 소미예요. 변화가 기록된 1일의 수면 시간이 평소보다 평균 60분 짧았어요. 오늘 마음은 어떤가요?",
  "riskLevel": "NONE",
  "cards": [
    {
      "type": "WEEK_SUMMARY",
      "routineId": null,
      "durationSec": null,
      "title": "이번 주 달라진 점",
      "body": "변화가 기록된 1일의 수면 시간이 평소보다 평균 60분 짧았어요."
    }
  ]
}
```

**사용자 메시지 요청과 응답 예시**

```json
{
  "subjectKey": "som-demo",
  "localDate": "2026-11-12",
  "mode": "REPLY",
  "userText": "잠깐 쉬어가고 싶어요.",
  "todayCheckin": {
    "supportNeed": "SHORT_BREAK"
  }
}
```

```json
{
  "text": "지금 하던 일을 잠시 내려놓고 어깨에 들어간 힘을 풀어봐요.",
  "riskLevel": "NONE",
  "cards": [
    {
      "type": "ROUTINE_RECOMMENDATION",
      "routineId": "BREATHING",
      "durationSec": 60,
      "title": null,
      "body": "잠시 쉬어가기 좋은 숨 고르기를 먼저 골랐어요."
    }
  ]
}
```

`riskLevel`은 `NONE`, `LOW`, `HIGH` 중 하나다. 위기 분기는 안내 문구를 반환하며 루틴 추천 카드를 생략한다. `cards`에는 `WEEK_SUMMARY` 또는 `ROUTINE_RECOMMENDATION`이 들어갈 수 있다. 추천 카드는 `routineId`, `durationSec`, 추천 이유인 `body`를 제공하며, 지정한 시간 안에 끝낼 수 있는 루틴이 없으면 생략한다. 모델을 사용하면 답변 문구는 달라질 수 있다. 모델이 설정되지 않았거나 호출 또는 응답 검증에 실패하면 기본 문구로 답한다. 모델 연결 제한은 1초, 읽기 제한은 12초다.

## 5. 털 깎임 연출

`POST /v1/routines/wool-trim`

`subjectKey`, `localDate`, `journalCompleted`는 필수다. `alreadyShown`은 선택이며 기본값은 `false`다. 마음 정리를 완료했고 연출을 아직 보여주지 않은 경우에만 `shouldPlay: true`를 반환한다.

**요청**

```json
{
  "subjectKey": "som-demo",
  "localDate": "2026-11-12",
  "journalCompleted": true,
  "alreadyShown": false
}
```

**응답**

```json
{
  "shouldPlay": true,
  "animationKey": "wool_trim",
  "message": "털 한 뭉치를 덜어냈어요!"
}
```

연출 대상이 아니면 `shouldPlay: false`, `animationKey: null`, `message: null`을 반환한다. 연출 요청은 피로도 점수와 단계를 변경하지 않는다.

## 6. 사용자 처리물 삭제

`DELETE /v1/subjects/{subject_key}`

경로에 사용자 키를 전달한다. 요청 본문은 없고, 성공 또는 동일 키로 재호출 시 모두 `204 No Content`를 반환한다.

## 7. 상태 확인

`GET /healthz`

B 서버가 요청을 받을 수 있으면 `200 OK`와 아래 JSON을 반환한다. 모델 준비 상태는 확인하지 않는다.

```json
{"status":"ok"}
```
