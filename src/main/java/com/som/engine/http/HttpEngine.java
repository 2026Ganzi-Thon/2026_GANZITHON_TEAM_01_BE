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
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "som.engine", name = "mode", havingValue = "real")
public class HttpEngine implements FatigueEngine, InsightEngine, RoutineEngine, ChatEngine, EngineDataEraser {

    private final RestClient client;

    public HttpEngine(RestClient.Builder builder, @Value("${som.engine.url}") URI url,
                      @Value("${som.engine.token}") String token) {
        if (url.getHost() == null || !("http".equals(url.getScheme()) || "https".equals(url.getScheme()))
                || url.getUserInfo() != null || url.getQuery() != null || url.getFragment() != null) {
            throw new IllegalArgumentException("som.engine.url must be an HTTP(S) service address");
        }
        if (token.isBlank() || token.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new IllegalArgumentException("som.engine.token must be set in real mode");
        }
        HttpClient transport = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(transport);
        requests.setReadTimeout(Duration.ofSeconds(14));
        this.client = builder.baseUrl(url.toString())
                .requestFactory(requests)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
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
