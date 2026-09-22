package com.lifelog.domain.jobapplication;

import com.lifelog.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobApplication {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String companyName;

    @Column(nullable = false, length = 200)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobApplicationStatus status;

    @Column(nullable = false)
    private LocalDate appliedAt;

    private String jobPostingUrl;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static JobApplication create(User user, String companyName, String position,
                                        JobApplicationStatus status, LocalDate appliedAt,
                                        String jobPostingUrl, String memo) {
        JobApplication jobApplication = new JobApplication();
        jobApplication.user = user;
        jobApplication.companyName = companyName;
        jobApplication.position = position;
        jobApplication.status = status;
        jobApplication.appliedAt = appliedAt;
        jobApplication.jobPostingUrl = jobPostingUrl;
        jobApplication.memo = memo;
        return jobApplication;
    }

    public void update(String companyName, String position, JobApplicationStatus status,
                       LocalDate appliedAt, String jobPostingUrl, String memo) {
        this.companyName = companyName;
        this.position = position;
        this.status = status;
        this.appliedAt = appliedAt;
        this.jobPostingUrl = jobPostingUrl;
        this.memo = memo;
    }
}
