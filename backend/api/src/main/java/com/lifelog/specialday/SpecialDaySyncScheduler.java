package com.lifelog.specialday;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 04:00(Asia/Seoul) 올해·내년 특일을 다시 받는다 — 임시공휴일 지정 등 변경 반영(최대 1일 지연).
 * {@code special-day.sync.enabled=false} 면 빈을 등록하지 않는다(테스트 프로필).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "special-day.sync.enabled", havingValue = "true", matchIfMissing = true)
public class SpecialDaySyncScheduler {

    private final SpecialDayService specialDayService;

    @Scheduled(cron = "${special-day.sync.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void syncCurrentAndNextYear() {
        if (!specialDayService.isSourceConfigured()) {
            return;
        }
        int year = specialDayService.currentYear();
        for (int target : new int[]{year, year + 1}) {
            if (!specialDayService.refresh(target)) {
                log.warn("특일 정기 동기화 실패: year={}", target);
            }
        }
    }
}
