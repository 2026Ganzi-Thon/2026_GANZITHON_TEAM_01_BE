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
- 소미는 기본적으로 검토 가능한 고정 문구를 사용한다. `SOM_OLLAMA_MODEL`에 로컬 Ollama에 설치한 모델 이름을 지정하면 현재 메시지의 의도 분류를 로컬 모델에 요청한다. 모델은 분류값만 반환하고, 오류 시 고정 문구로 처리한다. 키워드와 모델 분류는 MVP 안전 분기이며 운영 전 실제 사례 검증이 필요하다. [Ollama API](https://docs.ollama.com/api/generate)
