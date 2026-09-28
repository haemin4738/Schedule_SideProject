package com.lifelog.event;

import com.lifelog.domain.event.Event;
import com.lifelog.domain.event.EventCategory;
import com.lifelog.domain.event.EventRepository;
import com.lifelog.domain.user.User;
import com.lifelog.event.dto.EventSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventCacheServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventCacheService eventCacheService;

    @Test
    void findByUserAndRange_whenCalled_returnsMutableArrayListForRedisSerialization() {
        User user = User.create("owner@test.com", "encoded-pw", "소유자");
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 30, 23, 59);
        Event event = Event.create(user, "회의", null,
                LocalDateTime.of(2026, 9, 28, 10, 0), null,
                false, null, null, EventCategory.WORK);
        when(eventRepository.findAllByUserIdAndDateRange(1L, from, to)).thenReturn(List.of(event));

        List<EventSummary> result = eventCacheService.findByUserAndRange(1L, from, to);

        // Redis 캐시 직렬화기는 JDK 불변 리스트를 역직렬화하지 못한다 (RedisConfigTest 참고)
        assertThat(result).isExactlyInstanceOf(ArrayList.class);
        assertThat(result).extracting(EventSummary::title).containsExactly("회의");
    }
}
