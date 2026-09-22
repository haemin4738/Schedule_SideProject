package com.lifelog.domain.jobapplication;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface JobApplicationRepository {
    JobApplication save(JobApplication jobApplication);
    Optional<JobApplication> findById(Long id);
    Page<JobApplication> findByUserIdAndStatus(Long userId, JobApplicationStatus status, Pageable pageable);
    void delete(JobApplication jobApplication);
}
