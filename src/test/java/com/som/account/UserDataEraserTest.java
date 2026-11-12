package com.som.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.account.service.DeletionScope;
import com.som.account.service.UserDataEraser;
import com.som.support.ApiTestSupport;
import com.som.support.TestSupportConfig;
import com.som.support.UserDataSeeder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 사용자 소유 데이터 연쇄 삭제(A_SPEC §11). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class UserDataEraserTest {

    static final List<String> KEPT_ON_DATA = List.of("connections", "notification_settings", "user_consents");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserDataEraser eraser;

    @AfterEach
    void cleanUp() {
        ApiTestSupport.deleteUsers(jdbc, "ers");
    }

    @Test
    void 기록_삭제는_기록만_지우고_계정과_동의와_설정은_남기며_다른_사용자는_그대로() throws Exception {
        UserDataSeeder.seedAll(mockMvc, objectMapper, "ers01a");
        UserDataSeeder.seedAll(mockMvc, objectMapper, "ers01b");
        Long a = UserDataSeeder.userId(jdbc, "ers01a");
        Long b = UserDataSeeder.userId(jdbc, "ers01b");
        for (String table : UserDataSeeder.USER_TABLES) {
            assertThat(UserDataSeeder.rows(jdbc, table, a)).as("seeded " + table).isPositive();
        }

        eraser.erase(a, DeletionScope.DATA);

        for (String table : UserDataSeeder.USER_TABLES) {
            int expected = KEPT_ON_DATA.contains(table) ? 1 : 0;
            if (expected == 0) {
                assertThat(UserDataSeeder.rows(jdbc, table, a)).as("A " + table).isZero();
            } else {
                assertThat(UserDataSeeder.rows(jdbc, table, a)).as("A kept " + table).isPositive();
            }
            assertThat(UserDataSeeder.rows(jdbc, table, b)).as("B " + table).isPositive();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, a)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkin_stressors s JOIN checkins c ON c.id = s.checkin_id "
                + "WHERE c.user_id = ?", Integer.class, a)).isZero();
    }

    @Test
    void 탈퇴는_모든_테이블에서_그_사용자만_지운다() throws Exception {
        UserDataSeeder.seedAll(mockMvc, objectMapper, "ers02a");
        UserDataSeeder.seedAll(mockMvc, objectMapper, "ers02b");
        Long a = UserDataSeeder.userId(jdbc, "ers02a");
        Long b = UserDataSeeder.userId(jdbc, "ers02b");

        eraser.erase(a, DeletionScope.ACCOUNT);

        for (String table : UserDataSeeder.USER_TABLES) {
            assertThat(UserDataSeeder.rows(jdbc, table, a)).as("A " + table).isZero();
            assertThat(UserDataSeeder.rows(jdbc, table, b)).as("B " + table).isPositive();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, a)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, b)).isEqualTo(1);
    }

    @Test
    void user_id_컬럼이_있는_모든_테이블을_탈퇴_삭제가_덮는다() {
        List<String> withUserId = jdbc.queryForList("""
                SELECT table_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND column_name = 'user_id'
                """, String.class);
        Set<String> covered = new HashSet<>(UserDataEraser.tablesFor(DeletionScope.ACCOUNT));

        assertThat(covered).containsAll(withUserId);
        assertThat(withUserId).doesNotContain("deletion_requests"); // 익명화: user_id 없음
    }
}
