package com.lifelog.event.dto;

import com.lifelog.domain.event.Event;
import com.lifelog.domain.event.EventCategory;
import java.time.LocalDateTime;

public record EventResponse(
        Long id, String title, String description,
        LocalDateTime startAt, LocalDateTime endAt,
        boolean allDay, String location, String color,
        EventCategory eventCategory,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static EventResponse from(Event e) {
        return new EventResponse(e.getId(), e.getTitle(), e.getDescription(),
                e.getStartAt(), e.getEndAt(), e.isAllDay(),
                e.getLocation(), e.getColor(), e.getEventCategory(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
