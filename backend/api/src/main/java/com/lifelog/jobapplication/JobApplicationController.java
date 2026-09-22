package com.lifelog.jobapplication;

import com.lifelog.common.dto.ApiResponse;
import com.lifelog.common.dto.PagedResponse;
import com.lifelog.domain.jobapplication.JobApplicationStatus;
import com.lifelog.jobapplication.dto.JobApplicationRequest;
import com.lifelog.jobapplication.dto.JobApplicationResponse;
import com.lifelog.jobapplication.dto.JobApplicationSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/job-applications")
@RequiredArgsConstructor
@Validated
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    @GetMapping
    public PagedResponse<JobApplicationSummary> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) JobApplicationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Max(100) int size) {
        return PagedResponse.ok(jobApplicationService.list(userId, status,
                PageRequest.of(page, size, Sort.by("appliedAt").descending())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<JobApplicationResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody JobApplicationRequest request) {
        return ApiResponse.ok(jobApplicationService.create(userId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<JobApplicationResponse> get(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return ApiResponse.ok(jobApplicationService.get(userId, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<JobApplicationResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody JobApplicationRequest request) {
        return ApiResponse.ok(jobApplicationService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        jobApplicationService.delete(userId, id);
        return ApiResponse.ok(null);
    }
}
