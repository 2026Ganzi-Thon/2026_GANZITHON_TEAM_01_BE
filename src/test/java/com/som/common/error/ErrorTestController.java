package com.som.common.error;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 테스트 전용: 상황별 예외를 일으켜 GlobalExceptionHandler 응답을 확인한다. */
@RestController
class ErrorTestController {

    enum Mood { CALM, TIRED }

    record SignupForm(@NotBlank String loginId, @NotNull @Size(min = 10, max = 12) String password) {}

    record MoodForm(@NotNull Mood mood) {}

    private final Validator validator;

    ErrorTestController(Validator validator) {
        this.validator = validator;
    }

    @PostMapping("/test/errors/signup")
    Map<String, String> signup(@Valid @RequestBody SignupForm form) {
        return Map.of("ok", "true");
    }

    @PostMapping("/test/errors/mood")
    Map<String, String> mood(@Valid @RequestBody MoodForm form) {
        return Map.of("mood", form.mood().name());
    }

    @GetMapping("/test/errors/param")
    Map<String, String> param(@RequestParam @Size(max = 3) String name) {
        return Map.of("name", name);
    }

    @GetMapping("/test/errors/typed")
    Map<String, Integer> typed(@RequestParam int days) {
        return Map.of("days", days);
    }

    @PostMapping("/test/errors/violation")
    Map<String, String> violation(@RequestBody Map<String, String> body) {
        var violations = validator.validate(new SignupForm("user", body.get("password")));
        throw new ConstraintViolationException(violations);
    }

    @GetMapping("/test/errors/api")
    Map<String, String> api() {
        throw new ApiException(ErrorCode.CONFLICT);
    }

    @GetMapping("/test/errors/api-message")
    Map<String, String> apiMessage() {
        throw new ApiException(ErrorCode.CONSENT_REQUIRED, "필수 약관 동의가 필요합니다.");
    }

    @GetMapping("/test/errors/boom")
    Map<String, String> boom() {
        throw new IllegalStateException("boom-internal-detail");
    }
}
