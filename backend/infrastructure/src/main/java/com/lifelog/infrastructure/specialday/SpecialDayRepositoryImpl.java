package com.lifelog.infrastructure.specialday;

import com.lifelog.domain.specialday.SpecialDay;
import com.lifelog.domain.specialday.SpecialDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SpecialDayRepositoryImpl implements SpecialDayRepository {

    private final SpecialDayJpaRepository jpa;

    @Override
    public List<SpecialDay> findByDateBetween(LocalDate from, LocalDate to) {
        return jpa.findByDateBetween(from, to);
    }

    /** 벌크 delete 가 먼저 실행되므로 같은 (날짜, 종류, 이름)을 다시 넣어도 유니크 충돌이 없다 */
    @Override
    public void replaceYear(int year, List<SpecialDay> days) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        boolean outOfYear = days.stream().anyMatch(d -> d.getSolDate().getYear() != year);
        if (outOfYear) {
            throw new IllegalArgumentException("교체 대상 연도 밖의 특일이 포함되어 있습니다.");
        }
        jpa.deleteByDateBetween(from, to);
        jpa.saveAll(days);
        jpa.flush();
    }
}
