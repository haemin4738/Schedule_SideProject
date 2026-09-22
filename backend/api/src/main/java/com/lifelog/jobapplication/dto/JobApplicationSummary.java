package com.lifelog.jobapplication.dto;

import com.lifelog.domain.jobapplication.JobApplication;
import com.lifelog.domain.jobapplication.JobApplicationStatus;

import java.time.LocalDate;

public record JobApplicationSummary(
        Long id, String companyName, String position,
        JobApplicationStatus status, LocalDate appliedAt
) {
    public static JobApplicationSummary from(JobApplication j) {
        return new JobApplicationSummary(j.getId(), j.getCompanyName(), j.getPosition(),
                j.getStatus(), j.getAppliedAt());
    }
}
