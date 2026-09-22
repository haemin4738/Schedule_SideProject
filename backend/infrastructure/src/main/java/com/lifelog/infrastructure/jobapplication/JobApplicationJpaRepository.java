package com.lifelog.infrastructure.jobapplication;

import com.lifelog.domain.jobapplication.JobApplication;
import com.lifelog.domain.jobapplication.JobApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JobApplicationJpaRepository extends JpaRepository<JobApplication, Long> {

    @Query("SELECT j FROM JobApplication j WHERE j.user.id = :userId " +
           "AND (:status IS NULL OR j.status = :status)")
    Page<JobApplication> findByUserIdAndStatus(@Param("userId") Long userId,
                                               @Param("status") JobApplicationStatus status,
                                               Pageable pageable);
}
