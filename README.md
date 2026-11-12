# 솜 백엔드 B

Spring과 독립적으로 실행되는 FastAPI 엔진. 요청에 들어 있는 데이터만 사용하며 별도의 DB나 캐시가 없다. 경로와 DTO는 A 코드의 계약을 참고한 로컬 개발용 초안이다.

## 실행

Python 3.13에서 검증했다. 아래 명령은 저장소 루트에서 시작한다.

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python -m uvicorn som_engine.api:app --host 127.0.0.1 --port 8000 --no-access-log
```

[Swagger UI](http://127.0.0.1:8000/docs)에서 각 API의 필드와 응답을 확인할 수 있다. 내부 인증은 A↔B 연동 단계에서 붙인다. 현재 서버는 로컬에서 실행한다.

| 메서드와 경로 | 동작 |
| --- | --- |
| `GET /healthz` | 프로세스 상태 확인 |
| `POST /v1/fatigue/calculate` | 피로도 점수, 양의 4단계와 털 표시 계산 |
| `POST /v1/insights/summarize` | 최대 30일 내 유효 기록의 평균·변화·가장 무거웠던 날 집계 |
| `POST /v1/routines/recommend` | 요청의 카탈로그 ID를 단계·감정·도움 요청·수행 이력에 따라 정렬 |
| `POST /v1/chat/respond` | 소미의 인사·답변·추천 카드와 안전 분기 |
| `DELETE /v1/subjects/{subjectKey}` | 처리물 삭제 계약. 현재 저장물이 없어 반복 호출에도 204 |

```sh
curl -sS http://127.0.0.1:8000/v1/fatigue/calculate \
  -H 'Content-Type: application/json' \
  -d '{"subjectKey":"demo","localDate":"2026-11-12","checkin":{"mood":"TIRED","supportNeed":"SHORT_BREAK"}}'
```

## MVP 동작

- 피로도 가중치와 단계 경계는 `som_engine/policy.py`에 둔 초안이다. 가용 지표만으로 가중치를 다시 나눈다. 입력이 없으면 `INSUFFICIENT_DATA`와 `null` 점수를 반환한다.
- 수면·사용시간의 개인 기준선은 호출자가 최근 유효 기록으로 집계해 `baseline`으로 보낸다. 각 지표에 유효 기록이 5일 이상 있어야 개인값을 쓰고, 그보다 적으면 공통 기준을 쓴다. 연동 동의가 없는 지표는 계산에서 제외한다.
- 기간 요약의 `woolWeightKg`는 기간 내 마지막 유효 기록의 값이다. 털 깎임 연출은 점수에 반영하지 않는다.
## 소미 AI 대화

로컬 Ollama를 실행하고 사용할 모델을 내려받은 뒤, 서버를 아래와 같이 실행한다. 모델명은 `ollama list`로 확인한다.

```sh
SOM_OLLAMA_MODEL='설치한-모델명' \
  .venv/bin/python -m uvicorn som_engine.api:app --host 127.0.0.1 --port 8000 --no-access-log
```

```sh
curl -sS http://127.0.0.1:8000/v1/chat/respond \
  -H 'Content-Type: application/json' \
  -d '{"subjectKey":"demo","localDate":"2026-11-12","mode":"REPLY","userText":"오늘 발표가 잘 안 풀려서 자꾸 생각나요.","recentMessages":[{"role":"SOMI","text":"오늘 가장 마음에 남는 일은 뭐예요?"}]}'
```

모델이 현재 메시지와 최근 대화 최대 6턴을 참고해 소미 답변을 생성한다. 모델에 보내는 사용자 데이터는 해당 대화 내용으로 제한한다. 명시적인 위기 신호는 모델 호출 전에 별도 응답으로 처리한다. 모델이 위기 신호를 반환한 경우에도 별도 응답을 사용한다. 설정이 없거나 모델 호출이 실패하거나 응답 검증을 통과하지 못하면 기본 문구로 응답한다. 운영 전에는 모델별 대화 품질과 안전 분기를 검증해야 한다. [Ollama Chat API](https://docs.ollama.com/api/chat)
