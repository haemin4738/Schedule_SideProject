package com.lifelog.event;

import com.lifelog.common.dto.ApiResponse;
import com.lifelog.common.dto.PagedResponse;
import com.lifelog.event.dto.EventRequest;
import com.lifelog.event.dto.EventResponse;
import com.lifelog.event.dto.EventSummary;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public PagedResponse<EventSummary> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return PagedResponse.ok(eventService.list(userId, from, to,
                PageRequest.of(page, size, Sort.by("startAt").ascending())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EventResponse> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody EventRequest request) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ApiResponse.ok(eventService.create(userId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<EventResponse> get(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ApiResponse.ok(eventService.get(userId, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<EventResponse> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody EventRequest request) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ApiResponse.ok(eventService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        Long userId = Long.parseLong(userDetails.getUsername());
        eventService.delete(userId, id);
        return ApiResponse.ok(null);
    }
}
