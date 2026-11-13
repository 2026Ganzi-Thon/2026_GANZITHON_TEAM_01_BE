package com.som.settings.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.settings.dto.OnboardingAnswers;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OnboardingRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OnboardingRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<OnboardingAnswers> find(Long userId) {
        return jdbc.query("SELECT answers FROM onboarding_answers WHERE user_id = ?",
                (row, index) -> decode(row.getString(1)), userId).stream().findFirst();
    }

    public void save(Long userId, OnboardingAnswers answers) {
        try {
            jdbc.update("""
                    INSERT INTO onboarding_answers (user_id, answers) VALUES (?, ?)
                    ON CONFLICT (user_id) DO UPDATE SET answers = EXCLUDED.answers
                    """, userId, json.writeValueAsString(answers));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not encode onboarding answers");
        }
    }

    private OnboardingAnswers decode(String answers) {
        try {
            return json.readValue(answers, OnboardingAnswers.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored onboarding answers");
        }
    }
}
