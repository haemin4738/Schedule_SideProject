package com.lifelog.specialday;

import com.lifelog.domain.specialday.SpecialDay;
import com.lifelog.domain.specialday.SpecialDayData;
import com.lifelog.domain.specialday.SpecialDayRepository;
import com.lifelog.domain.specialday.SpecialDayYear;
import com.lifelog.domain.specialday.SpecialDayYearRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 외부에서 받은 연도 데이터를 한 트랜잭션으로 반영한다(연도 delete + insert, synced_at upsert).
 * 외부 호출은 트랜잭션 밖({@link SpecialDayService})에서 끝낸 뒤 이 빈을 호출한다.
 */
@Component
@RequiredArgsConstructor
public class SpecialDaySyncWriter {

    private final SpecialDayRepository specialDayRepository;
    private final SpecialDayYearRepository specialDayYearRepository;

    @Transactional
    public void replace(int year, List<SpecialDayData> days, LocalDateTime syncedAt) {
        specialDayRepository.replaceYear(year, days.stream().map(SpecialDay::from).toList());
        specialDayYearRepository.findByYear(year).ifPresentOrElse(
                synced -> synced.markSynced(syncedAt),
                () -> specialDayYearRepository.save(SpecialDayYear.create(year, syncedAt)));
    }
}
