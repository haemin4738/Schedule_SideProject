package com.lifelog.event;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.event.Event;
import com.lifelog.domain.event.EventRepository;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
import com.lifelog.event.dto.EventRequest;
import com.lifelog.event.dto.EventResponse;
import com.lifelog.event.dto.EventSummary;
import com.lifelog.sse.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventCacheService eventCacheService;
    private final SseService sseService;

    @Transactional
    public EventResponse create(Long userId, EventRequest request) {
        User user = getUser(userId);
        Event event = Event.create(user, request.title(), request.description(),
                request.startAt(), request.endAt(), request.allDay(),
                request.location(), request.color(), request.eventCategory());
        EventResponse response = EventResponse.from(eventRepository.save(event));
        eventCacheService.evictAll();
        sseService.publish(userId);
        return response;
    }

    @Transactional(readOnly = true)
    public Page<EventSummary> list(Long userId, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        List<EventSummary> all = eventCacheService.findByUserAndRange(userId, from, to);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<EventSummary> slice = start >= all.size() ? List.of() : all.subList(start, end);
        return new PageImpl<>(slice, pageable, all.size());
    }

    @Transactional(readOnly = true)
    public EventResponse get(Long userId, Long eventId) {
        return EventResponse.from(getOwnedEvent(userId, eventId));
    }

    @Transactional
    public EventResponse update(Long userId, Long eventId, EventRequest request) {
        Event event = getOwnedEvent(userId, eventId);
        event.update(request.title(), request.description(), request.startAt(),
                request.endAt(), request.allDay(), request.location(),
                request.color(), request.eventCategory());
        EventResponse response = EventResponse.from(eventRepository.save(event));
        eventCacheService.evictAll();
        sseService.publish(userId);
        return response;
    }

    @Transactional
    public void delete(Long userId, Long eventId) {
        Event event = getOwnedEvent(userId, eventId);
        eventRepository.delete(event);
        eventCacheService.evictAll();
        sseService.publish(userId);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("사용자를 찾을 수 없습니다."));
    }

    private Event getOwnedEvent(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> BusinessException.notFound("일정을 찾을 수 없습니다."));
        if (!event.getUser().getId().equals(userId)) {
            throw BusinessException.forbidden("본인의 일정만 접근할 수 있습니다.");
        }
        return event;
    }
}
