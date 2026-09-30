package com.lifelog.infrastructure.specialday;

import com.lifelog.domain.specialday.SpecialDay;
import com.lifelog.domain.specialday.SpecialDayKind;
import com.lifelog.domain.specialday.SpecialDayRepository;
import com.lifelog.domain.specialday.SpecialDayYear;
import com.lifelog.domain.specialday.SpecialDayYearRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SpecialDayRepositoryImpl / SpecialDayYearRepositoryImpl 통합 테스트.
 * 실제 MySQL(test profile, lifelog_test, ddl-auto=create-drop). 테스트마다 롤백되며,
 * 다른 테스트와 겹치지 않도록 미래 연도(2090~)만 사용한다.
 * RestClient 가 필요한 클라이언트 빈은 슬라이스에 없으므로 저장소 구현만 직접 import 한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.lifelog.domain")
@EnableJpaRepositories(basePackages = "com.lifelog.infrastructure")
@Import({SpecialDayRepositoryImpl.class, SpecialDayYearRepositoryImpl.class})
class SpecialDayRepositoryImplTest {

    @Autowired
    private SpecialDayRepository specialDayRepository;

    @Autowired
    private SpecialDayYearRepository specialDayYearRepository;

    @Autowired
    private EntityManager entityManager;

    private static SpecialDay day(LocalDate date, SpecialDayKind kind, String name) {
        return SpecialDay.create(date, kind, name, kind == SpecialDayKind.HOLIDAY);
    }

    @Test
    void findByDateBetween_whenRange_returnsInclusiveSortedByDateKindName() {
        LocalDate d1 = LocalDate.of(2090, 3, 1);
        LocalDate d2 = LocalDate.of(2090, 3, 5);
        specialDayRepository.replaceYear(2090, List.of(
                day(d2, SpecialDayKind.SOLAR_TERM, "경칩"),
                day(d1, SpecialDayKind.SOLAR_TERM, "절기"),
                day(d1, SpecialDayKind.ANNIVERSARY, "나기념일"),
                day(d1, SpecialDayKind.ANNIVERSARY, "가기념일"),
                day(d1, SpecialDayKind.HOLIDAY, "삼일절"),
                day(LocalDate.of(2090, 2, 28), SpecialDayKind.HOLIDAY, "범위 전"),
                day(LocalDate.of(2090, 3, 6), SpecialDayKind.HOLIDAY, "범위 후")));
        entityManager.clear();

        List<SpecialDay> result = specialDayRepository.findByDateBetween(d1, d2);

        assertThat(result).extracting(SpecialDay::getName)
                .containsExactly("삼일절", "가기념일", "나기념일", "절기", "경칩");
        assertThat(result.get(0).getId()).isNotNull();
        assertThat(result.get(0).isHoliday()).isTrue();
        assertThat(result.get(0).getKind()).isEqualTo(SpecialDayKind.HOLIDAY);
    }

    @Test
    void replaceYear_whenCalledAgain_replacesOnlyThatYear() {
        specialDayRepository.replaceYear(2091, List.of(
                day(LocalDate.of(2091, 1, 1), SpecialDayKind.HOLIDAY, "1월1일"),
                day(LocalDate.of(2091, 12, 31), SpecialDayKind.ANNIVERSARY, "연말")));
        specialDayRepository.replaceYear(2092, List.of(
                day(LocalDate.of(2092, 1, 1), SpecialDayKind.HOLIDAY, "1월1일")));

        // 같은 키를 다시 넣어도 유니크 충돌 없이 교체된다
        specialDayRepository.replaceYear(2091, List.of(
                day(LocalDate.of(2091, 1, 1), SpecialDayKind.HOLIDAY, "1월1일"),
                day(LocalDate.of(2091, 6, 6), SpecialDayKind.HOLIDAY, "현충일")));
        entityManager.clear();

        assertThat(specialDayRepository.findByDateBetween(LocalDate.of(2091, 1, 1), LocalDate.of(2091, 12, 31)))
                .extracting(SpecialDay::getName).containsExactly("1월1일", "현충일");
        assertThat(specialDayRepository.findByDateBetween(LocalDate.of(2092, 1, 1), LocalDate.of(2092, 12, 31)))
                .extracting(SpecialDay::getName).containsExactly("1월1일");
    }

    @Test
    void replaceYear_whenEmptyList_deletesYear() {
        specialDayRepository.replaceYear(2093, List.of(day(LocalDate.of(2093, 5, 5), SpecialDayKind.HOLIDAY, "어린이날")));

        specialDayRepository.replaceYear(2093, List.of());
        entityManager.clear();

        assertThat(specialDayRepository.findByDateBetween(LocalDate.of(2093, 1, 1), LocalDate.of(2093, 12, 31))).isEmpty();
    }

    @Test
    void replaceYear_whenDayOutsideYear_throwsException() {
        assertThatThrownBy(() -> specialDayRepository.replaceYear(2094,
                List.of(day(LocalDate.of(2095, 1, 1), SpecialDayKind.HOLIDAY, "다른 연도"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replaceYear_whenDuplicateKey_violatesUniqueConstraint() {
        LocalDate date = LocalDate.of(2096, 1, 1);

        assertThatThrownBy(() -> specialDayRepository.replaceYear(2096, List.of(
                day(date, SpecialDayKind.HOLIDAY, "1월1일"),
                day(date, SpecialDayKind.HOLIDAY, "1월1일"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void replaceYear_whenSameDateNameDifferentKind_allowsBoth() {
        LocalDate date = LocalDate.of(2097, 3, 1);
        specialDayRepository.replaceYear(2097, List.of(
                day(date, SpecialDayKind.HOLIDAY, "삼일절"),
                day(date, SpecialDayKind.ANNIVERSARY, "삼일절")));
        entityManager.clear();

        assertThat(specialDayRepository.findByDateBetween(date, date)).hasSize(2);
    }

    @Test
    void specialDayYear_whenSavedAndUpdated_findsLatestSyncedAt() {
        LocalDateTime first = LocalDateTime.of(2090, 1, 1, 4, 0);
        specialDayYearRepository.save(SpecialDayYear.create(2098, first));
        entityManager.flush();
        entityManager.clear();

        SpecialDayYear found = specialDayYearRepository.findByYear(2098).orElseThrow();
        found.markSynced(first.plusDays(1));
        entityManager.flush();
        entityManager.clear();

        assertThat(specialDayYearRepository.findByYear(2098)).get()
                .extracting(SpecialDayYear::getSyncedAt).isEqualTo(first.plusDays(1));
        assertThat(specialDayYearRepository.findByYear(2099)).isEmpty();
    }
}
