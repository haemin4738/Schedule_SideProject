package com.lifelog.jobapplication.dto;

import com.lifelog.domain.jobapplication.JobApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record JobApplicationRequest(
        @NotBlank(message = "회사명은 필수입니다.")
        @Size(max = 200, message = "회사명은 200자 이하여야 합니다.")
        String companyName,

        @NotBlank(message = "지원 직무는 필수입니다.")
        @Size(max = 200, message = "지원 직무는 200자 이하여야 합니다.")
        String position,

        @NotNull(message = "지원 상태는 필수입니다.")
        JobApplicationStatus status,

        @NotNull(message = "지원일은 필수입니다.")
        LocalDate appliedAt,

        @Size(max = 500, message = "채용공고 URL은 500자 이하여야 합니다.")
        String jobPostingUrl,

        String memo
) {}
