package com.lifelog.domain.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EventRepository {
    Event save(Event event);
    Optional<Event> findById(Long id);
    Page<Event> findByUserIdAndDateRange(Long userId, LocalDateTime from, LocalDateTime to, Pageable pageable);
    void delete(Event event);
}
