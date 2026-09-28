package com.lifelog.event;

import com.lifelog.domain.event.EventRepository;
import com.lifelog.event.dto.EventSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventCacheService {

    private final EventRepository eventRepository;

    @Cacheable(value = "events", key = "{ #userId, #from, #to }")
    @Transactional(readOnly = true)
    public List<EventSummary> findByUserAndRange(Long userId, LocalDateTime from, LocalDateTime to) {
        return eventRepository.findAllByUserIdAndDateRange(userId, from, to)
                .stream().map(EventSummary::from)
                // Redis 캐시 직렬화기가 JDK 불변 리스트(Stream.toList)의 타입 정보를 복원하지 못하므로 ArrayList로 반환한다
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // ponytail: allEntries=true clears all users' caches; acceptable for personal-scale app
    @CacheEvict(value = "events", allEntries = true)
    public void evictAll() {}
}
