package com.lifelog.domain.expense;

import com.lifelog.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "expense_categories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_expense_categories_user_type_name",
                columnNames = {"user_id", "type", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseCategory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20, nullable = false, updatable = false)
    private ExpenseType type;

    @Column(nullable = false, length = 50)
    private String name;

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

    public static ExpenseCategory create(User user, ExpenseType type, String name) {
        ExpenseCategory category = new ExpenseCategory();
        category.user = user;
        category.type = type;
        category.name = name;
        return category;
    }

    public void rename(String name) {
        this.name = name;
    }
}
