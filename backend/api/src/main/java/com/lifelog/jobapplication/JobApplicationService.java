package com.lifelog.jobapplication;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.jobapplication.JobApplication;
import com.lifelog.domain.jobapplication.JobApplicationRepository;
import com.lifelog.domain.jobapplication.JobApplicationStatus;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
import com.lifelog.jobapplication.dto.JobApplicationRequest;
import com.lifelog.jobapplication.dto.JobApplicationResponse;
import com.lifelog.jobapplication.dto.JobApplicationSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final UserRepository userRepository;

    @Transactional
    public JobApplicationResponse create(Long userId, JobApplicationRequest request) {
        User user = getUser(userId);
        JobApplication jobApplication = JobApplication.create(user, request.companyName(), request.position(),
                request.status(), request.appliedAt(), request.jobPostingUrl(), request.memo());
        return JobApplicationResponse.from(jobApplicationRepository.save(jobApplication));
    }

    @Transactional(readOnly = true)
    public Page<JobApplicationSummary> list(Long userId, JobApplicationStatus status, Pageable pageable) {
        return jobApplicationRepository.findByUserIdAndStatus(userId, status, pageable)
                .map(JobApplicationSummary::from);
    }

    @Transactional(readOnly = true)
    public JobApplicationResponse get(Long userId, Long id) {
        return JobApplicationResponse.from(getOwnedJobApplication(id, userId));
    }

    @Transactional
    public JobApplicationResponse update(Long userId, Long id, JobApplicationRequest request) {
        JobApplication jobApplication = getOwnedJobApplication(id, userId);
        jobApplication.update(request.companyName(), request.position(), request.status(),
                request.appliedAt(), request.jobPostingUrl(), request.memo());
        return JobApplicationResponse.from(jobApplicationRepository.save(jobApplication));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        JobApplication jobApplication = getOwnedJobApplication(id, userId);
        jobApplicationRepository.delete(jobApplication);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("사용자를 찾을 수 없습니다."));
    }

    private JobApplication getOwnedJobApplication(Long id, Long userId) {
        JobApplication jobApplication = jobApplicationRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("지원 내역을 찾을 수 없습니다."));
        if (!jobApplication.getUser().getId().equals(userId)) {
            throw BusinessException.forbidden("본인의 지원 내역만 접근할 수 있습니다.");
        }
        return jobApplication;
    }
}
