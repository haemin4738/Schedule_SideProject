package com.lifelog.specialday;

import com.lifelog.domain.specialday.SpecialDay;
import com.lifelog.domain.specialday.SpecialDayData;
import com.lifelog.domain.specialday.SpecialDayKind;
import com.lifelog.domain.specialday.SpecialDayRepository;
import com.lifelog.domain.specialday.SpecialDayYear;
import com.lifelog.domain.specialday.SpecialDayYearRepository;
import com.lifelog.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 특일 전체 컨텍스트 통합 테스트 — 실제 MySQL(lifelog_test). 테스트 트랜잭션을 쓰지 않으므로(@Transactional 없음)
 * {@link SpecialDaySyncWriter} 의 실제 커밋·롤백이 그대로 검증된다. 다른 테스트와 겹치지 않는 연도(2095)만 쓰고
 * 종료 시 JDBC 로 직접 정리한다. test 프로필은 인증키가 비어 있어 외부 API 를 호출하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpecialDayIntegrationTest {

    private static final int YEAR = 2095;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SpecialDaySyncWriter writer;

    @Autowired
    private SpecialDayRepository specialDayRepository;

    @Autowired
    private SpecialDayYearRepository specialDayYearRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanUp() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement days = connection.prepareStatement(
                     "DELETE FROM special_days WHERE sol_date BETWEEN ? AND ?");
             PreparedStatement years = connection.prepareStatement(
                     "DELETE FROM special_day_years WHERE sol_year = ?")) {
            days.setDate(1, Date.valueOf(LocalDate.of(YEAR, 1, 1)));
            days.setDate(2, Date.valueOf(LocalDate.of(YEAR, 12, 31)));
            days.executeUpdate();
            years.setInt(1, YEAR);
            years.executeUpdate();
        }
    }

    private static SpecialDayData data(int month, int day, SpecialDayKind kind, String name) {
        return new SpecialDayData(LocalDate.of(YEAR, month, day), kind, name, kind == SpecialDayKind.HOLIDAY);
    }

    private List<String> namesOfYear() {
        return specialDayRepository.findByDateBetween(LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31))
                .stream().map(SpecialDay::getName).toList();
    }

    @Test
    void context_whenTestProfileWithoutServiceKey_startsWithoutSpecialDaySyncScheduler() {
        // DATA_GO_KR_SERVICE_KEY 없이 기동되고, test 프로필(special-day.sync.enabled=false)에서는 정기 동기화 빈이 없다
        assertThat(context.getBeanNamesForType(SpecialDaySyncScheduler.class)).isEmpty();
        assertThat(context.getBean(SpecialDayService.class).isSourceConfigured()).isFalse();
    }

    @Test
    void specialDays_whenServiceKeyNotConfigured_returnsOkWithArrayWithoutExternalCall() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(1L);

        mockMvc.perform(get("/api/v1/special-days").param("from", "2026-01-01").param("to", "2026-12-31")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void replace_whenCommitted_persistsDaysAndSyncedAt() {
        LocalDateTime syncedAt = LocalDateTime.of(2090, 1, 1, 4, 0);

        writer.replace(YEAR, List.of(data(1, 1, SpecialDayKind.HOLIDAY, "1월1일")), syncedAt);
        writer.replace(YEAR, List.of(data(3, 1, SpecialDayKind.HOLIDAY, "삼일절")), syncedAt.plusDays(1));

        assertThat(namesOfYear()).containsExactly("삼일절");
        assertThat(specialDayYearRepository.findByYear(YEAR)).get()
                .extracting(SpecialDayYear::getSyncedAt).isEqualTo(syncedAt.plusDays(1));
    }

    @Test
    void replace_whenUniqueViolated_rollsBackDeleteAndSyncedAt() {
        LocalDateTime syncedAt = LocalDateTime.of(2090, 1, 1, 4, 0);
        writer.replace(YEAR, List.of(
                data(1, 1, SpecialDayKind.HOLIDAY, "1월1일"),
                data(3, 1, SpecialDayKind.HOLIDAY, "삼일절")), syncedAt);

        // 같은 (날짜, 종류, 이름) 두 건 → insert 중 유니크 위반 → 앞선 delete 와 synced_at 갱신까지 롤백
        List<SpecialDayData> duplicated = List.of(
                data(5, 5, SpecialDayKind.HOLIDAY, "어린이날"),
                data(5, 5, SpecialDayKind.HOLIDAY, "어린이날"));
        assertThatThrownBy(() -> writer.replace(YEAR, duplicated, syncedAt.plusDays(1)))
                .isInstanceOf(DataAccessException.class);

        assertThat(namesOfYear()).containsExactly("1월1일", "삼일절");
        assertThat(specialDayYearRepository.findByYear(YEAR)).get()
                .extracting(SpecialDayYear::getSyncedAt).isEqualTo(syncedAt);
    }
}
