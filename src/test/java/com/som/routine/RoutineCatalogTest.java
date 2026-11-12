package com.som.routine;

import static org.assertj.core.api.Assertions.assertThat;

import com.som.routine.domain.RoutineCatalogItem;
import com.som.routine.repository.RoutineCatalogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import com.som.support.TestSupportConfig;

/** 루틴 카탈로그 시드(A_SPEC §9). */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class RoutineCatalogTest {

    @Autowired RoutineCatalogRepository catalog;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 카탈로그_4건이_화면_순서와_타이머_값으로_시드된다() {
        List<RoutineCatalogItem> items = catalog.findAllByOrderByDisplayOrder();

        assertThat(items).extracting(RoutineCatalogItem::getRoutineId)
                .containsExactly("BREATHING", "SHOULDER_ROLL", "SEATED_TWIST", "WRIST_ARM_STRETCH");
        assertThat(items).extracting(RoutineCatalogItem::getDurationSec).containsExactly(60, 30, 30, 30);
        assertThat(items.get(0).getTitle()).isEqualTo("숨 고르기");
        assertThat(items.get(0).getAssetKey()).isEqualTo("routine_breathing");
        assertThat(items).allSatisfy(i -> assertThat(i.getInstruction()).isNotBlank());
        assertThat(catalog.findById("SEATED_TWIST")).isPresent();
        assertThat(catalog.findById("UNKNOWN")).isEmpty();
    }

    @Test
    void 카탈로그는_사용자_데이터가_아니다() {
        List<String> columns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'routine_catalog'
                """, String.class);
        assertThat(columns).isNotEmpty().doesNotContain("user_id");
    }
}
