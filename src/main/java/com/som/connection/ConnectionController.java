package com.som.connection;

import com.som.auth.web.CurrentUserId;
import com.som.connection.domain.DataSource;
import com.som.connection.dto.ConnectionChangeRequest;
import com.som.connection.dto.ConnectionResponse;
import com.som.connection.service.ConnectionService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 연동 소스 선택 동의·연결. 필수 약관 게이트 대상. 사용자는 세션의 @CurrentUserId로만 정한다. 모르는 source는 400. */
@RestController
@RequestMapping("/api/v1/me/connections")
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping
    List<ConnectionResponse> list(@CurrentUserId Long userId) {
        return connectionService.list(userId);
    }

    /** 바디는 받지 않는다(생략 가능). 필드가 오면 400 — 고지 내용·userId는 서버가 정한다. */
    @PostMapping("/{source}")
    ConnectionResponse connect(@CurrentUserId Long userId, @PathVariable DataSource source,
                               @RequestBody(required = false) ConnectionChangeRequest body) {
        return connectionService.connect(userId, source);
    }

    @DeleteMapping("/{source}")
    ConnectionResponse revoke(@CurrentUserId Long userId, @PathVariable DataSource source,
                              @RequestBody(required = false) ConnectionChangeRequest body) {
        return connectionService.revoke(userId, source);
    }
}
