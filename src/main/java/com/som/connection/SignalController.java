package com.som.connection;

import com.som.auth.web.CurrentUserId;
import com.som.connection.dto.SignalIngestRequest;
import com.som.connection.dto.SignalIngestResponse;
import com.som.connection.service.SignalService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 연동 데이터 수신(데모·개발용). som.signals.ingest-enabled=true일 때만 등록되고, 꺼져 있으면 경로가 없어 404.
 * 필수 약관 게이트 대상. 사용자는 세션의 @CurrentUserId로만 정한다.
 */
@RestController
@RequestMapping("/api/v1/signals")
@ConditionalOnProperty(prefix = "som.signals", name = "ingest-enabled", havingValue = "true")
public class SignalController {

    private final SignalService signalService;

    public SignalController(SignalService signalService) {
        this.signalService = signalService;
    }

    @PostMapping
    SignalIngestResponse ingest(@CurrentUserId Long userId, @Valid @RequestBody SignalIngestRequest request) {
        return signalService.ingest(userId, request);
    }
}
