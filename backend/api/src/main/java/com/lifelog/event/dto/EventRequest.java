package com.lifelog.event.dto;

import com.lifelog.domain.event.EventCategory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record EventRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title,

        String description,

        @NotNull(message = "시작 시간은 필수입니다.")
        LocalDateTime startAt,

        LocalDateTime endAt,
        boolean allDay,
        String location,
        String color,
        EventCategory eventCategory
) {

    /** 종료 시각은 시작 시각과 같거나 이후여야 한다 (종료 없음은 허용) */
    @AssertTrue(message = "종료 시간은 시작 시간보다 빠를 수 없습니다.")
    public boolean isEndAtNotBeforeStartAt() {
        return endAt == null || startAt == null || !endAt.isBefore(startAt);
    }
}
