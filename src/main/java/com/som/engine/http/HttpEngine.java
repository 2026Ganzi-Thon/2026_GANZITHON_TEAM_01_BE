package com.som.engine.http;

import com.som.engine.chat.ChatEngine;
import com.som.engine.chat.ChatRequest;
import com.som.engine.chat.ChatResult;
import com.som.engine.data.EngineDataEraser;
import com.som.engine.fatigue.FatigueEngine;
import com.som.engine.fatigue.FatigueRequest;
import com.som.engine.fatigue.FatigueResult;
import com.som.engine.insight.InsightEngine;
import com.som.engine.insight.InsightRequest;
import com.som.engine.insight.InsightResult;
import com.som.engine.routine.RoutineEngine;
import com.som.engine.routine.RoutineRecommendation;
import com.som.engine.routine.RoutineRequest;
import com.som.engine.routine.WoolTrimEngine;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * B(FastAPI) HTTP 구현. 구성(주소·토큰·타임아웃·JSON 규칙)은 EngineHttpConfig가 맡는다(real 모드에서만 생성).
 * 실패(연결·시간 초과·2xx 외 상태)는 예외로 던지고, 타임아웃·재시도·응답 검증은 EngineGateway와 호출 서비스가 맡는다.
 */
public class HttpEngine implements FatigueEngine, InsightEngine, RoutineEngine, ChatEngine, EngineDataEraser, WoolTrimEngine {

    private final RestClient client;

    public HttpEngine(RestClient client) {
        this.client = client;
    }

    @Override
    public FatigueResult calculate(FatigueRequest request) {
        var minutes = request.signals().stream().filter(signal -> "min".equals(signal.unit())).toList();
        var input = new FatigueRequest(request.subjectKey(), request.localDate(), request.timezone(), request.checkin(),
                minutes, request.consentedSources(), request.recentSignals());
        return post("/v1/fatigue/calculate", input, FatigueResult.class);
    }

    @Override
    public InsightResult summarize(InsightRequest request) {
        return post("/v1/insights/summarize", request, InsightResult.class);
    }

    @Override
    public RoutineRecommendation recommend(RoutineRequest request) {
        return post("/v1/routines/recommend", request, RoutineRecommendation.class);
    }

    @Override
    public Boolean shouldPlay(String subjectKey, LocalDate localDate) {
        var result = post("/v1/routines/wool-trim", new TrimRequest(subjectKey, localDate, true), TrimResult.class);
        return result == null ? null : result.shouldPlay();
    }

    private record TrimRequest(String subjectKey, LocalDate localDate, boolean journalCompleted) {}

    private record TrimResult(Boolean shouldPlay, String animationKey, String message) {}

    @Override
    public ChatResult respond(ChatRequest request) {
        return post("/v1/chat/respond", request, ChatResult.class);
    }

    @Override
    public void eraseSubject(String subjectKey) {
        var response = client.delete().uri("/v1/subjects/{subjectKey}", subjectKey).retrieve().toBodilessEntity();
        if (response.getStatusCode() != HttpStatus.NO_CONTENT) {
            throw new IllegalStateException("Unexpected status from engine deletion");
        }
    }

    private <T> T post(String path, Object body, Class<T> type) {
        return client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(type);
    }
}
