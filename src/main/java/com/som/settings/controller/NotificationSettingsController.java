package com.som.settings.controller;

import com.som.auth.web.CurrentUserId;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.som.settings.dto.NotificationSettingsResponse;
import com.som.settings.service.NotificationSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 알림 설정. 필수 약관 게이트 대상. 사용자는 세션의 @CurrentUserId로만 정한다. */
@RestController
@RequestMapping("/api/v1/me/notification-settings")
public class NotificationSettingsController {

    private final NotificationSettingsService service;

    public NotificationSettingsController(NotificationSettingsService service) {
        this.service = service;
    }

    @GetMapping
    NotificationSettingsResponse get(@CurrentUserId Long userId) {
        return service.get(userId);
    }

    /** 보낸 필드만 바꾼다: 필드 없음 = 그대로, null = 지움. 허용 필드는 enabled·slot·time("HH:mm"). */
    @PatchMapping
    NotificationSettingsResponse update(@CurrentUserId Long userId, @RequestBody ObjectNode body) {
        return service.update(userId, body);
    }
}
