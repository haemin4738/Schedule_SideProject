package com.lifelog.domain.specialday;

import java.time.LocalDate;
import java.util.List;

public interface SpecialDayRepository {

    /** from/to 양끝 포함. 날짜 → 종류({@link SpecialDayKind} 선언 순서) → 이름 순 정렬 */
    List<SpecialDay> findByDateBetween(LocalDate from, LocalDate to);

    /** 해당 연도(1/1~12/31)의 특일을 모두 지우고 days 로 교체. 트랜잭션 안에서 호출해야 한다. */
    void replaceYear(int year, List<SpecialDay> days);
}
