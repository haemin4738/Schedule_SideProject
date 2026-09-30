package com.lifelog.infrastructure.specialday;

import com.lifelog.domain.specialday.SpecialDayYear;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpecialDayYearJpaRepository extends JpaRepository<SpecialDayYear, Integer> {
}
