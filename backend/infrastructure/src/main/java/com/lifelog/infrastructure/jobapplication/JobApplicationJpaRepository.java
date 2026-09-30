package com.lifelog.infrastructure.jobapplication;

import com.lifelog.domain.jobapplication.JobApplication;
import com.lifelog.domain.jobapplication.JobApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

interface JobApplicationJpaRepository extends JpaRepository<JobApplication, Long> {

    @Query(value = "SELECT j FROM JobApplication j " +
                   "WHERE j.user.id = :userId " +
                   "AND (:status IS NULL OR j.status = :status) " +
                   "AND (:from IS NULL OR j.appliedAt >= :from) " +
                   "AND (:to IS NULL OR j.appliedAt <= :to) " +
                   "ORDER BY j.appliedAt DESC, j.id DESC",
           countQuery = "SELECT COUNT(j) FROM JobApplication j " +
                        "WHERE j.user.id = :userId " +
                        "AND (:status IS NULL OR j.status = :status) " +
                        "AND (:from IS NULL OR j.appliedAt >= :from) " +
                        "AND (:to IS NULL OR j.appliedAt <= :to)")
    Page<JobApplication> findByUserIdAndFilter(@Param("userId") Long userId,
                                               @Param("status") JobApplicationStatus status,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to,
                                               Pageable pageable);
}
