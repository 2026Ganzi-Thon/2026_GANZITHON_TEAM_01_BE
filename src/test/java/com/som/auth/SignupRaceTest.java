package com.som.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.som.auth.domain.User;
import com.som.auth.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** 사전 중복 검사를 통과한 뒤 유니크 제약에 걸리는 동시 가입 상황. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignupRaceTest {

    @Autowired MockMvc mockMvc;
    @MockitoSpyBean UserRepository users;

    private Long existingId;

    @AfterEach
    void cleanUp() {
        if (existingId != null) {
            users.deleteById(existingId);
        }
    }

    @Test
    void 사전검사를_통과해도_유니크_제약_위반이면_409_CONFLICT() throws Exception {
        existingId = users.saveAndFlush(User.register("surace01", "$argon2id$dummy",
                UUID.randomUUID().toString(), OffsetDateTime.now())).getId();
        doReturn(false).when(users).existsByLoginId(anyString()); // 다른 요청이 먼저 가입한 직후 상황

        mockMvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"surace01\",\"password\":\"abcde12345\",\"passwordConfirm\":\"abcde12345\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("이미 사용 중인 아이디예요."));
    }
}
