package com.lifelog.specialday;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.specialday.SpecialDay;
import com.lifelog.domain.specialday.SpecialDayData;
import com.lifelog.domain.specialday.SpecialDayKind;
import com.lifelog.domain.specialday.SpecialDayRepository;
import com.lifelog.domain.specialday.SpecialDaySource;
import com.lifelog.domain.specialday.SpecialDaySourceException;
import com.lifelog.domain.specialday.SpecialDayYear;
import com.lifelog.domain.specialday.SpecialDayYearRepository;
import com.lifelog.specialday.dto.SpecialDayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SpecialDayService 단위 테스트 — 외부 출처는 Mock, 저장소는 인메모리 가짜, 시각은 고정(가변) Clock.
 * 기준 시각: 2026-09-30 12:00 (Asia/Seoul) → 허용 연도 2004~2028.
 */
@ExtendWith(OutputCaptureExtension.class)
class SpecialDayServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");

    private MutableClock clock;
    private SpecialDaySource source;
    private FakeSpecialDayRepository dayRepository;
    private FakeYearRepository yearRepository;
    private SpecialDayService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        source = mock(SpecialDaySource.class);
        when(source.isConfigured()).thenReturn(true);
        dayRepository = new FakeSpecialDayRepository();
        yearRepository = new FakeYearRepository();
        service = new SpecialDayService(source, dayRepository, yearRepository,
                new SpecialDaySyncWriter(dayRepository, yearRepository), clock);
    }

    private static SpecialDayData data(int year, int month, int day, SpecialDayKind kind, String name) {
        return new SpecialDayData(LocalDate.of(year, month, day), kind, name, kind == SpecialDayKind.HOLIDAY);
    }

    private static final List<SpecialDayData> DATA_2026 = List.of(
            data(2026, 1, 1, SpecialDayKind.HOLIDAY, "1월1일"),
            data(2026, 3, 1, SpecialDayKind.HOLIDAY, "삼일절"),
            data(2026, 3, 3, SpecialDayKind.ANNIVERSARY, "납세자의 날"),
            data(2026, 3, 5, SpecialDayKind.SOLAR_TERM, "경칩"));

    private void markSynced(int year, SpecialDayData... days) {
        dayRepository.replaceYear(year, java.util.Arrays.stream(days).map(SpecialDay::from).toList());
        yearRepository.save(SpecialDayYear.create(year, LocalDateTime.of(2026, 1, 1, 4, 0)));
    }

    // ---------- 미동기화 / 동기화 ----------

    @Test
    void list_whenYearNotSynced_fetchesSavesAndReturnsRangeFromDb(CapturedOutput output) {
        when(source.fetchYear(2026)).thenReturn(DATA_2026);

        List<SpecialDayResponse> result = service.list(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).containsExactly(
                new SpecialDayResponse(LocalDate.of(2026, 3, 1), "삼일절", SpecialDayKind.HOLIDAY, true),
                new SpecialDayResponse(LocalDate.of(2026, 3, 3), "납세자의 날", SpecialDayKind.ANNIVERSARY, false),
                new SpecialDayResponse(LocalDate.of(2026, 3, 5), "경칩", SpecialDayKind.SOLAR_TERM, false));
        assertThat(dayRepository.all(2026)).hasSize(4);
        // synced_at 은 Asia/Seoul 현지 시각
        assertThat(yearRepository.findByYear(2026)).get()
                .extracting(SpecialDayYear::getSyncedAt).isEqualTo(LocalDateTime.of(2026, 9, 30, 12, 0));
        assertThat(output).contains("특일 동기화 완료: year=2026, count=4");
    }

    @Test
    void list_whenYearAlreadySynced_doesNotCallSource() {
        markSynced(2026, data(2026, 5, 5, SpecialDayKind.HOLIDAY, "어린이날"));

        List<SpecialDayResponse> result = service.list(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertThat(result).extracting(SpecialDayResponse::name).containsExactly("어린이날");
        verify(source, never()).fetchYear(anyInt());
    }

    @Test
    void list_whenSyncedYearHasNoData_returnsEmptyWithoutFetching() {
        markSynced(2026);

        assertThat(service.list(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31))).isEmpty();
        verify(source, never()).fetchYear(anyInt());
    }

    @Test
    void list_whenRangeSpansTwoYears_syncsOnlyUnsyncedYear() {
        markSynced(2026, data(2026, 12, 25, SpecialDayKind.HOLIDAY, "기독탄신일"));
        when(source.fetchYear(2027)).thenReturn(List.of(data(2027, 1, 1, SpecialDayKind.HOLIDAY, "1월1일")));

        List<SpecialDayResponse> result = service.list(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 31));

        assertThat(result).extracting(SpecialDayResponse::name).containsExactly("기독탄신일", "1월1일");
        verify(source, never()).fetchYear(2026);
        verify(source).fetchYear(2027);
    }

    // ---------- 외부 장애 ----------

    @Test
    void list_whenFetchFailsWithExistingRows_returnsExistingDataAndWarns(CapturedOutput output) {
        // 연도 표시 없이 행만 남아 있는 경우(예: 이전 동기화 흔적)에도 기존 데이터를 돌려준다
        dayRepository.replaceYear(2026, List.of(SpecialDay.from(data(2026, 3, 1, SpecialDayKind.HOLIDAY, "삼일절"))));
        when(source.fetchYear(2026)).thenThrow(new SpecialDaySourceException("special-day getRestDeInfo failed: HTTP 500"));

        List<SpecialDayResponse> result = service.list(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).extracting(SpecialDayResponse::name).containsExactly("삼일절");
        assertThat(yearRepository.findByYear(2026)).isEmpty();
        assertThat(output).contains("특일 외부 조회 실패", "year=2026", "HTTP 500");
    }

    @Test
    void list_whenFetchFailsWithoutData_returnsEmptyList() {
        when(source.fetchYear(2026)).thenThrow(new SpecialDaySourceException("timeout"));

        assertThat(service.list(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))).isEmpty();
    }

    @Test
    void list_whenFetchFailed_backsOffForTenMinutes() {
        when(source.fetchYear(2026)).thenThrow(new SpecialDaySourceException("timeout"));
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        service.list(from, to);
        clock.advance(SpecialDayService.FAILURE_BACKOFF.minusSeconds(1));
        service.list(from, to);
        verify(source, times(1)).fetchYear(2026);

        clock.advance(Duration.ofSeconds(1));
        service.list(from, to);
        verify(source, times(2)).fetchYear(2026);
    }

    @Test
    void list_whenBackoffOnlyForFailedYear_otherYearStillFetched() {
        when(source.fetchYear(2026)).thenThrow(new SpecialDaySourceException("timeout"));
        when(source.fetchYear(2027)).thenReturn(List.of());

        service.list(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        service.list(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 31));

        verify(source, times(1)).fetchYear(2026);
        verify(source, times(1)).fetchYear(2027);
    }

    @Test
    void list_whenSaveFails_logsErrorAndBacksOff(CapturedOutput output) {
        when(source.fetchYear(2026)).thenReturn(DATA_2026);
        dayRepository.failOnReplace = true;

        assertThat(service.list(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))).isEmpty();
        service.list(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        verify(source, times(1)).fetchYear(2026);
        assertThat(yearRepository.findByYear(2026)).isEmpty();
        assertThat(output).contains("특일 저장 실패", "cause=IllegalStateException");
    }

    // ---------- 키 미설정 / 허용 연도 ----------

    @Test
    void list_whenSourceNotConfigured_skipsFetchAndReturnsDbData() {
        when(source.isConfigured()).thenReturn(false);
        dayRepository.replaceYear(2026, List.of(SpecialDay.from(data(2026, 3, 1, SpecialDayKind.HOLIDAY, "삼일절"))));

        assertThat(service.list(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1))).hasSize(1);
        verify(source, never()).fetchYear(anyInt());
    }

    @Test
    void list_whenYearOutsideAllowedRange_skipsFetch() {
        when(source.fetchYear(2028)).thenReturn(List.of());
        when(source.fetchYear(2004)).thenReturn(List.of());

        service.list(LocalDate.of(2028, 12, 1), LocalDate.of(2029, 1, 31));
        service.list(LocalDate.of(2003, 12, 1), LocalDate.of(2004, 1, 31));

        verify(source).fetchYear(2028);
        verify(source).fetchYear(2004);
        verify(source, never()).fetchYear(2029);
        verify(source, never()).fetchYear(2003);
    }

    // ---------- 정규화 ----------

    @Test
    void list_whenSourceReturnsDuplicatesOrOtherYear_savesUniqueItemsOfThatYearOnly() {
        when(source.fetchYear(2026)).thenReturn(List.of(
                data(2026, 7, 17, SpecialDayKind.ANNIVERSARY, "제헌절"),
                data(2026, 7, 17, SpecialDayKind.ANNIVERSARY, "제헌절"),
                data(2026, 7, 17, SpecialDayKind.HOLIDAY, "제헌절"),
                data(2027, 1, 1, SpecialDayKind.HOLIDAY, "1월1일")));

        List<SpecialDayResponse> result = service.list(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(result).extracting(SpecialDayResponse::kind)
                .containsExactly(SpecialDayKind.HOLIDAY, SpecialDayKind.ANNIVERSARY);
        assertThat(dayRepository.all(2026)).hasSize(2);
    }

    // ---------- 입력 검증 ----------

    @Test
    void list_whenFromAfterTo_throwsBadRequest() {
        assertBadRequest(() -> service.list(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 1)));
    }

    @Test
    void list_whenRangeExceeds366Days_throwsBadRequest() {
        assertBadRequest(() -> service.list(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2)));
        verify(source, never()).fetchYear(anyInt());
    }

    @Test
    void list_whenRangeExactly366Days_isAllowed() {
        when(source.fetchYear(anyInt())).thenReturn(List.of());

        assertThat(service.list(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))).isEmpty();
    }

    @Test
    void list_whenFromOrToNull_throwsBadRequest() {
        assertBadRequest(() -> service.list(null, LocalDate.of(2026, 3, 1)));
        assertBadRequest(() -> service.list(LocalDate.of(2026, 3, 1), null));
    }

    private static void assertBadRequest(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------- 동시성 ----------

    @Test
    void list_whenConcurrentRequestsForUnsyncedYear_fetchesOnce() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(source.fetchYear(2026)).thenAnswer(inv -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return DATA_2026;
        });
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);

        CompletableFuture<List<SpecialDayResponse>> first = CompletableFuture.supplyAsync(() -> service.list(from, to));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        Thread[] secondThread = new Thread[1];
        CompletableFuture<List<SpecialDayResponse>> second = CompletableFuture.supplyAsync(() -> {
            secondThread[0] = Thread.currentThread();
            return service.list(from, to);
        });
        // 두 번째 요청이 연도 락에서 대기할 때까지 기다린 뒤 첫 요청을 끝낸다
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((secondThread[0] == null || secondThread[0].getState() != Thread.State.WAITING)
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        release.countDown();

        assertThat(first.get(5, TimeUnit.SECONDS)).hasSize(4);
        assertThat(second.get(5, TimeUnit.SECONDS)).hasSize(4);
        verify(source, times(1)).fetchYear(2026);
    }

    // ---------- refresh (정기 동기화) ----------

    @Test
    void refresh_whenAlreadySynced_fetchesAgainAndReplaces() {
        markSynced(2026, data(2026, 1, 1, SpecialDayKind.HOLIDAY, "옛 데이터"));
        when(source.fetchYear(2026)).thenReturn(DATA_2026);

        assertThat(service.refresh(2026)).isTrue();

        assertThat(dayRepository.all(2026)).extracting(SpecialDay::getName).doesNotContain("옛 데이터").hasSize(4);
        assertThat(yearRepository.findByYear(2026)).get()
                .extracting(SpecialDayYear::getSyncedAt).isEqualTo(LocalDateTime.of(2026, 9, 30, 12, 0));
    }

    @Test
    void refresh_whenInBackoff_stillFetches() {
        when(source.fetchYear(2026)).thenThrow(new SpecialDaySourceException("timeout")).thenReturn(DATA_2026);
        service.list(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

        assertThat(service.refresh(2026)).isTrue();
        verify(source, times(2)).fetchYear(2026);
    }

    @Test
    void refresh_whenFetchFails_keepsExistingDataAndReturnsFalse() {
        markSynced(2026, data(2026, 1, 1, SpecialDayKind.HOLIDAY, "1월1일"));
        when(source.fetchYear(2026)).thenThrow(new SpecialDaySourceException("timeout"));

        assertThat(service.refresh(2026)).isFalse();
        assertThat(dayRepository.all(2026)).extracting(SpecialDay::getName).containsExactly("1월1일");
    }

    @Test
    void refresh_whenNotConfiguredOrOutsideRange_returnsFalseWithoutFetch() {
        assertThat(service.refresh(2029)).isFalse();
        assertThat(service.refresh(2003)).isFalse();
        when(source.isConfigured()).thenReturn(false);
        assertThat(service.refresh(2026)).isFalse();

        verify(source, never()).fetchYear(anyInt());
        assertThat(service.isSourceConfigured()).isFalse();
    }

    @Test
    void currentYear_whenUtcIsPreviousYear_usesSeoulDate() {
        clock.set(Instant.parse("2026-12-31T15:30:00Z"));   // 서울 2027-01-01 00:30

        assertThat(service.currentYear()).isEqualTo(2027);
    }

    // ---------- 테스트 더블 ----------

    static final class MutableClock extends Clock {
        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    /** 연도별로 통째 교체하는 인메모리 저장소 — 정렬은 실제 쿼리와 같은 날짜 → 종류 → 이름 */
    static final class FakeSpecialDayRepository implements SpecialDayRepository {
        private final Map<Integer, List<SpecialDay>> byYear = new ConcurrentHashMap<>();
        volatile boolean failOnReplace;

        @Override
        public List<SpecialDay> findByDateBetween(LocalDate from, LocalDate to) {
            List<SpecialDay> result = new ArrayList<>();
            byYear.values().forEach(days -> days.stream()
                    .filter(d -> !d.getSolDate().isBefore(from) && !d.getSolDate().isAfter(to))
                    .forEach(result::add));
            result.sort(Comparator.comparing(SpecialDay::getSolDate)
                    .thenComparing(SpecialDay::getKind)
                    .thenComparing(SpecialDay::getName));
            return result;
        }

        @Override
        public void replaceYear(int year, List<SpecialDay> days) {
            if (failOnReplace) {
                throw new IllegalStateException("db down");
            }
            byYear.put(year, List.copyOf(days));
        }

        List<SpecialDay> all(int year) {
            return byYear.getOrDefault(year, List.of());
        }
    }

    static final class FakeYearRepository implements SpecialDayYearRepository {
        private final Map<Integer, SpecialDayYear> years = new ConcurrentHashMap<>();

        @Override
        public Optional<SpecialDayYear> findByYear(int year) {
            return Optional.ofNullable(years.get(year));
        }

        @Override
        public SpecialDayYear save(SpecialDayYear year) {
            years.put(year.getSolYear(), year);
            return year;
        }
    }
}
