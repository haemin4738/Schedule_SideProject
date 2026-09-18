package com.lifelog.event.dto;

import com.lifelog.domain.event.Event;
import com.lifelog.domain.event.EventCategory;
import java.time.LocalDateTime;

public record EventSummary(
        Long id, String title,
        LocalDateTime startAt, LocalDateTime endAt,
        boolean allDay, String color, EventCategory eventCategory
) {
    public static EventSummary from(Event e) {
        return new EventSummary(e.getId(), e.getTitle(),
                e.getStartAt(), e.getEndAt(),
                e.isAllDay(), e.getColor(), e.getEventCategory());
    }
}
