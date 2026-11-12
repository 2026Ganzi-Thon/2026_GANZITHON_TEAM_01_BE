package com.som.auth.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 테스트 전용 보호 엔드포인트: @CurrentUserId가 세션의 userId만 돌려주는지 확인한다. */
@RestController
class CurrentUserTestController {

    record NoteForm(String note) {}

    /** 쿼리의 userId는 받지만 쓰지 않는다(무시되는지 확인용). */
    @GetMapping("/test/auth/me")
    Map<String, Object> me(@CurrentUserId Long userId, @RequestParam(name = "userId", required = false) Long ignored) {
        return Map.of("userId", userId);
    }

    @PostMapping("/test/auth/me")
    Map<String, Object> post(@CurrentUserId Long userId, @RequestBody NoteForm form) {
        return Map.of("userId", userId);
    }
}
