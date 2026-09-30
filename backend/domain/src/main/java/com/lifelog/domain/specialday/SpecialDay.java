package com.lifelog.domain.specialday;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * 공휴일·기념일·24절기 (사용자와 무관한 공용 데이터).
 * 연 단위로 통째로 교체되므로 변경 메서드가 없다.
 * (sol_date, kind, name) 유니크 인덱스가 날짜 범위 조회에도 쓰인다.
 */
@Entity
@Table(name = "special_days",
        uniqueConstraints = @UniqueConstraint(name = "uk_special_days_date_kind_name",
                columnNames = {"sol_date", "kind", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpecialDay {

    public static final int NAME_MAX_LENGTH = 100;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sol_date", nullable = false)
    private LocalDate solDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20, nullable = false)
    private SpecialDayKind kind;

    @Column(length = NAME_MAX_LENGTH, nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean holiday;

    public static SpecialDay create(LocalDate solDate, SpecialDayKind kind, String name, boolean holiday) {
        if (solDate == null || kind == null) {
            throw new IllegalArgumentException("특일 날짜와 종류는 필수입니다.");
        }
        if (name == null || name.isBlank() || name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("특일 이름은 1~" + NAME_MAX_LENGTH + "자여야 합니다.");
        }
        SpecialDay day = new SpecialDay();
        day.solDate = solDate;
        day.kind = kind;
        day.name = name;
        day.holiday = holiday;
        return day;
    }

    public static SpecialDay from(SpecialDayData data) {
        return create(data.date(), data.kind(), data.name(), data.holiday());
    }
}
