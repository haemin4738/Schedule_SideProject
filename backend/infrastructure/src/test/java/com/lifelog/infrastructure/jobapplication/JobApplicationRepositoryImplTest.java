package com.lifelog.infrastructure.jobapplication;

import com.lifelog.domain.jobapplication.JobApplication;
import com.lifelog.domain.jobapplication.JobApplicationRepository;
import com.lifelog.domain.jobapplication.JobApplicationStatus;
import com.lifelog.domain.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    void findByUserIdAndFilter_whenStatusNull_returnsAllForUser() {
        jobApplicationRepository.save(newJobApplication(user1, "회사A", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 5)));
        jobApplicationRepository.save(newJobApplication(user1, "회사B", JobApplicationStatus.INTERVIEW_SCHEDULED, LocalDate.of(2026, 1, 10)));
        jobApplicationRepository.save(newJobApplication(user2, "다른유저 회사", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10)));
        entityManager.flush();
        entityManager.clear();

        Page<JobApplication> page = jobApplicationRepository.findByUserIdAndFilter(
                user1.getId(), null, null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(j -> j.getUser().getId().equals(user1.getId()));
    }

    @Test
    void findByUserIdAndFilter_whenStatusGiven_filtersByStatus() {
        jobApplicationRepository.save(newJobApplication(user1, "회사A", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 5)));
        jobApplicationRepository.save(newJobApplication(user1, "회사B", JobApplicationStatus.INTERVIEW_SCHEDULED, LocalDate.of(2026, 1, 10)));
        jobApplicationRepository.save(newJobApplication(user1, "회사C", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 15)));
        entityManager.flush();
        entityManager.clear();

        Page<JobApplication> page = jobApplicationRepository.findByUserIdAndFilter(
                user1.getId(), JobApplicationStatus.APPLIED, null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(j -> j.getStatus() == JobApplicationStatus.APPLIED);
    }

    @Test
    void findByUserIdAndFilter_whenPaginated_returnsRequestedPageOnly() {
        jobApplicationRepository.save(newJobApplication(user1, "회사A", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 5)));
        jobApplicationRepository.save(newJobApplication(user1, "회사B", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10)));
        jobApplicationRepository.save(newJobApplication(user1, "회사C", JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 15)));
        entityManager.flush();
        entityManager.clear();

        Pageable firstPage = PageRequest.of(0, 2);
        Page<JobApplication> page = jobApplicationRepository.findByUserIdAndFilter(user1.getId(), null, null, null, firstPage);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);

        Pageable secondPage = PageRequest.of(1, 2);
        Page<JobApplication> page2 = jobApplicationRepository.findByUserIdAndFilter(user1.getId(), null, null, null, secondPage);

        assertThat(page2.getContent()).hasSize(1);
        assertThat(page2.getNumber()).isEqualTo(1);
    }

    @Test
    void findByUserIdAndFilter_whenFromToGiven_includesBothEnds() {
        jobApplicationRepository.save(newJobApplication(user1, "이전", JobApplicationStatus.APPLIED, LocalDate.of(2026, 8, 31)));
        jobApplicationRepository.save(newJobApplication(user1, "시작일", JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 1)));
        jobApplicationRepository.save(newJobApplication(user1, "중간", JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 15)));
        jobApplicationRepository.save(newJobApplication(user1, "종료일", JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 30)));
        jobApplicationRepository.save(newJobApplication(user1, "이후", JobApplicationStatus.APPLIED, LocalDate.of(2026, 10, 1)));
        jobApplicationRepository.save(newJobApplication(user2, "타인", JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 15)));
        entityManager.flush();
        entityManager.clear();

        Page<JobApplication> page = jobApplicationRepository.findByUserIdAndFilter(user1.getId(), null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(JobApplication::getCompanyName)
                .containsExactly("종료일", "중간", "시작일");
    }

    @Test
    void findByUserIdAndFilter_whenOnlyFromGiven_appliesLowerBoundOnly() {
        jobApplicationRepository.save(newJobApplication(user1, "이전", JobApplicationStatus.APPLIED, LocalDate.of(2026, 8, 31)));
        jobApplicationRepository.save(newJobApplication(user1, "시작일", JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 1)));
        jobApplicationRepository.save(newJobApplication(user1, "먼 미래", JobApplicationStatus.APPLIED, LocalDate.of(2027, 3, 1)));
        entityManager.flush();
        entityManager.clear();

        Page<JobApplication> page = jobApplicationRepository.findByUserIdAndFilter(user1.getId(), null,
                LocalDate.of(2026, 9, 1), null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(JobApplication::getCompanyName)
                .containsExactly("먼 미래", "시작일");
    }

    @Test
    void findByUserIdAndFilter_whenOnlyToGiven_appliesUpperBoundOnly() {
        jobApplicationRepository.save(newJobApplication(user1, "먼 과거", JobApplicationStatus.APPLIED, LocalDate.of(2020, 1, 1)));
        jobApplicationRepository.save(newJobApplication(user1, "종료일", JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 30)));
        jobApplicationRepository.save(newJobApplication(user1, "이후", JobApplicationStatus.APPLIED, LocalDate.of(2026, 10, 1)));
        entityManager.flush();
        entityManager.clear();

        Page<JobApplication> page = jobApplicationRepository.findByUserIdAndFilter(user1.getId(), null,
                null, LocalDate.of(2026, 9, 30), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(JobApplication::getCompanyName)
                .containsExactly("종료일", "먼 과거");
    }

    @Test
    void findByUserIdAndFilter_whenStatusAndRangeGiven_appliesBoth() {
        jobApplicationRepository.save(newJobApplication(user1, "기간내 지원", JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 10)));
        jobApplicationRepository.save(newJobApplication(user1, "기간내 면접", JobApplicationStatus.INTERVIEW_SCHEDULED, LocalDate.of(2026, 9, 11)));
        jobApplicationRepository.save(newJobApplication(user1, "기간외 지원", JobApplicationStatus.APPLIED, LocalDate.of(2026, 10, 10)));
        entityManager.flush();
        entityManager.clear();

        Page<JobApplication> page = jobApplicationRepository.findByUserIdAndFilter(user1.getId(),
                JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(JobApplication::getCompanyName).containsExactly("기간내 지원");
    }

    @Test
    void findByUserIdAndFilter_whenAppliedAtTies_ordersByIdDesc() {
        JobApplication first = jobApplicationRepository.save(
                newJobApplication(user1, "먼저", JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 10)));
        JobApplication second = jobApplicationRepository.save(
                newJobApplication(user1, "나중", JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 10)));
        JobApplication older = jobApplicationRepository.save(
                newJobApplication(user1, "과거", JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 1)));
        entityManager.flush();
        entityManager.clear();

        Page<JobApplication> page = jobApplicationRepository.findByUserIdAndFilter(user1.getId(), null,
                null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(JobApplication::getId)
                .containsExactly(second.getId(), first.getId(), older.getId());
    }

    @Test
    void findByUserIdAndFilter_whenCallerPassesSort_ignoresItAndKeepsFixedOrder() {
        JobApplication early = jobApplicationRepository.save(
                newJobApplication(user1, "A", JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 1)));
        JobApplication late = jobApplicationRepository.save(
                newJobApplication(user1, "B", JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 20)));
        entityManager.flush();
        entityManager.clear();

        Page<JobApplication> page = jobApplicationRepository.findByUserIdAndFilter(user1.getId(), null,
                null, null, PageRequest.of(0, 10, Sort.by("appliedAt").ascending()));

        assertThat(page.getContent()).extracting(JobApplication::getId)
                .containsExactly(late.getId(), early.getId());
    }

    @Test
    void findByUserIdAndFilter_whenUnpagedWithSort_ignoresSortAndReturnsAllInFixedOrder() {
        JobApplication early = jobApplicationRepository.save(
                newJobApplication(user1, "A", JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 1)));
        JobApplication late = jobApplicationRepository.save(
                newJobApplication(user1, "B", JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 20)));
        entityManager.flush();
        entityManager.clear();

        Page<JobApplication> page = jobApplicationRepository.findByUserIdAndFilter(user1.getId(), null,
                // 쿼리에 없는 속성 — Sort 가 버려지지 않으면 ORDER BY 에 덧붙어 쿼리 해석 단계에서 실패한다
                null, null, Pageable.unpaged(Sort.by("notExistingProperty")));

        assertThat(page.getContent()).extracting(JobApplication::getId)
                .containsExactly(late.getId(), early.getId());
    }

    @Test
    void findByUserIdAndFilter_whenPagedAcrossTiedDates_returnsEachRowExactlyOnce() {
        for (int i = 0; i < 5; i++) {
            jobApplicationRepository.save(
                    newJobApplication(user1, "동일일자" + i, JobApplicationStatus.APPLIED, LocalDate.of(2026, 9, 10)));
        }
        entityManager.flush();
        entityManager.clear();

        List<Long> ids = new ArrayList<>();
        for (int p = 0; p < 3; p++) {
            ids.addAll(jobApplicationRepository.findByUserIdAndFilter(user1.getId(), null, null, null,
                    PageRequest.of(p, 2)).getContent().stream().map(JobApplication::getId).toList());
        }

        assertThat(ids).hasSize(5).doesNotHaveDuplicates();
        assertThat(ids).isSortedAccordingTo(Comparator.reverseOrder());
    }
}
