package com.lifelog.specialday;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class SpecialDaySyncSchedulerTest {

    private SpecialDayService service;
    private SpecialDaySyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        service = mock(SpecialDayService.class);
        scheduler = new SpecialDaySyncScheduler(service);
        when(service.isSourceConfigured()).thenReturn(true);
        when(service.currentYear()).thenReturn(2026);
    }

    @Test
    void syncCurrentAndNextYear_whenConfigured_refreshesThisAndNextYear(CapturedOutput output) {
        when(service.refresh(2026)).thenReturn(true);
        when(service.refresh(2027)).thenReturn(true);

        scheduler.syncCurrentAndNextYear();

        verify(service).refresh(2026);
        verify(service).refresh(2027);
        assertThat(output).doesNotContain("특일 정기 동기화 실패");
    }

    @Test
    void syncCurrentAndNextYear_whenOneYearFails_warnsAndContinues(CapturedOutput output) {
        when(service.refresh(2026)).thenReturn(false);
        when(service.refresh(2027)).thenReturn(true);

        scheduler.syncCurrentAndNextYear();

        verify(service).refresh(2027);
        assertThat(output).contains("특일 정기 동기화 실패: year=2026").doesNotContain("year=2027");
    }

    @Test
    void syncCurrentAndNextYear_whenNotConfigured_skips() {
        when(service.isSourceConfigured()).thenReturn(false);

        scheduler.syncCurrentAndNextYear();

        verify(service, never()).refresh(anyInt());
    }

    @Test
    void annotations_whenInspected_useSeoulCronAndEnabledProperty() throws NoSuchMethodException {
        Scheduled scheduled = SpecialDaySyncScheduler.class.getMethod("syncCurrentAndNextYear").getAnnotation(Scheduled.class);
        ConditionalOnProperty condition = SpecialDaySyncScheduler.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(scheduled.cron()).isEqualTo("${special-day.sync.cron:0 0 4 * * *}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
        assertThat(condition.name()).containsExactly("special-day.sync.enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isTrue();
    }
}
