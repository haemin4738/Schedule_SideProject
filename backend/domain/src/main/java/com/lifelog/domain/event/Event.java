package com.lifelog.domain.event;

import com.lifelog.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDateTime startAt;

    private LocalDateTime endAt;

    @Column(nullable = false)
    private boolean allDay = false;

    private String location;

    private String color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventCategory eventCategory = EventCategory.PERSONAL;

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

    public static Event create(User user, String title, String description,
                               LocalDateTime startAt, LocalDateTime endAt,
                               boolean allDay, String location, String color,
                               EventCategory eventCategory) {
        Event event = new Event();
        event.user = user;
        event.title = title;
        event.description = description;
        event.startAt = startAt;
        event.endAt = endAt;
        event.allDay = allDay;
        event.location = location;
        event.color = color;
        event.eventCategory = eventCategory != null ? eventCategory : EventCategory.PERSONAL;
        return event;
    }

    public void update(String title, String description, LocalDateTime startAt,
                       LocalDateTime endAt, boolean allDay, String location,
                       String color, EventCategory eventCategory) {
        this.title = title;
        this.description = description;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        this.location = location;
        this.color = color;
        this.eventCategory = eventCategory != null ? eventCategory : EventCategory.PERSONAL;
    }
}
