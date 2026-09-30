package com.lifelog.infrastructure.specialday;

import com.lifelog.domain.specialday.SpecialDayYear;
import com.lifelog.domain.specialday.SpecialDayYearRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SpecialDayYearRepositoryImpl implements SpecialDayYearRepository {

    private final SpecialDayYearJpaRepository jpa;

    @Override
    public Optional<SpecialDayYear> findByYear(int year) {
        return jpa.findById(year);
    }

    @Override
    public SpecialDayYear save(SpecialDayYear year) {
        return jpa.save(year);
    }
}
