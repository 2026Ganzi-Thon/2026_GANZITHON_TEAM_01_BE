package com.som.connection.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(name = "som.google-calendar.enabled", havingValue = "true")
class GoogleCalendarClient {

    private static final String SCOPE = "https://www.googleapis.com/auth/calendar.freebusy";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestClient http;

    GoogleCalendarClient(@Value("${som.google-calendar.client-id}") String clientId,
                         @Value("${som.google-calendar.client-secret}") String clientSecret,
                         @Value("${som.google-calendar.redirect-uri}") String redirectUri) {
        if (clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank()) {
            throw new IllegalStateException("Google Calendar OAuth requires client ID, client secret and redirect URI");
        }
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    URI authorize(String state, String challenge) {
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", "{id}")
                .queryParam("redirect_uri", "{redirect}")
                .queryParam("scope", "{scope}")
                .queryParam("response_type", "code")
                .queryParam("access_type", "online")
                .queryParam("state", "{state}")
                .queryParam("code_challenge", "{challenge}")
                .queryParam("code_challenge_method", "S256")
                .encode().buildAndExpand(Map.of("id", clientId, "redirect", redirectUri, "scope", SCOPE,
                        "state", state, "challenge", challenge)).toUri();
    }

    JsonNode busyIntervals(String code, String verifier, OffsetDateTime from, OffsetDateTime until) {
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.set("client_id", clientId);
            form.set("client_secret", clientSecret);
            form.set("code", code);
            form.set("redirect_uri", redirectUri);
            form.set("grant_type", "authorization_code");
            form.set("code_verifier", verifier);
            JsonNode response = http.post().uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
            String token = response == null ? "" : response.path("access_token").asText();
            if (token.isBlank()) {
                throw unavailable();
            }
            JsonNode calendar = http.post().uri("https://www.googleapis.com/calendar/v3/freeBusy")
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("timeMin", from.toInstant().toString(), "timeMax", until.toInstant().toString(),
                            "items", new Object[]{Map.of("id", "primary")}))
                    .retrieve().body(JsonNode.class);
            if (calendar == null || !calendar.path("calendars").isObject()) {
                throw unavailable();
            }
            var calendars = calendar.path("calendars").elements();
            JsonNode item = calendars.hasNext() ? calendars.next() : null;
            if (item == null || calendars.hasNext() || item.path("errors").size() > 0 || !item.path("busy").isArray()) {
                throw unavailable();
            }
            return item.path("busy");
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    static ApiException unavailable() {
        return new ApiException(ErrorCode.ENGINE_UNAVAILABLE, "Google 캘린더를 불러오지 못했어요. 다시 연결해 주세요.");
    }
}
