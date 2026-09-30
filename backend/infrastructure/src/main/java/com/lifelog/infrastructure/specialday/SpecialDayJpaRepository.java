package com.lifelog.infrastructure.specialday;

import com.lifelog.domain.specialday.SpecialDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

interface SpecialDayJpaRepository extends JpaRepository<SpecialDay, Long> {

    // kind 는 VARCHAR 로 저장되므로 문자열 순이 아니라 enum 선언 순서(HOLIDAY → ANNIVERSARY → SOLAR_TERM)로 정렬
    @Query("SELECT s FROM SpecialDay s " +
           "WHERE s.solDate BETWEEN :from AND :to " +
           "ORDER BY s.solDate ASC, " +
           "CASE s.kind " +
           "WHEN com.lifelog.domain.specialday.SpecialDayKind.HOLIDAY THEN 0 " +
           "WHEN com.lifelog.domain.specialday.SpecialDayKind.ANNIVERSARY THEN 1 " +
           "ELSE 2 END ASC, " +
           "s.name ASC")
    List<SpecialDay> findByDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SpecialDay s WHERE s.solDate BETWEEN :from AND :to")
    int deleteByDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
