package com.lifelog.infrastructure.jobapplication;

import com.lifelog.domain.jobapplication.JobApplication;
import com.lifelog.domain.jobapplication.JobApplicationRepository;
import com.lifelog.domain.jobapplication.JobApplicationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JobApplicationRepositoryImpl implements JobApplicationRepository {

    private final JobApplicationJpaRepository jpa;

    @Override public JobApplication save(JobApplication jobApplication) { return jpa.save(jobApplication); }
    @Override public Optional<JobApplication> findById(Long id) { return jpa.findById(id); }
    @Override public void delete(JobApplication jobApplication) { jpa.delete(jobApplication); }

    @Override
    public Page<JobApplication> findByUserIdAndFilter(Long userId, JobApplicationStatus status,
                                                      LocalDate from, LocalDate to, Pageable pageable) {
        // 정렬은 쿼리에 고정(appliedAt DESC, id DESC) — 호출자의 Sort는 무시해 중복/충돌 방지
        Pageable unsorted = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
                : pageable;
        return jpa.findByUserIdAndFilter(userId, status, from, to, unsorted);
    }
}
