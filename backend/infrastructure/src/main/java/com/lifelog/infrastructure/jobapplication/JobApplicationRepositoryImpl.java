package com.lifelog.infrastructure.jobapplication;

import com.lifelog.domain.jobapplication.JobApplication;
import com.lifelog.domain.jobapplication.JobApplicationRepository;
import com.lifelog.domain.jobapplication.JobApplicationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JobApplicationRepositoryImpl implements JobApplicationRepository {

    private final JobApplicationJpaRepository jpa;

    @Override public JobApplication save(JobApplication jobApplication) { return jpa.save(jobApplication); }
    @Override public Optional<JobApplication> findById(Long id) { return jpa.findById(id); }
    @Override public void delete(JobApplication jobApplication) { jpa.delete(jobApplication); }

    @Override
    public Page<JobApplication> findByUserIdAndStatus(Long userId, JobApplicationStatus status, Pageable pageable) {
        return jpa.findByUserIdAndStatus(userId, status, pageable);
    }
}
