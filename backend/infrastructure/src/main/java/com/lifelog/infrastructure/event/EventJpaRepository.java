package com.lifelog.infrastructure.event;

import com.lifelog.domain.event.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

interface EventJpaRepository extends JpaRepository<Event, Long> {

    @Query("SELECT e FROM Event e WHERE e.user.id = :userId " +
           "AND (:from IS NULL OR e.startAt >= :from) " +
           "AND (:to IS NULL OR e.startAt <= :to)")
    Page<Event> findByUserIdAndDateRange(@Param("userId") Long userId,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         Pageable pageable);
}
