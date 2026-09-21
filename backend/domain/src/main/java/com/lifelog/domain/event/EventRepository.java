package com.lifelog.domain.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository {
    Event save(Event event);
    Optional<Event> findById(Long id);
    Page<Event> findByUserIdAndDateRange(Long userId, LocalDateTime from, LocalDateTime to, Pageable pageable);
    List<Event> findAllByUserIdAndDateRange(Long userId, LocalDateTime from, LocalDateTime to);
    void delete(Event event);
}
