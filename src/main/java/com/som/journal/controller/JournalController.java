package com.som.journal.controller;

import com.som.auth.web.CurrentUserId;
import com.som.journal.dto.JournalRequest;
import com.som.journal.dto.JournalResponse;
import com.som.journal.service.JournalService;
import java.time.LocalDate;
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

/** 마음 정리. 필수 약관 게이트 대상. 사용자는 세션의 @CurrentUserId로만 정한다. */
@RestController
@RequestMapping("/api/v1/journal")
public class JournalController {

    private final JournalService journalService;

    public JournalController(JournalService journalService) {
        this.journalService = journalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    JournalResponse write(@CurrentUserId Long userId, @RequestBody JournalRequest body) {
        return journalService.write(userId, body);
    }

    @GetMapping
    List<JournalResponse> list(@CurrentUserId Long userId,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return journalService.list(userId, date);
    }
}
