package com.lifelog.domain.event;

import com.lifelog.domain.user.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EventTest {

    @Test
    void update_whenCalled_refreshesUpdatedAtBeforeFlush() {
        Event event = Event.create(User.create("a@test.com", "pw", "사용자"), "제목", null,
                LocalDateTime.of(2026, 10, 1, 9, 0), null, false, null, null, null);
        LocalDateTime before = LocalDateTime.now();

        event.update("새 제목", null, LocalDateTime.of(2026, 10, 1, 10, 0), null, false, null, null, null);

        // 수정 응답에 쓰이는 updatedAt 이 flush(@PreUpdate) 전에도 갱신돼 있어야 한다
        assertThat(event.getUpdatedAt()).isAfterOrEqualTo(before);
        assertThat(event.getTitle()).isEqualTo("새 제목");
    }
}
