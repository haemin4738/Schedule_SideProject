package com.lifelog.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lifelog.domain.event.EventCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record EventRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title,

        @Size(max = 10_000, message = "설명은 10,000자 이하여야 합니다.")
        String description,

        @NotNull(message = "시작 시간은 필수입니다.")
        LocalDateTime startAt,

        LocalDateTime endAt,
        boolean allDay,

        @Size(max = 255, message = "장소는 255자 이하여야 합니다.")
        String location,

        // null 은 '카테고리 기본 색'. 빈 문자열·형식이 틀린 값은 400 (웹·앱은 형식이 틀린 색을 null 로 보낸다)
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색상은 #RRGGBB 형식이어야 합니다.")
        String color,
        EventCategory eventCategory
) {

    /** 종료 시각은 시작 시각과 같거나 이후여야 한다 (종료 없음은 허용) */
    // 검증 전용 — JSON 속성·OpenAPI 스키마에 노출하지 않는다
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "종료 시간은 시작 시간보다 빠를 수 없습니다.")
    public boolean isEndAtNotBeforeStartAt() {
        return endAt == null || startAt == null || !endAt.isBefore(startAt);
    }
}
