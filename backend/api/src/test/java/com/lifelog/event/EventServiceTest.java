package com.lifelog.event;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.event.Event;
import com.lifelog.domain.event.EventCategory;
import com.lifelog.domain.event.EventRepository;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
import com.lifelog.event.dto.EventRequest;
import com.lifelog.event.dto.EventResponse;
import com.lifelog.event.dto.EventSummary;
import com.lifelog.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * EventService 단위 테스트. Repository/Cache/SSE는 Mock으로 대체한다 (순수 단위 테스트).
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EventCacheService eventCacheService;
    @Mock
    private SseService sseService;

    @InjectMocks
    private EventService eventService;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        owner = User.create("owner@test.com", "encoded-pw", "소유자");
        other = User.create("other@test.com", "encoded-pw", "타인");
        setId(owner, 1L);
        setId(other, 2L);
    }

    // User.id는 @GeneratedValue라 테스트에서 직접 세팅 필요 (리플렉션)
    private void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Event ownedEvent(Long id, User user) {
        Event event = Event.create(user, "제목", "설명",
                LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 11, 0),
                false, "장소", "#FFFFFF", EventCategory.PERSONAL);
        setId(event, id);
        return event;
    }

    private EventRequest sampleRequest() {
        return new EventRequest("제목", "설명",
                LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 11, 0),
                false, "장소", "#FFFFFF", EventCategory.WORK);
    }

    @Test
    void createEvent_whenUserExists_savesAndReturnsResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event e = invocation.getArgument(0);
            setId(e, 100L);
            return e;
        });

        EventResponse response = eventService.create(1L, sampleRequest());

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.title()).isEqualTo("제목");
        verify(eventCacheService).evictAll();
        verify(sseService).publish(1L);
    }

    @Test
    void createEvent_whenUserNotFound_throwsNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.create(999L, sampleRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(eventRepository, eventCacheService, sseService);
    }

    @Test
    void getEvent_whenOwnedByUser_returnsResponse() {
        Event event = ownedEvent(10L, owner);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        EventResponse response = eventService.get(1L, 10L);

        assertThat(response.id()).isEqualTo(10L);
    }

    @Test
    void getEvent_whenNotOwnedByUser_throwsForbidden() {
        Event event = ownedEvent(10L, owner);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.get(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getEvent_whenEventNotFound_throwsNotFound() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.get(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateEvent_whenOwnedByUser_updatesAndReturnsResponse() {
        Event event = ownedEvent(10L, owner);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventRequest updateRequest = new EventRequest("변경된 제목", "변경된 설명",
                LocalDateTime.of(2026, 2, 1, 9, 0), LocalDateTime.of(2026, 2, 1, 10, 0),
                true, "새 장소", "#000000", EventCategory.REMINDER);

        EventResponse response = eventService.update(1L, 10L, updateRequest);

        assertThat(response.title()).isEqualTo("변경된 제목");
        assertThat(response.eventCategory()).isEqualTo(EventCategory.REMINDER);
        verify(eventCacheService).evictAll();
        verify(sseService).publish(1L);
    }

    @Test
    void updateEvent_whenNotOwnedByUser_throwsForbiddenAndDoesNotSave() {
        Event event = ownedEvent(10L, owner);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.update(2L, 10L, sampleRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(eventCacheService, sseService);
    }

    @Test
    void deleteEvent_whenOwnedByUser_deletesEvent() {
        Event event = ownedEvent(10L, owner);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        eventService.delete(1L, 10L);

        verify(eventRepository).delete(event);
        verify(eventCacheService).evictAll();
        verify(sseService).publish(1L);
    }

    @Test
    void deleteEvent_whenNotOwnedByUser_throwsForbiddenAndDoesNotDelete() {
        Event event = ownedEvent(10L, owner);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.delete(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(eventRepository, never()).delete(any());
        verifyNoInteractions(eventCacheService, sseService);
    }

    @Test
    void listEvents_whenWithinSinglePage_returnsAllAsOnePage() {
        List<EventSummary> summaries = List.of(
                new EventSummary(1L, "일정1", LocalDateTime.of(2026, 1, 1, 9, 0), null, false, "#FFF", EventCategory.PERSONAL),
                new EventSummary(2L, "일정2", LocalDateTime.of(2026, 1, 2, 9, 0), null, false, "#FFF", EventCategory.WORK)
        );
        when(eventCacheService.findByUserAndRange(eq(1L), any(), any())).thenReturn(summaries);
        Pageable pageable = PageRequest.of(0, 20);

        var page = eventService.list(1L, null, null, pageable);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void listEvents_whenPageOffsetBeyondSize_returnsEmptyContent() {
        List<EventSummary> summaries = List.of(
                new EventSummary(1L, "일정1", LocalDateTime.of(2026, 1, 1, 9, 0), null, false, "#FFF", EventCategory.PERSONAL)
        );
        when(eventCacheService.findByUserAndRange(eq(1L), any(), any())).thenReturn(summaries);
        Pageable pageable = PageRequest.of(5, 20);

        var page = eventService.list(1L, null, null, pageable);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listEvents_whenSecondPageRequested_returnsRemainderOnly() {
        List<EventSummary> summaries = List.of(
                new EventSummary(1L, "일정1", LocalDateTime.of(2026, 1, 1, 9, 0), null, false, "#FFF", EventCategory.PERSONAL),
                new EventSummary(2L, "일정2", LocalDateTime.of(2026, 1, 2, 9, 0), null, false, "#FFF", EventCategory.WORK),
                new EventSummary(3L, "일정3", LocalDateTime.of(2026, 1, 3, 9, 0), null, false, "#FFF", EventCategory.OTHER)
        );
        when(eventCacheService.findByUserAndRange(eq(1L), any(), any())).thenReturn(summaries);
        Pageable pageable = PageRequest.of(1, 2);

        var page = eventService.list(1L, null, null, pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).id()).isEqualTo(3L);
    }
}
