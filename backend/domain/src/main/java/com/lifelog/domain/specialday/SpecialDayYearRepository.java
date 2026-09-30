package com.lifelog.domain.specialday;

import java.util.Optional;

public interface SpecialDayYearRepository {

    Optional<SpecialDayYear> findByYear(int year);

    SpecialDayYear save(SpecialDayYear year);
}
