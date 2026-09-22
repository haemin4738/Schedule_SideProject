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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JobApplicationService 단위 테스트. Repository는 Mock으로 대체한다 (순수 단위 테스트).
 */
@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    @Mock
    private JobApplicationRepository jobApplicationRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JobApplicationService jobApplicationService;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        owner = User.create("owner@test.com", "encoded-pw", "소유자");
        other = User.create("other@test.com", "encoded-pw", "타인");
        setId(owner, 1L);
        setId(other, 2L);
    }

    // User.id는 @GeneratedValue라 테스트에서 직접 세팅 필요 (리플렉션)
    private void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private JobApplication ownedJobApplication(Long id, User user) {
        JobApplication jobApplication = JobApplication.create(user, "회사A", "백엔드 개발자",
                JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10),
                "https://example.com/posting", "메모");
        setId(jobApplication, id);
        return jobApplication;
    }

    private JobApplicationRequest sampleRequest() {
        return new JobApplicationRequest("회사A", "백엔드 개발자",
                JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10),
                "https://example.com/posting", "메모");
    }

    @Test
    void createJobApplication_whenUserExists_savesAndReturnsResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> {
            JobApplication j = invocation.getArgument(0);
            setId(j, 100L);
            return j;
        });

        JobApplicationResponse response = jobApplicationService.create(1L, sampleRequest());

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.companyName()).isEqualTo("회사A");
    }

    @Test
    void createJobApplication_whenUserNotFound_throwsNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobApplicationService.create(999L, sampleRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(jobApplicationRepository);
    }

    @Test
    void getJobApplication_whenOwnedByUser_returnsResponse() {
        JobApplication jobApplication = ownedJobApplication(10L, owner);
        when(jobApplicationRepository.findById(10L)).thenReturn(Optional.of(jobApplication));

        JobApplicationResponse response = jobApplicationService.get(1L, 10L);

        assertThat(response.id()).isEqualTo(10L);
    }

    @Test
    void getJobApplication_whenNotOwnedByUser_throwsForbidden() {
        JobApplication jobApplication = ownedJobApplication(10L, owner);
        when(jobApplicationRepository.findById(10L)).thenReturn(Optional.of(jobApplication));

        assertThatThrownBy(() -> jobApplicationService.get(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getJobApplication_whenNotFound_throwsNotFound() {
        when(jobApplicationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobApplicationService.get(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateJobApplication_whenOwnedByUser_updatesAndReturnsResponse() {
        JobApplication jobApplication = ownedJobApplication(10L, owner);
        when(jobApplicationRepository.findById(10L)).thenReturn(Optional.of(jobApplication));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplicationRequest updateRequest = new JobApplicationRequest("회사B", "프론트엔드 개발자",
                JobApplicationStatus.INTERVIEW_SCHEDULED, LocalDate.of(2026, 2, 1),
                "https://example.com/other", "변경된 메모");

        JobApplicationResponse response = jobApplicationService.update(1L, 10L, updateRequest);

        assertThat(response.companyName()).isEqualTo("회사B");
        assertThat(response.status()).isEqualTo(JobApplicationStatus.INTERVIEW_SCHEDULED);
    }

    @Test
    void updateJobApplication_whenNotOwnedByUser_throwsForbiddenAndDoesNotSave() {
        JobApplication jobApplication = ownedJobApplication(10L, owner);
        when(jobApplicationRepository.findById(10L)).thenReturn(Optional.of(jobApplication));

        assertThatThrownBy(() -> jobApplicationService.update(2L, 10L, sampleRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(jobApplicationRepository, never()).save(any());
    }

    @Test
    void deleteJobApplication_whenOwnedByUser_deletesJobApplication() {
        JobApplication jobApplication = ownedJobApplication(10L, owner);
        when(jobApplicationRepository.findById(10L)).thenReturn(Optional.of(jobApplication));

        jobApplicationService.delete(1L, 10L);

        verify(jobApplicationRepository).delete(jobApplication);
    }

    @Test
    void deleteJobApplication_whenNotOwnedByUser_throwsForbiddenAndDoesNotDelete() {
        JobApplication jobApplication = ownedJobApplication(10L, owner);
        when(jobApplicationRepository.findById(10L)).thenReturn(Optional.of(jobApplication));

        assertThatThrownBy(() -> jobApplicationService.delete(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(jobApplicationRepository, never()).delete(any());
    }

    @Test
    void listJobApplications_whenStatusNull_returnsAllAsPage() {
        List<JobApplication> jobApplications = List.of(ownedJobApplication(1L, owner), ownedJobApplication(2L, owner));
        Pageable pageable = PageRequest.of(0, 20);
        Page<JobApplication> page = new PageImpl<>(jobApplications, pageable, 2);
        when(jobApplicationRepository.findByUserIdAndStatus(1L, null, pageable)).thenReturn(page);

        Page<JobApplicationSummary> result = jobApplicationService.list(1L, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void listJobApplications_whenStatusGiven_delegatesFilterToRepository() {
        List<JobApplication> jobApplications = List.of(ownedJobApplication(1L, owner));
        Pageable pageable = PageRequest.of(0, 20);
        Page<JobApplication> page = new PageImpl<>(jobApplications, pageable, 1);
        when(jobApplicationRepository.findByUserIdAndStatus(1L, JobApplicationStatus.APPLIED, pageable)).thenReturn(page);

        Page<JobApplicationSummary> result = jobApplicationService.list(1L, JobApplicationStatus.APPLIED, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(jobApplicationRepository).findByUserIdAndStatus(1L, JobApplicationStatus.APPLIED, pageable);
    }

    @Test
    void listJobApplications_whenSecondPageRequested_returnsRemainderOnly() {
        List<JobApplication> jobApplications = List.of(ownedJobApplication(3L, owner));
        Pageable pageable = PageRequest.of(1, 2);
        Page<JobApplication> page = new PageImpl<>(jobApplications, pageable, 3);
        when(jobApplicationRepository.findByUserIdAndStatus(1L, null, pageable)).thenReturn(page);

        Page<JobApplicationSummary> result = jobApplicationService.list(1L, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(3L);
    }
}
