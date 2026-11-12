package com.som.checkin.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.som.auth.domain.User;
import com.som.auth.repository.UserRepository;
import com.som.checkin.domain.Checkin;
import com.som.checkin.domain.CheckinStatus;
import com.som.checkin.domain.Mood;
import com.som.checkin.domain.Stressor;
import com.som.checkin.domain.SupportNeed;
import com.som.common.time.ServiceDates;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class CheckinRepositoryTest {

    static final LocalDate DAY = LocalDate.of(2026, 11, 11);

    @Autowired CheckinRepository checkins;
    @Autowired UserRepository users;
    @Autowired ServiceDates dates;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    @AfterEach
    void cleanUp() {
        String ids = "(SELECT id FROM users WHERE login_id LIKE 'ckr%')";
        jdbc.update("DELETE FROM checkin_stressors WHERE checkin_id IN (SELECT id FROM checkins WHERE user_id IN " + ids + ")");
        jdbc.update("DELETE FROM checkins WHERE user_id IN " + ids);
        jdbc.update("DELETE FROM fatigue_snapshots WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE 'ckr%')");
        jdbc.update("DELETE FROM users WHERE login_id LIKE 'ckr%'");
    }

    private Long user(String loginId) {
        return users.saveAndFlush(User.register(loginId, "x", UUID.randomUUID().toString(), dates.now())).getId();
    }

    @Test
    void 저장하고_다시_읽으면_stressors까지_같다() {
        Long userId = user("ckr01");
        checkins.saveAndFlush(Checkin.create(userId, DAY, Mood.TIRED, List.of(Stressor.WORK), SupportNeed.COMFORT,
                dates.now()));

        Checkin found = tx.execute(s -> {
            Checkin c = checkins.findByUserIdAndLocalDate(userId, DAY).orElseThrow();
            c.getStressors().size();
            return c;
        });
        assertThat(found.getMood()).isEqualTo(Mood.TIRED);
        assertThat(found.getStressors()).containsExactly(Stressor.WORK);
        assertThat(found.getSupportNeed()).isEqualTo(SupportNeed.COMFORT);
        assertThat(found.getStatus()).isEqualTo(CheckinStatus.SUBMITTED);
    }

    @Test
    void 같은_사용자_같은_날짜를_두_번_insert하면_유니크_위반() {
        Long userId = user("ckr02");
        checkins.saveAndFlush(Checkin.create(userId, DAY, Mood.CALM, List.of(), null, dates.now()));

        assertThatThrownBy(() -> checkins.saveAndFlush(
                Checkin.create(userId, DAY, Mood.OKAY, List.of(), null, dates.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_사용자는_같은_날짜에_각자_저장된다() {
        Long a = user("ckr03a");
        Long b = user("ckr03b");
        checkins.saveAndFlush(Checkin.create(a, DAY, Mood.CALM, List.of(), null, dates.now()));
        checkins.saveAndFlush(Checkin.create(b, DAY, Mood.TIRED, List.of(), null, dates.now()));

        assertThat(checkins.findByUserIdAndLocalDate(a, DAY).orElseThrow().getMood()).isEqualTo(Mood.CALM);
        assertThat(checkins.findByUserIdAndLocalDate(b, DAY).orElseThrow().getMood()).isEqualTo(Mood.TIRED);
    }

    @Test
    void 세_값이_모두_없으면_SKIPPED_하나라도_있으면_SUBMITTED() {
        assertThat(Checkin.create(1L, DAY, null, List.of(), null, dates.now()).getStatus())
                .isEqualTo(CheckinStatus.SKIPPED);
        assertThat(Checkin.create(1L, DAY, null, List.of(), SupportNeed.VENT, dates.now()).getStatus())
                .isEqualTo(CheckinStatus.SUBMITTED);
    }

    @Test
    void 재제출은_값을_덮어쓰고_상태를_다시_정한다() {
        Checkin checkin = Checkin.create(1L, DAY, Mood.CALM, List.of(Stressor.WORK), null, dates.now());
        checkin.update(null, List.of(), null, dates.now());

        assertThat(checkin.getMood()).isNull();
        assertThat(checkin.getStressors()).isEmpty();
        assertThat(checkin.getStatus()).isEqualTo(CheckinStatus.SKIPPED);
    }

    @Test
    void 기간_조회는_날짜_오름차순이고_범위_밖은_제외() {
        Long userId = user("ckr04");
        for (LocalDate d : List.of(DAY.plusDays(2), DAY, DAY.plusDays(1), DAY.plusDays(5))) {
            checkins.saveAndFlush(Checkin.create(userId, d, Mood.OKAY, List.of(), null, dates.now()));
        }

        assertThat(checkins.findByUserIdAndLocalDateBetweenOrderByLocalDate(userId, DAY, DAY.plusDays(2))
                .stream().map(Checkin::getLocalDate).toList())
                .containsExactly(DAY, DAY.plusDays(1), DAY.plusDays(2));
    }

    @Test
    void DB가_모르는_enum_값을_거부한다() {
        Long userId = user("ckr05");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO checkins (user_id, local_date, mood, status, created_at, updated_at)
                VALUES (?, ?, 'HAPPY', 'SUBMITTED', now(), now())
                """, userId, DAY)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
