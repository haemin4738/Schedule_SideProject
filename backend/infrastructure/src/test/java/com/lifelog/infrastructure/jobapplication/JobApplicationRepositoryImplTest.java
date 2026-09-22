package com.lifelog.infrastructure.jobapplication;

import com.lifelog.domain.jobapplication.JobApplication;
import com.lifelog.domain.jobapplication.JobApplicationRepository;
import com.lifelog.domain.jobapplication.JobApplicationStatus;
import com.lifelog.domain.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JobApplicationRepositoryImpl / JobApplicationJpaRepository 통합 테스트.
 * 실제 MySQL(test profile, lifelog_test 스키마, ddl-auto=create-drop)을 사용한다 (Mock DB 금지 컨벤션 준수).
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.lifelog.domain")
@EnableJpaRepositories(basePackages = "com.lifelog.infrastructure")
@Import(JobApplicationRepositoryImplTest.TestConfig.class)
class JobApplicationRepositoryImplTest {

    @TestConfiguration
    @ComponentScan(basePackages = "com.lifelog.infrastructure.jobapplication")
    static class TestConfig {
    }

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private EntityManager entityManager;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        user1 = User.create("user1-" + System.nanoTime() + "@test.com", "encoded-pw", "유저1");
        user2 = User.create("user2-" + System.nanoTime() + "@test.com", "encoded-pw", "유저2");
        entityManager.persist(user1);
        entityManager.persist(user2);
        entityManager.flush();
    }

    private JobApplication newJobApplication(User user, String companyName, JobApplicationStatus status, LocalDate appliedAt) {
        return JobApplication.create(user, companyName, "백엔드 개발자", status, appliedAt,
                "https://example.com/posting", "메모");
    }

    @Test
    void save_whenValidJobApplication_persistsAndAssignsId() {
        JobApplication jobApplication = newJobApplication(user1, "회사A", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10));

        JobApplication saved = jobApplicationRepository.save(jobApplication);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCompanyName()).isEqualTo("회사A");
        assertThat(saved.getUser().getId()).isEqualTo(user1.getId());
    }

    @Test
    void findById_whenExists_returnsJobApplication() {
        JobApplication saved = jobApplicationRepository.save(
                newJobApplication(user1, "회사A", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10)));
        entityManager.flush();
        entityManager.clear();

        Optional<JobApplication> found = jobApplicationRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCompanyName()).isEqualTo("회사A");
    }

    @Test
    void findById_whenNotExists_returnsEmpty() {
        Optional<JobApplication> found = jobApplicationRepository.findById(999_999L);

        assertThat(found).isEmpty();
    }

    @Test
    void delete_whenCalled_removesJobApplication() {
        JobApplication saved = jobApplicationRepository.save(
                newJobApplication(user1, "삭제될 회사", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10)));
        entityManager.flush();
        Long id = saved.getId();

        jobApplicationRepository.delete(saved);
        entityManager.flush();
        entityManager.clear();

        assertThat(jobApplicationRepository.findById(id)).isEmpty();
    }

    @Test
    void findByUserIdAndStatus_whenStatusNull_returnsAllForUser() {
        jobApplicationRepository.save(newJobApplication(user1, "회사A", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 5)));
        jobApplicationRepository.save(newJobApplication(user1, "회사B", JobApplicationStatus.INTERVIEW_SCHEDULED, LocalDate.of(2026, 1, 10)));
        jobApplicationRepository.save(newJobApplication(user2, "다른유저 회사", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10)));
        entityManager.flush();
        entityManager.clear();

        Page<JobApplication> page = jobApplicationRepository.findByUserIdAndStatus(
                user1.getId(), null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(j -> j.getUser().getId().equals(user1.getId()));
    }

    @Test
    void findByUserIdAndStatus_whenStatusGiven_filtersByStatus() {
        jobApplicationRepository.save(newJobApplication(user1, "회사A", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 5)));
        jobApplicationRepository.save(newJobApplication(user1, "회사B", JobApplicationStatus.INTERVIEW_SCHEDULED, LocalDate.of(2026, 1, 10)));
        jobApplicationRepository.save(newJobApplication(user1, "회사C", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 15)));
        entityManager.flush();
        entityManager.clear();

        Page<JobApplication> page = jobApplicationRepository.findByUserIdAndStatus(
                user1.getId(), JobApplicationStatus.APPLIED, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(j -> j.getStatus() == JobApplicationStatus.APPLIED);
    }

    @Test
    void findByUserIdAndStatus_whenPaginated_returnsRequestedPageOnly() {
        jobApplicationRepository.save(newJobApplication(user1, "회사A", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 5)));
        jobApplicationRepository.save(newJobApplication(user1, "회사B", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10)));
        jobApplicationRepository.save(newJobApplication(user1, "회사C", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 15)));
        entityManager.flush();
        entityManager.clear();

        Pageable firstPage = PageRequest.of(0, 2);
        Page<JobApplication> page = jobApplicationRepository.findByUserIdAndStatus(user1.getId(), null, firstPage);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);

        Pageable secondPage = PageRequest.of(1, 2);
        Page<JobApplication> page2 = jobApplicationRepository.findByUserIdAndStatus(user1.getId(), null, secondPage);

        assertThat(page2.getContent()).hasSize(1);
        assertThat(page2.getNumber()).isEqualTo(1);
    }
}
