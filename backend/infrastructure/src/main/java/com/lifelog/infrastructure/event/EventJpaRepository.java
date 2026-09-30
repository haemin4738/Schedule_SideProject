package com.lifelog.infrastructure.event;

import com.lifelog.domain.event.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

// 기간 조회는 [from, to] 와 겹치는 일정을 모두 반환한다 (from 이전에 시작해 기간 안에 끝나거나 걸쳐 있는 여러 날 일정 포함).
// 종료 시각이 없는 일정은 시작 시각을 종료 시각으로 본다
interface EventJpaRepository extends JpaRepository<Event, Long> {

    @Query("SELECT e FROM Event e WHERE e.user.id = :userId " +
           "AND (:from IS NULL OR COALESCE(e.endAt, e.startAt) >= :from) " +
           "AND (:to IS NULL OR e.startAt <= :to)")
    Page<Event> findByUserIdAndDateRange(@Param("userId") Long userId,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.user.id = :userId " +
           "AND (:from IS NULL OR COALESCE(e.endAt, e.startAt) >= :from) " +
           "AND (:to IS NULL OR e.startAt <= :to) " +
           "ORDER BY e.startAt ASC")
    List<Event> findAllByUserIdAndDateRange(@Param("userId") Long userId,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);
}
