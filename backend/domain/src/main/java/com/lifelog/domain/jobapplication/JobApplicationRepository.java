package com.lifelog.domain.jobapplication;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Optional;

public interface JobApplicationRepository {
    JobApplication save(JobApplication jobApplication);
    Optional<JobApplication> findById(Long id);
    /** status/from/to가 null이면 해당 조건 미적용. from/to 양끝 포함(appliedAt 기준). appliedAt DESC, id DESC 정렬. */
    Page<JobApplication> findByUserIdAndFilter(Long userId, JobApplicationStatus status,
                                               LocalDate from, LocalDate to, Pageable pageable);
    void delete(JobApplication jobApplication);
}
