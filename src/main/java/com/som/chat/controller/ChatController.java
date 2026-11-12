package com.som.chat.controller;

import com.som.auth.web.CurrentUserId;
import com.som.chat.dto.ChatMessageRequest;
import com.som.chat.dto.ChatPage;
import com.som.chat.dto.ChatTurnResponse;
import com.som.chat.service.ChatGreetingService;
import com.som.chat.service.ChatService;
import com.som.common.ratelimit.RateLimitProperties;
import com.som.common.ratelimit.RateLimitedException;
import com.som.common.ratelimit.RateLimiter;
import com.som.common.time.ServiceDates;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 대화. 필수 약관 게이트 대상. 사용자는 세션의 @CurrentUserId로만 정한다. "대화 마치기" API는 없다(A_SPEC §8). */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties.Chat limits;
    private final ChatGreetingService greetings;
    private final ServiceDates dates;

    public ChatController(ChatService chatService, RateLimiter rateLimiter, RateLimitProperties rateLimits,
                          ChatGreetingService greetings, ServiceDates dates) {
        this.chatService = chatService;
        this.greetings = greetings;
        this.dates = dates;
        this.rateLimiter = rateLimiter;
        this.limits = rateLimits.chat();
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    ChatTurnResponse send(@CurrentUserId Long userId, @RequestBody ChatMessageRequest body) {
        RateLimiter.Decision perMinute = rateLimiter.tryConsume("chat:minute:" + userId, limits.perUserMinute());
        RateLimiter.Decision perDay = rateLimiter.tryConsume("chat:day:" + userId, limits.perUserDay());
        if (!perMinute.allowed() || !perDay.allowed()) {
            throw new RateLimitedException(Math.max(perMinute.retryAfterSeconds(), perDay.retryAfterSeconds()));
        }
        greetings.ensureToday(userId); // 그날 첫 메시지는 선제 인사(A_SPEC §7)
        return chatService.send(userId, body.text());
    }

    @GetMapping("/messages")
    ChatPage messages(@CurrentUserId Long userId,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                      @RequestParam(required = false) String cursor,
                      @RequestParam(required = false) Integer size) {
        if (date.equals(dates.today()) && cursor == null) {
            greetings.ensureToday(userId); // 오늘 대화를 처음 열 때 선제 인사(하루 1회)
        }
        return chatService.page(userId, date, cursor, size);
    }

    @GetMapping("/dates")
    List<LocalDate> dates(@CurrentUserId Long userId,
                          @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return chatService.dates(userId, month);
    }
}
