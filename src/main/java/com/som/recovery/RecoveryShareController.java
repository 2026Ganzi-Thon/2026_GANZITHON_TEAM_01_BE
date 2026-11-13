package com.som.recovery;

import com.som.auth.web.CurrentUserId;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.time.ServiceDates;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recovery-shares")
public class RecoveryShareController {
    private final JdbcTemplate jdbc;
    private final ServiceDates dates;

    public RecoveryShareController(JdbcTemplate jdbc, ServiceDates dates) {
        this.jdbc = jdbc;
        this.dates = dates;
    }

    public record ShareInput(@NotBlank @Size(max = 200) String message) {
        @Override
        public String toString() { return "ShareInput[redacted]"; }
    }

    public record Share(long id, String message, boolean mine) {}

    @GetMapping
    List<Share> list(@CurrentUserId Long userId) {
        return jdbc.query("""
                SELECT id, message, user_id = ? AS mine FROM recovery_shares
                ORDER BY id DESC LIMIT 30
                """, (row, index) -> new Share(row.getLong("id"), row.getString("message"),
                row.getBoolean("mine")), userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Share share(@CurrentUserId Long userId, @Valid @RequestBody ShareInput input) {
        String message = input.message().strip();
        if (message.codePoints().anyMatch(Character::isISOControl)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "message: 한 줄로 작성해 주세요");
        }
        OffsetDateTime now = dates.now();
        long id = jdbc.queryForObject("""
                INSERT INTO recovery_shares (user_id, message, created_at) VALUES (?, ?, ?) RETURNING id
                """, Long.class, userId, message, now);
        return new Share(id, message, true);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@CurrentUserId Long userId, @PathVariable long id) {
        if (jdbc.update("DELETE FROM recovery_shares WHERE id = ? AND user_id = ?", id, userId) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }
}
