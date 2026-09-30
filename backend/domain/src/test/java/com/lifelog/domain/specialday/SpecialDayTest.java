package com.lifelog.domain.specialday;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SpecialDay / SpecialDayYear / SpecialDayData 도메인 단위 테스트 */
class SpecialDayTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 1);

    @Test
    void create_whenValid_setsAllFields() {
        SpecialDay day = SpecialDay.create(DATE, SpecialDayKind.HOLIDAY, "삼일절", true);

        assertThat(day.getId()).isNull();
        assertThat(day.getSolDate()).isEqualTo(DATE);
        assertThat(day.getKind()).isEqualTo(SpecialDayKind.HOLIDAY);
        assertThat(day.getName()).isEqualTo("삼일절");
        assertThat(day.isHoliday()).isTrue();
    }

    @Test
    void from_whenData_copiesFields() {
        SpecialDay day = SpecialDay.from(new SpecialDayData(DATE, SpecialDayKind.SOLAR_TERM, "경칩", false));

        assertThat(day.getKind()).isEqualTo(SpecialDayKind.SOLAR_TERM);
        assertThat(day.getName()).isEqualTo("경칩");
        assertThat(day.isHoliday()).isFalse();
    }

    @Test
    void create_whenDateOrKindNull_throwsException() {
        assertThatThrownBy(() -> SpecialDay.create(null, SpecialDayKind.HOLIDAY, "삼일절", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpecialDay.create(DATE, null, "삼일절", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void create_whenNameBlank_throwsException(String name) {
        assertThatThrownBy(() -> SpecialDay.create(DATE, SpecialDayKind.HOLIDAY, name, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_whenNameTooLong_throwsException() {
        String name = "가".repeat(SpecialDay.NAME_MAX_LENGTH + 1);

        assertThatThrownBy(() -> SpecialDay.create(DATE, SpecialDayKind.ANNIVERSARY, name, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SpecialDay.create(DATE, SpecialDayKind.ANNIVERSARY, "가".repeat(SpecialDay.NAME_MAX_LENGTH), false)
                .getName()).hasSize(SpecialDay.NAME_MAX_LENGTH);
    }

    @Test
    void specialDayYear_whenCreatedAndMarked_updatesSyncedAt() {
        LocalDateTime first = LocalDateTime.of(2026, 1, 1, 4, 0);
        LocalDateTime second = first.plusDays(1);

        SpecialDayYear year = SpecialDayYear.create(2026, first);
        year.markSynced(second);

        assertThat(year.getSolYear()).isEqualTo(2026);
        assertThat(year.getSyncedAt()).isEqualTo(second);
    }

    @Test
    void specialDayYear_whenSyncedAtNull_throwsException() {
        assertThatThrownBy(() -> SpecialDayYear.create(2026, null)).isInstanceOf(IllegalArgumentException.class);
        SpecialDayYear year = SpecialDayYear.create(2026, LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThatThrownBy(() -> year.markSynced(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void key_whenSameDateKindName_isEqualRegardlessOfHoliday() {
        SpecialDayData a = new SpecialDayData(DATE, SpecialDayKind.ANNIVERSARY, "납세자의 날", false);
        SpecialDayData b = new SpecialDayData(DATE, SpecialDayKind.ANNIVERSARY, "납세자의 날", true);

        assertThat(a.key()).isEqualTo(b.key());
        assertThat(a.key()).isNotEqualTo(new SpecialDayData(DATE, SpecialDayKind.HOLIDAY, "납세자의 날", false).key());
    }
}
