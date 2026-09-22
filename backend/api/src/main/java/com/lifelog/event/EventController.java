package com.lifelog.event;

import com.lifelog.common.dto.ApiResponse;
import com.lifelog.common.dto.PagedResponse;
import com.lifelog.event.dto.EventRequest;
import com.lifelog.event.dto.EventResponse;
import com.lifelog.event.dto.EventSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Validated
public class EventController {

    private final EventService eventService;

    @GetMapping
    public PagedResponse<EventSummary> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Max(100) int size) {
        return PagedResponse.ok(eventService.list(userId, from, to,
                PageRequest.of(page, size, Sort.by("startAt").ascending())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EventResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody EventRequest request) {
        return ApiResponse.ok(eventService.create(userId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<EventResponse> get(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return ApiResponse.ok(eventService.get(userId, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<EventResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody EventRequest request) {
        return ApiResponse.ok(eventService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        eventService.delete(userId, id);
        return ApiResponse.ok(null);
    }
}
