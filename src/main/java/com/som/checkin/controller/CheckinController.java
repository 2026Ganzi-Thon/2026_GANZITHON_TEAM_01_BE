package com.som.checkin.controller;

import com.som.auth.web.CurrentUserId;
import com.som.checkin.dto.CheckinRequest;
import com.som.checkin.dto.CheckinResponse;
import com.som.checkin.service.CheckinService;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.idempotency.IdempotentResult;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 하루 체크인. 필수 약관 게이트 대상. 사용자는 세션의 @CurrentUserId로만 정한다. */
@RestController
@RequestMapping("/api/v1/checkins")
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    @PostMapping
    ResponseEntity<CheckinResponse> submit(@CurrentUserId Long userId, @RequestBody CheckinRequest body,
                                           @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        IdempotentResult<CheckinResponse> result = checkinService.submit(userId, body, key);
        return ResponseEntity.status(result.status()).body(result.body());
    }

    /** ?date= 하나 또는 ?from=&to= 기간(둘 중 하나만). */
    @GetMapping
    ResponseEntity<?> find(@CurrentUserId Long userId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        boolean single = date != null;
        boolean range = from != null || to != null;
        if (single == range) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "date: date 또는 from·to 중 하나만 보내 주세요");
        }
        if (single) {
            return ResponseEntity.ok(checkinService.get(userId, date));
        }
        if (from == null || to == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, (from == null ? "from" : "to") + ": 값이 필요합니다");
        }
        return ResponseEntity.ok(checkinService.list(userId, from, to));
    }
}
