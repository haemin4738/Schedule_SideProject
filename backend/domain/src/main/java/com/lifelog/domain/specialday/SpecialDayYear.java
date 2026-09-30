package com.lifelog.domain.specialday;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 특일 데이터를 외부 출처에서 받아 저장한 연도와 마지막 동기화 시각 */
@Entity
@Table(name = "special_day_years")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpecialDayYear {

    @Id
    @Column(name = "sol_year")
    private Integer solYear;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    public static SpecialDayYear create(int solYear, LocalDateTime syncedAt) {
        if (syncedAt == null) {
            throw new IllegalArgumentException("동기화 시각은 필수입니다.");
        }
        SpecialDayYear year = new SpecialDayYear();
        year.solYear = solYear;
        year.syncedAt = syncedAt;
        return year;
    }

    public void markSynced(LocalDateTime syncedAt) {
        if (syncedAt == null) {
            throw new IllegalArgumentException("동기화 시각은 필수입니다.");
        }
        this.syncedAt = syncedAt;
    }
}
