package com.lifelog.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisConfigTest {

    record CachedItem(Long id, String title, LocalDateTime startAt, boolean allDay) {}

    private final RedisSerializer<Object> serializer = RedisConfig.cacheValueSerializer();

    private Object roundTrip(Object value) {
        return serializer.deserialize(serializer.serialize(value));
    }

    @Test
    void cacheValueSerializer_whenImmutableListFromStream_failsToDeserialize() {
        // JDK 불변 리스트는 타입 정보 없이 기록되어 읽을 수 없다 → 캐시 대상 메서드는 ArrayList를 반환해야 한다 (EventCacheService)
        byte[] bytes = serializer.serialize(Stream.of(
                new CachedItem(1L, "회의", LocalDateTime.of(2026, 9, 28, 10, 0), false)).toList());

        assertThatThrownBy(() -> serializer.deserialize(bytes)).isInstanceOf(SerializationException.class);
    }

    @Test
    void cacheValueSerializer_whenEmptyArrayList_roundTrips() {
        assertThat(roundTrip(new ArrayList<>())).isEqualTo(List.of());
    }

    @Test
    void cacheValueSerializer_whenArrayList_roundTrips() {
        List<CachedItem> value = new ArrayList<>(List.of(
                new CachedItem(2L, "운동", LocalDateTime.of(2026, 9, 29, 7, 30), true)));

        assertThat(roundTrip(value)).isEqualTo(value);
    }

    @Test
    void cacheValueSerializer_whenNull_roundTripsAsNull() {
        assertThat(serializer.deserialize(serializer.serialize(null))).isNull();
    }
}
