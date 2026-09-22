package com.lifelog.infrastructure.event;

import com.lifelog.domain.event.Event;
import com.lifelog.domain.event.EventCategory;
import com.lifelog.domain.event.EventRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EventRepositoryImpl / EventJpaRepository 통합 테스트.
 * 실제 MySQL(test profile, lifelog_test 스키마, ddl-auto=create-drop)을 사용한다 (Mock DB 금지 컨벤션 준수).
 * infrastructure 모듈에는 @SpringBootApplication이 없으므로 @DataJpaTest의 컴포넌트 스캔 기준점을
 * 이 테스트 클래스가 속한 패키지로 삼되, EventRepositoryImpl(@Repository)과 JPA 엔티티를 명시적으로 포함시킨다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.lifelog.domain")
@EnableJpaRepositories(basePackages = "com.lifelog.infrastructure")
@Import(EventRepositoryImplTest.TestConfig.class)
class EventRepositoryImplTest {

    @TestConfiguration
    @ComponentScan(basePackages = "com.lifelog.infrastructure.event")
    static class TestConfig {
    }

    @Autowired
    private EventRepository eventRepository;

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

    private Event newEvent(User user, String title, LocalDateTime startAt) {
        return Event.create(user, title, "설명", startAt, startAt.plusHours(1),
                false, "장소", "#FFFFFF", EventCategory.PERSONAL);
    }

    @Test
    void save_whenValidEvent_persistsAndAssignsId() {
        Event event = newEvent(user1, "회의", LocalDateTime.of(2026, 1, 10, 10, 0));

        Event saved = eventRepository.save(event);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("회의");
        assertThat(saved.getUser().getId()).isEqualTo(user1.getId());
    }

    @Test
    void findById_whenExists_returnsEvent() {
        Event saved = eventRepository.save(newEvent(user1, "회의", LocalDateTime.of(2026, 1, 10, 10, 0)));
        entityManager.flush();
        entityManager.clear();

        Optional<Event> found = eventRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("회의");
    }

    @Test
    void findById_whenNotExists_returnsEmpty() {
        Optional<Event> found = eventRepository.findById(999_999L);

        assertThat(found).isEmpty();
    }

    @Test
    void delete_whenCalled_removesEvent() {
        Event saved = eventRepository.save(newEvent(user1, "삭제될 일정", LocalDateTime.of(2026, 1, 10, 10, 0)));
        entityManager.flush();
        Long id = saved.getId();

        eventRepository.delete(saved);
        entityManager.flush();
        entityManager.clear();

        assertThat(eventRepository.findById(id)).isEmpty();
    }

    @Test
    void findByUserIdAndDateRange_whenFromAndToProvided_filtersAndPaginates() {
        eventRepository.save(newEvent(user1, "1월5일", LocalDateTime.of(2026, 1, 5, 9, 0)));
        eventRepository.save(newEvent(user1, "1월10일", LocalDateTime.of(2026, 1, 10, 9, 0)));
        eventRepository.save(newEvent(user1, "1월20일", LocalDateTime.of(2026, 1, 20, 9, 0)));
        eventRepository.save(newEvent(user1, "2월1일(범위밖)", LocalDateTime.of(2026, 2, 1, 9, 0)));
        eventRepository.save(newEvent(user2, "다른유저 1월10일", LocalDateTime.of(2026, 1, 10, 9, 0)));
        entityManager.flush();
        entityManager.clear();

        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 1, 31, 23, 59);
        Pageable pageable = PageRequest.of(0, 2);

        Page<Event> page = eventRepository.findByUserIdAndDateRange(user1.getId(), from, to, pageable);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent())
                .allMatch(e -> e.getUser().getId().equals(user1.getId()));
    }

    @Test
    void findByUserIdAndDateRange_whenSecondPageRequested_returnsRemainder() {
        eventRepository.save(newEvent(user1, "1월5일", LocalDateTime.of(2026, 1, 5, 9, 0)));
        eventRepository.save(newEvent(user1, "1월10일", LocalDateTime.of(2026, 1, 10, 9, 0)));
        eventRepository.save(newEvent(user1, "1월20일", LocalDateTime.of(2026, 1, 20, 9, 0)));
        entityManager.flush();
        entityManager.clear();

        Page<Event> page = eventRepository.findByUserIdAndDateRange(
                user1.getId(), null, null, PageRequest.of(1, 2));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getNumber()).isEqualTo(1);
    }

    @Test
    void findByUserIdAndDateRange_whenFromAndToNull_returnsAllForUser() {
        eventRepository.save(newEvent(user1, "1월5일", LocalDateTime.of(2026, 1, 5, 9, 0)));
        eventRepository.save(newEvent(user1, "3월1일", LocalDateTime.of(2026, 3, 1, 9, 0)));
        eventRepository.save(newEvent(user2, "다른유저", LocalDateTime.of(2026, 1, 10, 9, 0)));
        entityManager.flush();
        entityManager.clear();

        Page<Event> page = eventRepository.findByUserIdAndDateRange(
                user1.getId(), null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findAllByUserIdAndDateRange_whenRangeGiven_returnsSortedByStartAtAscending() {
        eventRepository.save(newEvent(user1, "늦은 일정", LocalDateTime.of(2026, 1, 20, 9, 0)));
        eventRepository.save(newEvent(user1, "이른 일정", LocalDateTime.of(2026, 1, 5, 9, 0)));
        eventRepository.save(newEvent(user1, "중간 일정", LocalDateTime.of(2026, 1, 10, 9, 0)));
        entityManager.flush();
        entityManager.clear();

        List<Event> events = eventRepository.findAllByUserIdAndDateRange(
                user1.getId(), LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 31, 0, 0));

        assertThat(events).hasSize(3);
        assertThat(events).extracting(Event::getTitle)
                .containsExactly("이른 일정", "중간 일정", "늦은 일정");
    }

    @Test
    void findAllByUserIdAndDateRange_whenFromAndToNull_returnsAllForUser() {
        eventRepository.save(newEvent(user1, "1월5일", LocalDateTime.of(2026, 1, 5, 9, 0)));
        eventRepository.save(newEvent(user1, "3월1일", LocalDateTime.of(2026, 3, 1, 9, 0)));
        entityManager.flush();
        entityManager.clear();

        List<Event> events = eventRepository.findAllByUserIdAndDateRange(user1.getId(), null, null);

        assertThat(events).hasSize(2);
    }

    @Test
    void findAllByUserIdAndDateRange_whenNoMatchingEvents_returnsEmptyList() {
        eventRepository.save(newEvent(user1, "1월5일", LocalDateTime.of(2026, 1, 5, 9, 0)));
        entityManager.flush();
        entityManager.clear();

        List<Event> events = eventRepository.findAllByUserIdAndDateRange(
                user1.getId(), LocalDateTime.of(2027, 1, 1, 0, 0), LocalDateTime.of(2027, 12, 31, 0, 0));

        assertThat(events).isEmpty();
    }
}
