package com.lifelog.domain.expense;

import com.lifelog.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses",
        indexes = {
                @Index(name = "idx_expenses_user_date", columnList = "user_id, transaction_date"),
                @Index(name = "idx_expenses_category", columnList = "category_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ExpenseCategory category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20, nullable = false)
    private ExpenseType type;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(length = 200)
    private String description;

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

    public static Expense create(User user, ExpenseCategory category, ExpenseType type, Long amount,
                                 LocalDate transactionDate, String description, String memo) {
        validate(category, type, amount);
        Expense expense = new Expense();
        expense.user = user;
        expense.category = category;
        expense.type = type;
        expense.amount = amount;
        expense.transactionDate = transactionDate;
        expense.description = description;
        expense.memo = memo;
        return expense;
    }

    public void update(ExpenseCategory category, ExpenseType type, Long amount,
                       LocalDate transactionDate, String description, String memo) {
        validate(category, type, amount);
        // @PreUpdate 는 flush 때 실행돼 수정 응답에 이전 updatedAt 이 나가므로 여기서도 갱신한다
        this.updatedAt = LocalDateTime.now();
        this.category = category;
        this.type = type;
        this.amount = amount;
        this.transactionDate = transactionDate;
        this.description = description;
        this.memo = memo;
    }

    private static void validate(ExpenseCategory category, ExpenseType type, Long amount) {
        if (category == null || type == null || category.getType() != type) {
            throw new IllegalArgumentException("카테고리 유형과 내역 유형이 일치하지 않습니다.");
        }
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다.");
        }
    }
}
