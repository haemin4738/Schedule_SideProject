package com.lifelog.jobapplication.dto;

import com.lifelog.domain.jobapplication.JobApplication;
import com.lifelog.domain.jobapplication.JobApplicationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record JobApplicationResponse(
        Long id, String companyName, String position,
        JobApplicationStatus status, LocalDate appliedAt,
        String jobPostingUrl, String memo,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static JobApplicationResponse from(JobApplication j) {
        return new JobApplicationResponse(j.getId(), j.getCompanyName(), j.getPosition(),
                j.getStatus(), j.getAppliedAt(), j.getJobPostingUrl(), j.getMemo(),
                j.getCreatedAt(), j.getUpdatedAt());
    }
}
