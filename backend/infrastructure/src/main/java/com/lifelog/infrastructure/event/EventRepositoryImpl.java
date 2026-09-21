package com.lifelog.infrastructure.event;

import com.lifelog.domain.event.Event;
import com.lifelog.domain.event.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EventRepositoryImpl implements EventRepository {

    private final EventJpaRepository jpa;

    @Override public Event save(Event event) { return jpa.save(event); }
    @Override public Optional<Event> findById(Long id) { return jpa.findById(id); }
    @Override public void delete(Event event) { jpa.delete(event); }

    @Override
    public Page<Event> findByUserIdAndDateRange(Long userId, LocalDateTime from,
                                                LocalDateTime to, Pageable pageable) {
        return jpa.findByUserIdAndDateRange(userId, from, to, pageable);
    }

    @Override
    public List<Event> findAllByUserIdAndDateRange(Long userId, LocalDateTime from, LocalDateTime to) {
        return jpa.findAllByUserIdAndDateRange(userId, from, to);
    }
}
