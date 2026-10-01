package com.lifelog.domain.jobapplication;

import com.lifelog.domain.user.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JobApplicationTest {

    @Test
    void update_whenCalled_refreshesUpdatedAtBeforeFlush() {
        JobApplication application = JobApplication.create(User.create("a@test.com", "pw", "사용자"),
                "회사", "백엔드", JobApplicationStatus.APPLIED, LocalDate.of(2026, 10, 1), null, null);
        LocalDateTime before = LocalDateTime.now();

        application.update("회사", "백엔드", JobApplicationStatus.DOCUMENT_PASS, LocalDate.of(2026, 10, 1), null, null);

        // 수정 응답에 쓰이는 updatedAt 이 flush(@PreUpdate) 전에도 갱신돼 있어야 한다
        assertThat(application.getUpdatedAt()).isAfterOrEqualTo(before);
        assertThat(application.getStatus()).isEqualTo(JobApplicationStatus.DOCUMENT_PASS);
    }
}
